package io.github.jasoncross.ftchelper.examples.flywheelbot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import io.github.jasoncross.ftchelper.examples.flywheelbot.Actions;
import io.github.jasoncross.ftchelper.examples.flywheelbot.Robot;
import io.github.jasoncross.ftchelper.examples.flywheelbot.Tunables;
import io.github.jasoncross.ftchelper.robot.Auto;
import io.github.jasoncross.ftchelper.robot.Steps;

/**
 * Example robot 1, file 5 of 5 (one per autonomous): shoot the preloaded pieces, then leave the
 * starting area. A new autonomous: copy this file, change the name and the steps.
 *
 * <p>The INIT menu (alliance, start delay, the "Shoot" choice below, profile) comes for free. Moves
 * and turns are written for BLUE and mirrored for RED. The intake deploy and the intake running are
 * done at START by Robot.java.
 */
@Autonomous(name = "FlywheelBot Shoot and Leave", group = "Examples", preselectTeleOp = "FlywheelBot TeleOp")
@Disabled
public class ShootAndLeave extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // The note for the drive team, then the spot as seen from the BLUE wall (mirrored for RED):
        // 56 in right of the centre line, 61.55 in from the centre toward our wall, facing the field.
        s.start("Back wall, touching the goal side, facing the goal", 56, -61.55, 0)
         .menuYesNo("Shoot", true)                                    // a choice in the INIT menu
         .onlyIf("Shoot")
             .run("Spin up", robot.launcher::spinUp)                  // starts, and goes on at once
             .waitUntil("At speed", robot.launcher::isReady, 3.0)
             .action(Actions.fireThree(robot))                        // waits until it is done
         .end()
         .parkWithSeconds(3)                                          // the last 3 s are for leaving
         .move("Leave", Tunables.Auto.leaveInches, 0, Tunables.Auto.leavePower);
    }
}
