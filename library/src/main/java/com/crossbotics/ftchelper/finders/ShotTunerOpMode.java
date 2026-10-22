package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.drive.DriverControls;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.launcher.Launcher;
import com.crossbotics.ftchelper.launcher.ShotTable;
import com.crossbotics.ftchelper.mechanism.AngleDevice;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.vision.TargetObservation;
import com.crossbotics.ftchelper.vision.TargetSource;

/**
 * Builds the shot table: the flywheel speed (and hood angle) for each distance. Park the robot at a
 * distance and shoot 3-5 at a speed, marking each shot HIT or MISS; try the next speed; save: the
 * tuner keeps the speed with the best hit ratio at that distance. Do it at 4-6 distances; it writes
 * the {@code ShotTable} code and the {@code Tunables.java} line for you.
 *
 * <p>The distance comes from the camera (the same reading the launcher uses in a match), averaged
 * over half a second of fresh pictures so one noisy frame does not go into the table. When the camera
 * does not see the goal (or there is none), from the odometry: where the robot is and
 * {@code Tunables.Aim.goalX, goalY} (drive to spots on the field; the robot starts at
 * {@link #startSpotIn()}). With neither, you set it by hand. With your robot settings it can also
 * drive (sticks), so you can move between distances without picking the robot up.
 *
 * <p>The speed starts from the table you already have at this distance (or {@code Tunables.Launcher.speedRpm}),
 * so you only fine-tune. Each shot keeps what it was FIRED with: the speed asked for, the hood angle, the
 * distance, and the flywheel speed MEASURED as the piece was fed. So a shot marked after you changed the
 * speed or the hood still counts for the speed and hood it really had, and the saved point gets the hood
 * the winning shots were fired with. A shot fed while the flywheel was well under the speed asked for
 * (it "sagged": slowed by the last shot or a low battery) is flagged (wait for Ready, charge the battery).
 * The launcher's speed trim is never used here (tables are recorded clean); its battery compensation
 * (with {@code kF} in Tunables) stays on.
 *
 * <p><b>One table per game piece.</b> With two or more tables in {@code Tunables.Launcher}
 * ({@code speedByDistancePollen}, {@code speedByDistanceNectar}), choose the one you are tuning (Y until
 * "> Table", then the D-pad): each keeps its own points and gets its own line to copy.
 *
 * <pre>
 * Gamepad 1:
 *   Y              = what the D-pad changes: SPEED, HOOD (if any), DISTANCE (no camera, no odometry),
 *                    TABLE (two or more tables)
 *   D-pad up/down  = big step (100 RPM / a fifth of the hood's range, 5 deg for 25 / 6 in),
 *                    right/left = small step (10 RPM / a fiftieth of the hood's range, 0.5 deg / 1 in)
 *                    (TABLE: left/right = the other table)
 *   LB             = flywheel on/off (off at START; off also stops shooting)
 *   RB             = shoot one         hold RT = keep shooting (let go to stop)
 *   A              = the oldest shot not marked yet was a HIT          B = it was a MISS
 *   left stick click  = undo the last HIT / MISS (that shot waits to be marked again)
 *   right stick click = drop the last shot not marked (a "shot" that never was: the wheel dipped)
 *   X              = save: the speed with the best hit ratio (hits / shots) at this distance
 *                    (replaces one within 1 in; shots not marked yet: mark them first; none marked
 *                    at all: X again within 3 s saves the speed now)
 *   hold BACK 1 s  = delete the saved point here (held, so it is not pressed by mistake)
 *   sticks         = drive (with robotConfig()), left trigger = slow
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * KEEP PEOPLE OUT OF THE LINE OF FIRE; eye protection on.
 *
 * <p>It shows each table twice: as {@code ShotTable} code and, for the robot in five files, as the
 * line for {@code Tunables.java}: {@code public static double[][] speedByDistance = {{48, 2550}, {72, 2900}};   // in Tunables.Launcher}.
 * Copy each line into {@code Tunables.java} (replace the line that is there): the robot uses the
 * file, not this screen. The lines are also saved on the Control Hub when you press STOP.
 *
 * <p>In your TeamCode, the robot in five files (next to Robot.java). It uses the robot's launcher,
 * camera and drive, with their numbers from Tunables.java:
 * <pre>{@code
 * @TeleOp(name = "Shot Tuner", group = "Finders")
 * public class ShotTuner extends ShotTunerOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 * With the older helpers instead:
 * <pre>{@code
 * @TeleOp(name = "Shot Tuner", group = "Finders")
 * public class ShotTuner extends ShotTunerOpMode {
 *     @Override protected Launcher createLauncher() { return new Robot(hardwareMap).launcher; }
 *     @Override protected TargetSource createCamera() { return WebcamTags.create(hardwareMap, "Webcam 1"); }
 *     @Override protected RobotConfig robotConfig() { return new RobotSettings(); }
 * }
 * }</pre>
 *
 * Docs: docs/modules/finders.md. Guide: docs/guides/finders.md
 */
