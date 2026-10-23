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
 * Studica's FTC Starter Bot for BIOBUZZ, file 1 of 5, with the same configuration names as Studica's
 * own Blocks code, so it runs on that robot as it is.
 *
 * - A tank drive: two driven 100 mm wheels at the front (Maverick 50.9:1 motors, bevel gears 1:1)
 *   and two free omni wheels at the back; the left motor runs reversed (Studica's code sets it so).
 * - The intake: four wheels on a Maverick 3.7:1 motor.
 * - The launcher: three 100 mm wheels on a Maverick 1:1 motor, through 13T to 26T bevel gears (the
 *   wheels turn at half the motor's speed), fed by a polycarbonate flap on a servo.
 *
 * Each part says which motor or servo it is. The Maverick motors and Studica's Multi-Mode Smart Servo
 * are not in the library's parts catalog, so Motors.custom(...) and Parts.customServo(...) describe them
 * with Studica's own numbers: the starting values and the simulator then use the real parts.
 *
 * What the library cannot do here: the launcher cannot take a gear between the motor and the wheel.
 * Its speed numbers are the motor's, not the wheel's (the wheels turn at half of it). Tune them by
 * watching shots.
 *
 * Where the facts come from: docs/guides/starter-bots.md and the simulator's robot file
 * sim/robots/starter-bot-studica.properties.
 *
 * Tutorial: docs/tutorials/start/01-robot-in-five-files.md walks through all five files.
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Launcher launcher;

    @Override
    protected void define() {
        // Each name in quotes ("leftDrive", "Intake", "FlyWheel", "fwActuator") = the device's name in the
        // robot configuration on the Driver Station. A part's numbers: Tunables, in its name's section.
        // The Maverick motors, from Studica's table (speed at 12 V, encoder counts per output turn):
        // https://www.studica.com/maverick-planetary-dc-gear-motor-wencoder-5091-ratio-gearbox (2026-10-23).
        // The drive: Maverick 50.9:1 (75001-509), 1221.6 counts per turn, 119.84 RPM.
        // The library reverses the left side by default, as Studica's code does.
        drive(Drive.tank("leftDrive", "rightDrive")
                .motor(Motors.custom("studica-maverick-50.9", 1221.6, 119.84)).wheelMm(100)
                // Check how YOUR Control Hub is mounted: the way its logo and USB port face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // Studica's code: right bumper = power 1 (in), left bumper = -1 (out).
        // The intake motor: Maverick 3.7:1 (75001-37), 88.8 counts per turn, 1648.65 RPM.
        intake = add(Intake.motor("Intake").part(Motors.custom("studica-maverick-3.7", 88.8, 1648.65)));

        // The launcher motor: Maverick 1:1, 24 counts per turn, 6100 RPM. The wheels turn at half its
        // speed (13T to 26T bevel gears). The flap ("fwActuator") feeds: X = 0 and A = 0.2 in Studica's
        // code. Its servo: the Multi-Mode Smart Servo, 300 degrees, 0.25 s per 60 degrees at 4.8 V, from
        // https://docs.wsr.studica.com/en/latest/docs/Motors/servo-motors.html (2026-10-23).
        launcher = add(Launcher.flywheel("FlyWheel", Motors.custom("studica-maverick-1", 24, 6100))
                .feeder(Indexer.servo("fwActuator")
                        .part(Parts.customServo("studica-multi-mode-smart-servo", 300, 0.25))));
    }
}
