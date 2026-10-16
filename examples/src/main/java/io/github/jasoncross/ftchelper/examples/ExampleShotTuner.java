// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.examples.starter.RobotSettings;
import io.github.jasoncross.ftchelper.finders.ShotTunerOpMode;
import io.github.jasoncross.ftchelper.launcher.Flywheel;
import io.github.jasoncross.ftchelper.launcher.Launcher;
import io.github.jasoncross.ftchelper.launcher.MotorFeeder;
import io.github.jasoncross.ftchelper.mechanism.PositionServo;
import io.github.jasoncross.ftchelper.vision.TargetSource;
import io.github.jasoncross.ftchelper.vision.WebcamTags;

/**
 * Example: the shot tuner, to build a launcher's shot table (distance -> speed, hood angle).
 * Use YOUR launcher here: the same set-up as your TeleOp (best: from your Robot class, e.g.
 * {@code return new Robot(hardwareMap).launcher;}). Needs a motor "flywheel", a motor "feeder",
 * a servo "hood" and a webcam "Webcam 1".
 * How to use it: docs/guides/finders.md.
 */
@TeleOp(name = "Shot Tuner", group = "Finders")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
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
