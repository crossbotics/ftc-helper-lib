package com.crossbotics.ftchelper.examples.cookbook;

// Not in your copy: the cookbook's own Robot.java has no hood, so this file uses the hood recipe's
// robot. In your TeamCode, "Robot" is your Robot.java (same folder: no import).
import com.crossbotics.ftchelper.examples.cookbook.HoodRecipes.Robot;

/*
 * Cookbook (docs/cookbook.md): a whole file, as it looks in your TeamCode. The lines between
 * "// recipe: name" and "// end recipe" are shown on the page; tools/check_docs.py keeps them the same.
 */
// recipe: hood-finder
import com.crossbotics.ftchelper.finders.HoodFinderOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

// The Hood Finder: the hood's two stops (and its angles) for Tunables.Hood. It reads the hood
// from Robot.java (servo or motor), so it works before Tunables.Hood has numbers.
@TeleOp(name = "Cookbook Hood Finder", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class HoodFinder extends HoodFinderOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();     // your Robot.java: the finder finds its hood
    }
}
// end recipe
