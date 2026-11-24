package com.crossbotics.ftchelper.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.launcher.Flywheel.SpeedControl;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.qualcomm.robotcore.hardware.DcMotor;
import org.junit.Test;

/** The speed control switch and bang-bang's own send path: stop always reaches the motor. */
public class FlywheelControlTest {
    private static final double E = 1e-9;
    private final FakeMotor m = new FakeMotor();
    private final Fakes.Clock c = new Fakes.Clock();

    private Flywheel wheel() {
        return new Flywheel("fly", m.motor).withClock(c::get).countsPerRev(28).maxRpm(6000).tolerance(100);
    }

    private Flywheel bang() {
        Flywheel f = wheel();
        assertTrue(f.speedControl(SpeedControl.BANG_BANG));
        return f;
    }

    private void loop(Flywheel f, double rpm, double sec) {
        c.addSec(sec);
        m.velocity = rpm / 60 * 28;
        f.update();
    }

    // At 3000 with the derived feedforward (power 3000 / 6000 at 12.8 V nominal).
    private Flywheel atSpeed() {
        Flywheel f = bang();
        f.setRpm(3000);
        loop(f, 3000, 0.02);
        return f;
    }

    @Test public void pidIsTheDefaultAndItsPathIsUnchanged() {
        Flywheel f = wheel();
        assertEquals(SpeedControl.PID, f.getSpeedControl());
        int[] reads = {0};
        f.bangBattery(() -> {
            reads[0]++;
            return 12.5;
        }, 12.8);
        f.setRpm(3000);
        loop(f, 2000, 0.02);
        loop(f, 2000, 0.5);
        assertEquals(1400, m.velocityCommand, E);
        assertEquals(DcMotor.RunMode.RUN_USING_ENCODER, m.mode);
        assertEquals("no power write", 0, m.powerWrites);
        assertEquals("PID never reads bang-bang's battery", 0, reads[0]);
        assertEquals(1, f.getFScale(), 0);
        f.stop();
        assertEquals(1, m.powerWrites);
        f.stop();
        assertEquals("PID's stop still skips the same 0 again", 1, m.powerWrites);
    }

    @Test public void aDeadEncoderDoesNotTripPid() {
        Flywheel f = wheel();
        f.setRpm(3000);
        for (int i = 0; i < 100; i++) loop(f, 0, 0.02);
        assertFalse(f.isFaulted());
        assertEquals("", f.getFault());
        assertEquals(1400, m.velocityCommand, E);
    }

    @Test public void bangBangSendsPlainPowerOnFloat() {
        atSpeed();
        assertEquals(DcMotor.RunMode.RUN_WITHOUT_ENCODER, m.mode);
        assertEquals(DcMotor.ZeroPowerBehavior.FLOAT, m.zeroPower);
        assertEquals("no hub speed command", 0, m.velocityCommand, 0);
        assertEquals((12.0 / 6000 * 3000) / 12.8, m.power, E);
    }

    @Test public void fullPowerBelowAndZeroAbove() {
        Flywheel f = atSpeed();
        loop(f, 2700, 0.02);
        assertEquals(1, m.power, 0);
        loop(f, 3200, 0.02);
        assertEquals(0, m.power, 0);
    }

    @Test public void stopAlwaysWritesZero() {
        Flywheel f = atSpeed();
        f.stop();
        assertEquals(0, m.power, 0);
        int writes = m.powerWrites;
        m.power = 0.8;                                      // someone else's write: "the same" must not skip it
        f.stop();
        assertEquals(writes + 1, m.powerWrites);
        assertEquals(0, m.power, 0);
    }

    @Test public void stopStillWritesZeroWhenTheModeFails() {
        Flywheel f = atSpeed();
        m.failSetMode = true;
        try {
            f.stop();
            fail("the failure is reported");
        } catch (RuntimeException expected) {
            assertEquals("mode failed", expected.getMessage());
        }
        assertEquals(0, m.power, 0);
        assertEquals(0, f.getTargetRpm(), 0);
    }

    @Test public void stopReachesTheFollowerWhenTheMainMotorFails() {
        Flywheel f = wheel();
        FakeMotor second = new FakeMotor();
        f.attachFollowerForTests(second.motor);
        f.speedControl(SpeedControl.BANG_BANG);
        f.setRpm(3000);
        loop(f, 2500, 0.02);
        assertTrue(second.power > 0);
        m.failSetPower = true;
        try {
            f.stop();
            fail("the failure is reported");
        } catch (RuntimeException expected) {
            assertEquals("motor unplugged", expected.getMessage());
        }
        assertEquals(0, second.power, 0);
    }

