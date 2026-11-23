package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.drive.DriverControls;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.launcher.Launcher;
import com.crossbotics.ftchelper.launcher.Turret;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.vision.AimController;
import com.crossbotics.ftchelper.vision.TargetObservation;
import com.crossbotics.ftchelper.vision.TargetSource;
import java.util.List;

/**
 * Finds the camera offset for aiming: where the target sits in the camera's view when the LAUNCHER
 * points straight at it. A camera that is not centered on the launcher (or not mounted straight)
 * needs one, or every shot goes a little to the side. Hold A to aim at the target, shoot, and move
 * the offset until the shots go in the middle.
 *
 * <p>It aims with a turret ({@link #createTurret()}) or, without one, by turning the robot
 * ({@link #robotConfig()} needed). It is set up with these pieces. For the robot in five files, also give
 * {@link #robot()}: then it starts at the {@code cameraOffset} in Tunables.java (class Turret with a turret,
 * class Aim for whole-robot aim), drives with that robot's drive, and shows the Tunables line to copy.
 * Without {@code robot()} it shows the line for the Java helpers.
 *
 * <pre>
 * Gamepad 1:
 *   hold A           = aim at the target
 *   D-pad right/left = aim 0.5 deg more right/left, up/down = 2 deg more right/left
 *                      (shots go LEFT of the middle? press RIGHT)
 *   RB               = shoot one (with createLauncher()), LB = flywheel off
 *   B                = stop the turret and the launcher now
 *   X                = home the turret (if it has a home switch; it will not aim before)
 *   sticks           = drive when not aiming (with robotConfig()), left trigger = slow
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * KEEP PEOPLE OUT OF THE LINE OF FIRE; eye protection on.
 *
 * <pre>{@code
 * @TeleOp(name = "Aim Tuner", group = "Finders")
 * public class AimTuner extends AimTunerOpMode {
 *     @Override protected TargetSource createCamera() { return WebcamTags.create(hardwareMap, "Webcam 1"); }
 *     @Override protected RobotConfig robotConfig() { return new RobotSettings(); }
 *     @Override protected Launcher createLauncher() { return new Robot(hardwareMap).launcher; }
 * }
 * }</pre>
 *
 * Docs: docs/modules/finders.md. Guide: docs/tuning/README.md
 */
public abstract class AimTunerOpMode extends FinderOpMode {

    // ----------------------------- you fill these in -----------------------------

    /**
     * Your robot in five files ({@code return new Robot();}), or null (default). With it the tuner starts
     * from the cameraOffset in Tunables.java and shows the line for that file.
     */
    protected RobotDefinition robot() {
        return null;
    }

    /** Your camera. It is started for you. */
    protected abstract TargetSource createCamera();

    /** Which target to aim at. Default: the first one the camera sees with a direction. */
    protected TargetObservation findTarget(TargetSource camera) {
        for (TargetObservation o : camera.targets()) if (o.hasBearing()) return o;
        return null;
    }

    /** Your turret, or null (default) to aim by turning the robot. */
    protected Turret createTurret() {
        return null;
    }

    /** Which way the turret turns to find its home switch: +1 or -1 (see {@code Turret.home}). Default -1. */
    protected double homeDirection() {
        return -1;
    }

    /** True (default) if the camera turns with the turret; false if it is on the robot body. */
    protected boolean cameraOnTurret() {
        return true;
    }

    /**
     * Your robot settings: needed to aim by turning the robot, and to drive. Default: the drive of
     * {@link #robot()}, or null = neither.
     */
    protected RobotConfig robotConfig() {
        return robotDef == null ? null : robotDef.robotConfig();
    }

    /** The aiming controller for turning the robot (the same gains as in your TeleOp). */
    protected AimController createAimController() {
        return new AimController(0.02, 0.003).deadband(1.0).maxPower(0.4);
    }

    /** Your launcher, for test shots, or null (default). With a shot table it uses the camera's distance. */
    protected Launcher createLauncher() {
        return null;
    }

    /** Other mechanisms to keep updated. */
    protected Subsystems createSubsystems() {
        return new Subsystems();
    }

    /**
     * The offset it starts at, degrees. Default NaN = the one in your code now: Tunables.java's
     * {@code cameraOffset} with {@link #robot()}, else the one set on your turret or aim controller (0 if
     * none was set). A number here: always start from it.
     */
    protected double startOffsetDeg() {
        return Double.NaN;
    }

    /** The speed for test shots when the launcher has no shot table, RPM. Default 3000. */
    protected double shotRpm() {
        return 3000;
    }

    // ----------------------------- the tuner -----------------------------

