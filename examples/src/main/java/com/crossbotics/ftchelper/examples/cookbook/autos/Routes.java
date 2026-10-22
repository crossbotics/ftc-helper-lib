// recipe: autos-routes
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first three imports below to:  import org.firstinspires.ftc.teamcode.Actions;
//   import org.firstinspires.ftc.teamcode.Robot;   import org.firstinspires.ftc.teamcode.Tunables;

import com.crossbotics.ftchelper.examples.cookbook.Actions;
import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.examples.cookbook.Tunables;
import com.crossbotics.ftchelper.robot.Steps;

/** The pieces every autonomous is made from. Write each piece once. No @Autonomous: not an OpMode. */
public class Routes {
    /** Score the piece the robot starts with: hold it, drive to the goal, score. */
    public static Steps scorePreload(Steps s, Robot robot) {
        return s.run("Hold the preload", robot.claw::close)
                .move("To the goal", Tunables.Auto.toGoalInches, 0, 0.6)          // 0 = forward
                .action(Actions.scoreHigh(robot), 5);
    }

    /** One more piece: back to the pieces, grab one, forward to the goal, score. */
    public static Steps cycle(Steps s, Robot robot) {
        return s.move("To the pieces", Tunables.Auto.pickupInches, 180, 0.6)    // 180 = backward
                .run("Grab", robot.claw::close)
                .waitSeconds("Let the claw close", 0.3)
                .move("Back to the goal", Tunables.Auto.pickupInches, 0, 0.6)
                .action(Actions.scoreHigh(robot), 5);
    }

    /** The park: back toward our wall. It keeps the last 3 seconds for itself, so it always runs. */
    public static Steps park(Steps s) {
        return s.parkWithSeconds(3)
                .move("Park", Tunables.Auto.parkInches, 180, 0.7);
    }
}
// end recipe
