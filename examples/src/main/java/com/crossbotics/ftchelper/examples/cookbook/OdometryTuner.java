package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.finders.OdometryTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/*
 * Cookbook (docs/cookbook.md): a whole file, as it looks in your TeamCode. The lines between
 * "// recipe: name" and "// end recipe" are shown on the page; tools/check_docs.py keeps them the same.
 */
// recipe: odometry-tuner
// The Odometry Tuner: which way each pod counts, the offsets, and a loop check.
@TeleOp(name = "Cookbook Odometry Tuner", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class OdometryTuner extends OdometryTunerOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();     // your Robot.java: the tuner uses its parts and numbers
    }
}
// end recipe