    private RobotDefinition robotDef;   // the robot in five files, or null
    private String section = "Aim";     // where cameraOffset is in Tunables.java
    private double startedFrom;
    private String startedWhere = "";
    private TargetSource camera;
    private Turret turret;
    private DriverControls driving;
    private AimController aim;
    private Launcher launcher;
    private Subsystems others;
    private double offset;
    private TargetObservation target;
    private boolean wasAiming = false;

    @Override protected String finderName() { return "Aim tuner"; }

    @Override
    protected void setup() {
        robotDef = robot();
        camera = createCamera();
        camera.start();
        turret = createTurret();
        RobotConfig config = robotConfig();
        if (config != null) driving = DriverControls.create(hardwareMap, config.drive(), null);
        if (turret == null && driving == null) {
            throw new IllegalStateException("The aim tuner needs createTurret() or robotConfig() to aim.");
        }
        aim = createAimController();
        launcher = createLauncher();
        others = createSubsystems();
        if (others == null) others = new Subsystems();
        offset = startOffset();
        startedFrom = offset;
        applyOffset();
    }

    // Where the offset starts: the given one, else the code's (never 0 over a number the team already found).
    private double startOffset() {
        if (robotDef != null) {
            robotDef.describe();
            if (turret != null) {
                section = "Turret";
                for (Module<?> m : robotDef.modules()) {
                    if (m instanceof com.crossbotics.ftchelper.robot.Turret) {
                        section = m.name();
                        break;
                    }
                }
            }
        }
        double given = startOffsetDeg();
        if (!Double.isNaN(given)) {
            startedWhere = "this program";
            return given;
        }
        if (robotDef != null) {
            startedWhere = "Tunables.java";
            return codeValue(robotDef.tunablesClass(), section, "cameraOffset", 0);
        }
        startedWhere = "your code";
        return turret != null ? turret.getCameraOffset() : aim.getOffset();
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.warn(turret != null ? "Hold {A}: the TURRET turns by itself." : "Hold {A}: the ROBOT turns by itself.");
        if (launcher != null) t.warn("{RB} SHOOTS. Safety glasses. Nobody in front.");
        if (driving != null) t.warn("The sticks drive. Let go = stop.");
        if (turret != null) t.warn("{X} turns it to find its start. {B} = stop.");
        t.text("Aim tuner: makes shots go in the MIDDLE");
        t.text("of the goal, not left or right of it.");
        t.line("Starts from", FinderResults.num(startedFrom, 1) + " deg (" + startedWhere + ")");
        if (Math.abs(startedFrom) >= 0.05) t.text("  That is " + side(startedFrom) + ".");
        t.line("Camera", camera.status());
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> changeOffset(0.5))
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> changeOffset(-0.5))
         .bindPress(Pad.G1, Btn.DPAD_UP, () -> changeOffset(2))
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> changeOffset(-2))
         .bindPress(Pad.G1, Btn.RB, this::shoot)
         .bindPress(Pad.G1, Btn.LB, () -> { if (launcher != null) launcher.stop(); })
         .bindPress(Pad.G1, Btn.B, () -> {
                    // SAFETY: one press stops what moves by itself (a homing, a spin-up, a waiting shot).
                    if (turret != null) turret.stop();
                    if (launcher != null) launcher.stop();
                })
         .bindPress(Pad.G1, Btn.X, () -> { if (turret != null) turret.home(homeDirection()); });
    }

    // Positive = aim further RIGHT. Aiming stops when the target's bearing equals the offset; to point
    // further right, the target must appear further LEFT in the camera's view (a bigger bearing), so
    // the offset goes UP.
    private void changeOffset(double deg) {
        offset += deg;
        applyOffset();
        writeResults();
    }

    private void applyOffset() {
        aim.offset(offset);
        if (turret != null) turret.cameraOffset(offset);
    }

    private void shoot() {
        if (launcher == null) return;
        if (launcher.hasTable() && target != null && target.hasDistance()) launcher.setDistance(target.distanceIn);
        else launcher.setRpm(shotRpm());
        launcher.shoot(1);
    }

    private void writeResults() {
        String value = FinderResults.num(offset, 1);
        if (robotDef != null || hasRobotFiles()) {
            results.setTunable("aim", section, "cameraOffset", value);
        } else if (turret != null) {
            results.setIn("aim", FinderResults.HELPER_CODE, "turret.cameraOffset(" + value + ")");
        } else {
            results.setIn("aim", FinderResults.HELPER_CODE, "new AimController(...).offset(" + value + ")");
        }
        results.set("aim:when", "Copy it when shots at 2 or 3 distances");
        results.set("aim:when2", "all go in the middle.");
    }

    @Override
    protected void step() {
        camera.update();
        target = findTarget(camera);
        // A (and the sticks) only count once gamepad 1 was let go after START or help (inputsReady()).
        boolean aiming = gamepad1.a && inputsReady();
        // A let go: forget the last picture, so the next aim starts fresh (no stale D part).
        if (wasAiming && !aiming) {
            aim.reset();
            // SAFETY: let go = stop. Without this the turret went on toward the last place it was sent.
            if (turret != null && !turret.isHoming()) turret.stop();
        }
        wasAiming = aiming;
        if (turret != null) {
            if (aiming) turret.aimAtTarget(target, cameraOnTurret());
            turret.update();
        }
        if (driving != null) {
            if (aiming && turret == null) {
                // Turn the robot toward the target (the controller gives 0 without a fresh target).
                driving.drive(0, 0, aim.turnPower(target), 1);
            } else {
                if (inputsReady()) driving.update(gamepad1);
                else driving.hold();
            }
        }
        if (launcher != null) launcher.update();
        others.update();
    }

    @Override
    protected void show(RobotTelemetry t) {
        t.warn("Hold {A} = the " + (turret != null ? "turret" : "robot") + " turns. Let go = stop.");
        if (launcher != null) t.warn("{RB} shoots. Nobody in front. {B} = stop.");
        t.section("Aim tuner");
        boolean seen = target != null && target.hasBearing();
        double left = seen ? target.bearingDeg - offset : 0;   // what is left to turn, + = to the left
        if (turret != null && !turret.getFault().isEmpty()) {
            t.warn(turret.getFault());
            t.text("Next: press STOP. Check the turret and switch.");
        } else if (turret != null && turret.isHoming()) {
            t.text("Finding the turret's start. {B} = stop.");
        } else if (turret != null && !turret.isHomed()) {
            t.warn("The turret does not know where it points.");
            t.text("Next: hands clear, then {X}: it finds its start.");
        } else if (!seen) {
            t.text("Next: point the camera at the goal's tag.");
        } else if (launcher == null) {
            t.text("Next: hold {A}. Is it on the middle of the goal?");
            t.text("Too far left: D-pad RIGHT. Too far right: LEFT.");
        } else {
            t.text("Next: hold {A}, then {RB} = shoot one.");
            t.text("Shot went LEFT: D-pad RIGHT.");
            t.text("Shot went RIGHT: D-pad LEFT.");
        }
        t.line("Aim offset", side(offset));
        if (results.isEmpty()) t.line("In " + startedWhere, side(startedFrom));
        if (seen) {
            t.text("Goal is " + degrees(target.bearingDeg) + " of the camera's middle.");
            t.line("Still to turn", degrees(left) + (Math.abs(left) <= 1 ? ": OK" : ""));
            if (target.hasDistance()) t.line("Distance", FinderResults.num(target.distanceIn, 1) + " in");
        }
        if (turret != null) t.line("Turret angle", FinderResults.num(turret.getAngle(), 1) + " deg");
        if (launcher != null) {
            List<String> not = notReadyWords(launcher.whyNotReady());
            t.line("Launcher", not.isEmpty() ? "ready" : not.get(0));
        }
    }

    // An offset in words: "10.0 deg to the LEFT" (+ is to the right).
    static String side(double offset) {
        return FinderResults.num(Math.abs(offset), 1) + " deg"
                + (Math.abs(offset) < 0.05 ? "" : offset > 0 ? " to the RIGHT" : " to the LEFT");
    }

    // "26.5 deg RIGHT": a word, never a sign to remember (a bearing is + to the left).
    private static String degrees(double ccw) {
        return FinderResults.num(Math.abs(ccw), 1) + " deg" + (Math.abs(ccw) < 0.05 ? "" : ccw > 0 ? " LEFT" : " RIGHT");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Makes shots go in the middle of the goal.",
            "Safety glasses. Nobody in front.",
            "1. Park 4-6 ft from the goal, facing it.",
            "   Turret: {X} = find its start. {B} = stop.",
            "2. Hold {A} = aim. {RB} = shoot one.",
            "   {LB} = flywheel off.",
            "3. Went LEFT: D-pad RIGHT. Went RIGHT: LEFT.",
            "   Right/left 0.5 deg. Up = 2 right, down = 2 left.",
            "4. Repeat at 2-3 distances. Offset changes with",
            "   distance? Move the camera in line with the",
            "   launcher.",
            "Sticks = drive. With a turret also while it aims.",
        };
    }

    // Help open: the launcher, turret and wheels stop; the camera keeps running.
    @Override
    protected void pause() {
        if (launcher != null) launcher.stop();
        if (turret != null) turret.stop();
        if (driving != null) driving.stop();
        others.stop();
    }

    @Override
    protected void stopAll() {
        RuntimeException first = null;
        try { if (launcher != null) launcher.stop(); } catch (RuntimeException e) { first = e; }
        try { if (turret != null) turret.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        try { if (driving != null) driving.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        try { if (others != null) others.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        try { if (camera != null) camera.close(); } catch (RuntimeException e) { if (first == null) first = e; }
        if (first != null) throw first;
    }
}
