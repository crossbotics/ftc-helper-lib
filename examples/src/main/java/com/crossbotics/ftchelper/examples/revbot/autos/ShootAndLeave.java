package com.crossbotics.ftchelper.examples.revbot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.revbot.Actions;
import com.crossbotics.ftchelper.examples.revbot.Robot;
import com.crossbotics.ftchelper.examples.revbot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * REV starter bot, file 5 of 5: fire the preloaded pieces, then leave the starting area. REV's own
 * autonomous is not on its page, so this is a simple first one: copy it, change the name and the steps.
 */
@Autonomous(name = "REV Bot Shoot and Leave", group = "Examples", preselectTeleOp = "REV Bot TeleOp")
@Disabled
public class ShootAndLeave extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // The spot is the simulator robot file's: back against our wall, 56 in left of the centre.
        s.start("Back against our wall, facing the field", -56, -61.9, 0)
         .action(Actions.fireAll(robot), 12)
         .parkWithSeconds(3)                                          // the last 3 s are for leaving
         .move("Leave", Tunables.Auto.leaveInches, 0, Tunables.Auto.leavePower);
    }

    // BIOBUZZ's field is turned half a turn between alliances (not mirrored): one route works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
