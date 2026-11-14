package com.crossbotics.ftchelper.examples.lessonbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.finders.MoveTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * The example robot's (lessonbot) Move Tuner: how exactly autonomous moves stop and turns end, and the
 * Tunables.Drive lines to copy. Run it after the Drive Tuner. "Tune and check" has it too.
 * more: https://crossbotics.com/ftc-helper/docs/modules/finders/#movetuneropmode
 */
@TeleOp(name = "Lesson Bot Move Tuner", group = "Finders")
@Disabled // hides it on the Driver Station: delete this line in your copy
public class MoveTuner extends MoveTunerOpMode {
    // The same drive autonomous uses, so it gets what you tune here.
    @Override
    protected RobotDefinition robot() {
        return new Robot();
    }
}
