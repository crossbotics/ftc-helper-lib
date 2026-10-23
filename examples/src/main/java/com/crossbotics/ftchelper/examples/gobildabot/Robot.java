package com.crossbotics.ftchelper.examples.gobildabot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * goBILDA's BIOBUZZ starter bot, file 1 of 5, with the same configuration names as goBILDA's own code
 * (BioBuzzStarterbotTeleop), so it runs on that robot as it is.
 *
 * - A tank drive: one motor per side; the right one runs reversed (goBILDA's code sets it so).
 * - The intake: a roller on a 117 RPM motor (50.9:1), and two corner wheels on CR servos (goBILDA
 *   Speed servos) that run with it.
 * - The launcher: a flywheel on a 312 RPM motor with its gearbox taken off (1:1, 28 counts per
 *   turn, so the 6000 RPM motor's numbers), fed by the "windmill", a goBILDA Torque servo in CR mode.
 *
 * Each part says which motor or servo it is (.part(...) or the id in the brackets), from goBILDA's
 * build guide: the starting values and the simulator then use that part's real numbers.
 *
 * Tutorial: docs/tutorials/start/01-robot-in-five-files.md walks through all five files.
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Launcher launcher;

    @Override
    protected void define() {
        // Each name in quotes ("left_drive", "intake", "launcher", "windmill"...) = the device's name in the
        // robot configuration on the Driver Station. A part's numbers: Tunables, in its name's section.
        drive(Drive.tank("left_drive", "right_drive")
                .motor("gobilda-5203-312").wheelMm(96)
                .reversed("right_drive")
                // Check how YOUR Control Hub is mounted: the way its logo and USB port face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // The roller and the two corner wheels run together, as in goBILDA's code. The roller's motor:
        // a 50.9:1 Yellow Jacket (5203-2402-0051), the 117 RPM motor.
        intake = add(Intake.motor("intake").part("gobilda-5203-117")
                .withCrServos("left_intake_servo", "right_intake_servo")
                .reversed("right_intake_servo"));

        // The windmill turns the other way in goBILDA's code (Direction.REVERSE): so here too. Its servo:
        // a 2000 Series Torque (2000-0025-0002) in CR mode.
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.crServo("windmill").part("gobilda-2000-torque").reversed()));
    }
}
