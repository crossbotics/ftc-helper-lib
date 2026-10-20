package io.github.jasoncross.ftchelper.robot;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.launcher.Feeder;
import io.github.jasoncross.ftchelper.launcher.Flywheel;
import io.github.jasoncross.ftchelper.launcher.ShotTable;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.tuning.Tunable;
import io.github.jasoncross.ftchelper.vision.TargetObservation;
import java.util.Collections;
import java.util.List;

/**
 * A flywheel launcher: a motor that spins a wheel to a speed, and an {@link Indexer} that feeds one
 * piece at a time once the speed is right.
 *
 * <pre>{@code
 * launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer")));
 * launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
 *         .speedFromDistance(camera));                       // speed from the camera's distance
 *
 * public static class Launcher {                             // Tunables.java
 *     public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 0;
 *     public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}};   // inches, rpm
 * }
 * }</pre>
 *
 * <p>{@link #fire()} spins up if needed and feeds one piece as soon as the wheel is at speed (it gives
 * up after 3 s and says why). The driver can step the speed up and down ({@link #adjust(int)}).
 * With {@link #speedFromDistance}, the speed comes from {@code speedByDistance} (blended between rows)
 * while the goal is seen; with no goal in view it keeps the last speed.
 *
 * <p>Suggested buttons (operator): LB spins up / down, RB fires one (hold it: keeps firing), D-pad up/down change the speed
 * by {@code stepRpm} (when the D-pad is free).
 *
 * Docs: docs/modules/robot.md (section "Launcher"), docs/modules/launcher.md
 */
public final class Launcher extends Module<Launcher> implements Toggleable {
    private final String motorId;
    private boolean reversed = false;
    private Indexer indexer = null;
    private Camera distanceCamera = null;
    private final Mode speedMode = new Mode("Speed from distance", false);
    private final List<String> checkNames = new java.util.ArrayList<>();
    private final List<java.util.function.BooleanSupplier> checks = new java.util.ArrayList<>();

    io.github.jasoncross.ftchelper.launcher.Launcher part;
    private Flywheel flywheel;
    private ShotTable table = null;
    private Tunable speedRpm, stepRpm;
    private int adjustSteps = 0;
    private boolean spinning = false;
    // fireWhileHeld: true while the button is held, and how many shots this press has asked for.
    private boolean holding = false;
    private int heldShots = 0;
    private double shotAngleDeg = Double.NaN;

    private Launcher(String motor, String motorId) {
        super(motor);
        Motors.get(motorId);
        this.motorId = motorId;
    }

    /**
     * A launcher with its flywheel on this motor.
     *
     * @param motor   the motor's configuration name
     * @param motorId the motor's parts-catalog id (see {@link Motors}), e.g. "gobilda-5203-6000"
     */
    public static Launcher flywheel(String motor, String motorId) {
        return new Launcher(motor, motorId);
    }

    /** The indexer that feeds it. Without one, {@link #fire()} only spins up. */
    public Launcher feeder(Indexer feeder) {
        indexer = feeder;
        feeder.inLauncher = true;
        return this;
    }

    /**
     * Which way a piece leaves the wheel, for the simulator: degrees up from the wheel shape's forward,
     * seen from the side (45 = forward and up, 90 = straight up, 135 = backward and up). For a wheel
     * mounted level on the robot that is the robot's forward; on a turned part (an arm, a tilted
     * slide) the angle turns with it.
     *
     * @param degrees e.g. 147 for a shot up and out of the back
     */
    public Launcher shotAngle(double degrees) {
        if (degrees < 0 || degrees > 180) {
            throw new IllegalArgumentException("Robot.java: shotAngle(" + degrees + "): 0 to 180 degrees (0 forward, 90 up, 180 back)");
        }
        shotAngleDeg = degrees;
        return this;
    }

    /** The flywheel turns the other way. */
    public Launcher reversed() {
        reversed = true;
        return this;
    }

