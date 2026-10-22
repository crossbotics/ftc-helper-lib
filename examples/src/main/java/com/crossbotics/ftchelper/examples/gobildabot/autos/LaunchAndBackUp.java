package com.crossbotics.ftchelper.examples.gobildabot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.gobildabot.Actions;
import com.crossbotics.ftchelper.examples.gobildabot.Robot;
import com.crossbotics.ftchelper.examples.gobildabot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * goBILDA starter bot, file 5 of 5: goBILDA's autonomous in steps. It starts with its back against our
 * wall, fires the preloaded pieces (the launcher shoots up and back over the robot), and moves 120 mm
 * away from the wall. (goBILDA's code launches for 10 s; here the action ends as soon as the last piece
 * is fed.)
 */
@Autonomous(name = "goBILDA Bot Launch and Back Up", group = "Examples", preselectTeleOp = "goBILDA Bot TeleOp")
@Disabled
public class LaunchAndBackUp extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // The spot (inches, seen from our wall): x -56, the back against our wall (the robot is 15.1 in
        // long, so its centre is 7.5 in from the wall's inside face at -70.6), facing the field.
        s.start("Back against our wall, left of centre, facing the field", -56, -63, 0)
         .action(Actions.fireAllWithIntake(robot), 12)
         .move("Away from the wall", Tunables.Auto.backInches, 0, Tunables.Auto.backPower);
    }

    // BIOBUZZ's field is turned half a turn between the alliances (not mirrored): the same route,
    // driven from each alliance's own wall, works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
