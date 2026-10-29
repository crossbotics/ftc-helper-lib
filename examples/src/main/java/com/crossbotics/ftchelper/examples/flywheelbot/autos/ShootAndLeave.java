package com.crossbotics.ftchelper.examples.flywheelbot.autos;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.examples.flywheelbot.Robot;
import com.crossbotics.ftchelper.examples.flywheelbot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;

/**
 * Example robot 1, file 5 of 5 (one per autonomous): shoot the preload, then leave the starting
 * area. The goal is our HIVE's up CELL: at the start it is on the HIVE's right side (seen from our
 * wall), so the robot starts to the right of it. A new autonomous: copy this file, change the name
 * and the steps.
 * more: https://crossbotics.com/ftc-helper/docs/guides/autonomous-builder/
 */
@Autonomous(name = "FlywheelBot Shoot and Leave", group = "Examples", preselectTeleOp = "FlywheelBot TeleOp")
@Disabled
public class ShootAndLeave extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        // A note for the drive team, then the spot in inches (seen from our wall), facing the field.
        s.start("Back wall, 2nd tile right of the centre line, facing the field", 48, -61.55, 0)
         .menuYesNo("Shoot", true)                                    // a choice in the INIT menu
         .onlyIf("Shoot")
             .move("Into range", 16, 0, 0.6)
             .aimAtGoal("Aim", 4, 45)                                 // turns the robot; goal not seen: looks 45 degrees each way
             .readyToLaunch("Ready", 3)                               // spins up, waits until at speed and aimed
             .action(robot.launcher.fireAllAction())                  // fires every piece it holds, only when aimed
             .turnTo("Face the field", 0, 0.5)
         .end()
         .parkWithSeconds(3)                                          // the last 3 s are for leaving
         .move("Leave", Tunables.Auto.leaveInches, 0, Tunables.Auto.leavePower);
    }

    // BIOBUZZ's field is turned half a turn between the alliances: the same route works for both.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }
}
