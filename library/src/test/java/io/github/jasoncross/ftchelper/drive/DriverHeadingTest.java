package io.github.jasoncross.ftchelper.drive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DriverHeadingTest {
    private double imu = 0;   // the pretend IMU's heading

    @Test public void continuesFromTheHeadingAutonomousSaved() {
        DriverHeading h = new DriverHeading(() -> imu, () -> imu = 0, 90.0);   // auto ended turned left 90
        assertTrue(h.isFromAuto());
        assertEquals(90, h.degrees(), 1e-9);
        imu = 100;                                     // the robot turns 100 more to the left
        assertEquals(-170, h.degrees(), 1e-9);         // 190 wraps to -170
    }

    @Test public void noSavedHeadingStartsAtZero() {
        imu = 30;
        DriverHeading h = new DriverHeading(() -> imu, () -> imu = 0, null);
        assertFalse(h.isFromAuto());
        assertEquals(30, h.degrees(), 1e-9);
    }

    @Test public void resetMakesNowZeroAndForgetsAuto() {
        DriverHeading h = new DriverHeading(() -> imu, () -> imu = 0, 45.0);
        imu = 20;
        h.reset();
        assertFalse(h.isFromAuto());
        assertEquals(0, h.degrees(), 1e-9);
    }

    @Test public void aSecondInitStillFindsTheSavedHeadingUntilStart() {
        io.github.jasoncross.ftchelper.util.PoseStore.save(new io.github.jasoncross.ftchelper.util.FieldPose(0, 0, 90));
        try {
            assertEquals(90, DriverHeading.takeSavedHeading(), 1e-9);   // first INIT
            assertEquals(90, DriverHeading.takeSavedHeading(), 1e-9);   // INIT again after STOP
            new DriverHeading(() -> 0, () -> { }, 90.0).forgetSaved();   // START
            assertEquals(null, DriverHeading.takeSavedHeading());
        } finally {
            io.github.jasoncross.ftchelper.util.PoseStore.clear();
        }
    }
}
