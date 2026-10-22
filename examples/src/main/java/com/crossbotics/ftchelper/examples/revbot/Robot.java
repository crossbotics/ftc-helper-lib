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
 * REV's DUO FTC Starter Bot for BIOBUZZ, file 1 of 5, with the same configuration names as REV's own
 * code (its TeleOp page), so it runs on that robot as it is.
 *
 * <ul>
 *   <li>A tank drive: one 20:1 UltraPlanetary HD Hex motor per side, 90 mm wheels, chain 1:1.</li>
 *   <li>The intake: flap wheels on a Core Hex motor.</li>
 *   <li>The launcher: two grip wheels on an HD Hex motor at 1:1 (no gearbox), fed by a roller on a
 *       Smart Robot Servo in continuous (CR) mode.</li>
 *   <li>The pollen pusher ("poker"): an arm on a servo at the left side. It combs POLLEN out of a FLOWER.</li>
 * </ul>
 *
 * <p>Where the facts come from: docs/guides/starter-bots.md and the simulator's robot file
 * sim/robots/starter-bot-rev.properties. REV does not say which way each motor turns, so check them
 * on your robot (lesson 2, docs/tutorials/start/02-drive.md).
 *
 * <p>Tutorial: docs/tutorials/start/01-robot-in-five-files.md walks through all five files.
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Launcher launcher;
    public Positioner poker;

    @Override
    protected void define() {
        // REV's code does not reverse a drive motor, so the library's default (the left side
        // reversed) is a guess: if the robot spins when you push forward, change it (lesson 2).
        drive(Drive.tank("leftDrive", "rightDrive")
                .motor("rev-hd-hex-up20").wheelMm(90)
                // Check how YOUR Control Hub is mounted: the way its logo and USB port face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // REV's code: right bumper takes POLLEN in, right trigger pushes it out. Which sign is "in"
        // is not on REV's page: if it pushes out when it should take in, add .reversed().
        intake = add(Intake.motor("intakeMotor"));

        // The flywheel (HD Hex at 1:1: the 6000 RPM motor's numbers) and its feeder roller.
        launcher = add(Launcher.flywheel("flywheel", "rev-hd-hex")
                .feeder(Indexer.crServo("crServo")));

        // The poker: a servo with two named spots (Tunables.PollenServo). Nothing moves at INIT, so
        // this line does NOT fold it: fold the poker by hand before INIT, so the robot fits the 18 in
        // start cube. It first moves when a button (A or B) or an action sends it somewhere.
        poker = add(Positioner.servo("pollenServo"));
    }
}
