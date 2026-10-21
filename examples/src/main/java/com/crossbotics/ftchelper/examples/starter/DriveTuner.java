// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.starter;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.finders.DriveTunerOpMode;

/**
 * STARTER KIT finder: finds the autonomous drive numbers in RobotSettings.auto() (wheel size and
 * strafe correction), with a push test and measured drive tests. Tape on the floor; 5 ft of space.
 * It shows the lines to copy into RobotSettings. How to use it: docs/guides/finders.md.
 */
@TeleOp(name = "Drive Tuner", group = "Finders")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
public class DriveTuner extends DriveTunerOpMode {
    // The same settings TeleOp and autonomous use, so what you tune here is what they get.
    @Override
    protected RobotConfig robotConfig() {
        return new RobotSettings();
    }
}
