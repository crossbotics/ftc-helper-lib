package com.crossbotics.ftchelper.examples.gobildastraferbot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * goBILDA's BIOBUZZ starter bot with mecanum wheels (on a Strafer chassis), file 1 of 5, with the
 * configuration names of goBILDA's own code, so it runs on that robot as it is: a mecanum drive, an
 * intake (a roller and two corner wheels) and a flywheel launcher fed by the "windmill".
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#gobilda-ftc-starterbot-with-mecanum-wheels
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Launcher launcher;

    @Override
    protected void define() {
        // The names in quotes = the devices' names in the robot configuration.
        // Order: front left, front right, back left, back right. The left side runs reversed, as in goBILDA's code.
        drive(Drive.mecanum("left_front_drive", "right_front_drive", "left_back_drive", "right_back_drive")
                .motor("gobilda-5203-312").wheelMm(104)
                .reversed("left_front_drive", "left_back_drive")
                // Check how YOUR Control Hub is mounted: the way its logo and USB ports face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // The roller (117 RPM motor) and the two corner wheels run together, as in goBILDA's code.
        // It carries 4 POLLEN and no NECTAR (too big for it): count yours by hand.
        intake = add(Intake.motor("intake").part("gobilda-5203-117")
                .withCrServos("left_intake_servo", "right_intake_servo")
                .reversed("right_intake_servo")
                .holds(4).holds(0, "NECTAR"));

        // Launcher: a 312 RPM motor without its gearbox (= 6000 RPM). Windmill: reversed, as in goBILDA's code.
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.crServo("windmillServo").part("gobilda-2000-torque").reversed()));
    }
}
