package com.crossbotics.ftchelper.examples.andymarkbot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Parts;
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
 * Each part says which motor or servo it is (.part(...)), from AndyMark's kit parts list: the
 * starting values and the simulator then use that part's real numbers. The kit's servo is not in the
 * library's parts catalog, so Parts.customServo(...) describes it with AndyMark's numbers.
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
        // Each name in quotes ("left_drive", "rollerI", "catapult"...) = the device's name in the robot
        // configuration on the Driver Station. A part's numbers: Tunables, in its name's section.
        // The kit's NeveRest Hex 19.2:1 is the same motor and ratio as the NeveRest Orbital 20.
        drive(Drive.tank("left_drive", "right_drive")
                .motor("am-neverest-orbital-20").wheelMm(76.2)
                .reversed("right_drive")
                // Check how YOUR Control Hub is mounted: the way its logo and USB port face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // AndyMark's code: rollerI is REVERSE, and rollerF runs with the intake. rollerI's servo: the kit's
        // 4-wire Programmable Servo, High Speed (am-4955a), in continuous mode. Not in the parts catalog:
        // AndyMark's numbers (300 degrees, 0.05 s per 60 degrees at 6 V, the only speed listed), from
        // https://andymark.com/am-4955 (read 2026-10-23).
        intake = add(Intake.crServos("rollerI", "rollerF")
                .part(Parts.customServo("am-4955a", 300, 0.05))
                .reversed("rollerI")
                .holds(4));

        // The catapult motor (REVERSE in AndyMark's code): the kit's NeveRest Hex 50.9:1 (am-5443), the same
        // motor and ratio as the NeveRest Orbital 50. Tunables.Catapult: power and feedSec.
        catapult = add(Indexer.motor("catapult").part("am-neverest-orbital-50").reversed());

        // The flower mechanism has a second version with a "stick" servo instead of the roller. For
        // that one, take "rollerF" out of the intake line and add:
        //     stick = add(Positioner.servo("stick").part("am-4955a"));   // Tunables.Stick: in = 0.46, out = 0.65
        // "am-4955a" (described on the intake line) if the stick has a High Speed servo too; AndyMark does
        // not say which of the kit's servos it is: use the one on yours.
    }
}
