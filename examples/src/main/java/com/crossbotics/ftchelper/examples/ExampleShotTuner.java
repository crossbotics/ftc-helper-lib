// The folder this file lives in. Change it to your TeamCode folder when you copy the file.
package com.crossbotics.ftchelper.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.examples.starter.RobotSettings;
import com.crossbotics.ftchelper.finders.ShotTunerOpMode;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.launcher.Launcher;
import com.crossbotics.ftchelper.launcher.MotorFeeder;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import com.crossbotics.ftchelper.vision.TargetSource;
import com.crossbotics.ftchelper.vision.WebcamTags;

/**
 * Example: the shot tuner, to build a launcher's shot table (distance -> speed, hood angle).
 * Use YOUR launcher here: the same set-up as your TeleOp (best: from your Robot class, e.g.
 * {@code return new Robot(hardwareMap).launcher;}). Needs a motor "flywheel", a motor "feeder",
 * a servo "hood" and a webcam "Webcam 1".
 * more: https://crossbotics.com/ftc-helper/docs/guides/finders/#shot-tuner-the-shot-table
 */
@TeleOp(name = "Shot Tuner", group = "Finders")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class ExampleShotTuner extends ShotTunerOpMode {

    // The launcher, as in tutorial 17. Use the flywheel numbers from the Flywheel Finder.
    @Override
    protected Launcher createLauncher() {
        Flywheel flywheel = Flywheel.create(hardwareMap, "flywheel").countsPerRev(28).pidf(10, 3, 0, 12);
        PositionServo hood = PositionServo.create(hardwareMap, "hood").degrees(0.10, 0, 0.90, 25).travelTime(0.4);
        MotorFeeder feeder = MotorFeeder.create(hardwareMap, "feeder").power(1.0).feedTime(0.25);
        return new Launcher(flywheel, feeder).hood(hood).shotInterval(0.3);
    }

    // The camera gives the distance. Return null instead to set the distance by hand.
    @Override
    protected TargetSource createCamera() {
        return WebcamTags.create(hardwareMap, "Webcam 1");
    }

    // Drive between distances with the sticks.
    @Override
    protected RobotConfig robotConfig() {
        return new RobotSettings();
    }
}
