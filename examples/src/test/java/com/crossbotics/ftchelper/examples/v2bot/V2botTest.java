package com.crossbotics.ftchelper.examples.v2bot;

// "import static" lets us write assertEquals(...) instead of Assert.assertEquals(...).
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.testkit.PretendDevices;
import org.junit.Test;

/**
 * Tests for a robot in five files (the V2 robot), on a laptop with no robot: the library's test kit
 * gives Robot.java pretend motors, servos and switches. Each test does what the robot app does (INIT,
 * START, a loop) and checks what the motors and servos were sent. Run them:
 *   ./gradlew :examples:testDebugUnitTest
 * Your own: copy this file into TeamCode/src/test/java/org/firstinspires/ftc/teamcode/ (fix the package
 * line) and change the names to your robot's. Guide: docs/guides/testing.md. Tutorial 13, section 7.
 */
public class V2botTest {

    // Tunables.java: every field and section name is one a part reads (the same check as INIT, which
    // would stop or warn on the robot). "[]" = an empty list: no problems.
    @Test public void tunablesNamesAreRight() {
        assertEquals("[]", new Robot().checkTunables().toString());
    }

    // INIT works: every part sets up from Robot.java and Tunables.java (a missing safety number, a
    // device name used twice or a bad interlock zone would stop it here).
    @Test public void initWorks() {
        new Robot().setup(new PretendDevices());
    }

    // The lift goes up when sent to "flower", and stops there.
    @Test public void liftGoesToTheFlowerHeight() {
        PretendDevices devices = new PretendDevices();   // a fresh pretend robot for each test
        Robot robot = new Robot();
        robot.setup(devices);                            // INIT
        robot.startForTests();                           // START
        robot.lift.goTo("flower");
        robot.all().update();                            // one loop
        assertTrue("power up", devices.motor("lift").power > 0);
        // Pretend the lift got there: the encoder reads the flower height (Tunables.Lift.flower = 1330).
        devices.motor("lift").position = 1330;
        robot.all().update();
        assertTrue(robot.lift.isAt("flower"));
    }

    // At START the turret looks for its home switch: until then its angle is unknown, so the
    // interlock rule keeps the wrist out of its zone and the Driver Station says why.
    @Test public void theWristWaitsUntilTheTurretIsHomed() {
        PretendDevices devices = new PretendDevices();
        Robot robot = new Robot();
        robot.setup(devices);
        robot.startForTests();
        robot.wrist.goTo("pickup");                      // 0.174: inside the wrist's zone (0 to 0.274)
        robot.all().update();
        String status = robot.turretClaw.status();
        assertTrue(status, status.contains("Turret angle unknown"));
        // The turret finds its switch: now its angle is known (and outside the zone), so the wrist may go.
        devices.press("turret_home", true);
        robot.all().update();
        robot.all().update();
        assertTrue(robot.turret.isHomed());
        assertEquals("", robot.turretClaw.status());
    }

    // Pause (gamepad 1 BACK) stops every motor at once.
    @Test public void pauseStopsTheLift() {
        PretendDevices devices = new PretendDevices();
        Robot robot = new Robot();
        robot.setup(devices);
        robot.startForTests();
        robot.lift.goTo("flower");
        robot.all().update();
        robot.pauseAll();
        assertEquals(0, devices.motor("lift").power, 1e-9);
    }
}
