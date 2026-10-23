package com.crossbotics.ftchelper.examples.cookbook;

import static com.crossbotics.ftchelper.robot.Starting.*;

import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Turret;
import com.crossbotics.ftchelper.robot.Wrist;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Cookbook: turrets and interlocks (docs/cookbook.md#turrets-and-interlocks). A turret with a home
 * switch, a slip-ring turret, an interlock rule that keeps a turret and a lift apart, and the lifter's
 * steps (wrist, claw, lift) in a safe order.
 *
 * The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class InterlockRecipes {

    /** The numbers these recipes read. In your TeamCode these sections go in Tunables.java. */
    public static class Numbers {
        // recipe: tunables-turret-switch
        public static class Turret {                                    // "turret" in the configuration
            public static double min = MEASURE;                         // furthest right, degrees (Motor Finder)
            public static double max = MEASURE;                         // furthest left, degrees: 0 = forward, + = left
            public static double homeAngle = MEASURE;                   // where the switch closes (turret-setup.md)
            public static double homingPower = DEFAULT;                 // how slowly it looks for the switch (0.2)
            public static double homingTimeout = DEFAULT;               // the most seconds it looks (5)
        }
        // end recipe

        public static class Lift {
            public static double min = 0, max = 1350;
            public static double pickup = 0, stowed = 44, flower = 1330;
        }

        public static class Wrist {
            public static double folded = 0.10, pickup = 0.167, carry = 0.40, flower = 0.509;
        }

        public static class Claw {
            public static double open = 0.55, closed = 0.47;
        }

        // recipe: tunables-interlock
        // The rule's own section, named like the rule in Robot.java. Each zone in its part's units.
        public static class TurretLift {                                // keepApart("TurretLift", ...) in Robot.java
            public static double turretFrom = MEASURE;                  // degrees: where the turret's zone starts
            public static double turretTo = MEASURE;                    // degrees: where it ends (robot's back)
            public static double liftAbove = MEASURE;                   // counts: the slide is up from here
            public static double liftStopSec = 0.2;                     // a heavy slide takes longer to stop (0.15)
        }
        // end recipe
    }

    /** A turret with a home switch, a lift with a wrist and a claw, and the rule that keeps them apart. */
    public static class Robot extends RobotDefinition {
        public Camera camera;
        public Turret turret;
        public Lift lift;
        public Wrist wrist;
        public Claw claw;

        @Override
        protected void define() {
            tunables(Numbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
            // recipe: turret-home-switch
            // A home switch: a touch sensor ("REV Touch Sensor") or a magnetic switch ("Digital
            // Device", e.g. the REV Magnetic Limit Switch). At START the turret turns slowly until it
            // finds it; after that, every time it passes the switch its angle is set right again.
            // "turret", "turret_home" = the motor's and the switch's names in the robot configuration.
            turret = add(Turret.motor("turret", "gobilda-5203-312").gearRatio(2.75)
                    .homeSwitch("turret_home")
                    .follows(camera));
            // end recipe
            lift = add(Lift.slide("lift").part("gobilda-5203-435"));
            wrist = add(Wrist.servo("wrist"));
            claw = add(Claw.servo("claw"));
            // recipe: interlock-rule
            // AFTER the parts' add lines: the turret stays out of its zone while the lift is in its
            // zone, and the other way round. Whoever is in its zone first keeps it.
            keepApart("TurretLift", turret.zone(), lift.zoneAbove());
            // end recipe
        }
    }

    /** The numbers for the slip-ring recipe. */
    public static class SlipRingNumbers {
        // recipe: tunables-turret-slip-ring
        public static class Turret {                                    // "turret" in the configuration
            // No min and max: it turns round and round.
            public static double kP = 0.015;                            // push per degree off, CR servo (0.02)
            public static double kD = DEFAULT;                          // brake near the target (0.001)
            public static double maxPower = 1.0;                        // a CR servo's full speed (0.8)
            public static double tolerance = DEFAULT;                   // "aimed" within this, degrees (1.0)
            public static double encoderOffset = MEASURE;               // encoder reading, pointing forward
        }
        // end recipe
    }

    /** A slip-ring turret on a CR servo with an absolute encoder. */
    public static class SlipRingRobot extends RobotDefinition {
        public Camera camera;
        public Turret turret;

        @Override
        protected void define() {
            tunables(SlipRingNumbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
            // recipe: turret-slip-ring
            // A CR servo turns it, an absolute encoder on an analog port says where it points (no
            // homing), and continuous(): a slip ring carries the wires, so no limits, the shortest way.
            // "turret", "turret_pos" = the servo's and the encoder's names in the robot configuration.
            turret = add(Turret.crServo("turret").analogEncoder("turret_pos").continuous().follows(camera));
            // end recipe
        }
    }

    /** The numbers for the servo turret recipe. */
    public static class ServoTurretNumbers {
        // recipe: tunables-turret-servo
        public static class Turret {                                    // "turret" in the configuration
            // Two servo positions and the turret's angle at each: 0 = straight forward, counterclockwise
            // (left) +. Its range is between the two angles.
            public static double posA = MEASURE;                        // servo position at one end (Servo Finder)
            public static double angleA = MEASURE;                      // the turret's angle there (an angle app)
            public static double posB = MEASURE;                        // servo position, other end (Servo Finder)
            public static double angleB = MEASURE;                      // the turret's angle there (an angle app)
            public static double travelSec = DEFAULT;                   // posA to posB, seconds (0.6, or the servo's)
            public static double startAngle = DEFAULT;                  // sent here at START (0)
        }
        // end recipe
    }

    /** A turret on a standard servo, with its full range. */
    public static class ServoTurretRobot extends RobotDefinition {
        public Camera camera;
        public Turret turret;

        @Override
        protected void define() {
            tunables(ServoTurretNumbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
            // recipe: turret-servo
            // A standard servo turns the turret to an angle by itself: no encoder, no PID, no homing.
            // fullRange(): the servo's whole turn (a goBILDA 2000: 300 degrees, not 270). Turn it on
            // BEFORE you measure posA and posB (Servo Finder: press Y while choosing the servo).
            // "turret" = the servo's name in the robot configuration. Numbers: Tunables.Turret.
            turret = add(Turret.servo("turret").part("gobilda-2000-speed").fullRange().follows(camera));
            // end recipe
        }
    }

    /** The numbers for the odometry-only aim recipe. */
    public static class OdometryAimNumbers {
        public static class Odometry {
            public static boolean checked = false;
        }

        public static class Turret {
            public static double min = -90, max = 90;
        }

        public static class Launcher {
            public static double speedRpm = 2600, readyBandRpm = 60, idleRpm = 1500;
            public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}};
        }

        public static class Indexer {
            public static double rest = 0.15, push = 0.55;
        }

        // recipe: tunables-aim-odometry-only
        public static class Aim {                                       // a fixed name: always Aim
            // Where the goal is, seen from YOUR drivers' wall (inches; the same numbers as driveTo).
            // BIOBUZZ: your HIVE's CELL that is up at the start.
            public static double goalX = 13;                            // inches right of centre (BIOBUZZ)
            public static double goalY = -13;                           // inches from centre, - = our half (BIOBUZZ)
            public static boolean mirrorForRed = false;                 // BIOBUZZ's field is turned, not mirrored
        }
        // end recipe
    }

    /** A turret and a speed from the odometry alone: no camera on the robot. */
    public static class OdometryAimRobot extends RobotDefinition {
        public Turret turret;
        public Launcher launcher;

        @Override
        protected void define() {
            tunables(OdometryAimNumbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: aim-odometry-only
            // No camera: the odometry says where the robot is, Tunables.Aim where the goal is. The
            // turret follows that direction and the launcher's speed comes from that distance.
            odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
            turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).followsOdometry());
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
                    .speedFromOdometry()
                    .requireReady("turret aimed", () -> turret.isAimed()));
            // end recipe
        }
    }

    // recipe: lifter-sequence
    /** Lifts a held ball over a FLOWER, lets it go and comes back folded (Actions.java). */
    public static Action scoreInFlower(Robot r) {
        return Action.named("Score in FLOWER")
                .then(() -> r.wrist.goTo("carry"))                  // 1. swing the claw out first
                .waitUntil("wrist out", r.wrist::isDone, 1.0)
                .then(() -> r.lift.goTo("flower"))                  // 2. lift (the interlock may make it wait)
                .waitUntil("lift up", r.lift::isAtTarget, 4.0)
                .then(() -> r.wrist.goTo("flower"))                 // 3. tip it over the FLOWER
                .waitUntil("over the FLOWER", r.wrist::isDone, 1.0)
                .then(r.claw::open)                                 // 4. let go
                .waitUntil("let go", r.claw::isDone, 1.0)
                .then(() -> r.wrist.goTo("carry"))                  // 5. back the same way
                .waitUntil("wrist back", r.wrist::isDone, 1.0)
                .then(() -> r.lift.goTo("stowed"))
                .waitUntil("lift down", r.lift::isAtTarget, 3.0)
                .then(() -> r.wrist.goTo("folded"), r.claw::close);
    }
    // end recipe

    /** The lifter's action on a button. */
    @TeleOp(name = "Cookbook Interlock", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            g2.y.press(scoreInFlower(robot)).says("score in a FLOWER");
        }
    }
}
