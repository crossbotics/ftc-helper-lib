package com.crossbotics.ftchelper.examples.flywheelbot;

import com.crossbotics.ftchelper.robot.Aim;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Positioner;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Shape;

/**
 * Example robot 1, file 1 of 5: what is on the robot. A tank drive, an intake that runs from START and
 * a servo that deploys it, a flywheel launcher with a servo feeder, and a webcam: the whole robot
 * turns to face the goal (no turret). Each part has its shapes for the simulator.
 * more: https://crossbotics.com/ftc-helper/docs/modules/robot/#shapes-and-parts
 */
public class Robot extends RobotDefinition {
    // One field per mechanism, so the other files can reach it: robot.intake.
    public Intake intake;
    public Launcher launcher;
    public Positioner intakeDeploy;
    public Camera camera;

    @Override
    protected void define() {
        // The names in quotes = the devices' names in the robot configuration.
        // The drive: one 312 RPM motor per side; the right one is mounted the other way round.
        drive(Drive.tank("left_drive", "right_drive")
                .motor("gobilda-5203-312").wheel("gobilda-hogback-96")
                .wheelsAtMm(380, 290)
                .reversed("right_drive"));

        // For the simulator: the chassis box and the whole robot's height, in mm (measure yours).
        chassisMm(400, 400, 100);
        heightMm(300);

        // The "kicker" servo pushes the folded intake over at START. First, so the intake can name it.
        intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque")
                .looks(Shape.arm().name("kicker").atMm(150, 90, 100).sizeMm(60, 20, 15)
                        .angle("stowed", 90).angle("deployed", 20)));

        // The intake arm: folded up (90 degrees) at the start; pushed over, it falls to its hard stop (-15).
        Shape intakeArm = Shape.arm().name("intake_arm").atMm(150, 0, 100).sizeMm(110, 200, 20)
                .range(-15, 90).startAt(90).dropsWhen(intakeDeploy, "deployed", -15);
        // The roller rides on the end of the arm.
        Shape intakeRoller = Shape.roller("gobilda-gecko-48-rex").on("intake_arm").atMm(110, 0, 0).lengthMm(300);
        // onAtStart(): runs from START, without a button. holds(3): up to three pieces.
        intake = add(Intake.motor("intake").part("gobilda-5203-1150").onAtStart().holds(3)
                .looks(intakeArm, intakeRoller));

        // A flywheel fed by the indexer servo (part of the launcher, not added on its own). Shots: 75 degrees up
        // (a steep shot drops into the CELL from above).
        // Fires only when LOCKED: the camera sees the goal and the robot's front points at it. Never blind.
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
                .looks(Shape.flywheel("gobilda-hogback-96").atMm(-40, 0, 170))
                .shotAngle(75)
                .requireReady("aimed", () -> aim().stage() == Aim.Stage.LOCKED));

        // The goal's AprilTag group for each alliance. robot.aimMode() turns the robot to face it.
        camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));

        // Right after START (never in INIT), push the intake down.
        atStart(() -> Actions.deployIntake(this));
    }
}
