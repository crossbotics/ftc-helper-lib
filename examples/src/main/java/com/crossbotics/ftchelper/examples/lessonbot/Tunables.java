package com.crossbotics.ftchelper.examples.lessonbot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE (docs/guides/tunables.md)

/**
 * The lesson robot, file 2 of 5: every number, one section per part, one number per line. The section's
 * name is the device's name in CamelCase ("lift" reads Lift). DEFAULT = the library's
 * default (in brackets in the comment). Tune the numbers live in the PRACTICE profile (hold GUIDE 1 s),
 * then copy them back here: the code is the record.
 */
public class Tunables {
    /** Lesson 2. calibrated: set true after the Drive Tuner (lesson 6); until then no autonomous runs. */
    public static class Drive {
        public static double maxPower = DEFAULT;                    // top wheel power (1.0)
        public static double practiceMaxPower = DEFAULT;            // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
        public static double strafeCorrection = DEFAULT;            // sideways moves: Drive Tuner (1.15)
        public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }

    public static class Intake {
        public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
    }

    /**
     * Lesson 4. Encoder counts from the bottom (the switch sets 0). min/max: the safe range (the Motor
     * Finder measures it). down, low, high: named heights (any names). kP..kF: DEFAULT, the
     * defaults for the motor Robot.java names (part "gobilda-5203-435"; INIT shows them). Tune them live
     * (lesson 4, step 6) and write the numbers in their place.
     */
    public static class Lift {
        public static double min = 0;                               // lowest safe point, counts (Motor Finder)
        public static double max = 2600;                            // highest safe point, counts (Motor Finder)
        public static double kP = DEFAULT;                          // push toward the height (from the motor)
        public static double kI = DEFAULT;                          // fix a small steady miss (0)
        public static double kD = DEFAULT;                          // brake near the height (from the motor)
        public static double kF = DEFAULT;                          // hold up against gravity (0.05)
        public static double down = 0;                              // a named height, counts: any name you like
        public static double low = 900;                             // a named height (Motor Finder)
        public static double high = 2400;                           // a named height (Motor Finder)
    }

    /** Lesson 4. Servo positions (the Servo Finder measures them). */
    public static class Claw {
        public static double open = 0.20;                           // servo position, open (Servo Finder)
        public static double closed = 0.75;                         // servo position, closed (Servo Finder)
    }

    /**
     * Lesson 6: the autonomous's distances, in one place. No part reads this section, but its numbers
     * are in the tuning menu too (TeleOp, PRACTICE, after START hold GUIDE 1 s): tune, save, run the autonomous,
     * then copy the good values back here (INIT lists them): the code is the record.
     */
    public static class Auto {
        public static double toGoalInches = 24;                     // drive to the goal
        public static double parkInches = 20;                       // drive into the park zone
    }
}
