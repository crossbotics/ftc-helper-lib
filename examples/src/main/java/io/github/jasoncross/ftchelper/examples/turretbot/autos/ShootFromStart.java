package io.github.jasoncross.ftchelper.examples.turretbot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import io.github.jasoncross.ftchelper.examples.turretbot.Robot;
import io.github.jasoncross.ftchelper.robot.Auto;
import io.github.jasoncross.ftchelper.robot.Steps;

/**
 * Example robot 2, file 5 of 5: the turret follows the goal from START (a mode), so the steps only
 * drive into range and fire. Each shot waits until the speed for this distance is reached and the
 * turret is aimed.
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
}
