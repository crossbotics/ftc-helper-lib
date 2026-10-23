package com.crossbotics.ftchelper.examples.turretbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.finders.HoodFinderOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * The turret robot's Hood Finder: finds Tunables.Hood's posA and posB (a hair off the hood's two
 * stops). Copy the lines it shows into Tunables.java.
 * more: https://crossbotics.com/ftc-helper/docs/guides/finders/#hood-finder
 */
@TeleOp(name = "TurretBot Hood Finder", group = "Finders")
@Disabled // hides it on the Driver Station: delete this line in your copy
public class HoodFinder extends HoodFinderOpMode {
    // The robot from Robot.java: its hood (servo or motor, reversed or not).
    @Override
    protected RobotDefinition robot() {
        return new Robot();
    }
}
