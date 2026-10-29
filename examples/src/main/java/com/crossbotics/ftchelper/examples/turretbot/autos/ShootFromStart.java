package com.crossbotics.ftchelper.examples.turretbot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.turretbot.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * Example robot 2, file 5 of 5: the turret follows the goal from START, so the steps only drive
 * into range, get ready, wait for the turret to aim, and fire. Each shot waits for the right speed
 * and the turret aimed. The goal is our HIVE's up CELL: at the start it is on the HIVE's right side
 * (seen from our wall), so the robot starts to the right of it.
 * more: https://crossbotics.com/ftc-helper/docs/guides/autonomous-builder/
 */
@Autonomous(name = "TurretBot Shoot from Start", group = "Examples", preselectTeleOp = "TurretBot TeleOp")
@Disabled
public class ShootFromStart extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // Inches: x 44 = the 2nd tile right of the centre line; y -61.5 = back against our wall.
        s.start("Back against our wall, 2nd tile right of the centre line, facing the field", 44, -61.5, 0)
         .move("Into range", 20, 0, 0.6)
         .turn("Face the HIVE", 45, 0.5)               // 45 degrees left: the camera sees the up CELL
         .readyToLaunch("Ready", 3)                    // spins up, waits until at speed
         .aimAtGoal("Aim", 3)                          // waits until the turret points at the goal
         .action(robot.launcher.fireAllAction())       // fires every piece it holds
         .parkWithSeconds(3)
         .move("Leave", 12, 90, 0.7);
    }

    // BIOBUZZ's field is turned half a turn between the alliances: the same route works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
