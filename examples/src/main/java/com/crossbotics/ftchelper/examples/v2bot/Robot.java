package com.crossbotics.ftchelper.examples.v2bot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Hood;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Interlock;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.Lights;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.PieceSensor;
import com.crossbotics.ftchelper.robot.Pieces;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Shape;
import com.crossbotics.ftchelper.robot.Turret;
import com.crossbotics.ftchelper.robot.Wrist;

/**
 * The V2 robot, file 1 of 5: a turret shooter AND a lift with a wrist and a claw, with an interlock
 * rule so the turret and the claw never hit each other. Modelled on team 8938's 2026-27 designs;
 * every number not measured on a built robot says "example".
 * more: https://crossbotics.com/ftc-helper/docs/guides/interlocks/#the-v2-robot
 */
public class Robot extends RobotDefinition {
    public Camera camera;
    public Pieces pieces;
    public Intake intake;
    public Turret turret;
    public Launcher launcher;
    public Lift lift;
    public Wrist wrist;
    public Claw claw;
    public Lights lights;
    public Interlock turretClaw;

    @Override
    protected void define() {
        // The names in quotes = the devices' names in the robot configuration.
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheelMm(104)
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
        // The Pinpoint and its two pods. Example offsets: measure your own (the Odometry Tuner does it).
        odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));

        // A Limelight 3A on the turret. It knows this season's goal tags for each alliance.
        camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));

        // The piece count: ABOVE the intake line, so it is up to date when the intake asks "full?".
        pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
                .comingIn(PieceSensor.color("intake_color"))
                .max(4)                                     // BIOBUZZ allows 4 at a time (G407)
                .startsWith(4, "POLLEN"));
        // The intake roller's motor: a 1150 RPM motor (an example, like every part here: use yours).
        intake = add(Intake.motor("intake").part("gobilda-5203-1150").stopsWhen(() -> pieces.isFull()));

        // homeSwitch: a REV Magnetic Limit Switch (REV-31-1462), a "Digital Device"; homed at START.
        // follows(camera): it follows the goal from START.
        turret = add(Turret.motor("turret", "gobilda-5203-312").gearRatio(2.75)
                .homeSwitch("turret_home")
                .follows(camera));

        // The flywheel (shot tables in motor RPM), its indexer, and a hood on a Torque servo (20T:108T).
        // It fires only when the turret is aimed and there is a piece to fire.
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
                .hood(Hood.servo("hood").part("gobilda-2000-torque").shotAngles(80, 55))
                .speedFromDistance(camera)
                .startTable("pollen")
                .requireReady("turret aimed", () -> turret.isAimed())
                .requireReady("a piece to fire", () -> !pieces.isEmpty()));

        // A goBILDA 4-stage Viper-Slide (3210-0004-0004) on a 435 RPM motor (5203-2402-0014).
        // looks(...): the simulator draws it in the back left corner; the wrist and claw ride on it.
        lift = add(Lift.slide("lift").part("gobilda-5203-435")
                .looks(Shape.slide().atMm(-188, 188, 12).sizeMm(27, 27, 288).travelMm(421)
                        .note("example: from the lifter design, measure your own")));
        // A goBILDA Speed servo (2000-0025-0003). Degrees from straight down: 0 folded, 145 its hard stop.
        wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed")
                .looks(Shape.arm().on(lift).atMm(0, -188, 142).sizeMm(115, 30, 20)
                        .angle("folded", -90).angle("flower", -212.8)));
        // The claw: two fingers on one goBILDA Torque servo (2000-0025-0002), geared together.
        claw = add(Claw.servo("claw").part("gobilda-2000-torque")
                .looks(Shape.claw().on(wrist).atMm(115, 0, 0).sizeMm(60, 120, 50)));

        // THE INTERLOCK: the turret and the claw (on the wrist) are never both in their zones.
        // The zones: Tunables.TurretClaw. isHolding(): true while it holds a part back.
        turretClaw = keepApart("TurretClaw", turret.zone(), wrist.zone());

        // A goBILDA RGB Indicator Light (a "Servo" in the configuration). Its colors: Controls.java.
        lights = add(Lights.gobilda("light"));
    }
}
