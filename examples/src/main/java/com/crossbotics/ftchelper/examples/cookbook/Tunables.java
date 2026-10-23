package com.crossbotics.ftchelper.examples.cookbook;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE (docs/guides/tunables.md)

/**
 * The cookbook's numbers (docs/cookbook.md): one section per part, named after the part's configuration
 * name in CamelCase ("intake_deploy" reads IntakeDeploy). One number per line with a short comment.
 * MEASURE = a number only your robot can give (INIT stops and names the tool that finds it). DEFAULT =
 * the library's starting value (in brackets in the comment). The tests write sample numbers for MEASURE.
 * The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md: change both
 * together (tools/check_docs.py checks them).
 */
public class Tunables {
    // recipe: tunables-drive
    public static class Drive {                                     // a fixed name: always Drive
        public static double maxPower = 0.6;                        // top wheel power: slow for first drives (1.0)
        public static double practiceMaxPower = 0.4;                // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
        public static double strafeCorrection = DEFAULT;            // sideways moves: Drive Tuner (1.15)
        public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }
    // end recipe

    // recipe: tunables-reset-spot
    public static class Odometry {                                  // a fixed name: always Odometry
        public static boolean checked = false;                      // true after the Odometry Tuner's Loop test
        // "Set pose here" (TeleOp: hold gamepad 1 START 1 s on this spot), written for BLUE.
        public static double resetX = -56;                          // inches right of centre, - = left
        public static double resetY = -61;                          // inches from centre, - = our half
        public static double resetHeading = 0;                      // degrees, 0 = facing the field
        // RED: false = the same numbers from the red wall; true (or left out) = mirrored left-right.
        public static boolean mirrorForRed = false;                 // BIOBUZZ: false
    }
    // end recipe

    // recipe: tunables-intake
    public static class Intake {                                    // "intake" in the configuration
        public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
    }
    // end recipe

    // recipe: tunables-lift
    public static class Lift {                                      // "lift" in the configuration
        public static double min = MEASURE;                         // lowest safe point, counts (Motor Finder)
        public static double max = MEASURE;                         // highest safe point, counts (Motor Finder)
        public static double kP = DEFAULT;                          // push toward the target (from the motor)
        public static double kI = DEFAULT;                          // fix a small steady miss (0)
        public static double kD = DEFAULT;                          // brake near the target (from the motor)
        public static double kF = DEFAULT;                          // hold up against gravity (0.05)
        public static double down = MEASURE;                        // a named height, counts: any name you like
        public static double low = MEASURE;                         // a named height (Motor Finder)
        public static double high = MEASURE;                        // a named height (Motor Finder)
    }
    // end recipe

    // recipe: tunables-lift-two-motors
    // A lift on two motors, Lift.slide("lift_left").part("gobilda-5203-435").alsoMotorReversed("lift_right"):
    // its section is the FIRST motor's name in CamelCase.
    public static class LiftLeft {                                  // "lift_left" in the configuration
        public static double min = MEASURE;                         // lowest safe point, counts (first motor)
        public static double max = MEASURE;                         // highest safe point, counts (first motor)
        public static double kP = DEFAULT;                          // push toward the target (from the motor)
        public static double kI = DEFAULT;                          // fix a small steady miss (0)
        public static double kD = DEFAULT;                          // brake near the target (from the motor)
        public static double kF = 0.06;                             // a heavier slide needs more hold (0.05)
        public static double down = MEASURE;                        // a named height (Motor Finder)
        public static double low = MEASURE;                         // a named height (Motor Finder)
        public static double high = MEASURE;                        // a named height (Motor Finder)
    }
    // end recipe

    // recipe: tunables-arm
    public static class Arm {                                       // "arm" in the configuration
        public static double min = MEASURE;                         // lowest safe angle, degrees, 0 = at INIT
        public static double max = MEASURE;                         // highest safe angle, degrees (Motor Finder)
        public static double kP = DEFAULT;                          // push toward the target (0.02)
        public static double kI = DEFAULT;                          // fix a small steady miss (0)
        public static double kD = DEFAULT;                          // brake near the target (0.001)
        public static double kF = 0.1;                              // hold up against gravity (0)
        public static double stowed = MEASURE;                      // a named angle, degrees: any name you like
        public static double pickup = MEASURE;                      // a named angle (Motor Finder)
        public static double score = MEASURE;                       // a named angle (Motor Finder)
    }
    // end recipe

    // recipe: tunables-claw
    public static class Claw {                                      // "claw" in the configuration
        // The Servo Finder shows "Tunables.Claw: P1 = 0.210, P2 = 0.770": write P1 as open, P2 as closed.
        public static double open = MEASURE;                        // servo position, open (Servo Finder)
        public static double closed = MEASURE;                      // servo position, closed (Servo Finder)
        public static double travelSec = DEFAULT;                   // time for a full move (0.3, or the servo's)
    }
    // end recipe