    @Test public void aFailedZeroInAnUpdateIsSentAgain() {
        Flywheel f = atSpeed();
        m.failSetPower = true;
        try {
            loop(f, 3300, 0.02);
            fail("the failure is reported");
        } catch (RuntimeException expected) {
            assertTrue(m.power > 0);
        }
        m.failSetPower = false;
        loop(f, 3300, 0.02);
        assertEquals(0, m.power, 0);
    }

    @Test public void smallChangesAreNotWrittenAgain() {
        Flywheel f = atSpeed();
        int writes = m.powerWrites;
        loop(f, 2990, 0.02);
        f.setRpm(3010);                                     // under 0.01 of power
        loop(f, 3000, 0.02);
        assertEquals(writes, m.powerWrites);
        f.setRpm(3300);
        loop(f, 3300, 0.02);
        assertEquals(writes + 1, m.powerWrites);
    }

    @Test public void aSwitchIsOnlyAllowedStopped() {
        Flywheel f = wheel();
        f.setRpm(3000);
        loop(f, 3000, 0.02);
        int revision = f.getControlRevision();
        assertFalse(f.speedControl(SpeedControl.BANG_BANG));
        assertEquals(SpeedControl.PID, f.getSpeedControl());
        assertEquals("a refused change still counts", revision + 1, f.getControlRevision());
        f.stop();
        int writes = m.powerWrites;
        assertTrue(f.speedControl(SpeedControl.BANG_BANG));
        assertEquals("the switch writes 0 first", writes + 1, m.powerWrites);
        assertEquals(0, m.power, 0);
        f.setRpm(2000);
        loop(f, 2000, 0.02);
        assertFalse(f.speedControl(SpeedControl.PID));
        f.stop();
        assertTrue(f.speedControl(SpeedControl.PID));
        f.setRpm(2000);
        loop(f, 2000, 0.02);
        assertEquals("PID sends its speed again", 2000 / 60.0 * 28, m.velocityCommand, E);
        assertEquals(DcMotor.RunMode.RUN_USING_ENCODER, m.mode);
    }

    @Test public void aSwitchClearsTheLastSentSpeed() {
        Flywheel f = wheel();
        f.setRpm(3000);
        loop(f, 3000, 0.02);
        f.stop();
        assertTrue(f.speedControl(SpeedControl.BANG_BANG));
        assertTrue(f.speedControl(SpeedControl.PID));
        m.velocityCommand = -1;
        m.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;
        f.setRpm(3000);
        loop(f, 3000, 0.02);
        assertEquals(1400, m.velocityCommand, E);
    }

    @Test public void aFailedSwitchKeepsTheOldControl() {
        Flywheel f = wheel();
        m.failSetPower = true;
        try {
            f.speedControl(SpeedControl.BANG_BANG);
            fail("the failure is reported");
        } catch (RuntimeException expected) {
            assertEquals(SpeedControl.PID, f.getSpeedControl());
        }
    }

    @Test public void theFeedforwardDividesByTheFilteredBattery() {
        double[] volts = {12.0};
        Flywheel f = wheel().bangFeedforward(0.5, 0.002).bangBattery(() -> volts[0], 12.8);
        f.speedControl(SpeedControl.BANG_BANG);
        f.setRpm(3000);
        loop(f, 3000, 0.02);
        assertEquals(6.5 / 12, m.power, E);
        volts[0] = 11;                                      // a dip: the highest of the last second counts
        for (int i = 0; i < 30; i++) loop(f, 3000, 0.02);
        assertEquals(6.5 / 12, m.power, E);
        for (int i = 0; i < 80; i++) loop(f, 3000, 0.02);
        assertEquals(6.5 / 11, m.power, E);
        volts[0] = 7.5;                                     // past today's limit: at most 1.3 x
        for (int i = 0; i < 80; i++) loop(f, 3000, 0.02);
        assertEquals(6.5 / 12.8 * Flywheel.MAX_F_SCALE, m.power, E);
        volts[0] = Double.NaN;                              // no reading: the last one is kept
        for (int i = 0; i < 80; i++) loop(f, 3000, 0.02);
        assertEquals(6.5 / 12.8 * Flywheel.MAX_F_SCALE, m.power, E);
    }

