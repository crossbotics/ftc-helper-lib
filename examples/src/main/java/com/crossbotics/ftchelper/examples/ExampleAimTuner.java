// The folder this file lives in. Change it to your TeamCode folder when you copy the file.
package com.crossbotics.ftchelper.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.examples.starter.RobotSettings;
import com.crossbotics.ftchelper.finders.AimTunerOpMode;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.launcher.Launcher;
import com.crossbotics.ftchelper.launcher.MotorFeeder;
import com.crossbotics.ftchelper.vision.TargetSource;
import com.crossbotics.ftchelper.vision.WebcamTags;

/**
 * Example: the aim tuner, to find the camera offset (where the target sits in the camera's view
 * when the launcher points straight at it). This robot aims by turning (no turret); for a turret,
 * also fill in createTurret(). Use YOUR camera and launcher (best: from your Robot class).
 * Needs a webcam "Webcam 1", a motor "flywheel" and a motor "feeder".
 * more: https://crossbotics.com/ftc-helper/docs/guides/finders/#aim-tuner-camera-offset
 */
@TeleOp(name = "Aim Tuner", group = "Finders")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class ExampleAimTuner extends AimTunerOpMode {

    @Override
    protected TargetSource createCamera() {
        return WebcamTags.create(hardwareMap, "Webcam 1");
    }

    // Aims by turning the robot, so it needs the drive settings.
    @Override
    protected RobotConfig robotConfig() {
        return new RobotSettings();
    }

    // For test shots. Without a shot table it shoots at shotRpm() (3000 unless you change it).
    @Override
    protected Launcher createLauncher() {
        Flywheel flywheel = Flywheel.create(hardwareMap, "flywheel").countsPerRev(28).pidf(10, 3, 0, 12);
        MotorFeeder feeder = MotorFeeder.create(hardwareMap, "feeder").power(1.0).feedTime(0.25);
        return new Launcher(flywheel, feeder).shotInterval(0.3);
    }
}
