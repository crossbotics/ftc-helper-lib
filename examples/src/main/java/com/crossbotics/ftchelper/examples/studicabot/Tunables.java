package com.crossbotics.ftchelper.examples.studicabot;

/**
 * Studica starter bot, file 2 of 5: the numbers. If you tune a number live on the robot, copy the new
 * value back into this file, or it is lost at the next build.
 */
public class Tunables {
    /** calibrated: set true after the Drive Tuner (autonomous refuses to run before). */
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        public static boolean calibrated = false;
    }

    public static class Intake {
        public static double inPower = 1.0, outPower = 1.0;
    }

    /**
     * The flywheel. Studica's code runs it at the right trigger's power, with no set speed. The number
     * here is the MOTOR's speed as the library counts it (see Robot.java): the motor's real top speed
     * shows as about 5200, so 4000 is a starting guess with room to speed up. Tune it by watching shots.
     */
    public static class FlyWheel {
        public static double speedRpm = 4000, stepRpm = 100, readyBandRpm = 150, idleRpm = 0;
    }

    /** The flap: rest = 0 and push = 0.2, from Studica's code (X and A). The times are guesses. */
    public static class FwActuator {
        public static double rest = 0.0, push = 0.2;
        public static double pushSec = 0.3, returnSec = 0.3;
    }

    public static class Auto {
        public static double leaveInches = 20, leavePower = 0.5;
    }
}
