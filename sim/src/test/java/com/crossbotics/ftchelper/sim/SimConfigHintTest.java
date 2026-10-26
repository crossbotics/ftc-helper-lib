package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A device the robot file does not have: the error says what to do in the simulator (the robot file),
 * not only "check the configuration on the Driver Station".
 */
public class SimConfigHintTest {

    @Test public void aMissingDeviceGetsTheRobotFileHintAfterItsFirstLine() {
        String err = "java.lang.IllegalArgumentException: No DcMotorEx named \"arm\" in the active robot configuration. "
                + "Check the spelling and the active configuration on the Driver Station.\nat Robot.define(Robot.java:12)";
        String out = SimDriverStation.withSimHint(err);
        String[] lines = out.split("\n");
        assertTrue(out, lines[0].startsWith("java.lang.IllegalArgumentException: No DcMotorEx named \"arm\""));
        assertEquals(SimDriverStation.CONFIG_HINT, lines[1]);
        assertTrue(out, lines[2].startsWith("at Robot.define"));
        assertEquals("added once", out, SimDriverStation.withSimHint(out));
    }

    @Test public void theSdksOwnMessageGetsItToo() {
        assertTrue(SimDriverStation.withSimHint("Unable to find a hardware device with name \"x\" and type DcMotor").contains("robot file"));
    }

    @Test public void otherErrorsStayAsTheyAre() {
        assertEquals("NullPointerException", SimDriverStation.withSimHint("NullPointerException"));
    }
}
