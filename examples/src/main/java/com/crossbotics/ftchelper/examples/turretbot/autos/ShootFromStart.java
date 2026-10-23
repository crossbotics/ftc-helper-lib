package com.crossbotics.ftchelper.examples.turretbot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.turretbot.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * Example robot 2, file 5 of 5: the turret follows the goal from START, so the steps only drive
 * into range and fire. Each shot waits for the right speed and the turret aimed.
 * more: https://crossbotics.com/ftc-helper/docs/guides/autonomous-builder/
 */
@Autonomous(name = "TurretBot Shoot from Start", group = "Examples", preselectTeleOp = "TurretBot TeleOp")
@Disabled
public class ShootFromStart extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Back wall, 2nd tile from the left, facing the field", 0)
         .move("Into range", 18, 0, 0.6)
         .run("Fire three", () -> robot.launcher.fire(3))
         .waitUntil("Shots done", () -> !robot.launcher.isShooting(), 8.0)
         .parkWithSeconds(3)
         .move("Leave", 12, 90, 0.7);
    }

    // BIOBUZZ's field is turned half a turn between the alliances: the same route works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
