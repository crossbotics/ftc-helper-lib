package com.crossbotics.ftchelper.examples.revbot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Positioner;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * REV's DUO FTC Starter Bot for BIOBUZZ, file 1 of 5: what is on the robot. The configuration names
 * are the ones in REV's own code (its TeleOp page), so it runs on that robot as it is. REV does not
 * say which way each motor turns: check them on your robot (lesson 2).
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#rev-bot-code
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Launcher launcher;
    public Positioner poker;

    @Override
    protected void define() {
        // Each name in quotes = the device's name in the robot configuration. Numbers: Tunables.
        // Left side reversed (the default) is a guess: if the robot spins when you push forward, change it.
        drive(Drive.tank("leftDrive", "rightDrive")
                .motor("rev-hd-hex-up20").wheelMm(90)
                // Check how YOUR Control Hub is mounted: the way its logo and USB port face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // If it pushes out when it should take in, add .reversed() (REV does not say which way is "in").
        intake = add(Intake.motor("intakeMotor").part("rev-core-hex"));

        // HD Hex at 1:1 (no gearbox); the feeder roller is a Smart Robot Servo in CR mode.
        launcher = add(Launcher.flywheel("flywheel", "rev-hd-hex")
                .feeder(Indexer.crServo("crServo").part("rev-smart-servo-v2-balanced")));

        // The poker, two named spots (Tunables.PollenServo). Nothing moves at INIT: fold it by hand
        // before INIT, so the robot fits the 18 in start cube.
        poker = add(Positioner.servo("pollenServo").part("rev-smart-servo-v2-balanced"));
    }
}
