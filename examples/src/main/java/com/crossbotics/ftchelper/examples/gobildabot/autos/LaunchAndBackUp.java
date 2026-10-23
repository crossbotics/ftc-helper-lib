package com.crossbotics.ftchelper.examples.gobildabot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.gobildabot.Actions;
import com.crossbotics.ftchelper.examples.gobildabot.Robot;
import com.crossbotics.ftchelper.examples.gobildabot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * goBILDA starter bot, file 5 of 5: goBILDA's autonomous in steps. Back against our wall, fire the
 * preload (up and back over the robot), then move 120 mm away from the wall.
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#gobilda-ftc-starterbot
 */
@Autonomous(name = "goBILDA Bot Launch and Back Up", group = "Examples", preselectTeleOp = "goBILDA Bot TeleOp")
@Disabled
public class LaunchAndBackUp extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // Inches: y -63 = the back against our wall (the robot's centre is 7.5 in from it).
        s.start("Back against our wall, left of centre, facing the field", -56, -63, 0)
         .action(Actions.fireAllWithIntake(robot), 12)
         .move("Away from the wall", Tunables.Auto.backInches, 0, Tunables.Auto.backPower);
    }

    // BIOBUZZ's field is turned half a turn between the alliances: the same route works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
