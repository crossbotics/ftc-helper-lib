// recipe: autos-choose-in-init
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first two imports below to:  import org.firstinspires.ftc.teamcode.Robot;
//   import org.firstinspires.ftc.teamcode.Tunables;

import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.examples.cookbook.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/** ONE autonomous: the drivers choose the start and the plan in INIT. */
@Autonomous(name = "Choose in INIT", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy
public class ChooseInInit extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        // Your menu lines (under the library's Alliance and Start delay lines). The FIRST option of
        // each is chosen at INIT: make it your safest plan.
        s.menuChoice("Start", "Near", "Far")
         .menuChoice("Plan", "Preload + Park", "Preload + Cycle + Park", "Park only");

        // The start spot follows the "Start" line: the note and the simulator's robot change with it.
        s.onlyIf("Start", "Near")
             .start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0)
         .end()
         .onlyIf("Start", "Far")
             .start("FAR: against our wall, 2nd tile right of centre, facing the field", 36, -61, 0)
             // Off the wall first: the FLOWER on our wall stands between the two start spots.
             .move("Off the wall", Tunables.Auto.offWallInches, 0, 0.5)
             .move("Across to the near side", Tunables.Auto.farToNearInches, 270, 0.7)   // 270 = left
         .end();

        // The plan: plain Java "if", because the pieces are methods. choice(...) gives the option
        // chosen, as text (a String). equals(...): the same text. "!" means "not".
        String plan = s.choice("Plan");
        if (!plan.equals("Park only")) Routes.scorePreload(s, robot);
        if (plan.equals("Preload + Cycle + Park")) Routes.cycle(s, robot);
        Routes.park(s);
    }
}
// end recipe
