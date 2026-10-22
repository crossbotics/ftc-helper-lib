package com.crossbotics.ftchelper.examples.v2bot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.v2bot.Actions;
import com.crossbotics.ftchelper.examples.v2bot.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * The V2 robot, file 5 of 5: an autonomous. At START the turret finds its home switch (slowly), then
 * follows the goal. The steps drive into range, fire the preload (each shot waits for the speed and for
 * the turret to be aimed), make sure the lifter is stowed, and leave.
 *
 * <p>The interlock rule works here too: had a step raised the lift while the turret pointed back,
 * the lift would wait below its line until the turret turned away.
 *
 * <p>The turret's angle at the end is carried into TeleOp (if TeleOp starts within 5 minutes and the
 * robot app was not restarted), so TeleOp does not need to home it again.
 */
@Autonomous(name = "V2 Bot Shoot and Stow", group = "Examples", preselectTeleOp = "V2 Bot TeleOp")
@Disabled
public class ShootAndStow extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // The spot: x -36 = the middle of the 2nd tile from the left; y -61.6 = an 18 in robot with its
        // back against our wall (inside face at -70.6). Inches, seen from our wall (lesson 7).
        s.start("Back against our wall, 2nd tile from the left, facing the field", -36, -61.6, 0)
         .move("Into range", 18, 0, 0.6)
         .waitUntil("Turret homed", () -> robot.turret.isHomed(), 6.0)
         .run("Fire the preload", () -> robot.launcher.fire(4))
         .waitUntil("Shots done", () -> !robot.launcher.isShooting(), 10.0)
         .action(Actions.stow(robot), 5.0)
         .parkWithSeconds(3)
         .move("Leave", 12, 90, 0.7);
    }

    // BIOBUZZ's field is turned half a turn between the alliances (not mirrored): the same route,
    // driven from each alliance's own wall, works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
