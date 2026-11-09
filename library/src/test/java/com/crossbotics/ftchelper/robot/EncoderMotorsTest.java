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
        for (EncoderMotor e : list) assertEquals("", e.skip());
        for (EncoderMotor e : list) assertEquals(e.name, 1, e.pushSign());
    }

    @Test public void aDriveMotorWhosePortReadsAPodIsSkipped() {
        Map<String, EncoderMotor> m = byName(new PodBot().encoderMotors());
        assertTrue(m.get("front_right").skip().contains("odometry pod"));
        assertEquals("", m.get("front_left").skip());
        assertFalse("with odometry, the drive's encoders are not read", m.get("front_left").encoderRead);
    }

    public static class Numbers {
        public static class HoodMotor { public static double posA = 0, posB = -400; }
        public static class Turret { public static double min = -170, max = 0, startAngle = 0; }
        public static class Arm { public static double min = -1200, max = 0; }
    }

    static class SignBot extends RobotDefinition {
        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000").hood(Hood.motor("hood_motor", "gobilda-5203-312")));
            add(Turret.motor("turret", "gobilda-5203-117"));
            add(Arm.motor("arm", "gobilda-5203-117"));
            add(Intake.motor("intake").optional());
        }
    }

    // A part whose room is below where it starts is pushed with negative power (never into its stop).
    @Test public void eachPartIsPushedTowardItsRoom() {
        Map<String, EncoderMotor> m = byName(new SignBot().encoderMotors());
        assertEquals("posB below posA", -1, m.get("hood_motor").pushSign());
        assertEquals("range below its start angle", -1, m.get("turret").pushSign());
        assertEquals("range below 0", -1, m.get("arm").pushSign());
        assertEquals(1, m.get("launcher").pushSign());
        assertTrue(m.get("intake").isOptional());
        assertFalse(m.get("arm").isOptional());
    }

    static class SharedPortBot extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            odometry(Odometry.twoWheels("intake", "perp"));
            add(Intake.motor("intake"));
            add(Turret.crServo("turret").encoder("front_left", 8192));
        }
    }

    // A port that counts something else (a pod, a CR servo turret's encoder) is skipped, for any part.
    @Test public void aPortThatCountsSomethingElseIsSkipped() {
        Map<String, EncoderMotor> m = byName(new SharedPortBot().encoderMotors());
        assertTrue(m.get("intake").skip(), m.get("intake").skip().contains("odometry pod"));
        assertTrue(m.get("front_left").skip(), m.get("front_left").skip().contains("Turret's encoder"));
        assertEquals("", m.get("front_right").skip());
    }
}
