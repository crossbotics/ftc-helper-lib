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
 * <ul>
 *   <li>A tank drive: one motor per side; the right one runs reversed (goBILDA's code sets it so).</li>
 *   <li>The intake: a roller on a motor, and two corner wheels on CR servos that run with it.</li>
 *   <li>The launcher: a flywheel on a 312 RPM motor with its gearbox taken off (1:1, 28 counts per
 *       turn, so the 6000 RPM motor's numbers), fed by the "windmill", a CR servo.</li>
 * </ul>
 *
 * <p>Tutorial: docs/tutorials/start/01-robot-in-five-files.md walks through all five files.
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Launcher launcher;

    @Override
    protected void define() {
        drive(Drive.tank("left_drive", "right_drive")
                .motor("gobilda-5203-312").wheelMm(96)
                .reversed("right_drive")
                // Check how YOUR Control Hub is mounted: the way its logo and USB port face.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // The roller and the two corner wheels run together, as in goBILDA's code.
        intake = add(Intake.motor("intake")
                .withCrServos("left_intake_servo", "right_intake_servo")
                .reversed("right_intake_servo"));

        // The windmill turns the other way in goBILDA's code (Direction.REVERSE): so here too.
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.crServo("windmill").reversed()));
    }
}
