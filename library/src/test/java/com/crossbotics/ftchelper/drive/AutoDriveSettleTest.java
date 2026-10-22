package com.crossbotics.ftchelper.drive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.FieldPose;
import org.junit.Test;

/**
 * The v0.17 review fixes in AutoDrive: move() waits (briefly) until a robot that rolls on has stopped,
 * driveTo's final approach is strong enough at the practice power limit, and one very slow loop (the
 * hub or the phone busy) is not taken for "stuck".
 */
public class AutoDriveSettleTest {
    /**
     * A pretend robot with weight: each wheel's speed follows its power with a lag (it rolls on after
     * power 0), and a wheel power under {@code stiction} does not move it at all.
     */
    static final class HeavyRobot {
        final FakeMotor fl = new FakeMotor(), fr = new FakeMotor(), bl = new FakeMotor(), br = new FakeMotor();
        final FakeMotor[] all = {fl, fr, bl, br};
        final double[] speed = new double[4];   // ticks per loop
        long nanos = 0;
        double heading = 0;
        double lag = 0.85;                      // how much of last loop's speed is kept
        double stiction = 0;
        double x, y;
        // A pause in the program (seconds) at the next loop: the clock jumps, nothing moves.
        double hiccupSec = 0;

        void step() {
            nanos += 10_000_000L;
            if (hiccupSec > 0) {
                nanos += (long) (hiccupSec * 1e9);
                hiccupSec = 0;
                return;
            }
            double[] d = new double[4];
            for (int i = 0; i < 4; i++) {
                double p = Math.abs(all[i].power) < stiction ? 0 : all[i].power;
                speed[i] = lag * speed[i] + (1 - lag) * p * 30;
                all[i].position += speed[i];
                d[i] = speed[i];
            }
            AutoDriveConfig c = config();
            double tpi = c.ticksPerInch();
            double forward = (d[0] + d[1] + d[2] + d[3]) / 4 / tpi;
            double right = (d[0] - d[1] - d[2] + d[3]) / 4 / tpi;
            heading += (-d[0] - d[2] + d[1] + d[3]) / 4 / 30 * 3;
            double h = Math.toRadians(heading);
            x += -forward * Math.sin(h) + right * Math.cos(h);
            y += forward * Math.cos(h) + right * Math.sin(h);
        }

        FieldPose pose() {
            return new FieldPose(x, y, Angles.wrapDegrees(heading));
        }

        AutoDrive auto(double powerLimit) {
            return new AutoDrive(config(), false, powerLimit, fl.motor, fr.motor, bl.motor, br.motor,
                    () -> heading, () -> heading = 0, () -> true, this::step, s -> { }, () -> nanos);
        }

        double fastestTicksPerLoop() {
            double m = 0;
            for (double s : speed) m = Math.max(m, Math.abs(s));
            return m;
        }
    }

    static AutoDriveConfig config() {
        AutoDriveConfig c = AutoDriveSimTest.config();
        return c;
    }

    // Codex 10: a fast move "ended" while the robot still rolled on. Now it brakes and waits (at most
    // 0.3 s) until it has stopped, so the next move starts from a robot that stands still.
    @Test public void aMoveEndsWithTheRobotStopped() {
        HeavyRobot r = new HeavyRobot();
        assertEquals(AutoDrive.Result.DONE, r.auto(1).move(24, 0, 1.0));
        double tpi = config().ticksPerInch();
        // Under 1 inch a second (10 ms loops): it has stopped.
        assertTrue("still rolling: " + r.fastestTicksPerLoop() / tpi * 100 + " in/s", r.fastestTicksPerLoop() / tpi * 100 < 1.0);
        for (FakeMotor m : r.all) assertEquals(0, m.power, 1e-9);
    }

    // At the practice power limit (0.4) the last inches used 0.12 x 0.4 = 0.05 wheel power: too weak to
    // move a real robot, so driveTo ended STALLED short of the point. The smallest powers are wheel powers now.
    @Test public void driveToFinishesAtThePracticePowerLimit() {
        HeavyRobot r = new HeavyRobot();
        r.stiction = 0.08;
        AutoDrive.Result res = r.auto(0.4).driveTo(r::pose, 6, 18, 0, 1.0, 0.5);
        assertEquals(AutoDrive.Result.DONE, res);
        assertEquals(6, r.x, 0.6);
        assertEquals(18, r.y, 0.6);
    }

    // Codex 13: one very slow loop (2.5 s) while the pose had not caught up is not "no progress for 2 s".
    @Test public void oneVerySlowLoopIsNotStuck() {
        HeavyRobot r = new HeavyRobot();
        AutoDrive a = r.auto(1);
        final boolean[] done = {false};
        a.setLoopHook(() -> {
            if (!done[0] && Math.hypot(r.x - 0, r.y - 24) < 4) {
                r.hiccupSec = 2.5;
                done[0] = true;
            }
        });
        assertEquals(AutoDrive.Result.DONE, a.driveTo(r::pose, 0, 24, 0, 0.6, 0.5));
        assertTrue(done[0]);
    }
}
