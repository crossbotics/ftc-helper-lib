package io.github.jasoncross.ftchelper.examples.gobildabot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import io.github.jasoncross.ftchelper.examples.gobildabot.Actions;
import io.github.jasoncross.ftchelper.examples.gobildabot.Robot;
import io.github.jasoncross.ftchelper.examples.gobildabot.Tunables;
import io.github.jasoncross.ftchelper.robot.Auto;
import io.github.jasoncross.ftchelper.robot.Steps;

/**
 * goBILDA starter bot, file 5 of 5: goBILDA's autonomous in steps. It starts against the goal, fires
 * the preloaded pieces, and backs off 120 mm. (goBILDA's code launches for 10 s; here the action ends
 * as soon as the last piece is fed.)
 */
@Autonomous(name = "goBILDA Bot Launch and Back Up", group = "Examples", preselectTeleOp = "goBILDA Bot TeleOp")
@Disabled
public class LaunchAndBackUp extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against the goal, facing it", 0)
         .action(Actions.fireAllWithIntake(robot), 12)
         .move("Back up", Tunables.Auto.backInches, 180, Tunables.Auto.backPower);
    }
}
