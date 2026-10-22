package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.qualcomm.robotcore.hardware.DcMotorSimple;
import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A slide on two motors (with=): both shafts turn together, both push, and a wrong direction is seen. */
public class SimTwoMotorLiftTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private SimRobot robot(String... lines) throws Exception {
        File f = tmp.newFile();
        Files.write(f.toPath(), (String.join("\n", lines) + "\n").getBytes("UTF-8"));
        return SimRobot.load(f.toPath());
    }

    private static final String LEFT = "motor.lift_left = lift, part=gobilda-5203-435, min=0, max=2900, gravity=0.2";

    // Runs the robot for this many seconds in 10 ms steps.
    private static void run(SimRobot r, double seconds) {
        for (int i = 0; i < Math.round(seconds / 0.01); i++) r.step(0.01);
    }

    @Test public void bothMotorsTurnOneSlide() throws Exception {
        SimRobot r = robot(LEFT, "motor.lift_right = lift, part=gobilda-5203-435, with=lift_left, mirrored=true");
        SimMotor left = r.motors.get("lift_left"), right = r.motors.get("lift_right");
        // The code reverses the right motor (they face each other): + power lifts on both.
        right.device.setDirection(DcMotorSimple.Direction.REVERSE);
        left.device.setPower(0.6);
        right.device.setPower(0.6);
        run(r, 0.3);
        assertTrue("the slide goes up: " + left.shaft(), left.shaft() > 100);
        assertEquals("its shaft turns the other way, with the slide", -left.shaft(), right.shaft(), 1e-9);
        assertEquals("both encoders read the same (the code reversed one)", left.device.getCurrentPosition(),
                right.device.getCurrentPosition());
    }

    @Test public void twoMotorsPushHarderThanOne() throws Exception {
        SimRobot both = robot(LEFT, "motor.lift_right = lift, part=gobilda-5203-435, with=lift_left");
        SimRobot one = robot(LEFT, "motor.lift_right = lift, part=gobilda-5203-435, with=lift_left");
        both.motors.get("lift_left").device.setPower(0.4);
        both.motors.get("lift_right").device.setPower(0.4);
        one.motors.get("lift_left").device.setPower(0.4);   // the other one gets nothing (a code mistake)
        run(both, 0.3);
        run(one, 0.3);
        assertTrue("two motors lift faster: " + both.motors.get("lift_left").shaft() + " vs "
                + one.motors.get("lift_left").shaft(), both.motors.get("lift_left").shaft() > one.motors.get("lift_left").shaft() + 50);
    }

    @Test public void motorsThatFightHardlyMoveIt() throws Exception {
        // mirrored=true but the code does NOT reverse the second motor: they push against each other.
        SimRobot r = robot(LEFT, "motor.lift_right = lift, part=gobilda-5203-435, with=lift_left, mirrored=true");
        r.motors.get("lift_left").device.setPower(0.6);
        r.motors.get("lift_right").device.setPower(0.6);
        run(r, 0.3);
        assertTrue("fighting: " + r.motors.get("lift_left").shaft(), r.motors.get("lift_left").shaft() < 20);
    }

    @Test public void withMustNameAnotherMotor() throws Exception {
        try {
            robot(LEFT, "motor.lift_right = lift, with=lift_middle");
            fail("with= a motor that is not there must stop");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("with=lift_middle is not another motor"));
        }
    }
}
