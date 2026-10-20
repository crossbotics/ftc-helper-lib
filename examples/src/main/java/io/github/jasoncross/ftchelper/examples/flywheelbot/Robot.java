package io.github.jasoncross.ftchelper.examples.flywheelbot;

import io.github.jasoncross.ftchelper.robot.Drive;
import io.github.jasoncross.ftchelper.robot.Indexer;
import io.github.jasoncross.ftchelper.robot.Intake;
import io.github.jasoncross.ftchelper.robot.Launcher;
import io.github.jasoncross.ftchelper.robot.Positioner;
import io.github.jasoncross.ftchelper.robot.RobotDefinition;
import io.github.jasoncross.ftchelper.robot.Shape;

/**
 * Example robot 1, file 1 of 5: WHAT is on the robot, and each part's name in the robot
 * configuration (the names you typed on the Driver Station).
 *
 * <p>The robot: a standard (tank) drive; an intake that runs from the start of the match (the driver
 * can stop it, restart it, and push pieces back out); one flywheel launcher whose speed the driver can
 * change; an indexer servo that pushes one piece at a time into the flywheel; and a servo that pushes
 * the intake down into its working position at the start of the match.
 *
 * <p>The other four files: {@link Tunables} (the numbers), {@link Controls} (the buttons and the
 * TeleOp), {@link Actions} (steps that use several parts) and {@code autos/} (the autonomous programs).
 *
 * <p>Guide: docs/modules/robot.md. Tutorial: docs/tutorials/22-robot-in-five-files.md
 */
public class Robot extends RobotDefinition {
    // One field per mechanism, so Controls, Actions and the autos can reach it by name: robot.intake.
    // "public": the other files need to see them.
    public Intake intake;
    public Launcher launcher;
    public Positioner intakeDeploy;

    @Override
    protected void define() {
        // The drive: two motors, one per side, goBILDA 312 RPM motors and 96 mm Hogback wheels (the
        // wheel's size comes from the parts catalog). The right motor is mounted facing the other way,
        // so it runs reversed.
        drive(Drive.tank("left_drive", "right_drive")
                .motor("gobilda-5203-312").wheel("gobilda-hogback-96")
                .wheelsAtMm(380, 290)
                .reversed("right_drive"));

        // For the simulator: the chassis box and the whole robot's height, in mm (measure yours).
        chassisMm(400, 400, 100);
        heightMm(300);

        // A servo with named positions (Tunables.IntakeDeploy: stowed, deployed). Its short arm (the
        // "kicker") pushes the folded intake over at START, then goes back. It comes first, so the
        // intake can name it.
        intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque")
                .looks(Shape.arm().name("kicker").atMm(150, 90, 100).sizeMm(60, 20, 15)
                        .angle("stowed", 90).angle("deployed", 20)));

        // The intake: its arm is folded straight up (90 degrees) at the start. Pushed over, it falls by
        // gravity to its hard stop (-15 degrees) and stays: passive, nothing in the code holds it. The
        // roller rides on the end of the arm. onAtStart(): it starts pulling pieces in at START,
        // without a button. holds(3): it carries up to three pieces.
        intake = add(Intake.motor("intake").part("gobilda-5203-1150").onAtStart().holds(3)
                .looks(Shape.arm().name("intake_arm").atMm(150, 0, 100).sizeMm(110, 200, 20)
                                .range(-15, 90).startAt(90).dropsWhen(intakeDeploy, "deployed", -15),
                        Shape.roller("gobilda-gecko-48-rex").on("intake_arm").atMm(110, 0, 0).lengthMm(300)));

        // The launcher: a flywheel on a 6000 RPM motor, fed one piece at a time by the indexer servo.
        // The indexer belongs to the launcher, so it is not added on its own. Shots leave forward and
        // up at 50 degrees.
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
                .looks(Shape.flywheel("gobilda-hogback-96").atMm(-40, 0, 170))
                .shotAngle(50));

        // Right after START (never in INIT: nothing may move before START), push the intake down.
        // "() -> ..." is a small piece of code that runs later, at START.
        atStart(() -> Actions.deployIntake(this));
    }
}
