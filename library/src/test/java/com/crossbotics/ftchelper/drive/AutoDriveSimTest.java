package com.crossbotics.ftchelper.drive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import com.crossbotics.ftchelper.FakeMotor;

/**
 * Runs AutoDrive against a simple pretend robot. Each idle() is one 10 ms loop: wheels turn
 * in proportion to their power, and the heading changes with the left/right difference.
 */
public class AutoDriveSimTest {

    static final class SimRobot {
        final FakeMotor fl = new FakeMotor(), fr = new FakeMotor(), bl = new FakeMotor(), br = new FakeMotor();
        final FakeMotor[] all = {fl, fr, bl, br};
        long nanos = 0;
        double heading = 0;
        boolean active = true;
        boolean stuck = false;
        int stopActiveAfterLoops = -1;
        int loops = 0;
        final StringBuilder log = new StringBuilder();
        static final double TICKS_PER_LOOP_AT_FULL = 30;    // about 5.6 wheel turns per second
        static final double DEG_PER_LOOP_AT_FULL = 3;

        void step() {
            nanos += 10_000_000L;
            loops++;
            if (stopActiveAfterLoops >= 0 && loops >= stopActiveAfterLoops) active = false;
            if (stuck) return;
            for (FakeMotor m : all) m.position += m.power * TICKS_PER_LOOP_AT_FULL;
            if (twoMotorTank) {
                heading += (-fl.power + fr.power) / 2 * DEG_PER_LOOP_AT_FULL;
            } else {
                heading += (-fl.power - bl.power + fr.power + br.power) / 4 * DEG_PER_LOOP_AT_FULL;
            }
        }

        boolean twoMotorTank = false;

        AutoDrive auto(AutoDriveConfig cfg, double powerLimit) {
            return new AutoDrive(cfg, false, powerLimit, fl.motor, fr.motor, bl.motor, br.motor,
                    () -> heading, () -> heading = 0, () -> active, this::step, log::append, () -> nanos);
        }

        AutoDrive tankAuto(AutoDriveConfig cfg, double powerLimit) {
            twoMotorTank = true;
            return new AutoDrive(cfg, true, powerLimit, fl.motor, fr.motor, null, null,
                    () -> heading, () -> heading = 0, () -> active, this::step, log::append, () -> nanos);
        }

        boolean allStopped() {
            for (FakeMotor m : all) if (m.power != 0) return false;
            return true;
        }

        double maxPowerSeen() {
            double max = 0;
            for (FakeMotor m : all) max = Math.max(max, m.maxAbsPower);
            return max;
        }
    }

    static AutoDriveConfig config() {
        AutoDriveConfig c = new AutoDriveConfig();
        c.calibrationConfirmed = true;
        c.wheelDiameterIn = 4.0;
        c.strafeCorrection = 1.0;     // the pretend robot does not slip
        return c;
    }

    @Test(expected = IllegalStateException.class)
    public void refusesToRunUntilCalibrationConfirmed() {
        AutoDriveConfig c = config();
        c.calibrationConfirmed = false;
        new SimRobot().auto(c, 1);
    }

    @Test public void forwardMoveReachesDistanceAndStops() {
        SimRobot r = new SimRobot();
        AutoDriveConfig c = config();
        assertEquals(AutoDrive.Result.DONE, r.auto(c, 1).move(24, 0, 0.6));
        double inches = r.fl.position / c.ticksPerInch();
        assertEquals(24, inches, 0.5);
        assertTrue(r.allStopped());
    }

    @Test public void powerLimitIsNeverExceeded() {
        SimRobot r = new SimRobot();
        AutoDrive auto = r.auto(config(), 0.4);
        auto.move(24, 0, 1.0);
        auto.move(24, 45, 1.0);
        auto.turn(90, 1.0);
        auto.moveWithTurn(24, 90, 0, 1.0, 1.0);
        assertTrue("max power " + r.maxPowerSeen(), r.maxPowerSeen() <= 0.4 + 1e-12);
    }

    @Test public void diagonalMoveTravelsTheFullDistance() {
        SimRobot r = new SimRobot();
        AutoDriveConfig c = config();
        assertEquals(AutoDrive.Result.DONE, r.auto(c, 1).move(24, 45, 0.6));
        double tpi = c.ticksPerInch();
        double forward = (r.fl.position + r.fr.position + r.bl.position + r.br.position) / 4 / tpi;
        double right = (r.fl.position - r.fr.position - r.bl.position + r.br.position) / 4 / tpi;
        assertEquals(24, Math.hypot(forward, right), 0.5);
        assertEquals(forward, right, 0.5);   // really at 45 degrees
    }

    @Test public void strafeCorrectionMakesWheelsTurnMore() {
        SimRobot r = new SimRobot();
        AutoDriveConfig c = config();
        c.strafeCorrection = 1.2;
        r.auto(c, 1).move(24, 90, 0.6);
        double wheelRight = (r.fl.position - r.fr.position - r.bl.position + r.br.position) / 4 / c.ticksPerInch();
        assertEquals(24 * 1.2, wheelRight, 0.6);
    }

    @Test public void negativeDistanceGoesBackward() {
        SimRobot r = new SimRobot();
        r.auto(config(), 1).move(-12, 0, 0.5);
        assertTrue(r.fl.position < 0);
    }

