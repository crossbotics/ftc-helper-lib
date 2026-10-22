package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.drive.DriverControls;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.launcher.Launcher;
import com.crossbotics.ftchelper.launcher.ShotTable;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.vision.TargetObservation;
import com.crossbotics.ftchelper.vision.TargetSource;

/**
 * Builds the shot table: the flywheel speed (and hood angle) for each distance. Park the robot at a
 * distance, run the launcher at the speed shown, fine-tune it until the shots go in, and save that
 * point. Do it at 4-6 distances; it writes the {@code ShotTable} code for you.
 *
 * <p>The distance comes from the camera (the same reading the launcher uses in a match). When the
 * camera does not see the goal (or there is none), from the odometry: where the robot is and
 * {@code Tunables.Aim.goalX, goalY} (drive to spots on the field; the robot starts at
 * {@link #startSpotIn()}). With neither, you set it by hand. With your robot settings it can also
 * drive (sticks), so you can move between distances without picking the robot up.
 *
 * <p><b>One table per game piece.</b> With two or more tables in {@code Tunables.Launcher}
 * ({@code speedByDistancePollen}, {@code speedByDistanceNectar}), choose the one you are tuning (Y to
 * TABLE, then the D-pad): each keeps its own points and gets its own line to copy.
 *
 * <pre>
 * Gamepad 1:
 *   Y              = what the D-pad changes: SPEED, HOOD (if any), DISTANCE (no camera, no odometry),
 *                    TABLE (two or more tables)
 *   D-pad up/down  = big step (100 RPM / 5 deg / 6 in), right/left = small step (10 RPM / 0.5 deg / 1 in)
 *                    (TABLE: left/right = the other table)
 *   LB             = flywheel on/off (off at START)
 *   RB             = shoot one         hold RT = keep shooting        B = stop shooting
 *   A              = save this point (replaces one within 1 in)       X = delete the point here
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

    /** The speed it starts at, RPM. Default 3000. */
    protected double startRpm() {
        return 3000;
    }

    /** The hood angle it starts at (if the hood has not been moved yet), degrees. Default 0. */
    protected double startHoodDeg() {
        return 0;
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
    private PositionServo hood;
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
    private double shotAt = Double.NaN;
    private double recoverySec = Double.NaN;
    private String note = "";
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
        hood = launcher.getHood();
        camera = createCamera();
        if (camera != null) camera.start();
        RobotConfig config = robotConfig();
        if (config != null) driving = DriverControls.create(hardwareMap, config.drive(), null);
        others = createSubsystems();
        if (others == null) others = new Subsystems();
        names = new java.util.ArrayList<>(tableNames());
        if (names.isEmpty()) names.add("main");
        for (String n : names) tables.put(n, new ShotPoints(hood != null));
        // Start on the table the launcher starts with (its startTable), else the first.
        com.crossbotics.ftchelper.robot.Launcher module = robotLauncher();
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
        rpm = startRpm();
        if (hood != null) {
            // A hood that was never moved has no angle yet (NaN): start from startHoodDeg().
            double now = hood.getTargetDegrees();
            hoodDeg = Double.isNaN(now) || Double.isInfinite(now) ? startHoodDeg() : now;
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
        if (names.size() > 1) t.line("Tables", FinderResults.commas(names) + " (Y to TABLE, D-pad to choose)");
        t.text("Steps: 1 pick the table. 2 park at the nearest distance. 3 LB spin up, RB shoot,");
        t.text("D-pad changes the speed until shots go in, A saves. 4 back 1-2 ft, again: 4-6 points.");
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
         .bindPress(Pad.G1, Btn.B, () -> { continuous = false; launcher.stopShooting(); })
         .bindPress(Pad.G1, Btn.A, this::savePoint)
         .bindPress(Pad.G1, Btn.X, this::deletePoint);
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
            case SPEED: rpm = Math.max(0, rpm + direction * (big ? 100 : 10)); break;
            case HOOD: hoodDeg += direction * (big ? 5 : 0.5); break;
            case TABLE:
                // The next (or previous) table; its points stay as they are.
                int i = names.indexOf(tuning);
                tuning = names.get(((i + direction) % names.size() + names.size()) % names.size());
                note = "Now tuning the " + tuning.toUpperCase(java.util.Locale.US) + " table.";
                break;
            default: manualDistance = Math.max(0, manualDistance + direction * (big ? 6 : 1)); break;
        }
    }

    // The distance now: from the camera (fresh pictures only), else the odometry, else the hand-set
    // one (only with neither). NaN = none. Sets distanceFrom.
    private double distance() {
        if (target != null && target.hasDistance()) {
            distanceFrom = "camera";
            return target.distanceIn;
        }
        TargetObservation odo = odometryGoal();
        if (odo != null) {
            distanceFrom = "odometry";
            return odo.distanceIn;
        }
        if (byHand()) {
            distanceFrom = "by hand";
            return manualDistance;
        }
        distanceFrom = "";
        return Double.NaN;
    }

    // The goal from the odometry, or null (no odometry or no goal point).
    private TargetObservation odometryGoal() {
        return odometryDistance ? robotDef.aim().fromOdometry() : null;
    }

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
        points.save(d, rpm, hoodDeg);
        note = "Saved " + FinderResults.num(d, 1) + " in -> " + FinderResults.num(rpm, 0) + " RPM"
                + (names.size() > 1 ? " (" + tuning.toUpperCase(java.util.Locale.US) + ")" : "") + ".";
        writeResults();
    }

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
            // The same table for the robot in five files. Its launcher has no hood, so only without one.
            if (hood == null) results.set("shots:" + name + ":t", points.tunablesLine(section, fieldFor(module, name)));
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

    @Override
    protected void show(RobotTelemetry t) {
        t.section("Shot tuner  (Y = what the D-pad changes)");
        t.line((field == Field.SPEED ? "> " : "  ") + "Speed", FinderResults.num(rpm, 0) + " RPM, measured "
                + FinderResults.num(launcher.getFlywheel().getRpm(), 0) + (spinning ? "" : "  (OFF: LB)"));
        if (hood != null) t.line((field == Field.HOOD ? "> " : "  ") + "Hood", FinderResults.num(hoodDeg, 1) + " deg");
        if (names.size() > 1) t.line((field == Field.TABLE ? "> " : "  ") + "Table", tuning.toUpperCase(java.util.Locale.US)
                + "  (" + FinderResults.commas(names) + ")");
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
        t.line("Ready", launcher.isReady() ? "YES" : "no: " + launcher.whyNotReady());
        t.line("Shots fired", launcher.getShotsFired());
        if (!Double.isNaN(recoverySec)) t.line("Recovery", FinderResults.num(recoverySec, 2) + " s");
        if (!launcher.getLastProblem().isEmpty()) t.warn(launcher.getLastProblem());
        t.text("RB = shoot, hold RT = keep shooting, B = stop, A = save point, X = delete point");
        t.text("Next: " + nextStep(d));
        if (!note.isEmpty()) t.text(note);
        for (ShotTable.Shot p : tables.get(tuning).points()) {
            t.text("  " + FinderResults.num(p.distance, 1) + " in: " + FinderResults.num(p.rpm, 0) + " RPM"
                    + (hood != null ? ", " + FinderResults.num(p.hoodDeg, 1) + " deg" : ""));
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
        if (tables.get(tuning).points().isEmpty()) return "RB to shoot; D-pad changes the speed; A saves when shots go in.";
        return "move 1-2 ft and do it again (4-6 points), then copy the lines into Tunables.java.";
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Builds the shot table: distance -> speed (+ hood).",
            "Nobody in the line of fire. Eye protection on.",
            "LB = flywheel on/off (off at START).",
            "Y = what the D-pad changes: SPEED / HOOD /",
            "  DISTANCE (by hand) / TABLE (pollen, nectar...).",
            "D-pad up/down: 100 RPM / 5 deg / 6 in steps;",
            "  right/left: 10 RPM / 0.5 deg / 1 in steps.",
            "RB = shoot one, hold RT = keep shooting, B = stop.",
            "A = save this point, X = delete the point here.",
            "Sticks = drive (if set up), left trigger = slow.",
            "Park near. Change speed until shots go in: A.",
            "Back 1-2 ft, again: 4-6 points. Charged battery.",
            "Copy the lines into Tunables.java at the end.",
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
