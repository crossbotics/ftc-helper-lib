package com.crossbotics.ftchelper.examples.lessonbot;

/**
 * The lesson robot, file 2 of 5: every number, one section per part. The section's name is the
 * device's name in CamelCase ("lift" reads Lift). Tune the numbers live in the PRACTICE profile (hold
 * GUIDE 1 s), then copy them back here: the code is the record.
 */
public class Tunables {
    /** Lesson 2. calibrated: set true after the Drive Tuner (lesson 6); until then no autonomous runs. */
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        public static boolean calibrated = false;
    }

    public static class Intake {
        public static double inPower = 1.0, outPower = 0.7;
    }

    /**
     * Lesson 4. Encoder counts from the bottom (the switch sets 0). min/max: the safe range (the Motor
     * Finder measures it). kP..kF: how it holds a height. down, low, high: named heights (any names).
     */
    public static class Lift {
        public static double min = 0, max = 2600;
        public static double kP = 0.004, kI = 0, kD = 0.0002, kF = 0.05;
        public static double down = 0, low = 900, high = 2400;
    }

    /** Lesson 4. Servo positions (the Servo Finder measures them). */
    public static class Claw {
        public static double open = 0.20, closed = 0.75;
    }

    /** Lesson 6: the autonomous's distances, in one place (change them here and build again). */
    public static class Auto {
        public static double toGoalInches = 24, parkInches = 20;
    }
}
