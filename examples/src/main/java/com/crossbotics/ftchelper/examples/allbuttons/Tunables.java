package com.crossbotics.ftchelper.examples.allbuttons;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE

/** Every pre-made button, written out: the numbers (the library's starting values). */
public class Tunables {
    public static class Drive {
        public static double maxPower = DEFAULT;                    // top wheel power (1.0)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }

    public static class Feeder {                                    // "feeder" in the configuration
        public static double power = DEFAULT;                       // feeder power, 0 to 1 (1.0)
        public static double feedSec = DEFAULT;                     // seconds per piece (0.4)
    }
}
