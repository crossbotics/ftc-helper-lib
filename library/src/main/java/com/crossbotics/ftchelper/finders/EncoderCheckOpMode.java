package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.robot.EncoderMotor;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Encoder Check: finds an encoder cable in the wrong port, or missing. It gives each motor in Robot.java a
 * short, low push (30% for 0.3 s), one at a time, and reads EVERY motor port's encoder. Each motor gets a
 * result:
 * <ul>
 *   <li>OK: its own port counted, up.</li>
 *   <li>NO COUNTS: no port counted (encoder cable missing, or the motor did not turn).</li>
 *   <li>counts on "X" instead: another motor's port counted (encoder cables swapped).</li>
 *   <li>counts BACKWARD: its port counted down for positive power (power wires swapped, or a reversed encoder).</li>
 * </ul>
 * Why it matters: a lift, an arm, a flywheel's speed and the drive's distances all trust the encoder. A cable
 * in the wrong port makes them read ANOTHER motor, and they run away or never stop.
 *
 * <p>Each motor is set up as Robot.java sets it up (its direction), so positive power lifts a lift and the
 * count must go UP. A lift's second motor ({@code alsoMotor}) is pushed with its first. A motor whose encoder
 * nothing reads (an intake) may have no cable: "no counts" is fine there. CR servos and servos are not
 * checked; nor is a drive motor whose port reads an odometry pod (the Odometry Tuner checks pods).
 *
 * <p><b>Robot on a block.</b> Hold A: the check runs while A is held (let go = everything stops). Y = start
 * again. It is in "Tune and check" for every robot in five files. On its own, in TeamCode:
 * <pre>{@code
 * @TeleOp(name = "Encoder Check", group = "Finders")
 * public class EncoderCheck extends EncoderCheckOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 *
 * Docs: docs/modules/finders.md. Guide: docs/guides/finders.md, "Encoder Check"
 */
public abstract class EncoderCheckOpMode extends FinderOpMode {

    /** Your robot in five files ({@code return new Robot();}): its motors are checked. */
    protected abstract RobotDefinition robot();

    private EncoderCheck check;
    private final List<DcMotorEx> pushed = new ArrayList<>();
    private boolean aHeld = false;
    private boolean again = false;

    @Override protected String finderName() { return "Encoder check"; }

    @Override
    protected void setup() {
        RobotDefinition r = robot();
        if (r == null) throw new IllegalStateException("Encoder check: it needs your Robot.java (robot() returns null)");
        // Dead-wheel pods on motor ports: not read here (pushing a drive wheel on a block never turns them).
        Set<String> pods = new HashSet<>();
        Odometry o = r.odometrySpec();
        if (o != null && (o.kindName().equals("threeWheels") || o.kindName().equals("twoWheels"))) pods.addAll(o.names());
        // Every motor port in the configuration that can be read, by its configuration name.
        List<EncoderCheck.Port> ports = new ArrayList<>();
        for (final DcMotorEx m : hardwareMap.getAll(DcMotorEx.class)) {
            Set<String> names = hardwareMap.getNamesOf(m);
            if (names.isEmpty()) continue;
            String n = names.iterator().next();
            if (pods.contains(n)) continue;
            ports.add(new EncoderCheck.Port(n, m.getPortNumber(), m::getCurrentPosition));
        }
        List<EncoderCheck.Job> jobs = new ArrayList<>();
        for (EncoderMotor em : r.encoderMotors()) {
            EncoderCheck.Job job = new EncoderCheck.Job(em.name, em.part, em.encoderRead, em.skip);
            // Not in this configuration (an optional part not built yet): skipped, not a fault.
            DcMotorEx first = hardwareMap.tryGet(DcMotorEx.class, em.name);
            if (job.skip.isEmpty() && first == null) job = new EncoderCheck.Job(em.name, em.part, em.encoderRead, "not in the configuration");
            if (job.skip.isEmpty()) {
                job.power.add(motor(em.name, em.reversed)::setPower);
                for (int i = 0; i < em.alsoMotors().size(); i++) {
                    String mate = em.alsoMotors().get(i);
                    job.mates.add(mate);
                    if (hardwareMap.tryGet(DcMotorEx.class, mate) != null) job.power.add(motor(mate, em.alsoReversed(i))::setPower);
                }
            }
            jobs.add(job);
        }
        check = new EncoderCheck(jobs, ports, () -> com.crossbotics.ftchelper.core.Time.nanos() / 1e9);
    }

    // Set up as Robot.java does (its direction), brake (it stops quickly), plain power, power 0 (nothing moves in INIT).
    private DcMotorEx motor(String name, boolean reversed) {
        DcMotorEx m = MotorSetup.get(hardwareMap, name).reversed(reversed).brake().runWithoutEncoder().done();
        pushed.add(m);
        return m;
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.warn("Wheels off the ground! Put the robot on a block. Lifts and arms move a little.");
        t.text("Then press {PLAY}. Hold {A}: each motor gets a short push.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.A, () -> aHeld = true).bindRelease(Pad.G1, Btn.A, () -> aHeld = false);
        c.bindPress(Pad.G1, Btn.Y, () -> again = true);
    }

    @Override
    protected void step() {
        if (again) {
            again = false;
            check.restart();
        }
        check.update(aHeld);
    }

    @Override
    protected void show(RobotTelemetry t) {
        t.section("Encoder check: hold {A} (let go = stop), {Y} = again");
        int bad = 0;
        List<EncoderCheck.Job> jobs = check.jobs();
        for (int i = 0; i < jobs.size(); i++) {
            EncoderCheck.Job j = jobs.get(i);
            String state = j.result != null ? j.result
                    : i == check.at() ? (check.pushing() ? "PUSHING" : aHeld ? "waiting for everything to stop" : "next: hold {A}")
                    : "not yet";
            if (j.result != null && !j.ok) bad++;
            t.line("\"" + j.name + "\" (" + j.part + ")", state);
        }
        if (check.done()) {
            if (bad == 0) t.text("Done: every encoder is in its own port and counts up.");
            else t.warn(bad + " problem(s). Fix the cables, then {Y} to check again. Steps: docs/guides/finders.md");
        }
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Checks each motor's encoder cable.",
            "1. Robot on a block: wheels and lifts move a little.",
            "2. Hold {A}: one motor at a time gets a short push.",
            "   Let go of {A}: everything stops.",
            "3. OK = its own port counted up.",
            "   NO COUNTS = cable missing (or it did not turn).",
            "   counts on \"X\" = encoder cables swapped.",
            "   BACKWARD = power wires or encoder reversed.",
            "4. Fix it, then {Y} = check again.",
            "Run it after any motor or cable change.",
        };
    }

    @Override
    protected void stopAll() {
        // Every motor, even if stopping one fails: then the first error is reported.
        if (check != null) check.stop();
        RuntimeException first = null;
        for (DcMotorEx m : pushed) {
            try { m.setPower(0); } catch (RuntimeException e) { if (first == null) first = e; }
        }
        if (first != null) throw first;
    }
}