public abstract class ShotTunerOpMode extends FinderOpMode {

    /** Camera pictures older than this are not used for the distance (milliseconds). */
    public static final double MAX_TARGET_AGE_MS = 250;

    /** The marked shots start again when the distance moves more than this from where they began (inches). */
    public static final double SPOT_MOVE_IN = ShotPoints.Trials.SPOT_MOVE_IN;

    /** Hold BACK this long to delete the saved point here (seconds). */
    public static final double DELETE_HOLD_SEC = 1.0;

    /**
     * How far under the speed asked for the flywheel may be at a shot before the tuner calls it
     * "sagged" (RPM): the larger of 60 and 3% of the speed.
     *
     * @param setRpm the speed asked for
     */
    public static double sagLimitRpm(double setRpm) {
        return Math.max(60, 0.03 * setRpm);
    }

    // ----------------------------- you fill these in -----------------------------

    /**
     * Your robot in five files: {@code return new Robot();}. The tuner sets it up and uses its first
     * launcher, its first camera and its drive. Its other parts are set up but stay still. Default
     * null: then give {@link #createLauncher()} (and, if you have them, the camera and robotConfig()).
     */
    protected RobotDefinition robot() {
        return null;
    }

    /**
     * Your launcher (flywheel, feeder, hood), set up as in your TeleOp. Default: the first launcher
     * of {@link #robot()}. Give one of the two.
     */
    protected Launcher createLauncher() {
        com.crossbotics.ftchelper.robot.Launcher module = robotLauncher();
        if (module == null) {
            throw new IllegalStateException(robotDef == null
                    ? "The shot tuner needs your robot: add \"@Override protected RobotDefinition robot() { return new Robot(); }\" (or createLauncher())."
                    : "The shot tuner found no Launcher in Robot.java.");
        }
        if (!module.isPresent()) {
            throw new IllegalStateException("The shot tuner: \"" + module.deviceName() + "\" is not in the robot configuration ("
                    + module.name() + " is optional in Robot.java, so it is off). Add it to the configuration first.");
        }
        return module.part();
    }

    /**
     * Your camera, or null to set the distance by hand. It is started for you. Default: the first
     * camera of {@link #robot()}, or null.
     */
    protected TargetSource createCamera() {
        if (robotDef == null) return null;
        for (Module<?> m : robotDef.modules()) if (m instanceof Camera) return ((Camera) m).source();
        return null;
    }

    /** Which target the distance is measured to. Default: the first one the camera sees with a distance. */
    protected TargetObservation findTarget(TargetSource camera) {
        for (TargetObservation o : camera.targets()) if (o.hasDistance()) return o;
        return null;
    }

    /** Your robot settings, to drive with the sticks. Default: the drive of {@link #robot()}, or null = no driving. */
    protected RobotConfig robotConfig() {
        return robotDef == null ? null : robotDef.robotConfig();
    }

    /** Other mechanisms to keep updated (for example an intake that loads the launcher). */
    protected Subsystems createSubsystems() {
        return new Subsystems();
    }

    /**
     * The speed it starts at, RPM. Default NaN = automatic: the speed the robot's shot table already
     * gives at the distance seen (each table its own; again after you switch tables), or
     * {@code Tunables.Launcher.speedRpm} before a distance is known, or 3000 without a robot. A number
     * here: always start from it.
     */
    protected double startRpm() {
        return Double.NaN;
    }

    /**
     * The hood angle it starts at, in the hood's scale. Default NaN = automatic: the robot's shot table at
     * the distance seen (a table with hood angles) until you change the hood yourself, else the robot
     * hood's {@code startAngle}, else 0. A number here: always start from it.
     */
    protected double startHoodDeg() {
        return Double.NaN;
    }

    /**
     * Where you put the robot before INIT, for the odometry: {x, y, heading} as seen from your drivers'
     * wall (x inches right of the field's centre line, y inches from the centre toward the far wall,
     * heading 0 = facing away from you, counterclockwise +). Default {0, 0, 0}: the middle of the field,
     * facing away from you. Only used with an {@code odometry(...)} line and {@code Tunables.Aim.goalX, goalY}.
     */
    protected double[] startSpotIn() {
        return new double[] {0, 0, 0};
    }

    /**
     * The tables you can tune, by name ("pollen" for {@code speedByDistancePollen}, "main" for
     * {@code speedByDistance}). Default: the robot launcher's tables; with none, one table "main".
     */
    protected java.util.List<String> tableNames() {
        com.crossbotics.ftchelper.robot.Launcher module = robotLauncher();
        if (module != null && !module.tableNames().isEmpty()) return module.tableNames();
        return java.util.Collections.singletonList("main");
    }

