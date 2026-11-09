package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** RobotDefinition.encoderMotors(): the Encoder Check's list of motors, from Robot.java alone (no hardware). */
public class EncoderMotorsTest {

    static class Bot extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right").reversed("front_left", "back_left"));
            add(Intake.motor("intake").reversed());
            add(Lift.slide("lift_left").reversed().alsoMotorReversed("lift_right"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000").reversed()
                    .feeder(Indexer.motor("feed_motor"))
                    .hood(Hood.motor("hood_motor", "gobilda-5203-312")));
            add(Turret.crServo("turret_cr"));
        }
    }

    static class PodBot extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            odometry(Odometry.twoWheels("front_right", "perp"));
        }
    }

    private static Map<String, EncoderMotor> byName(List<EncoderMotor> list) {
        Map<String, EncoderMotor> out = new HashMap<>();
        for (EncoderMotor m : list) out.put(m.name, m);
        return out;
    }

    @Test public void listsEveryMotorWithItsDirectionAndWhetherItsEncoderIsRead() {
        List<EncoderMotor> list = new Bot().encoderMotors();
        Map<String, EncoderMotor> m = byName(list);
        // The drive first, in Robot.java's order.
        assertEquals("front_left", list.get(0).name);
        assertTrue(m.get("front_left").reversed);
        assertFalse(m.get("front_right").reversed);
        assertTrue("no odometry: the drive's distances come from its encoders", m.get("front_left").encoderRead);
        // An intake runs on power: nothing reads its encoder.
        assertFalse(m.get("intake").encoderRead);
        assertTrue(m.get("intake").reversed);
        // A two-motor lift: one entry, the second motor pushed with it (reversed() flips both, then alsoMotorReversed).
        EncoderMotor lift = m.get("lift_left");
        assertTrue(lift.encoderRead);
        assertTrue(lift.reversed);
        assertEquals("[lift_right]", lift.alsoMotors().toString());
        assertFalse("reversed() != alsoMotorReversed: the second one turns forward", lift.alsoReversed(0));
        assertFalse("the second motor is not its own entry", m.containsKey("lift_right"));
        // The launcher and its own parts.
        assertTrue(m.get("launcher").encoderRead);
        assertTrue(m.get("launcher").reversed);
        assertFalse(m.get("feed_motor").encoderRead);
        assertTrue(m.get("hood_motor").encoderRead);
        // A CR servo turret has no motor to push.
        assertFalse(m.containsKey("turret_cr"));
        for (EncoderMotor e : list) assertEquals("", e.skip);
    }

    @Test public void aDriveMotorWhosePortReadsAPodIsSkipped() {
        Map<String, EncoderMotor> m = byName(new PodBot().encoderMotors());
        assertTrue(m.get("front_right").skip.contains("odometry pod"));
        assertEquals("", m.get("front_left").skip);
        assertFalse("with odometry, the drive's encoders are not read", m.get("front_left").encoderRead);
    }
}
