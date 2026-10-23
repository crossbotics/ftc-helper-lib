package com.crossbotics.ftchelper.examples.turretbot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Hood;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Lights;
import com.crossbotics.ftchelper.robot.Positioner;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Turret;

/**
 * Example robot 2, file 1 of 5: a mecanum robot with a turret and a Limelight that follow the goal,
 * a flywheel with a hood (speed and angle from the distance, two shot tables), a claw on a fold-out
 * pivot, and a light for the drivers.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/09-shooter-and-camera/
 */
public class Robot extends RobotDefinition {
    public Camera camera;
    public Intake intake;
    public Turret turret;
    public Launcher launcher;
    public Positioner clawFold;
    public Claw claw;
    public Lights lights;

    @Override
    protected void define() {
        // The names in quotes = the devices' names in the robot configuration.
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheelMm(104)
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // The goal's tag group for each alliance. autoPipelines: in INIT it keeps the pipeline that sees best.
        camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));

        // .part(...) = which motor or servo it is (the id on its label; these are examples).
        intake = add(Intake.motor("intake").part("gobilda-5203-1150").onAtStart());

        // 5 motor turns per turret turn. follows(camera): follow mode is on from START.
        turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));

        // Speed and hood angle from the distance, starting on the POLLEN table. Fires only when aimed.
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
                .hood(Hood.servo("hood").part("gobilda-2000-torque").shotAngles(80, 55))
                .speedFromDistance(camera)
                .startTable("pollen")
                .requireReady("turret aimed", () -> turret.isAimed()));

        // The claw's fold (Tunables.ClawFold: stowed, grab, flower) and the claw (Tunables.Claw: open, closed).
        clawFold = add(Positioner.servo("claw_fold").part("gobilda-2000-torque"));
        claw = add(Claw.servo("claw").part("gobilda-2000-torque"));

        // A goBILDA RGB Indicator Light on a servo port ("Servo" in the configuration). Colors: Controls.java.
        lights = add(Lights.gobilda("light"));
    }
}
