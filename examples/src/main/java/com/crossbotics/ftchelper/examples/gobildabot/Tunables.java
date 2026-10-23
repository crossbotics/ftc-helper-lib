package com.crossbotics.ftchelper.examples.gobildabot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT_FROM_CATALOG (docs/guides/tunables.md)

/**
 * goBILDA starter bot, file 2 of 5: the numbers, one per line. The launcher's come from goBILDA's code:
 * it spins to 1250 counts per second (1250 / 28 x 60 = 2679 RPM) and feeds above 1200 (107 RPM below),
 * with the hub's speed control at P 40, F 12.5. DEFAULT_FROM_CATALOG = the library's default (in brackets).
 */
public class Tunables {
    /** calibrated: set true after the Drive Tuner (autonomous refuses to run before). */
    public static class Drive {
        public static double maxPower = DEFAULT_FROM_CATALOG;       // top wheel power (1.0)
        public static double practiceMaxPower = DEFAULT_FROM_CATALOG; // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT_FROM_CATALOG;      // top power with the slow trigger held (0.3)
        public static double strafeCorrection = DEFAULT_FROM_CATALOG; // not used: a tank drive cannot strafe (1.15)
        public static double turnKp = DEFAULT_FROM_CATALOG;         // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT_FROM_CATALOG;         // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT_FROM_CATALOG;   // auto: least power that moves it (0.30)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }

    /** goBILDA's code runs the intake at full power both ways. */
    public static class Intake {
        public static double inPower = DEFAULT_FROM_CATALOG;        // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = 1.0;                        // power pushing pieces out: goBILDA's code
    }

    public static class Launcher {
        public static double speedRpm = 2679;                       // shot speed: goBILDA's 1250 counts per second
        public static double stepRpm = 50;                          // speed change per D-pad press
        public static double readyBandRpm = 107;                    // "at speed" this close: goBILDA feeds above 1200
        public static double idleRpm = DEFAULT_FROM_CATALOG;        // speed between shots, 0 = stop (0)
        // goBILDA's own tested numbers for this kit bot (its starter code: P 40, F 12.5), so kept here.
        // Without these lines the library would start from the parts catalog's (kF 11.7, kP 1.17, kI 0.117).
        public static double kP = 40;                               // push per speed error: goBILDA's number
        public static double kI = 0;                                // fix a steady error: goBILDA's number
        public static double kD = 0;                                // brake changes: goBILDA's number
        public static double kF = 12.5;                             // steady power for a speed: goBILDA's number
        public static double nominalVolts = 12.8;                   // battery volts the gains were found at
        public static double shotIntervalSec = DEFAULT_FROM_CATALOG; // least time between two pieces, s (0.25)
    }

    /**
     * The windmill: full power, for feedSec per piece. goBILDA's code runs it for as long as the button
     * is held; 0.3 s per piece is a starting guess: watch it and tune it.
     */
    public static class Windmill {
        public static double power = DEFAULT_FROM_CATALOG;          // feeder power, 0 to 1 (1.0)
        public static double feedSec = 0.3;                         // seconds per piece: a starting guess
    }

    public static class Auto {
        public static double backInches = 4.7;                      // goBILDA's auto: 120 mm off the wall
        public static double backPower = 0.4;                       // power for that move: goBILDA's auto
        public static double collectInches = 24;                    // AutoCookbook: drive to the pieces (and back)
        public static double leaveInches = 20;                      // AutoCookbook: leave the launch zone
    }
}
