package com.crossbotics.ftchelper.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.control.PidController;

public class SafeMotorTest {
    private static final double E = 1e-9;

    @Test public void softLimitsWithoutSwitch() {
        FakeMotor m = new FakeMotor();
        SafeMotor lift = new SafeMotor(m.motor).setLimits(0, 1000);
        assertFalse(lift.isWaitingForHoming());
        lift.update();
        assertEquals(0, lift.setPower(-0.5), E);       // at 0, cannot go down
        assertEquals(0.5, lift.setPower(0.5), E);
        m.position = 1000;
        assertEquals(0, lift.setPower(0.5), E);        // at top
        assertTrue(lift.isAtUpper());
        assertEquals(-0.5, lift.setPower(-0.5), E);
    }

    @Test public void raisedStartMustHomeDownFirstThenWorksNormally() {
        FakeMotor m = new FakeMotor();
        m.position = 0;                                  // robot turned on with the lift half up
        boolean[] bottom = {false};
        SafeMotor lift = new SafeMotor(m.motor).setLimits(0, 1000).setLowerSwitch(() -> bottom[0]);
        lift.update();
        assertTrue(lift.isWaitingForHoming());
        assertEquals(0, lift.setPower(0.8), E);          // no going up before homing
        assertEquals(-0.3, lift.setPower(-0.8), E);      // down, at homing power only
        m.position = -600;                               // it went down past the old "zero"
        assertEquals(-0.3, lift.setPower(-0.8), E);
        bottom[0] = true;
        lift.update();                                   // switch pressed: homed, power stopped
        assertTrue(lift.isHomed());
        assertEquals(0, m.power, E);
        assertEquals(0, lift.getPosition());
        bottom[0] = false;
        lift.update();
        assertEquals(0.8, lift.setPower(0.8), E);        // now normal
    }

    @Test public void alreadyOnTheSwitchHomesAtOnce() {
        FakeMotor m = new FakeMotor();
        m.position = 37;
        SafeMotor lift = new SafeMotor(m.motor).setLimits(0, 1000).setLowerSwitch(() -> true);
        lift.update();
        assertTrue(lift.isHomed());
        assertEquals(0, lift.getPosition());
    }

    @Test public void homingCanBeTurnedOff() {
        FakeMotor m = new FakeMotor();
        SafeMotor lift = new SafeMotor(m.motor).setLimits(0, 1000)
                .setLowerSwitch(() -> false).setRequireHoming(false);
        lift.update();
        assertEquals(0.8, lift.setPower(0.8), E);
    }

    @Test public void switchPressedDuringUpdateStopsMotorAtOnce() {
        FakeMotor m = new FakeMotor();
        boolean[] top = {false};
        SafeMotor lift = new SafeMotor(m.motor).setLimits(0, 1000).setUpperSwitch(() -> top[0]);
        m.position = 500;
        lift.update();
        lift.setPower(0.6);
        assertEquals(0.6, m.power, E);
        top[0] = true;
        lift.update();                                   // no setPower call this loop
        assertEquals(0, m.power, E);
        assertEquals(0, lift.getAppliedPower(), E);
        assertEquals(0.6, lift.getRequestedPower(), E);
    }

    @Test public void repeatedSamePowerIsSentOnlyOnce() {
        FakeMotor m = new FakeMotor();
        m.position = 500;
        SafeMotor lift = new SafeMotor(m.motor).setLimits(0, 1000);
        lift.setPower(0.4);
        lift.setPower(0.4);
        lift.setPower(0.4);
        assertEquals(1, m.powerWrites);
        lift.setPower(0.5);
        assertEquals(2, m.powerWrites);
        lift.stop();
        assertEquals(0, m.power, E);
    }

    @Test public void maxPowerAndSlowZone() {
        FakeMotor m = new FakeMotor();
        m.position = 900;
        SafeMotor lift = new SafeMotor(m.motor).setLimits(0, 1000).setSlowZone(200, 0.25).setMaxPower(0.8);
        assertEquals(0.625, lift.setPower(1), E);
        m.position = 500;
        assertEquals(0.8, lift.setPower(1), E);
    }

    @Test public void moveToKeepsTargetInsideLimits() {
        FakeMotor m = new FakeMotor();
        m.position = 990;
        SafeMotor lift = new SafeMotor(m.motor).setLimits(0, 1000);
        PidController pid = new PidController(0.01, 0, 0, 0, () -> 0L);
        // Target 5000 is clamped to 1000: error 10 -> power 0.1, not 1.0.
        assertEquals(0.1, lift.moveTo(5000, pid), E);
    }
}
