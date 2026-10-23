package com.crossbotics.ftchelper.examples.lessonbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.finders.DriveTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * The lesson robot's Drive Tuner: measures the wheel size and the strafe correction, and shows
 * the lines to copy into Robot.java and Tunables.Drive.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/06-first-autonomous/
 */
@TeleOp(name = "Lesson Bot Drive Tuner", group = "Finders")
@Disabled // hides it on the Driver Station: delete this line in your copy
public class DriveTuner extends DriveTunerOpMode {
    // The same drive TeleOp and autonomous use, so they get what you tune here.
    @Override
    protected RobotDefinition robot() {
        return new Robot();
    }
}
