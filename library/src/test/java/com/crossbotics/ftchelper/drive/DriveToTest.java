package com.crossbotics.ftchelper.drive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.FieldPose;
import org.junit.Test;

/**
 * driveTo against the pretend robot of {@link AutoDriveSimTest}, with a pretend odometry that adds up
 * the wheels' motion on the field (x right, y away from the drivers, heading CCW from "away").
 */
public class DriveToTest {
    private final AutoDriveSimTest.SimRobot r = new AutoDriveSimTest.SimRobot();
    private final AutoDriveConfig c = AutoDriveSimTest.config();
    private double x, y;
    private final double[] last = {0, 0, 0, 0};

    private FieldPose pose() {
        return new FieldPose(x, y, Angles.wrapDegrees(r.heading));
    }

    // One loop: the pretend robot moves, then the pretend odometry adds up where it went.
    private void loop() {
        r.step();
        double tpi = c.ticksPerInch();
        double[] now = {r.fl.position, r.fr.position, r.bl.position, r.br.position};
        double dFl = (now[0] - last[0]) / tpi, dFr = (now[1] - last[1]) / tpi, dBl = (now[2] - last[2]) / tpi, dBr = (now[3] - last[3]) / tpi;
        System.arraycopy(now, 0, last, 0, 4);
        double forward = (dFl + dFr + dBl + dBr) / 4;
        double right = (dFl - dFr - dBl + dBr) / 4;
        double h = Math.toRadians(r.heading);
        // Heading 0 faces +y (away); the robot's right then points +x.
        x += -forward * Math.sin(h) + right * Math.cos(h);
        y += forward * Math.cos(h) + right * Math.sin(h);
    }

    private AutoDrive auto() {
        return new AutoDrive(c, false, 1, r.fl.motor, r.fr.motor, r.bl.motor, r.br.motor,
                () -> r.heading, () -> r.heading = 0, () -> r.active, this::loop, s -> { }, () -> r.nanos);
    }

    @Test public void drivesToAFieldPointAndHeading() {
        AutoDrive.Result res = auto().driveTo(this::pose, 12, 24, 0, 0.6, 1.0);
        assertEquals(AutoDrive.Result.DONE, res);
        assertEquals(12, x, 1.0);
        assertEquals(24, y, 1.0);
        assertTrue(r.allStopped());
    }

    @Test public void drivesAndTurnsAtTheSameTime() {
        AutoDrive.Result res = auto().driveTo(this::pose, -10, 30, 90, 0.6, 1.0);
        assertEquals(AutoDrive.Result.DONE, res);
        assertEquals(-10, x, 1.0);
        assertEquals(30, y, 1.0);
        assertEquals(90, Angles.wrapDegrees(r.heading), c.turnToleranceDeg + 0.5);
    }

    @Test public void aBumpIsCorrected() {
        AutoDrive a = auto();
        // Halfway, something pushes the robot 6 in to the left: it still ends at the point.
        final boolean[] bumped = {false};
        a.setLoopHook(() -> {
            if (!bumped[0] && y > 12) {
                x -= 6;
                bumped[0] = true;
            }
        });
        assertEquals(AutoDrive.Result.DONE, a.driveTo(this::pose, 0, 24, 0, 0.6, 1.0));
        assertTrue(bumped[0]);
        assertEquals(0, x, 1.0);
        assertEquals(24, y, 1.0);
    }

    @Test public void stuckRobotStopsAsStalled() {
        r.stuck = true;
        assertEquals(AutoDrive.Result.STALLED, auto().driveTo(this::pose, 0, 24, 0, 0.6, 1.0));
        assertTrue(r.allStopped());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void tankCannotDriveTo() {
        r.tankAuto(c, 1).driveTo(this::pose, 0, 24, 0, 0.6, 1.0);
    }

    @Test public void stopsWhenTheOdometryFails() {
        final int[] loops = {0};
        AutoDrive a = auto();
        a.setLoopHook(() -> loops[0]++);
        assertEquals(AutoDrive.Result.NO_ODOMETRY, a.driveTo(this::pose, () -> loops[0] < 5, 0, 24, 0, 0.6, 1.0));
        assertTrue(r.allStopped());
    }
}
