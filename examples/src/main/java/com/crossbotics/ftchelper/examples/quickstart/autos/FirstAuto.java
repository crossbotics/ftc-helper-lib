package com.crossbotics.ftchelper.examples.quickstart.autos;

import com.crossbotics.ftchelper.examples.quickstart.Robot;
import com.crossbotics.ftchelper.examples.quickstart.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * Quick start, file 5 of 5: a first autonomous that drives forward and stops. It only drives the robot
 * after the Drive Tuner (Tunables.Drive.calibrated = true, lesson 6); the simulator runs it anyway.
 */
// STEP 8: the name on the Driver Station, and the TeleOp it chooses next. Delete @Disabled in TeamCode.
@Autonomous(name = "Quick Start Auto", group = "Examples", preselectTeleOp = "Quick Start TeleOp")
@Disabled
public class FirstAuto extends Auto<Robot> {

    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ's field is turned half a turn: the same route for both alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        // STEP 8: where the drive team puts the robot, and how far it drives.
        s.start("Against our wall, facing the field", 0)
         .move("Leave the start", Tunables.Auto.leaveInches, 0, 0.4);
    }
}
