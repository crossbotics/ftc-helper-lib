package com.crossbotics.ftchelper.examples.v2bot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.v2bot.Actions;
import com.crossbotics.ftchelper.examples.v2bot.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * The V2 robot, file 5 of 5: an autonomous. At START the turret finds its home switch, then follows
 * the goal. Drive into range, fire the preload, stow the lifter, and leave. TeleOp keeps the
 * turret's angle.
 * more: https://crossbotics.com/ftc-helper/docs/guides/turret-setup/#from-autonomous-to-teleop
 */
@Autonomous(name = "V2 Bot Shoot and Stow", group = "Examples", preselectTeleOp = "V2 Bot TeleOp")
@Disabled
public class ShootAndStow extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // Inches: x -36 = the 2nd tile from the left; y -61.6 = an 18 in robot's back against our wall.
        s.start("Back against our wall, 2nd tile from the left, facing the field", -36, -61.6, 0)
         .move("Into range", 18, 0, 0.6)
         .waitUntil("Turret homed", () -> robot.turret.isHomed(), 6.0)
         .run("Fire the preload", () -> robot.launcher.fire(4))
         .waitUntil("Shots done", () -> !robot.launcher.isShooting(), 10.0)
         .action(Actions.stow(robot), 5.0)
         .parkWithSeconds(3)
         .move("Leave", 12, 90, 0.7);
    }

    // BIOBUZZ's field is turned half a turn between the alliances: the same route works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
