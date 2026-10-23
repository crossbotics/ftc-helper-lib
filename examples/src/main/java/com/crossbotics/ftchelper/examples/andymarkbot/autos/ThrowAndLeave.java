package com.crossbotics.ftchelper.examples.andymarkbot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.andymarkbot.Actions;
import com.crossbotics.ftchelper.examples.andymarkbot.Robot;
import com.crossbotics.ftchelper.examples.andymarkbot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * AndyMark starter bot, file 5 of 5: throw the preloaded pieces, then leave the starting area.
 * AndyMark's manual says to shoot from against the field wall with the launcher in line with the goal.
 */
@Autonomous(name = "AndyMark Bot Throw and Leave", group = "Examples", preselectTeleOp = "AndyMark Bot TeleOp")
@Disabled
public class ThrowAndLeave extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // The spot is the simulator robot file's: back against our wall, 56 in left of the centre.
        s.start("Back against our wall, launcher in line with the goal", -56, -62.55, 0)
         .action(Actions.throwAll(robot), 12)
         .parkWithSeconds(3)                                          // the last 3 s are for leaving
         .move("Leave", Tunables.Auto.leaveInches, 0, Tunables.Auto.leavePower);
    }

    // BIOBUZZ's field is turned half a turn between alliances (not mirrored): one route works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
