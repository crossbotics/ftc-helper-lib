package com.crossbotics.ftchelper.examples.andymarkbot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Parts;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * AndyMark's Robits BIOBUZZ StarterBot, file 1 of 5: what is on the robot. The configuration names
 * are the ones in AndyMark's own code (RobitsStarterBotTeleop), so it runs on that robot as it is.
 * The library has no catapult part: the catapult is an Indexer on a motor (one timed turn per press).
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#andymark-bot-code
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Indexer catapult;

    @Override
    protected void define() {
        // Each name in quotes = the device's name in the robot configuration. Numbers: Tunables.
        // NeveRest Hex 19.2:1 = the same motor and ratio as the NeveRest Orbital 20.
        drive(Drive.tank("left_drive", "right_drive")
                .motor("am-neverest-orbital-20").wheelMm(76.2)
                .reversed("right_drive")
                // Check how YOUR Control Hub is mounted: the way its logo and USB port face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // rollerF runs with the intake. Servo: High Speed Programmable Servo (am-4955a), AndyMark's numbers.
        intake = add(Intake.crServos("rollerI", "rollerF")
                .part(Parts.customServo("am-4955a", 300, 0.05))
                .reversed("rollerI")
                .holds(4));

        // NeveRest Hex 50.9:1 (am-5443) = the NeveRest Orbital 50. Tunables.Catapult: power, feedSec.
        catapult = add(Indexer.motor("catapult").part("am-neverest-orbital-50").reversed());

        // Flower "stick" version: take "rollerF" out of the intake line and add (use your stick's servo):
        //     stick = add(Positioner.servo("stick").part("am-4955a"));   // Tunables.Stick: in = 0.46, out = 0.65
    }
}
