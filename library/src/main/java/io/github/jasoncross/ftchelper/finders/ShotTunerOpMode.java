package io.github.jasoncross.ftchelper.finders;

import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.drive.DriverControls;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.launcher.Launcher;
import io.github.jasoncross.ftchelper.launcher.ShotTable;
import io.github.jasoncross.ftchelper.mechanism.PositionServo;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.vision.TargetObservation;
import io.github.jasoncross.ftchelper.vision.TargetSource;

/**
 * Builds the shot table: the flywheel speed (and hood angle) for each distance. Park the robot at a
 * distance, run the launcher at the speed shown, fine-tune it until the shots go in, and save that
 * point. Do it at 4-6 distances; it writes the {@code ShotTable} code for you.
 *
 * <p>The distance comes from the camera (the same reading the launcher uses in a match), or, without
 * a camera, you set it by hand. With your robot settings it can also drive (sticks), so you can move
 * between distances without picking the robot up.
 *
 * <pre>
 * Gamepad 1:
 *   Y              = what the D-pad changes: SPEED, HOOD (if any), DISTANCE (no camera)
 *   D-pad up/down  = big step (100 RPM / 5 deg / 6 in), right/left = small step (10 RPM / 0.5 deg / 1 in)
 *   LB             = flywheel on/off (off at START)
 *   RB             = shoot one         hold RT = keep shooting        B = stop shooting
 *   A              = save this point (replaces one within 1 in)       X = delete the point here
 *   sticks         = drive (with robotConfig()), left trigger = slow
 * </pre>
 *
 * KEEP PEOPLE OUT OF THE LINE OF FIRE; eye protection on.
 *
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

    /** Your launcher (flywheel, feeder, hood), set up as in your TeleOp. */
    protected abstract Launcher createLauncher();

    /** Your camera, or null to set the distance by hand. It is started for you. */
    protected TargetSource createCamera() {
        return null;
    }

    /** Which target the distance is measured to. Default: the first one the camera sees with a distance. */
    protected TargetObservation findTarget(TargetSource camera) {
        for (TargetObservation o : camera.targets()) if (o.hasDistance()) return o;
        return null;
    }

    /** Your robot settings, to drive with the sticks. Null (default) = no driving. */
    protected RobotConfig robotConfig() {
        return null;
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

    // ----------------------------- the tuner -----------------------------

    private enum Field { SPEED, HOOD, DISTANCE }

    private Launcher launcher;
    private PositionServo hood;
    private TargetSource camera;
    private DriverControls driving;
    private Subsystems others;
    private ShotPoints points;
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
        launcher = createLauncher();
        hood = launcher.getHood();
        camera = createCamera();
        if (camera != null) camera.start();
        RobotConfig config = robotConfig();
        if (config != null) driving = DriverControls.create(hardwareMap, config.drive(), null);
        others = createSubsystems();
        if (others == null) others = new Subsystems();
        points = new ShotPoints(hood != null);
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

    // Y: the next thing the D-pad changes. HOOD only with a hood, DISTANCE only without a camera.
    private void nextField() {
        // At most one pass through the list: the first usable one after the current one.
        for (int i = 0; i < Field.values().length; i++) {
            field = Field.values()[(field.ordinal() + 1) % Field.values().length];
            boolean usable = !(field == Field.HOOD && hood == null) && !(field == Field.DISTANCE && camera != null);
            if (usable) return;
        }
    }

    private void change(int direction, boolean big) {
        switch (field) {
            case SPEED: rpm = Math.max(0, rpm + direction * (big ? 100 : 10)); break;
            case HOOD: hoodDeg += direction * (big ? 5 : 0.5); break;
            default: manualDistance = Math.max(0, manualDistance + direction * (big ? 6 : 1)); break;
        }
    }

    // The distance now: from the camera (fresh pictures only), or the hand-set one. NaN = none.
    private double distance() {
        if (camera == null) return manualDistance;
        return target != null && target.hasDistance() ? target.distanceIn : Double.NaN;
    }

    private void savePoint() {
        double d = distance();
        if (Double.isNaN(d)) {
            note = "No distance: the camera does not see the target.";
            return;
        }
        if (Double.isNaN(hoodDeg) || Double.isInfinite(hoodDeg)) {
            note = "No hood angle yet: change it with the D-pad first.";
            return;
        }
        points.save(d, rpm, hoodDeg);
        note = "Saved " + FinderResults.num(d, 1) + " in -> " + FinderResults.num(rpm, 0) + " RPM.";
        writeResults();
    }

    private void deletePoint() {
        double d = distance();
        if (!Double.isNaN(d) && points.removeNear(d)) {
            note = "Deleted the point at " + FinderResults.num(d, 1) + " in.";
            writeResults();
        }
    }

    private void writeResults() {
        results.removeStartingWith("shots:");
        int n = 0;
        for (String line : points.codeLines()) results.set("shots:" + n++, line);
    }

    @Override
    protected void step() {
        if (camera != null) {
            camera.update();
            TargetObservation seen = findTarget(camera);
            target = seen != null && seen.isFresh(System.nanoTime(), MAX_TARGET_AGE_MS) ? seen : null;
        }
        if (driving != null) driving.update(gamepad1);
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
        boolean rt = gamepad1.right_trigger > 0.5;
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
        double d = distance();
        String dText = Double.isNaN(d) ? "no target" : FinderResults.num(d, 1) + " in";
        if (camera == null) t.line((field == Field.DISTANCE ? "> " : "  ") + "Distance", dText + " (by hand)");
        else t.line("  Distance", dText + (target != null && target.hasBearing()
                ? ", bearing " + FinderResults.num(target.bearingDeg, 1) + " deg" : ""));
        if (atLimit) t.warn("At the flywheel's maxRpm or the hood's limit");
        t.line("Ready", launcher.isReady() ? "YES" : "no: " + launcher.whyNotReady());
        t.line("Shots fired", launcher.getShotsFired());
        if (!Double.isNaN(recoverySec)) t.line("Recovery", FinderResults.num(recoverySec, 2) + " s");
        if (!launcher.getLastProblem().isEmpty()) t.warn(launcher.getLastProblem());
        t.text("RB = shoot, hold RT = keep shooting, B = stop, A = save point, X = delete point");
        if (!note.isEmpty()) t.text(note);
        for (ShotTable.Shot p : points.points()) {
            t.text("  " + FinderResults.num(p.distance, 1) + " in: " + FinderResults.num(p.rpm, 0) + " RPM"
                    + (hood != null ? ", " + FinderResults.num(p.hoodDeg, 1) + " deg" : ""));
        }
    }

    @Override
    protected void stopAll() {
        RuntimeException first = null;
        try { if (launcher != null) launcher.stop(); } catch (RuntimeException e) { first = e; }
        try { if (driving != null) driving.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        try { if (others != null) others.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        try { if (camera != null) camera.close(); } catch (RuntimeException e) { if (first == null) first = e; }
        if (first != null) throw first;
    }
}
