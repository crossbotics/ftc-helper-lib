package com.crossbotics.ftchelper.examples.flywheelbot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT_FROM_CATALOG and MEASURE (docs/guides/tunables.md)

/**
 * Example robot 1, file 2 of 5: every NUMBER, one section per part. The section's name is the part's
 * configuration name in CamelCase ("intake_deploy" reads {@code IntakeDeploy}).
 *
 * Tune on the robot with the tuning menu (GUIDE on gamepad 1, PRACTICE profile only): a changed value
 * is saved on the Control Hub, and INIT lists every value that differs from this file, ready to copy
 * back. This file stays the record; the saved values are only for tuning.
 *
 * A number you delete here falls back to the library's default (INIT says which).
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
     * readyBandRpm: how close to the speed counts as "ready to feed". kP..kF: the hub's speed control,
     * DEFAULT_FROM_CATALOG until the Flywheel Finder (INIT shows the defaults for this motor: kF 11.7,
     * kP 1.17, kI 0.117). Then write its numbers in their place.
     */
    public static class Launcher {
        public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 0;
        public static double kP = DEFAULT_FROM_CATALOG, kI = DEFAULT_FROM_CATALOG, kD = DEFAULT_FROM_CATALOG, kF = DEFAULT_FROM_CATALOG;
        public static double shotIntervalSec = 0.25;   // the least time between two pieces (seconds)
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

    /**
     * Whole-robot aim (Controls.java: G1 RB). turnKp: turn power per degree off; maxTurnPower: the
     * most it turns by itself; toleranceDeg: this close counts as aimed. rumbleDeg: gamepad 2 buzzes
     * within this many degrees (the closer, the stronger). cameraOffset: the camera's bearing to the
     * goal when the launcher points at it (0 if the camera points the same way as the launcher).
     */
    public static class Aim {
        public static double turnKp = 0.02, turnKd = 0.002, maxTurnPower = 0.5, toleranceDeg = 2;
        public static double cameraOffset = 0, rumbleDeg = 4;
    }

    /**
     * Distances and speeds for the autonomous programs, tuned like the rest: they are in the tuning
     * menu too ("Auto/leaveInches"), and the autonomous uses a saved value at its next INIT. Copy a
     * good value back here: the code is the record.
     */
    public static class Auto {
        public static double leaveInches = 24, leavePower = 0.6;
    }
}
