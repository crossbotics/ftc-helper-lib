package com.crossbotics.ftchelper.examples.andymarkbot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * AndyMark's Robits BIOBUZZ StarterBot, file 1 of 5, with the same configuration names as AndyMark's
 * own code (RobitsStarterBotTeleop), so it runs on that robot as it is.
 *
 * - A tank (skid-steer) drive: one NeveRest 19.2:1 per side, 3 in wheels, belts; the right motor
 *   runs reversed (AndyMark's code sets it so).
 * - The intake: rubber-band rollers on a CR servo ("rollerI"), and the flower roller ("rollerF",
 *   a CR servo) that runs with it.
 * - The catapult: a NeveRest 50.9:1 winds rubber bands and lets go once per turn.
 *
 * What the library cannot do here: it has no catapult part. The closest real part is an
 * {@link Indexer} on a motor: it runs the motor at a set power for a set time per press. Tune that time
 * so one press is one turn of the catapult, which is one throw. The library does not know when the
 * catapult is wound or when it lets go (there is no sensor on it), so it cannot wait for "ready" the
 * way a flywheel launcher does. AndyMark's own code runs the motor for as long as the trigger is held.
 *
 * Where the facts come from: docs/guides/starter-bots.md and the simulator's robot file
 * sim/robots/starter-bot-andymark.properties.
 *
 * Tutorial: docs/tutorials/start/01-robot-in-five-files.md walks through all five files.
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Indexer catapult;

    @Override
    protected void define() {
        // The kit's NeveRest Hex 19.2:1 is the same motor and ratio as the NeveRest Orbital 20.
        drive(Drive.tank("left_drive", "right_drive")
                .motor("am-neverest-orbital-20").wheelMm(76.2)
                .reversed("right_drive")
                // Check how YOUR Control Hub is mounted: the way its logo and USB port face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // AndyMark's code: rollerI is REVERSE, and rollerF runs with the intake.
        intake = add(Intake.crServos("rollerI", "rollerF")
                .reversed("rollerI")
                .holds(4));

        // The catapult motor (REVERSE in AndyMark's code). Tunables.Catapult: power and feedSec.
        catapult = add(Indexer.motor("catapult").reversed());

        // The flower mechanism has a second version with a "stick" servo instead of the roller. For
        // that one, take "rollerF" out of the intake line and add:
        //     stick = add(Positioner.servo("stick"));   // Tunables.Stick: in = 0.46, out = 0.65
    }
}