    @Test public void stuckRobotStallsAndStops() {
        SimRobot r = new SimRobot();
        r.stuck = true;
        assertEquals(AutoDrive.Result.STALLED, r.auto(config(), 1).move(24, 0, 0.6));
        assertTrue(r.allStopped());
        assertTrue(r.log.toString().contains("stuck"));
        assertTrue("stopped soon after the stall window", r.nanos < 1_000_000_000L);
    }

    @Test public void stalledMoveWithTurnDoesNotStartTurning() {
        SimRobot r = new SimRobot();
        r.stuck = true;
        AutoDrive auto = r.auto(config(), 1);
        assertEquals(AutoDrive.Result.STALLED, auto.moveWithTurn(24, 0, 90, 0.6, 0.5));
        assertTrue(r.allStopped());
        assertTrue("no long turn after the stall", r.nanos < 1_000_000_000L);
    }

    @Test public void moveWithTurnEndsAtTheHeading() {
        SimRobot r = new SimRobot();
        AutoDriveConfig c = config();
        assertEquals(AutoDrive.Result.DONE, r.auto(c, 1).moveWithTurn(24, 0, 45, 0.6, 0.5));
        assertEquals(45, r.heading, c.turnToleranceDeg + 0.5);
    }

    @Test public void moveWithTurnFinishingTurnUsesTheSameDeadline() {
        SimRobot r = new SimRobot();
        AutoDriveConfig c = config();
        c.actionTimeoutSec = 0.5;
        c.turnKp = 0.0005;            // far too weak: the finishing turn cannot complete
        c.turnKd = 0;
        c.stallExitEnabled = false;
        AutoDrive.Result result = r.auto(c, 1).moveWithTurn(6, 0, 170, 1.0, 0.5);
        assertEquals(AutoDrive.Result.TIMED_OUT, result);
        assertTrue("whole call within one timeout", r.nanos <= 520_000_000L);
        assertTrue(r.allStopped());
    }

    @Test public void turnToReachesTargetBothWays() {
        SimRobot r = new SimRobot();
        AutoDriveConfig c = config();
        AutoDrive auto = r.auto(c, 1);
        assertEquals(AutoDrive.Result.DONE, auto.turnTo(90, 0.5));
        assertEquals(90, r.heading, c.turnToleranceDeg + 0.3);
        assertEquals(AutoDrive.Result.DONE, auto.turn(-120, 0.5));
        assertEquals(-30, r.heading, c.turnToleranceDeg + 0.3);
        assertTrue(r.allStopped());
    }

    @Test public void stopRequestEndsTheMove() {
        SimRobot r = new SimRobot();
        r.stopActiveAfterLoops = 5;
        assertEquals(AutoDrive.Result.STOPPED, r.auto(config(), 1).move(48, 0, 0.6));
        assertTrue(r.allStopped());
    }

    @Test public void timeoutEndsTheMove() {
        SimRobot r = new SimRobot();
        AutoDriveConfig c = config();
        c.actionTimeoutSec = 0.2;
        assertEquals(AutoDrive.Result.TIMED_OUT, r.auto(c, 1).move(100, 0, 0.6));
        assertTrue(r.allStopped());
    }

    @Test public void motorsStopEvenWhenTheLoopHookThrows() {
        SimRobot r = new SimRobot();
        AutoDrive auto = r.auto(config(), 1);
        auto.setLoopHook(() -> {
            if (r.loops > 3) throw new RuntimeException("bug in team code");
        });
        try {
            auto.move(48, 0, 0.6);
            fail("the error should come through");
        } catch (RuntimeException expected) {
            assertTrue(r.allStopped());
        }
    }

    // ---------- Standard (tank) drive, 2 motors ----------

    @Test public void tankMovesForwardBackAndTurns() {
        SimRobot r = new SimRobot();
        AutoDriveConfig c = config();
        AutoDrive auto = r.tankAuto(c, 0.5);
        assertEquals(AutoDrive.Result.DONE, auto.move(24, 0, 0.6));
        assertEquals(24, r.fl.position / c.ticksPerInch(), 0.5);
        assertEquals(24, r.fr.position / c.ticksPerInch(), 0.5);
        assertEquals(AutoDrive.Result.DONE, auto.move(12, 180, 0.6));
        assertEquals(12, r.fl.position / c.ticksPerInch(), 0.5);
        assertEquals(AutoDrive.Result.DONE, auto.turnTo(90, 0.5));
        assertEquals(90, r.heading, c.turnToleranceDeg + 0.3);
        assertTrue(r.allStopped());
        assertEquals(0, r.bl.powerWrites);              // no back motors were touched
        assertTrue(r.maxPowerSeen() <= 0.5 + 1e-12);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tankCannotMoveSideways() {
        new SimRobot().tankAuto(config(), 1).move(12, 90, 0.5);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void tankCannotMoveWithTurn() {
        new SimRobot().tankAuto(config(), 1).moveWithTurn(12, 0, 90, 0.5, 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void mecanumNeedsFourMotors() {
        SimRobot r = new SimRobot();
        new AutoDrive(config(), false, 1, r.fl.motor, r.fr.motor, null, null,
                () -> 0, () -> { }, () -> true, () -> { }, s -> { }, () -> 0L);
    }
}
