package com.crossbotics.ftchelper.examples.gobildastraferbot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.gobildastraferbot.Actions;
import com.crossbotics.ftchelper.examples.gobildastraferbot.Robot;
import com.crossbotics.ftchelper.examples.gobildastraferbot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * goBILDA mecanum starter bot, file 5 of 5: a first autonomous. goBILDA's mecanum code has no
 * autonomous, so this one is ours: back against our wall, fire the preload, then leave the zone.
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#gobilda-ftc-starterbot-with-mecanum-wheels
 */
@Autonomous(name = "goBILDA Strafer Bot Launch and Leave", group = "Examples", preselectTeleOp = "goBILDA Strafer Bot TeleOp")
@Disabled
public class LaunchAndLeave extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // Inches: y -62.95 = the back against our wall (the robot's centre is 7.5 in from it).
        s.start("Back against our wall, left of centre, facing the field", -56, -62.95, 0)
         .action(Actions.fireAllWithIntake(robot), 12)
         .move("Leave", Tunables.Auto.leaveInches, 0, Tunables.Auto.leavePower);
    }

    // BIOBUZZ's field is turned half a turn between the alliances: the same route works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
