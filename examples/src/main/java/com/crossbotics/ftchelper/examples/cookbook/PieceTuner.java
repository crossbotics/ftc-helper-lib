package com.crossbotics.ftchelper.examples.cookbook;

/*
 * Cookbook (docs/cookbook.md): a whole file, as it looks in your TeamCode (under the package line that
 * Android Studio writes). The lines between "// recipe: name" and "// end recipe" are shown on the
 * page; tools/check_docs.py keeps them the same.
 *
 * Here "Robot" is the game-pieces recipe robot (it has the piece sensors); in your TeamCode it is your
 * own Robot.java, so this one import line is not in your copy.
 */
import com.crossbotics.ftchelper.examples.cookbook.PieceRecipes.Robot;
// recipe: piece-tuner
import com.crossbotics.ftchelper.finders.PieceTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

// The Piece Tuner: each sensor's readings, and the numbers for Tunables.java.
@TeleOp(name = "Cookbook Piece Tuner", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class PieceTuner extends PieceTunerOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();     // your Robot.java: the tuner finds its piece sensors
    }
}
// end recipe
