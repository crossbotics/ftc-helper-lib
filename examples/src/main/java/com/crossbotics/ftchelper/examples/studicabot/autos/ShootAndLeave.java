package com.crossbotics.ftchelper.examples.studicabot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.studicabot.Actions;
import com.crossbotics.ftchelper.examples.studicabot.Robot;
import com.crossbotics.ftchelper.examples.studicabot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * Studica starter bot, file 5 of 5: fire the preloaded pieces, then leave the starting area. Studica's
 * own autonomous is not on its page, so this is a simple first one: copy it, change the name and the steps.
 */
@Autonomous(name = "Studica Bot Shoot and Leave", group = "Examples", preselectTeleOp = "Studica Bot TeleOp")
@Disabled
public class ShootAndLeave extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // The spot is the simulator robot file's: back against our wall, 56 in left of the centre.
        s.start("Back against our wall, facing the field", -56, -63.5, 0)
         .action(Actions.fireAll(robot), 12)
         .parkWithSeconds(3)                                          // the last 3 s are for leaving
         .move("Leave", Tunables.Auto.leaveInches, 0, Tunables.Auto.leavePower);
    }

    // BIOBUZZ's field is turned half a turn between the alliances (not mirrored): the same route,
    // driven from each alliance's own wall, works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
