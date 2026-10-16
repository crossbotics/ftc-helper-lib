// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.examples.starter.RobotSettings;
import io.github.jasoncross.ftchelper.finders.AimTunerOpMode;
import io.github.jasoncross.ftchelper.launcher.Flywheel;
import io.github.jasoncross.ftchelper.launcher.Launcher;
import io.github.jasoncross.ftchelper.launcher.MotorFeeder;
import io.github.jasoncross.ftchelper.vision.TargetSource;
import io.github.jasoncross.ftchelper.vision.WebcamTags;

/**
 * Example: the aim tuner, to find the camera offset (where the target sits in the camera's view
 * when the launcher points straight at it). This robot aims by turning (no turret); for a turret,
 * also fill in createTurret(). Use YOUR camera and launcher (best: from your Robot class).
 * Needs a webcam "Webcam 1", a motor "flywheel" and a motor "feeder".
 * How to use it: docs/guides/finders.md.
 */
@TeleOp(name = "Aim Tuner", group = "Finders")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
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