    // recipe: tunables-wrist
    public static class Wrist {                                     // "wrist" in the configuration
        public static double down = MEASURE;                        // servo position: any name you like
        public static double level = MEASURE;                       // servo position (Servo Finder)
        public static double up = MEASURE;                          // servo position (Servo Finder)
        public static double travelSec = DEFAULT;                   // time for a full move (0.3, or the servo's)
    }
    // end recipe

    // The same numbers as flywheelbot's: robots in one app share the tuning names ("IntakeDeploy/stowed").
    // recipe: tunables-positioner
    public static class IntakeDeploy {                              // "intake_deploy" in the configuration
        public static double stowed = MEASURE;                      // servo position, folded (Servo Finder)
        public static double deployed = MEASURE;                    // servo position, folded out (Servo Finder)
        public static double travelSec = DEFAULT;                   // time for a full move (0.3, or the servo's)
    }
    // end recipe

    public static class Limelight {
        public static double pipeline = 0;
    }

    // recipe: tunables-turret
    public static class Turret {                                    // "turret" in the configuration
        public static double min = MEASURE;                         // furthest right, degrees (Motor Finder)
        public static double max = MEASURE;                         // furthest left, degrees: 0 = forward, + = left
        public static double startAngle = DEFAULT;                  // where you set it by hand before INIT (0)
        public static double kP = DEFAULT;                          // push per degree off (0.02)
        public static double kD = DEFAULT;                          // brake near the target (0.001)
        public static double maxPower = DEFAULT;                    // the most power (0.8)
        public static double tolerance = DEFAULT;                   // "aimed" within this, degrees (1.0)
        public static double cameraOffset = DEFAULT;                // aims a little to one side: Aim Tuner (0)
    }
    // end recipe

    // recipe: tunables-launcher
    public static class Launcher {                                  // "launcher" in the configuration
        public static double speedRpm = DEFAULT;                    // shot speed without the table (60% of top)
        public static double stepRpm = DEFAULT;                     // speed change per D-pad press (100)
        public static double readyBandRpm = DEFAULT;                // "at speed" = this close to the target (60)
        public static double idleRpm = 1500;                        // spins between shots: quicker first shot (0)
        public static double shotIntervalSec = DEFAULT;             // least time between two pieces (0.25)
        // The hub's speed control: the defaults for the motor until the Flywheel Finder (INIT shows them).
        // Then write the Finder's numbers in their place.
        public static double kP = DEFAULT;                          // push per speed error (kF / 10)
        public static double kI = DEFAULT;                          // fix a steady error (kP / 10)
        public static double kD = DEFAULT;                          // brake changes (0)
        public static double kF = DEFAULT;                          // steady power for a speed (from the motor)
        // {inches to the goal, RPM}, blended between rows. Example rows: write the Shot Tuner's instead.
        public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}, {96, 3250}};
    }
    // end recipe

    // recipe: tunables-indexer
    public static class Indexer {                                   // "indexer" in the configuration
        public static double rest = MEASURE;                        // servo position, out of the way (Servo Finder)
        public static double push = MEASURE;                        // servo position, piece pushed (Servo Finder)
        public static double pushSec = DEFAULT;                     // time to push (0.25, or the servo's speed)
        public static double returnSec = DEFAULT;                   // time to come back (the same as pushSec)
    }
    // end recipe

    // recipe: tunables-auto
    /** Your own numbers: a section no part reads. Every number here is in the tuning menu too. */
    public static class Auto {
        public static double leaveInches = 24;                      // drive off the start line
        public static double toGoalInches = 30;                     // drive to the goal
        public static double parkInches = 20;                       // drive into the park zone
        public static double goalX = -30, goalY = -30;              // scoring spot: inches from centre (- y = our half)
        public static double goalHeading = 45;                      // facing there, degrees
        public static double parkX = -56, parkY = -36;              // park spot: inches from centre (- y = our half)
        // Several autos (guides/autos.md): the cycle, and the FAR start's way to the near side.
        public static double pickupInches = 20;                     // drive onto a piece
        public static double offWallInches = 12;                    // back off the wall
        public static double farToNearInches = 72;                  // the far start to the near side
    }
    // end recipe

    // recipe: tunables-buttons
    /** Optional: slower gestures, e.g. for new drivers. Leave the section out for the defaults (in brackets). */
    public static class Buttons {                                   // a fixed name: always Buttons
        public static double tapGapSec = 0.45;                      // most time between the taps of a double-tap (0.35)
        public static double holdSec = 0.4;                         // a press this long is a hold, not a tap (0.3)
    }
    // end recipe
}
