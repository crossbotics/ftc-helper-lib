package io.github.jasoncross.ftchelper.examples.flywheelbot;

/**
 * Example robot 1, file 2 of 5: every NUMBER, one section per part. The section's name is the part's
 * configuration name in CamelCase ("intake_deploy" reads {@code IntakeDeploy}).
 *
 * <p>Tune on the robot with the tuning menu (GUIDE on gamepad 1, PRACTICE profile only): a changed value
 * is saved on the Control Hub, and INIT lists every value that differs from this file, ready to copy
 * back. This file stays the record; the saved values are only for tuning.
 *
 * <p>A number you delete here falls back to the library's default (INIT says which).
 */
public class Tunables {
    /** The drivetrain. calibrated: set true after the Drive Tuner (autonomous refuses to run before). */
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        public static boolean calibrated = false;
    }

    /** Intake roller power, 0 to 1. */
    public static class Intake {
        public static double inPower = 1.0, outPower = 0.7;
    }

    /**
     * The flywheel, in RPM. speedRpm is the shot speed; one D-pad press changes it by stepRpm.
     * readyBandRpm: how close to the speed counts as "ready to feed". kP..kF: the hub's speed control
     * (goBILDA's starter bot code uses P 40, F 12.5 for this motor; the Flywheel Finder finds your own).
     */
    public static class Launcher {
        public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 0;
        public static double kP = 40, kI = 0, kD = 0, kF = 12.5;
    }

    /** The indexer servo: its rest and push positions (0 to 1) and how long each move takes. */
    public static class Indexer {
        public static double rest = 0.15, push = 0.55, pushSec = 0.25, returnSec = 0.25;
    }

    /**
     * The intake deploy servo's positions (0 to 1), and about how long it takes to move between them
     * (a slow, strong servo: watch it and time it).
     */
    public static class IntakeDeploy {
        public static double stowed = 0.0, deployed = 0.8;
        public static double travelSec = 1.0;
    }

    /** Distances and speeds for the autonomous programs, tuned like the rest. */
    public static class Auto {
        public static double leaveInches = 24, leavePower = 0.6;
    }
}