    /**
     * Another check before each piece is fed, e.g. {@code .requireReady("turret aimed", () -> turret.isAimed())}.
     * While it is false the launcher waits (and gives up after 3 s, saying which check failed).
     *
     * @param what  a few words for the Driver Station
     * @param check true when ready
     */
    public Launcher requireReady(String what, java.util.function.BooleanSupplier check) {
        checkNames.add(what);
        checks.add(check);
        return this;
    }

    /** Sets the speed from this camera's distance to the goal ({@code Tunables...speedByDistance}). */
    public Launcher speedFromDistance(Camera camera) {
        distanceCamera = camera;
        return this;
    }

    @Override
    List<Module<?>> children() {
        return indexer == null ? Collections.<Module<?>>emptyList() : Collections.<Module<?>>singletonList(indexer);
    }

    @Override
    void build(Devices devices, TunableSection t) {
        if (distanceCamera != null && distanceCamera.robot != robot) {
            throw new IllegalStateException("Robot.java: " + name() + " takes its speed from a camera that is not "
                    + "added: write camera = add(Camera...) and speedFromDistance(camera)");
        }
        Motors.Kind kind = Motors.get(motorId);
        double codeSpeed = t.number("speedRpm", 2600);
        if (codeSpeed > kind.freeRpm) {
            throw new IllegalStateException("Tunables." + name() + ".speedRpm = " + codeSpeed + " is above the "
                    + motorId + "'s top speed (" + kind.freeRpm + " RPM)");
        }
        speedRpm = t.tunable("speedRpm", 2600, 50).range(0, kind.freeRpm);
        stepRpm = t.tunable("stepRpm", 100, 10).range(0, 1000);
        DcMotorEx motor = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                .coast().runUsingEncoder().done();
        flywheel = new Flywheel(name(), motor)
                .countsPerRev(kind.countsPerRev)
                .maxRpm(kind.freeRpm)
                .tolerance(t.number("readyBandRpm", 60));
        if (t.has("kF")) {
            flywheel.pidf(t.number("kP", 0), t.number("kI", 0), t.number("kD", 0), t.number("kF", 0));
        }
        Feeder feed = indexer != null ? indexer.forLauncher() : new NoFeeder();
        part = new io.github.jasoncross.ftchelper.launcher.Launcher(flywheel, feed)
                .idleRpm(t.number("idleRpm", 0))
                .shotInterval(t.number("shotIntervalSec", 0.25));
        for (int i = 0; i < checks.size(); i++) part.requireReady(checkNames.get(i), checks.get(i));
        double[][] rows = t.table("speedByDistance");
        if (rows != null && rows.length > 0) {
            table = new ShotTable();
            for (double[] r : rows) if (r.length >= 2) table.add(r[0], r[1]);
            part.table(table);
        } else if (distanceCamera != null) {
            throw new IllegalStateException(name() + ".speedFromDistance needs Tunables." + name()
                    + ".speedByDistance = {{inches, rpm}, ...}");
        }
        part.setRpm(speedRpm.get());
    }

    @Override
    void start() {
        if (distanceCamera != null) speedMode.on();
    }

    // ----------------------------- commands -----------------------------

    /** Spins up to the shot speed and keeps it there. */
    public void spinUp() {
        needBuilt(part);
        touch();
        spinning = true;
        part.spinUp();
    }

    /** Back to the idle speed ({@code idleRpm}, 0 = coast to a stop); cancels shots still waiting. */
    public void idle() {
        needBuilt(part);
        touch();
        spinning = false;
        part.idle();
    }

    /** Spins up if idle, idles if spinning. */
    @Override
    public void toggle() {
        if (spinning) idle();
        else spinUp();
    }

    /** Fires one piece (spins up first if needed; feeds as soon as it is at speed). */
    public void fire() {
        fire(1);
    }

    /** Fires {@code count} pieces, one after another. */
    public void fire(int count) {
        needBuilt(part);
        touch();
        spinning = true;
        part.shoot(count);
    }

    /**
     * For a held button, every loop: fires one piece, then the next as soon as that one is fed, for as
     * long as the button is held (the launcher keeps its time between shots). Pair it with
     * {@link #ceaseFire()} on release: {@code g1.rb.hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire)}.
     */
    public void fireWhileHeld() {
        needBuilt(part);
        touch();
        spinning = true;
        if (!holding) {
            holding = true;
            heldShots = 0;
        }
        if (!part.isShooting()) {
            part.shoot(1);
            heldShots++;
        }
    }