    @Test public void anUntunedKvComesFromTheTopSpeed() {
        assertEquals(12.0 / 6000, wheel().getBangKv(), E);
        assertEquals(12.0 / 5000, wheel().maxRpm(5000).getBangKv(), E);
        assertEquals(0.0022, wheel().bangFeedforward(0, 0.0022).getBangKv(), E);
    }

    @Test public void aLateLoopUsesTheFeedforwardOnlyAndWarns() {
        Flywheel f = atSpeed();
        loop(f, 2500, 0.02);
        assertEquals(1, m.power, 0);
        loop(f, 2500, 0.12);
        assertTrue("no full power: " + m.power, m.power < 0.6);
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry);
        f.addTelemetry(t);
        t.update();
        assertTrue(ft.lastScreen.toString(), ft.lastScreen.toString().contains("Program too slow: 0.12 seconds (limit 0.08)"));
        loop(f, 2500, 0.02);
        assertEquals("back to normal", 1, m.power, 0);
    }

    @Test public void aStopIsNotALateLoop() {
        Flywheel f = atSpeed();
        f.stop();
        c.addSec(2);
        f.setRpm(3000);
        loop(f, 2000, 0.02);
        assertEquals("a fresh start at full", 1, m.power, 0);
    }

    @Test public void aFollowerAddedAfterTheSwitchGetsPlainPower() {
        Flywheel f = atSpeed();
        FakeMotor second = new FakeMotor();
        second.mode = DcMotor.RunMode.RUN_USING_ENCODER;
        f.attachFollowerForTests(second.motor);
        loop(f, 3000, 0.02);
        assertEquals(DcMotor.RunMode.RUN_WITHOUT_ENCODER, second.mode);
        assertEquals(DcMotor.ZeroPowerBehavior.FLOAT, second.zeroPower);
        assertEquals(m.power, second.power, E);
    }

    @Test public void neverNegative() {
        Flywheel f = bang();
        f.setRpm(-3000);
        loop(f, 0, 0.02);
        assertEquals(0, m.power, 0);
        f.setRpm(Double.NaN);
        loop(f, 2000, 0.02);
        assertEquals(0, m.power, 0);
        f.setRpm(600);
        for (int i = 0; i < 100; i++) loop(f, 4000, 0.02);
        assertEquals("slowing to an eject speed only coasts", 0, m.power, 0);
        assertEquals(DcMotor.ZeroPowerBehavior.FLOAT, m.zeroPower);
        assertEquals(0, m.maxAbsPower, 0);
    }

    @Test public void aDeadEncoderLatchesAndGivesZeroPower() {
        Flywheel f = bang();
        f.setRpm(3000);
        for (int i = 0; i < 35; i++) loop(f, 0, 0.02);
        assertTrue(f.isFaulted());
        assertEquals(0, m.power, 0);
        assertFalse(f.isReady());
        assertTrue(f.getFault(), f.getFault().contains("stays near 0"));
        for (int i = 0; i < 50; i++) loop(f, 3000, 0.02);
        assertTrue("a good reading does not clear it", f.isFaulted());
        assertEquals(0, m.power, 0);
        f.stop();
        f.setRpm(3000);
        loop(f, 3000, 0.02);
        assertTrue("a stop alone does not either", f.isFaulted());
        f.clearFault();
        loop(f, 2990, 0.02);
        assertFalse(f.isFaulted());
        assertTrue(m.power > 0);
    }

    @Test public void aBackwardOrImpossibleReadingLatches() {
        Flywheel f = atSpeed();
        loop(f, -500, 0.02);
        assertFalse(f.isFaulted());
        loop(f, -500, 0.02);
        assertTrue(f.isFaulted());
        assertEquals(0, m.power, 0);
        f.stop();
        f.clearFault();
        f.setRpm(3000);
        loop(f, 3000, 0.02);
        loop(f, Double.NaN, 0.02);
        assertTrue(f.isFaulted());
        assertEquals(0, m.power, 0);
    }

    @Test public void aSpinUpDoesNotTrip() {
        Flywheel f = bang();
        f.setRpm(3000);
        double rpm = 0;
        for (int i = 0; i < 300; i++) {
            loop(f, Math.rint(rpm / 43) * 43, 0.02);
            rpm += (m.power * 6000 - rpm) * 0.02 / 1.5;
            assertFalse("at " + i, f.isFaulted());
        }
        assertEquals(3000, rpm, 300);
    }
}
