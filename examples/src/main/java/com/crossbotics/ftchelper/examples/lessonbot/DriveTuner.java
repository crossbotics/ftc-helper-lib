package com.crossbotics.ftchelper.examples.lessonbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.finders.DriveTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * The lesson robot's Drive Tuner (lesson 6, docs/tutorials/start/06-first-autonomous.md). It
 * measures the wheel size and the strafe correction, and shows the lines to copy:
 * {@code Robot.java: .wheelMm(100.0)} and {@code Tunables.Drive: strafeCorrection = 1.120}.
 * How to use it: docs/guides/finders.md.
 */
@TeleOp(name = "Lesson Bot Drive Tuner", group = "Finders")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
public class DriveTuner extends DriveTunerOpMode {
    // The robot from Robot.java: the tuner uses its drive (motors, wheel, IMU, Tunables.Drive), the
    // same one TeleOp and autonomous use, so what you tune here is what they get.
    @Override
    protected RobotDefinition robot() {
        return new Robot();
    }
}
