package com.crossbotics.ftchelper.examples.studicabot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * Studica's FTC Starter Bot for BIOBUZZ, file 1 of 5, with the same configuration names as Studica's
 * own Blocks code, so it runs on that robot as it is.
 *
 * <ul>
 *   <li>A tank drive: two driven 100 mm wheels at the front (Maverick 50.9:1 motors, bevel gears 1:1)
 *       and two free omni wheels at the back; the left motor runs reversed (Studica's code sets it so).</li>
 *   <li>The intake: four wheels on a Maverick 3.7:1 motor.</li>
 *   <li>The launcher: three 100 mm wheels on a Maverick 1:1 motor, through 13T to 26T bevel gears (the
 *       wheels turn at half the motor's speed), fed by a polycarbonate flap on a servo.</li>
 * </ul>
 *
 * <p><b>What the library cannot do here:</b>
 * <ul>
 *   <li>The Maverick motors are not in the library's motor list ({@code Motors}). The closest goBILDA
 *       motors stand in for them (see the notes below). Studica's numbers: 50.9:1 = 119.84 RPM and
 *       1221.6 encoder counts per turn; 1:1 = 6100 RPM and 24 counts per turn.</li>
 *   <li>The launcher cannot take a gear between the motor and the wheel. Its speed numbers are the
 *       motor's, as the library counts them, not the wheel's. Tune them by watching shots.</li>
 * </ul>
 *
 * <p>Where the facts come from: docs/guides/starter-bots.md and the simulator's robot file
 * sim/robots/starter-bot-studica.properties.
 *
 * <p>Tutorial: docs/tutorials/start/01-robot-in-five-files.md walks through all five files.
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Launcher launcher;

    @Override
    protected void define() {
        // Stand-in: goBILDA's 117 RPM motor (1425.1 counts per turn) is close to the Maverick 50.9:1
        // in speed. Its counts are not: gearRatio(1221.6 / 1425.1) makes the counts per wheel turn the
        // Maverick's, so autonomous distances come out right. There are no real gears here.
        // The library reverses the left side by default, as Studica's code does.
        drive(Drive.tank("leftDrive", "rightDrive")
                .motor("gobilda-5203-117").gearRatio(1221.6 / 1425.1).wheelMm(100)
                // Check how YOUR Control Hub is mounted: the way its logo and USB port face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // Studica's code: right bumper = power 1 (in), left bumper = -1 (out).
        intake = add(Intake.motor("Intake"));

        // Stand-in: goBILDA's 6000 RPM motor (28 counts per turn) for the Maverick 1:1 (24 counts).
        // So the speed the library shows is 24/28 of the motor's real speed, and the wheels turn at
        // half the motor's speed. The flap ("fwActuator") feeds: X = 0 and A = 0.2 in Studica's code.
        launcher = add(Launcher.flywheel("FlyWheel", "gobilda-5203-6000")
                .feeder(Indexer.servo("fwActuator")));
    }
}
