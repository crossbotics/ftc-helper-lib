package com.crossbotics.ftchelper.examples.lessonbot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE

/**
 * The lesson robot, file 2 of 5: every number, one per line. Section name = the device's name in
 * CamelCase ("lift" reads Lift). DEFAULT = the library's default (in brackets). Tune live, then
 * copy the numbers back here: the code is the record.
 * more: https://crossbotics.com/ftc-helper/docs/guides/tunables/
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

    /** Lesson 4. Encoder counts from the bottom (the switch sets 0). Tune kP..kF live (lesson 4, step 6). */
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

    /** Lesson 6: the autonomous's distances. Also in the tuning menu: tune, then copy them back here. */
    public static class Auto {
        public static double toGoalInches = 24;                     // drive to the goal
        public static double parkInches = 20;                       // drive into the park zone
    }
}
