package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/*
 * Cookbook (docs/cookbook.md): a whole file, as it looks in your TeamCode. The lines between
 * "// recipe: name" and "// end recipe" are shown on the page; tools/check_docs.py keeps them the same.
 */
// recipe: auto-first
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
