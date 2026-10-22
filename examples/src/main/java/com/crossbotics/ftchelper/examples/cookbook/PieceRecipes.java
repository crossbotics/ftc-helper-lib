package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.finders.PieceTunerOpMode;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.LightColor;
import com.crossbotics.ftchelper.robot.Lights;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.PieceSensor;
import com.crossbotics.ftchelper.robot.Pieces;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Cookbook: game pieces (docs/cookbook.md#game-pieces). Which piece is in the robot, how many it holds,
 * and which one fires next, from a colour sensor, a distance sensor or a second camera.
 *
 * <p>The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class PieceRecipes {

    /** A robot that counts its pieces: an intake, a launcher, two colour sensors, a light. */
    public static class Robot extends RobotDefinition {
        public Intake intake;
        public Launcher launcher;
        public Pieces pieces;
        public Lights lights;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));

            // recipe: pieces-count
            // At the top of the class: public Pieces pieces;   Here in define():
            // the kinds of piece (BIOBUZZ: yellow POLLEN, red and blue NECTAR), a sensor where they
            // come in, one at the "ready to fire" slot (optional), the most the rules allow, the preload.
            pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
                    .comingIn(PieceSensor.color("intake_color"))    // a REV Color Sensor V3
                    .readyToFire(PieceSensor.color("ready_color"))  // another one
                    .max(4)                                         // BIOBUZZ G407: no more than 4
                    .startsWith(4, "POLLEN"));                      // the 4 POLLEN preload
            // end recipe

            // recipe: intake-stops-when-full
            // ADD .stopsWhen(...) to the intake line you already have (do not write a second intake line).
            // It does not pull in a 5th piece ("() ->": asked every loop). Pushing out still works.
            intake = add(Intake.motor("intake").stopsWhen(() -> pieces.isFull()));
            // end recipe

            // recipe: launcher-needs-a-piece
            // Each feed counts one piece out. requireReady: no feed with nothing to fire.
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.servo("indexer"))
                    .requireReady("a piece to fire", () -> !pieces.isEmpty()));
            // end recipe

            lights = add(Lights.gobilda("light"));
        }
    }

    /** Other sensors: a distance sensor that tells sizes, a second webcam. */
    public static class OtherSensors extends RobotDefinition {
        public Pieces pieces;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: pieces-other-sensors
            // A REV 2m Distance Sensor across the intake: a NECTAR (3.6 in) comes closer than a
            // POLLEN (2.8 in). Size only: it cannot tell a red NECTAR from a blue one.
            // A second webcam on the ready slot: colours (the main colour in a box of its picture).
            pieces = add(Pieces.kinds("POLLEN", "NECTAR")
                    .comingIn(PieceSensor.distance("intake_distance"))
                    .readyToFire(PieceSensor.camera("Webcam 2"))
                    .max(4));
            // end recipe
        }
    }

    /** Using the count: a buzz when full, lights, a line on the screen. */
    @TeleOp(name = "Cookbook Pieces", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: pieces-use
            // A buzz on gamepad 2 when the robot holds the most it may.
            g2.rumbleWhen(robot.pieces::isFull);
            // Two buzzes when a NECTAR is next to fire.
            g2.rumbleWhen(robot.pieces.whenNextIs("RED_NECTAR"), 2);
            // A light the drivers see: green = full, red = empty (needs a Lights line in Robot.java, and
            // import com.crossbotics.ftchelper.robot.LightColor; at the top of this file).
            robot.lights.show(LightColor.GREEN).when(robot.pieces.whenFull());
            robot.lights.show(LightColor.RED).when(robot.pieces.whenEmpty());
            // Pieces shows "3/4, next POLLEN" by itself; this adds what the intake sees now.
            show("At the intake", robot.pieces::atIntake);
            // The count went wrong? Already on the buttons: hold G2's left stick button 1 s = count 0,
            // and hold the intake's RT 1 s to pull in although the count says full.
            // end recipe
        }
    }

    // recipe: piece-tuner
    // The Piece Tuner: each sensor's readings, and the numbers for Tunables.java.
    @TeleOp(name = "Cookbook Piece Tuner", group = "Finders")
    @Disabled   // delete this line in your copy, or you will not see it
    public static class PieceTuner extends PieceTunerOpMode {
        @Override
        protected RobotDefinition robot() {
            return new Robot();     // your Robot.java: the tuner finds its piece sensors
        }
    }
    // end recipe
}
