package com.crossbotics.ftchelper.examples.cookbook;

/**
 * The cookbook's numbers (docs/cookbook.md). Every robot in this folder reads its sections from here:
 * one section per part, named after the part's configuration name in CamelCase ("intake_deploy" reads
 * IntakeDeploy).
 *
 * <p>The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing, so change both together.
 */
public class Tunables {
    // recipe: tunables-drive
    public static class Drive {
        // Keep it slow for the first drives; raise maxPower when everything works.
        public static double maxPower = 0.6, practiceMaxPower = 0.4, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        // false until the Drive Tuner (lesson 6): until then no autonomous drives the robot.
        public static boolean calibrated = false;
    }
    // end recipe

    // recipe: tunables-reset-spot
    public static class Odometry {
        public static boolean checked = false;   // true after the Odometry Tuner's Loop test passes
        // "Set pose here" (TeleOp: hold gamepad 1 START 1 s with the robot on this spot). Inches and
        // degrees seen from your wall, written for BLUE like a start spot: our left corner tile, facing the field.
        public static double resetX = -56, resetY = -61, resetHeading = 0;
        // RED: false = the same numbers from the red wall (BIOBUZZ's field is turned half a turn);
        // true (or left out) = mirrored left-right, for a mirrored field.
        public static boolean mirrorForRed = false;
    }
    // end recipe

    public static class Intake {
        public static double inPower = 1.0, outPower = 0.7;
    }

    // recipe: tunables-lift
    public static class Lift {
        public static double min = 0, max = 2600;              // the safe range, counts (Motor Finder)
        public static double kP = 0.004, kI = 0, kD = 0.0002, kF = 0.05;
        public static double down = 0, low = 900, high = 2400; // named heights: any names you like
    }
    // end recipe

    // recipe: tunables-arm
    public static class Arm {
        public static double min = -10, max = 120;              // degrees, 0 = where it is at INIT
        public static double kP = 0.02, kI = 0, kD = 0.001, kF = 0.1;
        public static double stowed = 0, pickup = 10, score = 95;
    }
    // end recipe

    // recipe: tunables-claw
    public static class Claw {
        // From the Servo Finder's line "Tunables.Claw: P1 = 0.210, P2 = 0.770", P1 and P2 renamed.
        public static double open = 0.210, closed = 0.770;
        public static double travelSec = 0.4;                   // about how long it takes to move
    }
    // end recipe

    public static class Wrist {
        public static double down = 0.15, level = 0.45, up = 0.80;
    }

    // The same numbers as flywheelbot's: robots in one app share the tuning names ("IntakeDeploy/stowed").
    public static class IntakeDeploy {
        public static double stowed = 0.0, deployed = 0.8;
    }

    public static class Limelight {
        public static double pipeline = 0;
    }

    public static class Turret {
        public static double min = -90, max = 90;               // degrees, counterclockwise +, 0 = forward
        public static double kP = 0.02, kI = 0, kD = 0.001;
        public static double maxPower = 0.8, tolerance = 1.5, cameraOffset = 0;
        // Slows down within slowZone degrees of a limit, to slowZonePower; a goal (from the odometry) more
        // than outOfRangeHold degrees past a limit: it holds where it points (the library's numbers).
        public static double slowZone = 10, slowZonePower = 0.25, outOfRangeHold = 10;
    }

    // recipe: tunables-launcher
    public static class Launcher {
        public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 1500;
        public static double kP = 40, kI = 0, kD = 0, kF = 12.5;   // from the Flywheel Finder
        public static double shotIntervalSec = 0.25;   // the least time between two pieces (seconds)
        // {inches to the goal, RPM}: blended between rows (from the Shot Tuner).
        public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}, {96, 3250}};
    }
    // end recipe

    public static class Indexer {
        public static double rest = 0.15, push = 0.55, pushSec = 0.25, returnSec = 0.25;
    }

    // recipe: tunables-auto
    /** Your own numbers: a section no part reads. Every number here is in the tuning menu too. */
    public static class Auto {
        public static double leaveInches = 24, toGoalInches = 30, parkInches = 20;
        public static double goalX = -30, goalY = -30, goalHeading = 45;
        public static double parkX = -56, parkY = -36;
        public static double pickupInches = 20, farToNearInches = 72;   // several autos (guides/autos.md)
    }
    // end recipe

    // recipe: tunables-buttons
    /** Optional: the gesture times. Leave this section out to use these defaults. */
    public static class Buttons {
        public static double tapGapSec = 0.35;   // the most time between the taps of a double-tap
        public static double holdSec = 0.3;      // a press this long is a hold, not a tap
    }
    // end recipe
}
