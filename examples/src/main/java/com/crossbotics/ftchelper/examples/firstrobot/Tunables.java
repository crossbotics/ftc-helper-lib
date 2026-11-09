package com.crossbotics.ftchelper.examples.firstrobot;

// recipe: first-tunables
import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE

/** The five files, file 2 of 5: the numbers. One section per part, one number per line. */
public class Tunables {
    public static class Drive {
        public static double maxPower = 0.5;                        // top wheel power: slow for the first drives (1.0)
        public static double practiceMaxPower = DEFAULT;            // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
        public static double strafeCorrection = DEFAULT;            // sideways moves: the Drive Tuner finds it (1.15)
        public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }
}
// end recipe
