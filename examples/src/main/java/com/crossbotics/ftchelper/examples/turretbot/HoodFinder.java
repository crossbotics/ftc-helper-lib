package com.crossbotics.ftchelper.examples.turretbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.finders.HoodFinderOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * The turret robot's Hood Finder (lesson 9): finds Tunables.Hood's posA and posB, the servo positions
 * a hair off the hood's two stops, and (if you measure them with an angle app) angleA, angleB. It reads
 * the hood's name from Robot.java, so it works before Tunables.Hood has any numbers. Copy the lines it
 * shows into Tunables.java. How to use it: docs/guides/finders.md#hood-finder.
 */
@TeleOp(name = "TurretBot Hood Finder", group = "Finders")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
public class HoodFinder extends HoodFinderOpMode {
    // The robot from Robot.java: its hood (servo or motor, reversed or not).
    @Override
    protected RobotDefinition robot() {
        return new Robot();
    }
}
