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
 * the {@code Tunables.java} line for you (without Robot.java: the {@code ShotTable} code).
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
 * <p><b>Like a match.</b> With your robot in five files and a camera with {@code goal(...)} in Robot.java, the
 * tuner sees the goal the way TeleOp and the autos do: the same camera, its lighting test in INIT (as in a
 * match), the alliance you choose in INIT (only its goal counts), and the goal's distance as the launcher
 * reads it. RB turns the whole robot to the goal with TeleOp's aim ({@code Tunables.Aim}: toleranceDeg,
 * the saved cameraOffset, the angle aim if on) and shoots once it is aimed and Ready. Not aimed for
 * {@link #AIM_TIMEOUT_SEC}, or the goal not in view: no shot, so the table is built only from aimed shots.
 * (A robot whose turret follows the goal: the tuner does not turn the turret. Point it yourself.)
 *
 * <pre>
 * In INIT:  X = BLUE, B = RED (only that alliance's goal counts). The camera's lighting test runs.
 * Gamepad 1:
 *   Y (tap)        = what the D-pad changes: SPEED, HOOD (if any), DISTANCE (no camera, no odometry),
 *                    TABLE (two or more tables)
 *   D-pad up/down  = big step (100 RPM / a fifth of the hood's range, 5 deg for 25 / 6 in),
 *                    right/left = small step (10 RPM / a fiftieth of the hood's range, 0.5 deg / 1 in)
 *                    (TABLE: left/right = the other table)
 *   LB             = flywheel on/off (off at START; off also stops shooting)
 *   RB             = shoot one (a robot that aims turns to the goal first)   hold RT = keep shooting (let go to stop)
 *   hold Y 0.5 s   = the camera's lighting test again (aim the camera at a tag first)
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
 * <p>For the robot in five files it shows the line for {@code Tunables.java}, class Launcher:
 * {@code public static double[][] speedByDistance = {{48, 2550}, {72, 2900}};}. It holds ONLY the rows saved
 * in this run: older rows are never mixed in (only the newest counts are trusted). The screen says how many
 * rows in your code it replaces, and warns when they are fewer or under {@link #ENOUGH_ROWS}. Without
 * Robot.java it shows the {@code ShotTable} code. The lines are also saved on the Control Hub at STOP.
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
 * Docs: docs/modules/finders.md. Guide: docs/tuning/13-shot-tuner.md
 */
public abstract class ShotTunerOpMode extends FinderOpMode {

    // Its numbers are powers or speeds: they depend on the battery.
    @Override
    protected boolean batteryMatters() {
        return true;
    }

    /** Camera pictures older than this are not used for the distance (milliseconds). */
    public static final double MAX_TARGET_AGE_MS = 250;

    /** The marked shots start again when the distance moves more than this from where they began (inches). */
    public static final double SPOT_MOVE_IN = ShotPoints.Trials.SPOT_MOVE_IN;

    /** The longest RB waits for the robot to turn to the goal (seconds): then no shot, and a note says why. */
    public static final double AIM_TIMEOUT_SEC = 2.0;

    /** Hold Y this long to run the camera's lighting test again (seconds). A shorter press: the next field. */
    public static final double RELIGHT_HOLD_SEC = 0.5;

    /** A table with fewer rows than this is not done (4 to 6 distances). */
    static final int ENOUGH_ROWS = 4;

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
     * Your camera, or null to set the distance by hand. It is started for you. Default: the camera that
     * aims in a match (the first with {@code goal(...)} in Robot.java), else the first camera of
     * {@link #robot()}, or null.
     */
    protected TargetSource createCamera() {
        if (robotDef == null) return null;
        // The camera that aims in a match (the first with goal(...)), else the first camera.
        Camera aiming = robotDef.aim().camera();
        if (aiming != null) return aiming.source();
        for (Module<?> m : robotDef.modules()) if (m instanceof Camera) return ((Camera) m).source();
        return null;
    }

    /**
     * Which target the distance is measured to. Default: with a robot that aims the whole robot (a camera with
     * {@code goal(...)} in Robot.java, no turret following the goal), the goal the aim uses in a match ({@code robot.aim()}: this alliance's goal only, fresh
     * pictures, the tags' distance); otherwise the first target the camera sees with a distance.
     */
    protected TargetObservation findTarget(TargetSource camera) {
        if (matchGoal()) {
            TargetObservation g = robotDef.aim().goal();
            return g != null && !g.fromOdometry() ? g : null;
        }
        for (TargetObservation o : camera.targets()) if (o.hasDistance()) return o;
        return null;
    }

    // True when the distance and the aim come from the robot's own goal camera (as in TeleOp and the autos).
    // (Whole-robot aim only: a turret robot keeps the first target with a distance, as before.)
    private boolean matchGoal() {
        return aims && robotCamera != null && robotCamera.knowsGoal() && camera == robotCamera.source();
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
     * wall (x inches right of the field's center line, y inches from the center toward the far wall,
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
    // The robot's goal camera (Robot.java), or null: its lighting test runs in INIT, as in TeleOp.
    private Camera robotCamera;
    // True when RB turns the whole robot to the goal before each shot (Robot.java aims, no following turret).
    private boolean aims = false;
    // The alliance chosen in INIT ({X} blue, {B} red): only its goal counts. Last loop's X and B (edges).
    private com.crossbotics.ftchelper.match.Alliance alliance = com.crossbotics.ftchelper.match.Alliance.BLUE;
    private boolean xWasDown = true, bWasDown = true;
    // A shot waiting for the aim (seconds since START it was asked; NaN = none), and START's first loop done.
    private double aimingSince = Double.NaN;
    private boolean shotFromRt = false;   // the waiting shot came from holding RT (let go: it is dropped)
    private double shotAskedAt = Double.NaN;   // when the waiting shot was asked for (seconds)
    /** The longest a shot waits in all (aimed but the flywheel not Ready, seconds): then it is dropped and says why. */
    public static final double SHOT_WAIT_SEC = 4.0;
    private boolean startedCamera = false;
    // Hold Y: when it was pressed (NaN = up) and whether this hold already ran the lighting test.
    private double ySince = Double.NaN;
    private boolean yUsed = false;
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
    private static final String NO_GOAL = "No shot: the camera does not see the ";
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
        // The match's camera and alliance: the robot's goal camera (its lighting test runs in INIT), and the
        // alliance last chosen (BLUE if none; X / B in INIT change it), so only that alliance's goal counts.
        if (robotDef != null) {
            robotCamera = robotDef.aim().camera();
            alliance = com.crossbotics.ftchelper.match.MatchMemory.allianceOr(com.crossbotics.ftchelper.match.Alliance.BLUE);
            robotDef.setAlliance(alliance);
            aims = robotCamera != null && camera == robotCamera.source() && !robotDef.aim().turretAims();
        }
        RobotConfig config = robotConfig();
        if (config != null) driving = DriverControls.create(hardwareMap, config.drive(), null);
        // RB turns the robot to the goal the way TeleOp does (cameraOffset, the angle aim); the stick still wins.
        if (driving != null && aims) driving.turnAssist(robotDef.aim()::turnFor);
        if (driving == null) aims = false;   // no drive: it cannot turn, so it shoots where it points
        // The feed itself waits for the aim (as TeleOp's "aimed" check), so a queued piece never leaves unaimed.
        if (aims) launcher.requireReady("aimed at the goal", this::aimedNow);
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
        if (aims) {
            t.warn("{RB} turns the robot to the goal, then FIRES.");
        } else if (robotDef != null && robotDef.aim().turretAims()) {
            t.warn("It does NOT turn the turret. {RB} FIRES.");
            t.warn("Point the turret at the goal yourself.");
        } else {
            t.warn("It does NOT aim. {RB} FIRES where it points.");
        }
        t.warn("Safety glasses. Nobody in the line of fire.");
        if (hood != null) t.warn("The hood moves at START. Hands clear.");
        if (driving != null) t.warn("The sticks drive. Let go = stop.");
        t.text("Shot tuner: the flywheel speed for each");
        t.text("distance (the shot table). {LB} = flywheel on.");
        if (robotDef != null) {
            // X = BLUE, B = RED (a fresh press; not with START held: START + B pairs a gamepad).
            boolean x = gamepad1.x && !gamepad1.start, b = gamepad1.b && !gamepad1.start;
            if (x && !xWasDown) alliance = com.crossbotics.ftchelper.match.Alliance.BLUE;
            if (b && !bWasDown) alliance = com.crossbotics.ftchelper.match.Alliance.RED;
            xWasDown = x;
            bWasDown = b;
            robotDef.setAlliance(alliance);
            t.line("Alliance", alliance + "  ({X} = blue, {B} = red)");
        }
        // The robot's goal camera: TeleOp's INIT work (the lighting test runs now, as in a match).
        if (robotCamera != null && camera == robotCamera.source()) robotCamera.checkInInit(t);
        else if (camera != null) t.line("Camera", camera.status());
        if (odometryDistance) {
            double[] spot = startSpotIn();
            t.line("Odometry", "put the robot at x " + FinderResults.num(spot[0], 0) + ", y " + FinderResults.num(spot[1], 0) + " in");
            t.text("  (x, y = halfway between its edges, seen");
            t.text("  from your wall), " + facing(spot.length > 2 ? spot[2] : 0) + ".");
            t.text("  Not there yet? Move it. Press INIT again.");
        }
        if (names.size() > 1) t.line("Tables", FinderResults.commas(names));
    }

    // A heading in words: "facing away from you", "turned 90 deg left".
    private static String facing(double heading) {
        if (heading == 0) return "facing away from you";
        return "turned " + FinderResults.num(Math.abs(heading), 0) + " deg " + (heading > 0 ? "left" : "right");
    }

    @Override
    protected void bind(ControllerBindings c) {
        // Y: a tap changes the field, a hold runs the lighting test again: both watched in step().
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> change(1, true))
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> change(-1, true))
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> change(1, false))
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> change(-1, false))
         .bindPress(Pad.G1, Btn.LB, () -> {
                    spinning = !spinning;
                    if (spinning) {
                        startRobotLauncher();
                    } else {
                        stopAiming();      // first: dropping a waiting shot idles the launcher...
                        launcher.stop();   // ...then fully off (it stays off until spinUp)
                        stopRobotLauncher();
                    }
                })
         .bindPress(Pad.G1, Btn.RB, () -> { if (spinning) shootOne(false); })
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

    // One shot. A robot that aims: the aim turns to the goal, and the launcher feeds only when it is Ready AND
    // aimed (the "aimed at the goal" check, see setup()). No goal in the camera now: no shot.
    private void shootOne(boolean fromRt) {
        if (!aims) {
            note = "";       // the last problem is not this shot's
            launcher.shoot(1);
            return;
        }
        if (!Double.isNaN(aimingSince) || launcher.isShooting()) return;   // one at a time
        if (target == null) {
            cancelShot(NO_GOAL + alliance + " goal\nNext: point the robot at it. Press {RB} again.");
            return;
        }
        robotDef.aim().mode().on();   // TeleOp's aim: the turn assist turns to the goal
        robotDef.aim().relock();      // as TeleOp's quick turn: each shot must get within toleranceDeg again
        aimingSince = nowSec();
        shotAskedAt = aimingSince;
        shotFromRt = fromRt;
        note = "";           // the last problem is not this shot's
        launcher.shoot(1);            // waits for Ready and "aimed at the goal"
    }

    // True when a shot may be fed: the camera sees this alliance's goal NOW (not the odometry's guess) and
    // TeleOp's aim says the robot is aimed (toleranceDeg, cameraOffset, the angle aim).
    private boolean aimedNow() {
        return target != null && !target.fromOdometry() && robotDef.aim().mode().isOn() && robotDef.aim().isAimed();
    }

    // A waiting shot is dropped (not fed), the aim stops, and the robot is the driver's again. why: a note, or null.
    private void cancelShot(String why) {
        aimingSince = Double.NaN;
        continuous = false;
        if (launcher != null && launcher.isShooting()) launcher.idle();   // the queued piece is not fed
        if (aims) robotDef.aim().mode().off();
        if (why != null) note = why;
    }

    // The aim stops (LB off, help, a relight, STOP): any shot waiting is dropped.
    private void stopAiming() {
        cancelShot(null);
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
        distanceFrom = Double.isNaN(d) ? "" : average.source() + ", averaged";
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
            note = "Not saved: no distance.\nNext: point the camera at the goal.";
            return;
        }
        ShotPoints points = tables.get(tuning);
        if (Double.isNaN(hoodDeg) || Double.isInfinite(hoodDeg)) {
            note = "Not saved: no hood angle.\nNext: set it with the D-pad ({Y} chooses).";
            return;
        }
        if (trials.unmarkedCount() > 0) {
            note = "Not saved: shots wait to be marked.\nNext: mark all " + trials.unmarkedCount() + ", then {X}.";
            return;
        }
        if (tally.isEmpty()) {
            // Nothing proves this speed goes in: only save it when asked twice.
            double now = nowSec();
            if (Double.isNaN(noShotsAskedAt) || now - noShotsAskedAt > CONFIRM_SEC) {
                noShotsAskedAt = now;
                note = "No shots marked here: shoot 3 to 5 first.\nPress {X} again to save the speed now anyway.";
                return;
            }
        }
        noShotsAskedAt = Double.NaN;
        double speed = rpm, hoodSaved = hoodDeg, measured = Double.NaN;
        int hits = 0, shots = 0;
        if (!tally.isEmpty()) {
            double[] best = tally.best();
            if (best == null) {
                note = "Not saved: none went in at this spot.\nNext: try another speed.";
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
        note = "Saved " + FinderResults.num(d, 1) + " in: use " + FinderResults.num(speed, 0) + " RPM"
                + (names.size() > 1 ? ", " + tuning.toUpperCase(java.util.Locale.US) : "")
                + (hood != null ? ", hood " + hoodText(hoodSaved) : "")
                + (shots > 0 ? "\n" + hits + " of " + shots + " went in." : "\nNOT tested: no shots marked.")
                + (shots > 0 && shots < 3 ? "\nOnly " + count(shots, "shot") + ": 3 to 5 is surer." : "")
                + (!Double.isNaN(measured) && speed - measured > sagLimitRpm(speed)
                        ? "\nThe flywheel fell to " + FinderResults.num(measured, 0) + " RPM at these shots." : "");
        trials.clear();
        writeResults();
    }

    // Hold BACK 1 s: delete the saved point here.
    private void deletePoint() {
        double d = distance();
        if (!Double.isNaN(d) && tables.get(tuning).removeNear(d)) {
            note = "Deleted the saved distance " + FinderResults.num(d, 1) + " in.";
            writeResults();
        }
    }

    private void writeResults() {
        results.removeStartingWith("shots:");
        com.crossbotics.ftchelper.robot.Launcher module = robotLauncher();
        boolean fiveFiles = robotDef != null;
        String section = module != null ? module.name() : "Launcher";
        // The warnings first: they must be read before the line is copied.
        String missing = null;
        for (String name : names) {
            ShotPoints points = tables.get(name);
            int now = points.size();
            if (now == 0) continue;
            String label = names.size() > 1 ? name.toUpperCase(java.util.Locale.US) + ": " : "";
            if (now < ENOUGH_ROWS && missing == null) missing = label + now + " of " + ENOUGH_ROWS + " distances saved.";
            int inCode = rowsInCode(module, name);
            if (inCode >= 0) {
                results.set("shots:" + name + ":replace", (label.isEmpty() ? "This " : label) + "replaces " + count(inCode, "row") + " in your code with "
                        + (now == 1 ? "this 1" : "these " + now) + ".");
                results.set("shots:" + name + ":only", now < inCode
                        ? "Old rows are NOT kept: you would lose " + (inCode - now) + "."
                        : "Old rows are NOT kept: only this run's.");
            }
        }
        boolean any = false;
        for (String name : names) {
            ShotPoints points = tables.get(name);
            if (points.points().isEmpty()) continue;
            any = true;
            if (fiveFiles) {
                // The table for the robot in five files ({inches, rpm} or, with a hood, {inches, rpm, hood}).
                results.setTunable("shots:" + name + ":t", section, fieldFor(module, name), points.tableValue());
            } else {
                String where = names.size() > 1 ? name.toUpperCase(java.util.Locale.US) + " table:" : FinderResults.HELPER_CODE;
                results.setIn("shots:" + name + ":t", where, points.codeLines().toArray(new String[0]));
            }
        }
        if (!any) results.notDone("no distance saved.");
        else if (missing != null) results.notDone(missing);
        else results.done();
    }

    // The rows this table has in the code (Tunables.java), or -1 without Robot.java.
    private int rowsInCode(com.crossbotics.ftchelper.robot.Launcher module, String name) {
        if (module == null) return -1;
        ShotTable table = module.table(name);
        return table == null ? 0 : table.points().size();
    }

    // The warning over the buttons when a line to copy would lose rows, or has too few.
    private String replaceWarning() {
        for (String name : names) {
            int now = tables.get(name).size();
            if (now == 0) continue;
            int inCode = rowsInCode(robotLauncher(), name);
            if (now < inCode) return "Copy now = lose rows: " + inCode + " in code, " + now + " here.";
            if (now < ENOUGH_ROWS) return "Only " + count(now, "row") + ": you need 4 to 6.";
        }
        return "";
    }

    private static String count(int n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }

    // The Tunables field of a table: the launcher's own (e.g. "speedByDistancePollen"), or made from the name.
    private static String fieldFor(com.crossbotics.ftchelper.robot.Launcher module, String name) {
        String f = module == null ? null : module.tableField(name);
        if (f != null) return f;
        if (name.equals("main")) return "speedByDistance";
        return "speedByDistance" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // LB turned the wheel on. The robot's launcher owns the feed stop and the feed wheel; it was set up but not
    // started, so nothing moved in INIT. Now a person pressed LB: the feed stop goes to hold (it opens for each
    // feed), and an always-on feed wheel may run while the wheel is spun up.
    private void startRobotLauncher() {
        com.crossbotics.ftchelper.robot.Launcher module = robotLauncher();
        if (module == null || module.part() != launcher) return;
        launcher.setRpm(rpm);
        module.startFeedForTuner();
        module.spinUp();
    }

    // LB turned the wheel off (or help opened): the robot's launcher knows it, so an always-on feed wheel stops.
    private void stopRobotLauncher() {
        com.crossbotics.ftchelper.robot.Launcher module = robotLauncher();
        if (module != null && module.part() == launcher) module.stop();
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
        // START: the camera's START work, as in a match (an unfinished lighting test keeps its best setting).
        if (!startedCamera) {
            startedCamera = true;
            if (robotCamera != null) robotCamera.started();
        }
        // The odometry: a fresh pose every loop (the rest of the robot stays still).
        if (odometry != null) odometry.update();
        if (camera != null) {
            camera.update();
            TargetObservation seen = findTarget(camera);
            target = seen != null && seen.isFresh(System.nanoTime(), MAX_TARGET_AGE_MS) ? seen : null;
            // "does not see the goal" is no longer true once it does: the note goes.
            if (target != null && note.startsWith(NO_GOAL)) note = "";
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
        // Y: a tap (let go before RELIGHT_HOLD_SEC) = the next field; a hold = the lighting test again.
        boolean y = gamepad1.y && inputsReady();
        if (y && Double.isNaN(ySince)) {
            ySince = nowSec();
            yUsed = false;
        } else if (y && !yUsed && nowSec() - ySince >= RELIGHT_HOLD_SEC) {
            yUsed = true;
            relight();
        } else if (!y && !Double.isNaN(ySince)) {
            if (!yUsed) nextField();
            ySince = Double.NaN;
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
        // SAFETY: RT held before LB turned the flywheel on must not start shooting: a fresh press does.
        if (!rt || !spinning) continuous = false;
        else if (freshPress) continuous = true;   // a new press tries again (shoot() clears an old jam)
        else if (!launcher.getLastProblem().isEmpty()) continuous = false;
        // RT let go while its shot waits for the aim: that shot is dropped too.
        if (!rt && shotFromRt && !Double.isNaN(aimingSince)) cancelShot(null);
        if (spinning && continuous && !launcher.isShooting() && Double.isNaN(aimingSince)) shootOne(true);
        // A shot waiting: the launcher feeds it when Ready and aimed. Not aimed for AIM_TIMEOUT_SEC (or the goal
        // lost): it is dropped and the aim stops, so no table point comes from a shot that was not aimed. The
        // time counts only while NOT aimed (spinning up after a speed change is fine).
        if (!Double.isNaN(aimingSince)) {
            if (!spinning) {
                cancelShot(null);
            } else if (!launcher.isShooting()) {
                // Fed, or the launcher gave up (its note shows). Fed with RT held: the next shot aims again
                // (shootOne relocks). Otherwise the aim stops, so the robot never turns on its own later (when it
                // is moved to the next spot, say).
                aimingSince = Double.NaN;
                if (!launcher.getLastProblem().isEmpty() || !continuous) cancelShot(null);
            } else if (launcher.getShotsLeft() > 0 && nowSec() - shotAskedAt > SHOT_WAIT_SEC) {   // not while it feeds
                StringBuilder why = new StringBuilder("No shot: not Ready in " + FinderResults.num(SHOT_WAIT_SEC, 0) + " s.");
                for (String w : notReadyWords(launcher.whyNotReady())) why.append("\n").append(w);
                cancelShot(why + "\nNext: check the speed and the battery.");
            } else if (aimedNow()) {
                aimingSince = nowSec();
            } else if (nowSec() - aimingSince > AIM_TIMEOUT_SEC) {
                double e = robotDef.aim().errorDeg();
                cancelShot("No shot: not aimed for " + FinderResults.num(AIM_TIMEOUT_SEC, 0) + " s."
                        + (target == null || Double.isNaN(e) ? "\nThe camera does not see the goal."
                           : "\nIt was " + FinderResults.num(Math.abs(e), 1) + " deg off.")
                        + "\nNext: point it closer, then {RB} again.");
            }
        }
        // No shot waiting and RT not held: the aim is off, so the robot never turns on its own.
        if (aims && Double.isNaN(aimingSince) && !continuous && robotDef.aim().mode().isOn()) robotDef.aim().mode().off();
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

    // Hold Y: the robot camera's lighting test again (the camera sees no targets until it is done).
    private void relight() {
        if (robotCamera == null || camera != robotCamera.source()) {
            note = "No lighting test for this camera.";
            return;
        }
        stopAiming();
        continuous = false;
        note = robotCamera.relight() ? "Lighting test again.\nNext: aim the camera at a tag. Then wait."
                : "No lighting test. It needs autoExposure() in\nRobot.java, and autoLighting not false.";
    }

    // The hood angle for the tally: NaN without a hood.
    private double hoodNow() {
        return hood == null ? Double.NaN : hoodDeg;
    }

    @Override
    protected void show(RobotTelemetry t) {
        String stop = "{LB} = stop. ";
        if (spinning) t.warn("Flywheel ON. " + stop + "Nobody in front.");
        else t.warn("{LB} spins the flywheel. Nobody in front.");
        if (robotCamera != null && robotCamera.lighting() != null) {
            com.crossbotics.ftchelper.vision.LightingSweep light = robotCamera.lighting();
            // Only while the camera still sees nothing: a goal in view means the setting it kept works here.
            if (light.gaveUp() && target == null) {
                t.warn("The camera saw NO tag in its lighting test.");
                t.warn("Point it at the goal's tag. Hold {Y} 0.5 s.");
            } else if (!light.isDone()) {
                t.warn("Lighting test runs. Keep a tag in view.");
            }
        }
        if (!Double.isNaN(lastSagRpm)) {
            t.warn("Last shot: flywheel " + FinderResults.num(lastSagRpm, 0) + " RPM too slow.");
            t.warn("Wait for Ready : YES before each shot.");
        }
        if (atLimit) t.warn("At the limit (maxRpm, or the hood's end).");
        showProblem(t);
        String replace = replaceWarning();
        if (!replace.isEmpty()) t.warn(replace);

        double d = distance();
        t.section("Shot tuner");
        textLines(t, "Next: " + nextStep(d));
        t.line((field == Field.SPEED ? "> " : "  ") + "Speed", FinderResults.num(rpm, 0) + " RPM, now "
                + FinderResults.num(launcher.getFlywheel().getRpm(), 0) + (spinning ? "" : "  (OFF)"));
        if (hood != null) t.line((field == Field.HOOD ? "> " : "  ") + "Hood", hoodText(hoodDeg));
        if (names.size() > 1) t.line((field == Field.TABLE ? "> " : "  ") + "Table", tuning.toUpperCase(java.util.Locale.US));
        String dText = Double.isNaN(d) ? "no goal seen" : FinderResults.num(d, 1) + " in (" + distanceFrom + ")";
        t.line((byHand() && field == Field.DISTANCE ? "> " : "  ") + "Distance", dText);
        TargetObservation odo = odometryGoal();
        // Both seen: compare them. Far apart = move Tunables.Aim's goal point (one table must fit both).
        if (odo != null && target != null && target.hasDistance()) {
            t.line("  Odometry says", FinderResults.num(odo.distanceIn, 1) + " in");
        }
        // With lines to copy the screen is full: the setup lines and key hints make room (the help page has the keys).
        boolean copying = !results.isEmpty();
        if (robotDef != null && !copying) t.line("  Alliance", alliance + (matchGoal() ? " (its goal only)" : ""));
        if (!aims) {
            if (!copying) t.text(robotDef != null && robotDef.aim().turretAims()
                    ? "It does NOT turn the turret. Point it yourself." : "It does NOT aim. Point it at the goal yourself.");
        } else if (!Double.isNaN(aimingSince)) {
            double e = robotDef.aim().errorDeg();
            t.line("  Aim", Double.isNaN(e) ? "goal NOT seen" : "turning, " + FinderResults.num(Math.abs(e), 1) + " deg off");
        }
        t.line("Ready", readyWords());
        int waiting = trials.unmarkedCount();
        if (waiting > 0) t.line("Shots to mark", waiting);
        // The way teams tune: hits / shots at this speed (and hood), and every one tried at this spot.
        int shots = tally.shots(rpm, hoodNow());
        if (!copying || shots > 0) t.line("Went in at " + FinderResults.num(rpm, 0) + " RPM", tally.hits(rpm, hoodNow()) + " of " + shots
                + (shots < 3 ? " (shoot 3 to 5)" : ""));
        if (tally.tries() > 1) {
            String[] tried = tally.summary().split(", ");
            for (int i = Math.max(0, tried.length - 4); i < tried.length; i++) {
                t.text("  Tried " + tried[i].replaceAll("(\\d+)/(\\d+)", "$1 of $2").replace(" @", ","));
            }
        }
        // Every table's point count, so you know when you are done (4-6 each).
        java.util.List<String> counts = new java.util.ArrayList<>();
        for (String n : names) counts.add((names.size() > 1 ? n.toUpperCase(java.util.Locale.US) + " " : "") + tables.get(n).size());
        if (!copying || names.size() > 1) t.line("Distances saved", FinderResults.commas(counts) + " (need 4 to 6)");
        t.debug("Back at speed after a piece", Double.isNaN(recoverySec) ? "-" : FinderResults.num(recoverySec, 2) + " s");
        boolean moreFields = hood != null || byHand() || names.size() > 1;
        if (!copying) {
            if (moreFields) t.text("D-pad changes the > line. {Y} = next line.");
            else t.text("D-pad = change the speed.");
        }
        textLines(t, note);
    }

    // The launcher's last problem, in words that cannot read as a pass.
    private void showProblem(RobotTelemetry t) {
        String problem = launcher.getLastProblem();
        if (problem.isEmpty()) return;
        if (problem.startsWith("gave up: ")) {
            t.warn("No shot: it waited too long.");
            for (String w : notReadyWords(problem.substring("gave up: ".length()))) t.warn(w);
        } else if (problem.equals("feeder jammed")) {
            t.warn("The feeder is jammed. {LB} = stop.");
            t.warn("Clear it, then shoot again.");
        } else {
            for (String part : problem.split(" / ")) t.warn(part);
        }
    }

    // "YES", or the first thing that is missing. The aim turns on only at RB, so before that it is not a problem.
    private String readyWords() {
        if (!spinning) return "no. Flywheel is OFF";
        boolean aimLater = aims && Double.isNaN(aimingSince);
        boolean rest = launcher.getFlywheel().isReady() && (hood == null || hood.isDone());
        if (aimLater && rest) return "YES ({RB} turns to the goal first)";
        java.util.List<String> not = notReadyWords(launcher.whyNotReady());
        if (aimLater) not.remove("NOT aimed at the goal");
        return not.isEmpty() ? "YES" : "no. " + not.get(0);
    }

    // The next thing to do, in a few words, from what the tuner sees now.
    private String nextStep(double d) {
        if (trials.unmarkedCount() > 0) return "mark the oldest shot.\n{A} = it went IN. {B} = it MISSED.";
        if (!spinning) return "press {LB}: the flywheel spins up.";
        if (Double.isNaN(d)) {
            return odometryDistance ? "point the camera at the goal.\nStill none? Check the odometry." : "point the camera at the goal.";
        }
        if (launcher.isShooting()) return "wait for the shot. {LB} = cancel it.";
        ShotPoints points = tables.get(tuning);
        boolean savedHere = false;
        for (ShotTable.Shot p : points.points()) {
            if (Math.abs(p.distance - d) <= SPOT_MOVE_IN) savedHere = true;
        }
        if (savedHere && tally.isEmpty()) {
            if (points.size() >= ENOUGH_ROWS) return "copy the lines below,\nor move 1-2 ft and add a distance.";
            return "move 1-2 ft farther. Do the same there.";
        }
        if (tally.shots(rpm, hoodNow()) < 3) {
            return launcher.getFlywheel().isReady() ? "{RB} = shoot one (hold {RT} = keep going)."
                    : "wait for Ready : YES.";
        }
        if (tally.tries() < 2) return "D-pad to another speed. Shoot 3 to 5.\n{X} = save the best speed here.";
        return "{X} = save the best speed here.";
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Safety glasses. Nobody in the line of fire.",
            "1. Park at the nearest distance. {LB} = flywheel.",
            "2. Ready: {RB} = shoot one. Hold {RT} = keep on.",
            "3. {A} = went IN, {B} = MISSED (the oldest shot).",
            "   Left stick click = undo a mark.",
            "   Right stick click = no piece came out.",
            "4. 3 to 5 shots a speed, 2 or 3 speeds.",
            "   D-pad up/down 100 RPM, right/left 10 RPM.",
            "5. {X} = save. Move 1-2 ft. 4 to 6 distances.",
            "{Y} = D-pad line. Hold {Y} 0.5 s = lighting test.",
            "Hood: up/down = a fifth, right/left = a small step.",
            "Hold {BACK} 1 s = delete the distance saved here.",
            "The new line REPLACES the whole old table.",
        };
    }

    // Help open: the flywheel, feeding and wheels stop; the camera keeps running.
    @Override
    protected void pause() {
        ySince = Double.NaN;   // a Y held into help is not a tap after it
        yUsed = true;
        spinning = false;
        continuous = false;
        stopAiming();
        launcher.stop();
        stopRobotLauncher();
        if (driving != null) driving.stop();
        others.stop();
    }

    @Override
    protected void stopAll() {
        RuntimeException first = null;
        try { if (aims && robotDef != null) stopAiming(); } catch (RuntimeException e) { first = e; }
        try { if (launcher != null) launcher.stop(); } catch (RuntimeException e) { first = e; }
        try { if (driving != null) driving.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        try { if (others != null) others.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        try { if (camera != null) camera.close(); } catch (RuntimeException e) { if (first == null) first = e; }
        // The robot in five files: every part it made stops too.
        try { if (robotDef != null && robotDef.all() != null) robotDef.all().stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        if (first != null) throw first;
    }
}
