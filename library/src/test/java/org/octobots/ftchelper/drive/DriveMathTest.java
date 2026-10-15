package org.octobots.ftchelper.drive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DriveMathTest {
    private static final double E = 1e-9;

    @Test public void mixBasicDirections() {
        assertArrayEquals(new double[]{1, 1, 1, 1}, MecanumMixer.mix(1, 0, 0, 1), E);
        assertArrayEquals(new double[]{1, -1, -1, 1}, MecanumMixer.mix(0, 1, 0, 1), E);
        assertArrayEquals(new double[]{1, -1, 1, -1}, MecanumMixer.mix(0, 0, 1, 1), E);
    }

    @Test public void mixScalesTogetherAndRespectsLimit() {
        double[] p = MecanumMixer.mix(1, 1, 0, 1);
        assertArrayEquals(new double[]{1, 0, 0, 1}, p, E);
        p = MecanumMixer.mix(1, 0, 0, 0.4);
        assertArrayEquals(new double[]{0.4, 0.4, 0.4, 0.4}, p, E);
        assertArrayEquals(new double[4], MecanumMixer.mix(Double.NaN, 0, 0, 1), E);
    }

    /** Last season's check: no wheel ever goes past the limit, for every stick combination. */
    @Test public void mixNeverExceedsLimitForAnyInput() {
        int checked = 0;
        for (int f = -10; f <= 10; f++) for (int s = -10; s <= 10; s++) for (int t = -10; t <= 10; t++) {
            double[] p = MecanumMixer.mix(f / 10.0, s / 10.0, t / 10.0, 0.7);
            for (double v : p) assertTrue(Math.abs(v) <= 0.7 + 1e-12);
            checked++;
        }
        assertEquals(9261, checked);
    }

    @Test public void fieldToRobotRotatesTheRightWay() {
        // Heading 0: nothing changes.
        assertArrayEquals(new double[]{1, 0}, MecanumMixer.fieldToRobot(1, 0, 0), E);
        // Robot turned 90 degrees left: "away from driver" is the robot's right.
        assertArrayEquals(new double[]{0, 1}, MecanumMixer.fieldToRobot(1, 0, 90), E);
        // Robot turned 90 degrees right: "away from driver" is the robot's left.
        assertArrayEquals(new double[]{0, -1}, MecanumMixer.fieldToRobot(1, 0, -90), E);
        // Robot turned around: field-right is robot-left.
        assertArrayEquals(new double[]{0, -1}, MecanumMixer.fieldToRobot(0, 1, 180), E);
    }

    @Test public void fieldDirectionMatchesFieldToRobot() {
        for (int heading = -180; heading <= 180; heading += 30) {
            for (int dir = -180; dir <= 180; dir += 45) {
                double rad = Math.toRadians(dir);
                double[] a = MecanumMixer.fieldDirection(dir, heading);
                double[] b = MecanumMixer.fieldToRobot(Math.cos(rad), Math.sin(rad), heading);
                assertArrayEquals(a, b, 1e-9);
            }
        }
    }

    @Test public void reverseDriveFlipsTranslationOnly() {
        ReverseDrive r = new ReverseDrive();
        assertEquals(0.5, r.apply(0.5), E);
        assertTrue(r.toggle());
        assertEquals(-0.5, r.apply(0.5), E);
        assertFalse(r.toggle());
        r.set(true);
        assertTrue(r.isReversed());
    }

    @Test public void taperCruisesThenSlows() {
        assertEquals(0.8, MotionMath.taperSpeed(0.5, 0.3, 0.8, 0.1), E);
        assertEquals(0.55, MotionMath.taperSpeed(0.05, 0.3, 0.8, 0.1), E);
        assertEquals(0.3, MotionMath.taperSpeed(0, 0.3, 0.8, 0.1), E);
        assertEquals(0.8, MotionMath.taperSpeed(2, 0.3, 0.8, 0.1), E);
    }

    @Test public void stallNeedsNoProgressForTheWholeWindow() {
        StallDetector s = new StallDetector(0.25, 0.4);
        assertFalse(s.update(10, 0.0));
        assertFalse(s.update(9.9, 0.2));     // a little progress, window still open
        assertFalse(s.update(9.85, 0.39));
        assertTrue(s.update(9.8, 0.41));     // only 0.2 in within 0.41 s: stuck
        s.reset();
        assertFalse(s.update(10, 0));
        // Fast loops, steady 1 in/s: never stuck, even though each loop moves only 0.01 in.
        for (int i = 1; i <= 200; i++) assertFalse(s.update(10 - i * 0.01, i * 0.01));
    }

    @Test public void stallNeverFiresWhenAlmostThere() {
        StallDetector s = new StallDetector(0.25, 0.4);
        s.update(0.2, 0);
        assertFalse(s.update(0.2, 5));
    }

    @Test public void ticksPerInchFromConfig() {
        AutoDriveConfig c = new AutoDriveConfig();
        c.ticksPerRev = 537.7;
        c.wheelDiameterIn = 4.0;
        c.gearRatio = 1.0;
        assertEquals(537.7 / (Math.PI * 4.0), c.ticksPerInch(), E);
        assertFalse(c.calibrationConfirmed);
    }
}
