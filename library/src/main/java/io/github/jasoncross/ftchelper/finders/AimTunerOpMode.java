package io.github.jasoncross.ftchelper.finders;

import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.drive.DriverControls;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.launcher.Launcher;
import io.github.jasoncross.ftchelper.launcher.Turret;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.vision.AimController;
import io.github.jasoncross.ftchelper.vision.TargetObservation;
import io.github.jasoncross.ftchelper.vision.TargetSource;

/**
 * Finds the camera offset for aiming: where the target sits in the camera's view when the LAUNCHER
 * points straight at it. A camera that is not centered on the launcher (or not mounted straight)
 * needs one, or every shot goes a little to the side. Hold A to aim at the target, shoot, and move
 * the offset until the shots go in the middle.
 *
 * <p>It aims with a turret ({@link #createTurret()}) or, without one, by turning the robot
 * ({@link #robotConfig()} needed).
 *
 * <pre>
 * Gamepad 1:
 *   hold A           = aim at the target
 *   D-pad right/left = aim 0.5 deg more right/left, up/down = 2 deg more right/left
 *                      (shots go LEFT of the middle? press RIGHT)
 *   RB               = shoot one (with createLauncher()), LB = flywheel off
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
 * Docs: docs/modules/finders.md. Guide: docs/guides/finders.md
 */
public abstract class AimTunerOpMode extends FinderOpMode {

    // ----------------------------- you fill these in -----------------------------

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

    /** Your robot settings: needed to aim by turning the robot, and to drive. Null = neither. */
    protected RobotConfig robotConfig() {
        return null;
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

    /** The offset it starts at, degrees (the one in your code now). Default 0. */
    protected double startOffsetDeg() {
        return 0;
    }

    /** The speed for test shots when the launcher has no shot table, RPM. Default 3000. */
    protected double shotRpm() {
        return 3000;
    }

    // ----------------------------- the tuner -----------------------------

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
        offset = startOffsetDeg();
        applyOffset();
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.text("Aim tuner: finds the camera offset so shots go in the middle.");
        t.warn("Nobody in the line of fire. Eye protection on.");
        t.line("Camera", camera.status());
        t.text("Press START.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> changeOffset(0.5))
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> changeOffset(-0.5))
         .bindPress(Pad.G1, Btn.DPAD_UP, () -> changeOffset(2))
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> changeOffset(-2))
         .bindPress(Pad.G1, Btn.RB, this::shoot)
         .bindPress(Pad.G1, Btn.LB, () -> { if (launcher != null) launcher.stop(); })
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
        if (turret != null) {
            results.set("aim", "turret.cameraOffset(" + FinderResults.num(offset, 1) + ")   // with the turret's other settings");
        } else {
            results.set("aim", "new AimController(...).offset(" + FinderResults.num(offset, 1) + ")   // with your aim gains");
        }
    }

    @Override
    protected void step() {
        camera.update();
        target = findTarget(camera);
        // A (and the sticks) only count once gamepad 1 was let go after START or help (inputsReady()).
        boolean aiming = gamepad1.a && inputsReady();
        // A let go: forget the last picture, so the next aim starts fresh (no stale D part).
        if (wasAiming && !aiming) aim.reset();
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
        t.section("Aim tuner  (hold A = aim)");
        t.line("Offset", FinderResults.num(offset, 1) + " deg  (D-pad right/up = aim more to the right)");
        if (target == null || !target.hasBearing()) {
            t.text("No target in view.");
        } else {
            t.line("Bearing", FinderResults.num(target.bearingDeg, 1) + " deg (left = +)");
            t.line("Still to turn", FinderResults.num(target.bearingDeg - offset, 1) + " deg");
            if (target.hasDistance()) t.line("Distance", FinderResults.num(target.distanceIn, 1) + " in");
        }
        if (turret != null) {
            t.line("Turret", FinderResults.num(turret.getAngle(), 1) + " deg");
            if (turret.isHoming()) t.text("Homing...");
            else if (!turret.isHomed()) t.warn("Turret not homed: press X (it will not aim before)");
            if (!turret.getFault().isEmpty()) t.warn(turret.getFault());
        }
        if (launcher != null) {
            t.line("Launcher", launcher.isReady() ? "ready" : launcher.whyNotReady());
            t.text("RB = shoot one, LB = flywheel off. Shots go left: D-pad RIGHT. Shots go right: D-pad LEFT.");
        }
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds the camera offset so shots go in the middle.",
            "Nobody in the line of fire. Eye protection on.",
            "1. Park 4-6 ft from the target, facing it.",
            "   Turret with a home switch: X = home it first.",
            "2. Hold A = aim. RB = shoot one. LB = flywheel off.",
            "3. Shots go LEFT of the middle: D-pad RIGHT.",
            "   Shots go RIGHT: D-pad LEFT. (right/left = 0.5",
            "   deg, up/down = 2 deg.)",
            "4. Repeat at 2-3 distances. If the offset that",
            "   works changes with distance, the camera is off",
            "   to the side: move it onto the launcher's line.",
            "Sticks = drive when not aiming. Help stops homing:",
            "press X again after. STOP saves the line.",
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
