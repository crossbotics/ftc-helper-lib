package com.crossbotics.ftchelper.examples.studicabot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Motors;
import com.crossbotics.ftchelper.robot.Parts;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * Studica's FTC Starter Bot for BIOBUZZ, file 1 of 5: what is on the robot. The configuration names
 * are the ones in Studica's own Blocks code, so it runs on that robot as it is. The launcher's speed
 * numbers are the MOTOR's: its wheels turn at half of it (13T to 26T bevel gears).
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#studica-bot-code
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Launcher launcher;

    @Override
    protected void define() {
        // Each name in quotes = the device's name in the robot configuration. Numbers: Tunables.
        // Maverick 50.9:1 (75001-509): 1221.6 counts per turn, 119.84 RPM (Studica's table).
        drive(Drive.tank("leftDrive", "rightDrive")
                .motor(Motors.custom("studica-maverick-50.9", 1221.6, 119.84)).wheelMm(100)
                // Check how YOUR Control Hub is mounted: the way its logo and USB port face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // Maverick 3.7:1 (75001-37): 88.8 counts per turn, 1648.65 RPM.
        intake = add(Intake.motor("Intake").part(Motors.custom("studica-maverick-3.7", 88.8, 1648.65)));

        // Maverick 1:1: 24 counts per turn, 6100 RPM; the wheels turn at half its speed.
        // The flap servo: Multi-Mode Smart Servo, 300 degrees, 0.25 s per 60 degrees (Studica's page).
        launcher = add(Launcher.flywheel("FlyWheel", Motors.custom("studica-maverick-1", 24, 6100))
                .feeder(Indexer.servo("fwActuator")
                        .part(Parts.customServo("studica-multi-mode-smart-servo", 300, 0.25))));
    }
}