    /**
     * The button is let go: no more shots. The press's first shot still goes (a quick tap fires one,
     * even while the wheel is still spinning up); a later shot still waiting is dropped.
     */
    public void ceaseFire() {
        if (part == null) return;
        touch();
        if (holding && heldShots > 1) part.stopShooting();
        holding = false;
    }

    /**
     * Changes the speed by {@code steps} x {@code stepRpm} (e.g. +1 or -1 from a D-pad). Kept until
     * the program ends; the Tunables value is not changed.
     */
    public void adjust(int steps) {
        // Not touch(): changing the speed is not taking the launcher away from an action.
        adjustSteps += steps;
    }

    /** Speed from the camera's distance: on from START when {@link #speedFromDistance} is used. */
    public Mode speedMode() {
        return speedMode;
    }

    /** True when the wheel is at speed (and every other check passes). */
    public boolean isReady() {
        return part != null && part.isReady();
    }

    /** True while shots are waiting or a piece is being fed. */
    public boolean isShooting() {
        return part != null && part.isShooting();
    }

    /** The speed it is aiming for now, RPM. */
    public double targetRpm() {
        return part == null ? 0 : part.getFlywheel().getTargetRpm();
    }

    /** The launcher.Launcher inside, for anything this module does not offer. */
    public io.github.jasoncross.ftchelper.launcher.Launcher part() {
        return part;
    }

    @Override
    public void update() {
        if (speedMode.isOn() && table != null && distanceCamera != null) {
            TargetObservation goal = distanceCamera.goal();
            // No goal in view: keep the last speed (a sudden change would spoil the next shot).
            if (goal != null && goal.hasDistance()) part.setDistance(goal.distanceIn);
        } else {
            part.setRpm(speedRpm.get() + adjustSteps * stepRpm.get());
        }
        part.update();
    }

    @Override
    public void stop() {
        if (part == null) return;
        touch();
        spinning = false;
        part.stop();
    }

    @Override
    void halt() {
        if (part != null) part.stopShooting();   // drop the shots still waiting; keep the speed
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (part != null) part.addTelemetry(t);
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        Pad op = plan.operator();
        op.lb.toggle(this).says("spin up / down");
        op.rb.hold(this::fireWhileHeld, this::ceaseFire).says("fire one; hold: keep firing");
        if (distanceCamera == null) {
            op.dpadUp.press(() -> adjust(1)).says("speed up");
            op.dpadDown.press(() -> adjust(-1)).says("speed down");
        }
    }

    // Its motor is named in Launcher.flywheel(name, id).
    @Override
    String checkPart(String id) {
        throw new IllegalStateException("Robot.java: the launcher's motor part goes in Launcher.flywheel(\"" + device + "\", \"" + id + "\")");
    }

    @Override
    boolean drawsLooks() {
        return true;
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        String feederLine = indexer != null ? ", feeder=" + indexer.device : "";
        out.add(fmt("motor.%s = flywheel, part=%s%s, shotDrop=0.15", device, motorId, feederLine));
        if (looks.isEmpty()) out.add(fmt("shape.%s = flywheel, at=-1,0,6, size=4x2x4, motor=%s   # TODO measure", device, device));
        else lookLines(out, t);
        if (Double.isNaN(shotAngleDeg)) {
            out.add(fmt("shoot.%s = shape=%s, angle=45, share=0.5   # TODO the shot angle (or a shot table: tools/launcher)", device, spinningShapeName()));
        } else {
            out.add(fmt("shoot.%s = shape=%s, angle=%s, share=0.5", device, spinningShapeName(), Shape.n(shotAngleDeg)));
        }
    }

    // No indexer: shots only spin the wheel.
    private static final class NoFeeder implements Feeder {
        @Override public void feedOne() { }
        @Override public boolean isBusy() { return false; }
        @Override public void update() { }
    }
}