    // ----------------------------- the tuner -----------------------------

    private enum Field { SPEED, HOOD, DISTANCE, TABLE }

    private RobotDefinition robotDef;   // the robot in five files, or null
    private Launcher launcher;
    private AngleDevice hood;
    // The robot's Hood module (five files), or null: its range sets the D-pad steps.
    private com.crossbotics.ftchelper.robot.Hood robotHood;
    // True once the hood was changed by hand (then the table no longer sets it), and if startHoodDeg() was given.
    private boolean hoodTouched = false;
    private boolean autoStartHood = true;
    private TargetSource camera;
    private DriverControls driving;
    private Subsystems others;
    // One set of saved points per table, and the table being tuned.
    private final java.util.Map<String, ShotPoints> tables = new java.util.LinkedHashMap<>();
    private java.util.List<String> names;
    private String tuning;
    // The odometry and the goal point (null / false: no odometry distance).
    private com.crossbotics.ftchelper.odometry.PoseTracker odometry;
    private boolean odometryDistance = false;
    // Where the distance came from this loop: "camera", "odometry", "by hand" or "".
    private String distanceFrom = "";
    private Field field = Field.SPEED;
    private double rpm;
    private double hoodDeg;
    private double manualDistance = 48;
    private boolean spinning = false;   // off at START: LB turns it on
    private TargetObservation target;
    private int lastShots = 0;
    // HIT / MISS: the shots fired and not marked yet (each with the speed, hood and distance it was fired
    // with), the marks for undo, and the hits and shots at each speed at this spot (cleared on a new spot,
    // a new table, or a save).
    private final ShotPoints.Trials trials = new ShotPoints.Trials();
    private final ShotPoints.Tally tally = trials.tally();
    private int lastFired = 0;
    // The distance, averaged over half a second of fresh readings; the last camera picture used.
    private final ShotPoints.DistanceAverage average = new ShotPoints.DistanceAverage();
    private long lastPicture = Long.MIN_VALUE;
    // True once the speed was changed by hand (then the table no longer sets it), and if startRpm() was given.
    private boolean rpmTouched = false;
    private boolean autoStartRpm = true;
    // The last shot fed while the flywheel was too slow: how far under (RPM); NaN = none.
    private double lastSagRpm = Double.NaN;
    // Hold BACK to delete: when it was pressed (seconds), and whether this hold already deleted.
    private double backSince = Double.NaN;
    private boolean backUsed = false;
    private double shotAt = Double.NaN;
    private double recoverySec = Double.NaN;
    private String note = "";
    // X with no shots marked: the first press only warns, a second one within this time saves the speed now.
    private double noShotsAskedAt = Double.NaN;
    static final double CONFIRM_SEC = 3;
    // Hold-RT shooting: on only after a fresh RT press; off after B, a jam or a "gave up".
    private boolean continuous = false;
    private boolean rtWasDown = false;
    // True when the D-pad tried to go past the flywheel's or hood's limit.
    private boolean atLimit = false;

    @Override protected String finderName() { return "Shot tuner"; }

