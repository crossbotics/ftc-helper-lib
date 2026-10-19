package io.github.jasoncross.ftchelper.robot;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.tuning.Tunable;
import io.github.jasoncross.ftchelper.vision.TargetObservation;
import java.util.List;

/**
 * A turret on one motor that turns inside its limits, by hand or following the goal with a
 * {@link Camera}. Angles in degrees, counterclockwise +, 0 = straight forward (where it points when
 * the program starts).
 *
 * <pre>{@code
 * turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));   // Robot.java
 *
 * public static class Turret {                                // Tunables.java
 *     public static double min = -90, max = 90;              // its range: it never turns past these
 *     public static double kP = 0.02, kI = 0, kD = 0.001;
 *     public static double maxPower = 0.8, tolerance = 1.0, cameraOffset = 0;
 * }
 * }</pre>
 *
 * <p><b>Follow mode</b> ({@link #followMode()}, on from START when {@link #follows} is used): every
 * loop it turns toward the camera's goal. A goal past the range: it waits at the limit and the
 * Driver Station says "goal out of turret range". The stick takes over while it is moved, then
 * following goes on.
 *
 * <p>Suggested buttons: operator right stick X turns it by hand; operator BACK switches following
 * on and off.
 *
 * Docs: docs/modules/robot.md (section "Turret")
 */
public final class Turret extends Module<Turret> {
    private final String motorId;
    private double gearRatio = 1;
    private boolean reversed = false;
    private Camera camera = null;
    private boolean cameraOnTurret = true;
    private final Mode follow = new Mode("Turret follow", false);
    io.github.jasoncross.ftchelper.launcher.Turret part;
    private Tunable kP, kI, kD;
    private boolean stickMoving = false;
    private double min, max;

    private Turret(String motor, String motorId) {
        super(motor);
        Motors.get(motorId);
        this.motorId = motorId;
    }

    /**
     * A turret on this motor.
     *
     * @param motor   the motor's configuration name
     * @param motorId the motor's parts-catalog id (see {@link Motors})
     */
    public static Turret motor(String motor, String motorId) {
        return new Turret(motor, motorId);
    }

    /** Motor turns per turret turn (the gears or belt). Default 1. */
    public Turret gearRatio(double motorTurnsPerTurretTurn) {
        gearRatio = motorTurnsPerTurretTurn;
        return this;
    }

    /** Positive power turns the other way (if + turns it clockwise). */
    public Turret reversed() {
        reversed = true;
        return this;
    }

    /** Follows this camera's goal (follow mode on from START). The camera turns with the turret. */
    public Turret follows(Camera c) {
        camera = c;
        return this;
    }

    /** The camera is on the robot's body, not on the turret. */
    public Turret cameraOnBody() {
        cameraOnTurret = false;
        return this;
    }

    double countsPerDegree() {
        return Motors.get(motorId).countsPerRev * gearRatio / 360.0;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        if (camera != null && camera.robot != robot) {
            throw new IllegalStateException("Robot.java: " + name() + " follows a camera that is not added: "
                    + "write camera = add(Camera...) and follows(camera)");
        }
        min = t.number("min", -90);
        max = t.number("max", 90);
        kP = t.tunable("kP", 0.02, 0.002).range(0, 1);
        kI = t.tunable("kI", 0, 0.0005).range(0, 1);
        kD = t.tunable("kD", 0.001, 0.0002).range(0, 1);
        DcMotorEx motor = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                .brake().resetEncoder().runWithoutEncoder().done();
        part = new io.github.jasoncross.ftchelper.launcher.Turret(name(), motor)
                .countsPerDegree(countsPerDegree())
                .limits(min, max)
                .pid(kP.get(), kI.get(), kD.get(), 0)
                .maxPower(t.number("maxPower", 0.8))
                .tolerance(t.number("tolerance", 1.0))
                .slowZone(t.number("slowZone", 10), t.number("slowZonePower", 0.25))
                .cameraOffset(t.number("cameraOffset", 0));
    }

    @Override
    void start() {
        if (camera != null) follow.on();
    }

    // ----------------------------- commands -----------------------------

    /** Following the camera's goal: switch it with a button ({@code g2.back.toggle(robot.turret.followMode())}). */
    public Mode followMode() {
        return follow;
    }

    /**
     * Turns to an angle (inside the limits) and stops following.
     *
     * @param deg robot-relative, counterclockwise +, 0 = forward
     */
    public void aimAt(double deg) {
        needBuilt(part);
        touch();
        follow.off();
        part.aimAt(deg);
    }

    /**
     * By hand, -1 to 1 (a stick, every loop). While it is moved, following pauses.
     *
     * @param power + = counterclockwise
     */
    public void manual(double power) {
        needBuilt(part);
        stickMoving = power != 0;
        if (stickMoving) touch();
        part.manual(power);
    }

    /** True when it points where it should (within the tolerance). */
    public boolean isAimed() {
        return part != null && part.isAimed();
    }

    /** True when the goal is past its range (it waits at the limit). */
    public boolean isOutOfRange() {
        return part != null && part.isOutOfRange();
    }

    /** The angle now, degrees. */
    public double angle() {
        return part == null ? 0 : part.getAngle();
    }

    /** The launcher.Turret inside, for anything this module does not offer. */
    public io.github.jasoncross.ftchelper.launcher.Turret part() {
        return part;
    }

    @Override
    public void update() {
        part.pid(kP.get(), kI.get(), kD.get(), 0);
        if (follow.isOn() && camera != null && !stickMoving) {
            TargetObservation goal = camera.goal();
            part.aimAtTarget(goal, cameraOnTurret);
        }
        part.update();
    }

    @Override
    public void stop() {
        if (part == null) return;
        touch();
        follow.off();
        part.stop();
    }

    @Override
    void halt() {
        if (part != null && part.isHomed()) part.aimAt(part.getAngle());   // hold where it points now
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (part == null) return;
        if (follow.isOn() && part.isOutOfRange()) t.warn("goal out of turret range");
        t.line(name(), fmt("%.0f deg%s", part.getAngle(), follow.isOn() ? " following" : ""));
        part.addTelemetry(t);
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        Pad op = plan.operator();
        op.rightStickX.use(v -> manual(-v)).says("by hand (right = clockwise)");
        if (camera != null) op.back.toggle(follow).says("follow the goal on/off");
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        double cpd = countsPerDegree();
        double lo = t.number("min", -90), hi = t.number("max", 90);
        out.add(fmt("motor.%s = lift, part=%s, min=%.0f, max=%.0f, gravity=0", device, motorId, lo * cpd, hi * cpd));
        // start = min: the simulator counts the shape's angle from the motor's min stop.
        out.add(fmt("shape.%s = turret, at=0,0,8, size=6x6x2, motor=%s, ticksPerDeg=%.3f, start=%.0f   # TODO measure",
                device, device, cpd, lo));
    }
}
