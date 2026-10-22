package com.crossbotics.ftchelper.examples.gobildabot;

/**
 * goBILDA starter bot, file 2 of 5: the numbers. The launcher's come from goBILDA's code: it spins to
 * 1250 counts per second (1250 / 28 x 60 = 2679 RPM) and feeds above 1200 (107 RPM below), with the
 * hub's speed control at P 40, F 12.5.
 */
public class Tunables {
    /** calibrated: set true after the Drive Tuner (autonomous refuses to run before). */
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        public static boolean calibrated = false;
    }

    /** goBILDA's code runs the intake at full power both ways. */
    public static class Intake {
        public static double inPower = 1.0, outPower = 1.0;
    }

    public static class Launcher {
        public static double speedRpm = 2679, stepRpm = 50, readyBandRpm = 107, idleRpm = 0;
        public static double kP = 40, kI = 0, kD = 0, kF = 12.5, nominalVolts = 12.8;
        public static double shotIntervalSec = 0.25;   // the least time between two pieces (seconds)
    }

    /**
     * The windmill: full power, for feedSec per piece. goBILDA's code runs it for as long as the button
     * is held; 0.3 s per piece is a starting guess: watch it and tune it.
     */
    public static class Windmill {
        public static double power = 1.0, feedSec = 0.3;
    }

    public static class Auto {
        public static double backInches = 4.7, backPower = 0.4;   // goBILDA's auto: 120 mm off the wall
        public static double collectInches = 24, leaveInches = 20;  // AutoCookbook's route
    }
}