    @Override
    protected void setup() {
        // The robot in five files: every part is made now (INIT), with its numbers from Tunables.java.
        robotDef = robot();
        if (robotDef != null) robotDef.setup(hardwareMap);
        launcher = createLauncher();
        // Robot.java's "fire only when aimed" checks are off here: you aim by hand in the tuner, and
        // the turret (if any) stays still.
        if (robotDef != null) launcher.clearReadyChecks();
        // No speed trim here: the table must hold the real speeds, not this match's fix. (The tuner
        // sets the speed itself, so the launcher's D-pad trim never reaches it either.) The flywheel's
        // battery compensation stays on: it only helps the wheel reach the speed asked for.
        launcher.speedScale(1);
        hood = launcher.getHood();
        camera = createCamera();
        if (camera != null) camera.start();
        RobotConfig config = robotConfig();
        if (config != null) driving = DriverControls.create(hardwareMap, config.drive(), null);
        others = createSubsystems();
        if (others == null) others = new Subsystems();
        com.crossbotics.ftchelper.robot.Launcher module = robotLauncher();
        names = new java.util.ArrayList<>(tableNames());
        if (names.isEmpty()) names.add("main");
        robotHood = module != null ? module.hood() : null;
        trials.hoodDecimals(hoodDecimals());
        for (String n : names) tables.put(n, new ShotPoints(hood != null, hoodDecimals()));
        // Start on the table the launcher starts with (its startTable), else the first.
        tuning = module != null && names.contains(module.tableName()) ? module.tableName() : names.get(0);
        // Odometry: the distance from where the robot is to Tunables.Aim's goal point. The robot starts
        // at startSpotIn(); the alliance is the last one chosen (BLUE if none), for a mirrored goal.
        if (robotDef != null && robotDef.odometry() != null && robotDef.aim().hasGoalPoint()) {
            odometry = robotDef.odometry();
            odometryDistance = true;
            robotDef.setAlliance(com.crossbotics.ftchelper.match.MatchMemory.allianceOr(com.crossbotics.ftchelper.match.Alliance.BLUE));
            double[] spot = startSpotIn();
            odometry.setPose(new com.crossbotics.ftchelper.util.FieldPose(spot[0], spot[1], spot.length > 2 ? spot[2] : 0));
        }
        launcher.stop();   // off until LB
        // The speed to start from: startRpm() if given; else the robot's speedRpm (the table at the
        // distance takes over in step() once a distance is seen), else 3000.
        double given = startRpm();
        autoStartRpm = Double.isNaN(given);
        double tunablesRpm = module != null ? module.speedRpm() : Double.NaN;
        rpm = !autoStartRpm ? given : !Double.isNaN(tunablesRpm) ? tunablesRpm : 3000;
        if (hood != null) {
            // startHoodDeg() if given; else where the hood is going (a hood never moved has no angle yet:
            // NaN), else the robot hood's home, else 0. The table at the distance takes over in step().
            double givenHood = startHoodDeg();
            autoStartHood = Double.isNaN(givenHood);
            double now = hood.getTargetDegrees();
            double home = robotHood != null ? robotHood.startAngle() : Double.NaN;
            hoodDeg = !autoStartHood ? givenHood : !Double.isNaN(now) && !Double.isInfinite(now) ? now : !Double.isNaN(home) ? home : 0;
        }
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.text("Shot tuner: builds the shot table (distance -> speed" + (hood != null ? ", hood" : "") + ").");
        t.warn("Nobody in the line of fire. Eye protection on.");
        if (camera != null) t.line("Camera", camera.status());
        if (odometryDistance) {
            double[] spot = startSpotIn();
            t.line("Odometry", "put the robot at x " + FinderResults.num(spot[0], 0) + ", y " + FinderResults.num(spot[1], 0)
                    + " in, heading " + FinderResults.num(spot.length > 2 ? spot[2] : 0, 0) + " (seen from your wall), then INIT ("
                    + robotDef.alliance() + ")");
        }
        if (names.size() > 1) t.line("Tables", FinderResults.commas(names) + " (Y until > Table, D-pad to choose)");
        t.text("Steps: 1 pick the table. 2 park at the nearest distance. 3 LB spin up, RB shoot 3-5,");
        t.text("A = HIT, B = MISS for each; D-pad to the next speed, again. X saves the best speed.");
        t.text("4 back 1-2 ft, again: 4-6 points per table.");
        t.text("5 copy the lines into Tunables.java (also saved on the hub at STOP).");
        t.text("The flywheel stays off until you press LB. Press START.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.Y, this::nextField)
         .bindPress(Pad.G1, Btn.DPAD_UP, () -> change(1, true))
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> change(-1, true))
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> change(1, false))
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> change(-1, false))
         .bindPress(Pad.G1, Btn.LB, () -> {
                    spinning = !spinning;
                    if (!spinning) launcher.stop();   // fully off (it stays off until spinUp)
                })
         .bindPress(Pad.G1, Btn.RB, () -> { if (spinning) launcher.shoot(1); })
         .bindPress(Pad.G1, Btn.A, () -> note = trials.mark(true))
         .bindPress(Pad.G1, Btn.B, () -> note = trials.mark(false))
         .bindPress(Pad.G1, Btn.L_STICK_BTN, () -> note = trials.undo())
         .bindPress(Pad.G1, Btn.R_STICK_BTN, () -> note = trials.dropLast())
         .bindPress(Pad.G1, Btn.X, this::savePoint);
        // Delete (hold BACK 1 s) is watched in step(): a hold, so it is not pressed by mistake.
    }

    // Y: the next thing the D-pad changes. HOOD only with a hood, DISTANCE only by hand (no camera,
    // no odometry), TABLE only with two or more tables.
    private void nextField() {
        // At most one pass through the list: the first usable one after the current one.
        for (int i = 0; i < Field.values().length; i++) {
            field = Field.values()[(field.ordinal() + 1) % Field.values().length];
            boolean usable = !(field == Field.HOOD && hood == null) && !(field == Field.DISTANCE && !byHand())
                    && !(field == Field.TABLE && names.size() < 2);
            if (usable) return;
        }
    }

    // True when the distance is set by hand: no camera and no odometry.
    private boolean byHand() {
        return camera == null && !odometryDistance;
    }

    private void change(int direction, boolean big) {
        switch (field) {
            case SPEED:
                rpm = Math.max(0, rpm + direction * (big ? 100 : 10));
                rpmTouched = true;   // yours now: the table no longer sets it
                break;
            case HOOD:
                hoodDeg += direction * hoodStep(big);
                hoodTouched = true;   // yours now: the table no longer sets it
                break;
            case TABLE:
                // The next (or previous) table; its points stay as they are.
                int i = names.indexOf(tuning);
                tuning = names.get(((i + direction) % names.size() + names.size()) % names.size());
                // Another game piece: its own hits, and its own table's speed to start from.
                trials.clear();
                rpmTouched = false;
                hoodTouched = false;
                note = "Now tuning the " + tuning.toUpperCase(java.util.Locale.US) + " table.";
                break;
            default: manualDistance = Math.max(0, manualDistance + direction * (big ? 6 : 1)); break;
        }
    }

    // The hood's D-pad step: a fifth (big) or a fiftieth (small) of the robot hood's range (5 and 0.5
    // for 0-25 deg, 0.009 for a servo-position hood over 0.45); 5 and 0.5 without a robot hood.
    private double hoodStep(boolean big) {
        if (robotHood == null || Double.isNaN(robotHood.minAngle())) return big ? 5 : 0.5;
        double range = robotHood.maxAngle() - robotHood.minAngle();
        return range * (big ? 0.2 : 0.02);
    }

    // Decimals for the hood: 1 (degrees), 3 (a servo position), 0 (encoder ticks).
    private int hoodDecimals() {
        if (robotHood == null || robotHood.usesAngles()) return 1;
        return robotHood.isMotor() ? 0 : 3;
    }

    // A hood number for the screen: "12.5 deg", or the position "0.420" / "350 ticks".
    private String hoodText(double v) {
        if (robotHood == null || robotHood.usesAngles()) return FinderResults.num(v, 1) + " deg";
        return robotHood.isMotor() ? FinderResults.num(v, 0) + " ticks" : FinderResults.num(v, 3);
    }

    // The distance now: the camera's (fresh pictures only), else the odometry's, averaged over the last
    // half second (step() adds the readings); else the hand-set one (only with neither). NaN = none.
    // Sets distanceFrom.
    private double distance() {
        if (byHand()) {
            distanceFrom = "by hand";
            return manualDistance;
        }
        double d = average.average(nowSec());
        distanceFrom = Double.isNaN(d) ? "" : average.source() + ", " + average.count(nowSec()) + " readings";
        return d;
    }

    // Adds this loop's fresh reading to the average: a NEW camera picture, else the odometry.
    private void readDistance() {
        double now = nowSec();
        if (target != null && target.hasDistance()) {
            if (target.timeNanos != lastPicture) {   // each picture once
                lastPicture = target.timeNanos;
                average.add(now, target.distanceIn, "camera");
            }
            return;
        }
        TargetObservation odo = odometryGoal();
        if (odo != null) average.add(now, odo.distanceIn, "odometry");
    }

    private static double nowSec() {
        return System.nanoTime() / 1e9;
    }

    // The goal from the odometry, or null (no odometry or no goal point).
    private TargetObservation odometryGoal() {
        return odometryDistance ? robotDef.aim().fromOdometry() : null;
    }

    // X: save the speed with the best hit ratio at this distance. With no shots marked, it warns first;
    // X again within CONFIRM_SEC saves the speed now. Shots waiting to be marked must be marked first.
    private void savePoint() {
        double d = distance();
        if (Double.isNaN(d)) {
            note = "No distance: the camera does not see the target.";
            return;
        }
        ShotPoints points = tables.get(tuning);
        if (Double.isNaN(hoodDeg) || Double.isInfinite(hoodDeg)) {
            note = "No hood angle yet: change it with the D-pad first.";
            return;
        }
        if (trials.unmarkedCount() > 0) {
            note = "Mark the " + trials.unmarkedCount() + " shot(s) first (A = HIT, B = MISS), then X.";
            return;
        }
        if (tally.isEmpty()) {
            // Nothing proves this speed goes in: only save it when asked twice.
            double now = nowSec();
            if (Double.isNaN(noShotsAskedAt) || now - noShotsAskedAt > CONFIRM_SEC) {
                noShotsAskedAt = now;
                note = "No shots marked here: shoot and mark 3-5 first (A / B). Press X again to save the speed now anyway.";
                return;
            }
        }
        noShotsAskedAt = Double.NaN;
        double speed = rpm, hoodSaved = hoodDeg, measured = Double.NaN;
        int hits = 0, shots = 0;
        if (!tally.isEmpty()) {
            double[] best = tally.best();
            if (best == null) {
                note = "Nothing went in yet at this spot: not saved. Try another speed.";
                return;
            }
            speed = best[0];
            // The hood the winning shots were FIRED with (not where the hood is now).
            if (hood != null && !Double.isNaN(best[1])) hoodSaved = best[1];
            measured = tally.measuredRpm(best[0], best[1]);
            hits = tally.hits(best[0], best[1]);
            shots = tally.shots(best[0], best[1]);
        }
        points.save(d, speed, hoodSaved, measured, hits, shots);
        note = "Saved " + FinderResults.num(d, 1) + " in -> " + FinderResults.num(speed, 0) + " RPM"
                + (hood != null ? ", hood " + hoodText(hoodSaved) : "")
                + (shots > 0 ? " (" + hits + "/" + shots + " in)" : " (no shots marked)")
                + (names.size() > 1 ? " " + tuning.toUpperCase(java.util.Locale.US) : "") + "."
                + (shots > 0 && shots < 3 ? " Only " + shots + " shots: 3-5 is surer." : "")
                + (!Double.isNaN(measured) && speed - measured > sagLimitRpm(speed)
                        ? " The flywheel sagged to " + FinderResults.num(measured, 0) + " at these shots." : "");
        trials.clear();
        writeResults();
    }

    // Hold BACK 1 s: delete the saved point here.
    private void deletePoint() {
        double d = distance();
        if (!Double.isNaN(d) && tables.get(tuning).removeNear(d)) {
            note = "Deleted the point at " + FinderResults.num(d, 1) + " in.";
            writeResults();
        }
    }

    private void writeResults() {
        results.removeStartingWith("shots:");
        com.crossbotics.ftchelper.robot.Launcher module = robotLauncher();
        String section = module != null ? module.name() : "Launcher";
        for (String name : names) {
            ShotPoints points = tables.get(name);
            if (points.points().isEmpty()) continue;
            int n = 0;
            if (names.size() > 1) results.set("shots:" + name + ":label", "// " + name.toUpperCase(java.util.Locale.US) + " table");
            for (String line : points.codeLines()) results.set("shots:" + name + ":" + n++, line);
            // The same table for the robot in five files ({inches, rpm} or, with a hood, {inches, rpm, hood}).
            results.set("shots:" + name + ":t", points.tunablesLine(section, fieldFor(module, name)));
        }
    }

    // The Tunables field of a table: the launcher's own (e.g. "speedByDistancePollen"), or made from the name.
    private static String fieldFor(com.crossbotics.ftchelper.robot.Launcher module, String name) {
        String f = module == null ? null : module.tableField(name);
        if (f != null) return f;
        if (name.equals("main")) return "speedByDistance";
        return "speedByDistance" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // The first Launcher in Robot.java, or null (no robot() or no launcher).
    private com.crossbotics.ftchelper.robot.Launcher robotLauncher() {
        if (robotDef == null) return null;
        for (Module<?> m : robotDef.modules()) {
            if (m instanceof com.crossbotics.ftchelper.robot.Launcher) return (com.crossbotics.ftchelper.robot.Launcher) m;
        }
        return null;
    }

    @Override
    protected void step() {
        // The odometry: a fresh pose every loop (the rest of the robot stays still).
        if (odometry != null) odometry.update();
        if (camera != null) {
            camera.update();
            TargetObservation seen = findTarget(camera);
            target = seen != null && seen.isFresh(System.nanoTime(), MAX_TARGET_AGE_MS) ? seen : null;
        }
        readDistance();
        double d = distance();
        // Moved to another spot: the hits marked at the old one do not count here, and shots fired
        // there and not marked yet are dropped.
        String moved = trials.check(d);
        if (!moved.isEmpty()) note = moved;
        // Start from the table's speed at this distance until you change the speed yourself.
        if (autoStartRpm && !rpmTouched && !Double.isNaN(d)) {
            com.crossbotics.ftchelper.robot.Launcher module = robotLauncher();
            ShotTable table = module == null ? null : module.table(tuning);
            if (table != null && !table.points().isEmpty()) rpm = Math.round(table.lookup(d).rpm / 10) * 10.0;
        }
        // The hood the same way: from the table's hood column at this distance until you change it.
        if (hood != null && autoStartHood && !hoodTouched && !Double.isNaN(d)) {
            com.crossbotics.ftchelper.robot.Launcher module = robotLauncher();
            ShotTable table = module == null ? null : module.table(tuning);
            if (table != null && table.hasHood() && !table.points().isEmpty()) hoodDeg = table.lookup(d).hoodDeg;
        }
        // Hold BACK 1 s: delete the saved point here (once per hold).
        boolean back = gamepad1.back && inputsReady();
        if (!back) {
            backSince = Double.NaN;
            backUsed = false;
        } else if (Double.isNaN(backSince)) {
            backSince = nowSec();
        } else if (!backUsed && nowSec() - backSince >= DELETE_HOLD_SEC) {
            backUsed = true;
            deletePoint();
        }
        // The sticks and RT only count once gamepad 1 was let go after START or help (inputsReady()).
        if (driving != null) {
            if (inputsReady()) driving.update(gamepad1);
            else driving.hold();
        }
        // Keep the speed and hood inside what the hardware really does, so a saved point is what
        // was really tested (the flywheel and the hood would cut it silently otherwise).
        double maxRpm = launcher.getFlywheel().getMaxRpm();
        atLimit = rpm > maxRpm;
        rpm = Math.min(rpm, maxRpm);
        if (hood != null) {
            hood.setDegrees(hoodDeg);
            // Where the hood is really going after its limits (not where a slow move is now).
            double real = hood.getTargetDegrees();
            if (!Double.isNaN(real) && Math.abs(real - hoodDeg) > 0.05) {
                atLimit = true;
                hoodDeg = real;
            }
        }
        // Hold RT: keep shooting, one piece at a time. It starts on a fresh RT press and stops for
        // good after B, a jam or a "gave up" (press RT again to go on), so a jam is never pushed on.
        boolean rt = gamepad1.right_trigger > 0.5 && inputsReady();
        boolean freshPress = rt && !rtWasDown;
        rtWasDown = rt;
        if (!rt) continuous = false;
        else if (freshPress) continuous = true;   // a new press tries again (shoot() clears an old jam)
        else if (!launcher.getLastProblem().isEmpty()) continuous = false;
        if (spinning && continuous && !launcher.isShooting()) launcher.shoot(1);
        if (spinning) {
            launcher.setRpm(rpm);
            launcher.spinUp();
        }
        launcher.update();
        others.update();
        // A piece was fed this loop: keep the speed asked for, the hood, the distance, and the speed
        // MEASURED now (as it was fed, before the shot slows the wheel), for A / B to mark.
        int fired = launcher.getShotsFired();
        if (fired > lastFired) {
            double measured = launcher.getFlywheel().getRpm();
            for (int i = lastFired; i < fired; i++) trials.fired(rpm, hoodNow(), measured, d);
            lastSagRpm = rpm - measured > sagLimitRpm(rpm) ? rpm - measured : Double.NaN;
        }
        lastFired = fired;
        // Recovery: from a shot (the flywheel's shot counter goes up) until it is ready again.
        int shots = launcher.getFlywheel().getShotCount();
        if (shots > lastShots) {
            lastShots = shots;
            shotAt = System.nanoTime() / 1e9;
        }
        if (!Double.isNaN(shotAt) && launcher.getFlywheel().isReady()) {
            recoverySec = System.nanoTime() / 1e9 - shotAt;
            shotAt = Double.NaN;
        }
    }

    // The hood angle for the tally: NaN without a hood.
    private double hoodNow() {
        return hood == null ? Double.NaN : hoodDeg;
    }

    @Override
    protected void show(RobotTelemetry t) {
        t.section("Shot tuner  (Y = what the D-pad changes)");
        t.line((field == Field.SPEED ? "> " : "  ") + "Speed", FinderResults.num(rpm, 0) + " RPM, measured "
                + FinderResults.num(launcher.getFlywheel().getRpm(), 0) + (spinning ? "" : "  (OFF: LB)"));
        if (hood != null) t.line((field == Field.HOOD ? "> " : "  ") + "Hood", hoodText(hoodDeg));
        if (names.size() > 1) t.line((field == Field.TABLE ? "> " : "  ") + "Table", tuning.toUpperCase(java.util.Locale.US)
                + "  (" + FinderResults.commas(names).toUpperCase(java.util.Locale.US) + ")");   // all in capitals, as the table in use
        double d = distance();
        String dText = Double.isNaN(d) ? "no target" : FinderResults.num(d, 1) + " in (" + distanceFrom + ")";
        TargetObservation odo = odometryGoal();
        TargetObservation aimedBy = target != null && target.hasDistance() ? target : odo;
        if (byHand()) t.line((field == Field.DISTANCE ? "> " : "  ") + "Distance", dText);
        else t.line("  Distance", dText + (aimedBy != null && aimedBy.hasBearing()
                ? ", bearing " + FinderResults.num(aimedBy.bearingDeg, 1) + " deg" : ""));
        // Both seen: compare them. Far apart = move Tunables.Aim's goal point (one table must fit both).
        if (odo != null && target != null && target.hasDistance()) {
            t.line("  Odometry says", FinderResults.num(odo.distanceIn, 1) + " in");
        }
        if (atLimit) t.warn("At the flywheel's maxRpm or the hood's limit");
        if (!Double.isNaN(lastSagRpm)) {
            t.warn("Last shot: the flywheel was " + FinderResults.num(lastSagRpm, 0)
                    + " RPM under the speed (sagged). Wait for Ready; charge the battery.");
        }
        t.line("Ready", launcher.isReady() ? "YES" : "no: " + launcher.whyNotReady());
        int waiting = trials.unmarkedCount();
        t.line("Shots fired", launcher.getShotsFired() + (waiting == 0 ? "" : ", " + waiting + " to mark (A hit / B miss)"));
        // The way teams tune: hits / shots at this speed (and hood), and every one tried at this spot.
        t.line("Hits at " + FinderResults.num(rpm, 0) + (hood != null ? ", hood " + hoodText(hoodDeg) : ""),
                tally.hits(rpm, hoodNow()) + "/" + tally.shots(rpm, hoodNow()) + (tally.shots(rpm, hoodNow()) < 3 ? "  (shoot 3-5)" : ""));
        if (!tally.isEmpty()) t.line("Tried here", tally.summary());
        // Every table's point count, so you know when you are done (4-6 each).
        java.util.List<String> counts = new java.util.ArrayList<>();
        for (String n : names) counts.add((names.size() > 1 ? n.toUpperCase(java.util.Locale.US) + " " : "") + tables.get(n).size());
        t.line("Points", FinderResults.commas(counts) + "  (4-6 per table)");
        if (!Double.isNaN(recoverySec)) t.line("Recovery", FinderResults.num(recoverySec, 2) + " s");
        if (!launcher.getLastProblem().isEmpty()) t.warn(launcher.getLastProblem());
        t.text("RB shoot, hold RT keep shooting, A HIT, B MISS, X save best, hold BACK 1 s delete");
        t.text("Left stick click = undo the last mark, right stick click = drop the last shot (no piece)");
        t.text("Next: " + nextStep(d));
        if (!note.isEmpty()) t.text(note);
        for (ShotPoints.Saved s : tables.get(tuning).saved()) {
            ShotTable.Shot p = s.shot;
            String extra = ShotPoints.note(s);
            t.text("  " + FinderResults.num(p.distance, 1) + " in: " + FinderResults.num(p.rpm, 0) + " RPM"
                    + (hood != null ? ", hood " + hoodText(p.hoodDeg) : "")
                    + (extra.isEmpty() ? "" : " (" + extra + ")"));
        }
        if (!results.isEmpty()) {
            t.text("Copy each line below into Tunables.java (replace the line there): the robot uses the file, not this screen.");
        }
    }

    // The next thing to do, in a few words, from what the tuner sees now.
    private String nextStep(double d) {
        if (!spinning) return "LB to spin up the flywheel.";
        if (Double.isNaN(d)) {
            return odometryDistance ? "point the camera at the goal, or check the odometry." : "point the camera at the goal.";
        }
        if (trials.unmarkedCount() > 0) return "A = HIT or B = MISS for each shot.";
        if (tally.shots(rpm, hoodNow()) < 3) return "RB to shoot 3-5 at this speed, marking each A HIT / B MISS.";
        if (tally.tries() < 2) return "D-pad to another speed, shoot 3-5 again; X saves the best.";
        if (tables.get(tuning).points().isEmpty()) return "X saves the best speed here.";
        return "X saves; move 1-2 ft and do it again (4-6 points), then copy the lines into Tunables.java.";
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Builds the shot table: distance -> speed (+ hood).",
            "Nobody in the line of fire. Eye protection on.",
            "LB = flywheel on/off. Y = what the D-pad changes:",
            "  SPEED / HOOD / DISTANCE (by hand) / TABLE.",
            "D-pad up/down: 100 RPM / 1/5 of the hood /",
            "  6 in; right/left: 10 RPM / 1/50 / 1 in.",
            "RB = shoot one, hold RT = keep shooting.",
            "A = HIT, B = MISS (the oldest shot not marked).",
            "L stick click = undo mark, R = drop last shot.",
            "X = save the best speed. Hold BACK 1 s = delete.",
            "Sticks = drive (if set up), left trigger = slow.",
            "3-5 shots a speed, 2-3 speeds, X; back 1-2 ft.",
            "4-6 points per table, then copy into Tunables.",
        };
    }

    // Help open: the flywheel, feeding and wheels stop; the camera keeps running.
    @Override
    protected void pause() {
        spinning = false;
        continuous = false;
        launcher.stop();
        if (driving != null) driving.stop();
        others.stop();
    }

    @Override
    protected void stopAll() {
        RuntimeException first = null;
        try { if (launcher != null) launcher.stop(); } catch (RuntimeException e) { first = e; }
        try { if (driving != null) driving.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        try { if (others != null) others.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        try { if (camera != null) camera.close(); } catch (RuntimeException e) { if (first == null) first = e; }
        // The robot in five files: every part it made stops too.
        try { if (robotDef != null && robotDef.all() != null) robotDef.all().stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        if (first != null) throw first;
    }
}
