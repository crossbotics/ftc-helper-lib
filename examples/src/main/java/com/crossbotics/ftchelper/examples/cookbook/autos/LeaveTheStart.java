// recipe: auto-first
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first import below to:  import org.firstinspires.ftc.teamcode.Robot;

import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

// The name on the Driver Station, and the TeleOp it picks next (use YOUR TeleOp's name).
@Autonomous(name = "Cookbook Leave the Start", group = "Cookbook", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy, or the Driver Station will not list it
public class LeaveTheStart extends Auto<Robot> {
    @Override
    protected void steps(Steps s, Robot robot) {
        // s = the list of steps, robot = your Robot.java. Step lines go here:
        s.start("Against our wall, facing the field", 0)        // the note the drive team reads
         .move("Leave the start", 24, 0, 0.4);                  // 24 inches = 2 feet, forward, 0.4 power
    }
}
// end recipe
