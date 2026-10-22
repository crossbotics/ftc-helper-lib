package com.crossbotics.ftchelper.examples.revbot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT_FROM_CATALOG and MEASURE (docs/guides/tunables.md)

/**
 * REV starter bot, file 2 of 5: the numbers. REV's pages do not give the flywheel's speed or the
 * poker's positions, so those numbers are starting guesses: find the real ones on the robot with the
 * finders (docs/guides/finders.md). If you tune a number live on the robot, copy the new value back
 * into this file, or it is lost at the next build.
 */
public class Tunables {
    /** calibrated: set true after the Drive Tuner (autonomous refuses to run before). */
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        public static boolean calibrated = false;
    }

    public static class IntakeMotor {
        public static double inPower = 1.0, outPower = 1.0;
    }

    /**
     * The flywheel. REV's code spins it "to the target velocity", but the number is not on the page:
     * 3000 RPM is a guess. Find yours with the Flywheel Finder (kF and kP) and by watching shots.
     */
    public static class Flywheel {
        public static double speedRpm = 3000, stepRpm = 100, readyBandRpm = 100, idleRpm = 0;
        // The hub's speed control: the defaults for the motor until the Flywheel Finder (INIT shows them).
        // Then write its numbers in their place.
        public static double kP = DEFAULT_FROM_CATALOG, kI = DEFAULT_FROM_CATALOG, kD = DEFAULT_FROM_CATALOG, kF = DEFAULT_FROM_CATALOG;
    }

    /** The feeder roller (CR servo): full power for feedSec per piece. 0.4 s is a guess: watch it and tune it. */
    public static class CrServo {
        public static double power = 1.0, feedSec = 0.4;
    }

    /**
     * The poker. REV's code has two preset spots (A = down, B = up); the numbers are not on the page.
     * up = folded along the side (the start); down = out over a FLOWER. Both are guesses: find these
     * with the Servo Finder BEFORE the first TeleOp, so the poker does not swing into the frame.
     */
    public static class PollenServo {
        public static double up = 0.0, down = 0.5;
    }

    public static class Auto {
        public static double leaveInches = 20, leavePower = 0.5;
    }
}
