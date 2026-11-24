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
 *   <li>OK: its own port counted, the way it was pushed.</li>
 *   <li>NO COUNTS: no port counted (encoder cable missing, or the motor did not turn).</li>
 *   <li>counts on "X" instead: another motor's port counted (encoder cables swapped).</li>
 *   <li>counts BACKWARD: its port counted the other way (power wires swapped, or a reversed encoder).</li>
 * </ul>
 * Why it matters: a lift, an arm, a flywheel's speed and the drive's distances all trust the encoder. A cable
 * in the wrong port makes them read ANOTHER motor, and they run away or never stop.
 *
 * <p>Each motor is set up as Robot.java sets it up (its direction) and pushed toward the side where its part
 * has room ({@link EncoderMotor#pushSign()}: a lift up). It never pushes while another port still moves. The
 * count turns with the direction setting, so a wrong {@code reversed()} cannot show in the counts: the screen
 * tells the driver to watch the part. A lift's second motor ({@code alsoMotor}) is pushed with its first. A
 * motor whose encoder nothing reads (an intake) may have no cable: "no counts" is fine there. CR servos and
 * servos are not checked; nor is a motor whose port reads an odometry pod or a CR servo turret's encoder.
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
 * Docs: docs/modules/finders.md. Guide: docs/tuning/02-wiring-checks.md, "Encoder Check"
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
            ports.add(new EncoderCheck.Port(n, m::getCurrentPosition));
        }
        List<EncoderCheck.Job> jobs = new ArrayList<>();
        for (EncoderMotor em : r.encoderMotors()) {
            jobs.add(job(em));
        }
        check = new EncoderCheck(jobs, ports, () -> com.crossbotics.ftchelper.core.Time.nanos() / 1e9);
    }

    // One motor's job. Not in this configuration: an optional part (fine), or a fault (INIT of your
    // OpModes stops on it). A second motor that is missing: not pushed alone (half a lift), a fault.
    private EncoderCheck.Job job(EncoderMotor em) {
        if (!em.skip().isEmpty()) return new EncoderCheck.Job(em.name, em.part, em.encoderRead, em.skip());
        if (hardwareMap.tryGet(DcMotorEx.class, em.name) == null) {
            return em.isOptional() ? new EncoderCheck.Job(em.name, em.part, em.encoderRead, "not in the configuration (optional part)")
                    : new EncoderCheck.Job(em.name, em.part, em.encoderRead, "NOT IN THE CONFIGURATION: add it, or mark the part optional()", true);
        }
        for (String mate : em.alsoMotors()) {
            if (hardwareMap.tryGet(DcMotorEx.class, mate) == null) {
                return new EncoderCheck.Job(em.name, em.part, em.encoderRead,
                        "its second motor \"" + mate + "\" is NOT IN THE CONFIGURATION: not pushed alone", true);
            }
        }
        EncoderCheck.Job job = new EncoderCheck.Job(em.name, em.part, em.encoderRead, null);
        job.sign = em.pushSign();
        job.power.add(motor(em.name, em.reversed)::setPower);
        for (int i = 0; i < em.alsoMotors().size(); i++) {
            job.mates.add(em.alsoMotors().get(i));
            job.power.add(motor(em.alsoMotors().get(i), em.alsoReversed(i))::setPower);
        }
        return job;
    }

    // Set up as Robot.java does (its direction), brake (it stops quickly), plain power, power 0 (nothing moves in INIT).
    private DcMotorEx motor(String name, boolean reversed) {
        DcMotorEx m = MotorSetup.get(hardwareMap, name).reversed(reversed).brake().runWithoutEncoder().done();
        pushed.add(m);
        return m;
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.warn("Wheels off the ground! Put the robot on a block.");
        t.warn("Lifts and arms down. Hands clear.");
        t.warn("Holding {A} moves each part. Let go = stop.");
        t.text("Encoder check: is each motor's turn counter");
        t.text("(its encoder) plugged into its own port?");
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
        t.warn("Parts move while you hold {A}. Let go = stop.");
        List<EncoderCheck.Job> jobs = check.jobs();
        t.section("Encoder check: " + Math.min(check.at() + (check.done() ? 0 : 1), jobs.size()) + " of " + jobs.size());
        int bad = 0, skipped = 0;
        EncoderCheck.Job firstBad = null;
        for (EncoderCheck.Job j : jobs) {
            if (j.result != null && !j.ok) {
                bad++;
                if (firstBad == null) firstBad = j;
            }
            if (!j.skip.isEmpty() && !j.skipIsFault) skipped++;
        }
        if (!check.done()) {
            t.text("Next: hold {A} until every motor is checked.");
            t.text("Watch: a lift or an arm must move UP.");
        } else if (bad == 0) {
            t.text("Done: no cable fault in the checks it ran.");
            if (skipped > 0) t.line("Skipped, so not checked", skipped);
            t.text("A lift or arm moved DOWN? Robot.java is wrong:");
            t.text("  change reversed() on that part's line.");
        } else {
            t.warn(bad + " problem(s). STOP before you touch a cable.");
            t.text("Fix this one first: " + firstBad.name);
            for (String line : firstBad.next) t.text("  " + line);
            t.text("Then run this check again.");
        }
        for (int i = 0; i < jobs.size(); i++) {
            EncoderCheck.Job j = jobs.get(i);
            String state = j.result != null ? j.result
                    : i == check.at() ? (check.pushing() ? "PUSHING" : aHeld ? "waiting until all is still" : "next")
                    : "not yet";
            t.line("\"" + j.name + "\" (" + j.part + ")", state);
        }
        String moving = check.stillMoving();
        if (moving != null) {
            t.text("\"" + moving + "\" still moves. Wait:");
            t.text("it goes on when everything is still.");
        }
        if (check.done()) t.text("{Y} = check all again.");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Checks each motor's encoder cable.",
            "1. Robot on a block: wheels and lifts move.",
            "2. Hold {A}: each motor gets a short push.",
            "   Let go of {A}: everything stops.",
            "3. OK = its own port counted the right way.",
            "   NO COUNTS = a cable, or it did not turn.",
            "   counts on \"X\" = another motor's port.",
            "   BACKWARD = check its wires.",
            "4. The counts cannot show a wrong reversed().",
            "   So watch: a lift or an arm must go UP.",
            "5. STOP, fix it, check again. {Y} = start over.",
            "Run it after any motor or cable change.",
        };
    }

    @Override
    protected void stopAll() {
        // Every motor, even if stopping one fails: then the first error is reported.
        RuntimeException first = null;
        try {
            if (check != null) check.stop();
        } catch (RuntimeException e) {
            first = e;
        }
        for (DcMotorEx m : pushed) {
            try { m.setPower(0); } catch (RuntimeException e) { if (first == null) first = e; }
        }
        if (first != null) throw first;
    }
}
