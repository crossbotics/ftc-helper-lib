package com.crossbotics.ftchelper.examples.turretbot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Positioner;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Turret;

/**
 * Example robot 2, file 1 of 5: a mecanum robot with a turret that follows the goal.
 *
 * <ul>
 *   <li>The same intake as example robot 1 (on from the start).</li>
 *   <li>A turret with a Limelight on it that follows the goal, but never turns past its range
 *       (Tunables.Turret: -90 to +90 degrees, 180 in all).</li>
 *   <li>A flywheel whose speed comes from the distance to the goal (Tunables.Launcher.speedByDistance),
 *       fed by an indexer servo. It only fires when the turret is aimed.</li>
 *   <li>A claw on a fold-out pivot: positions to grab a piece and to let it go into a FLOWER.</li>
 * </ul>
 *
 * <p>Modes do the always-on work (the turret following, the speed from distance); actions do the
 * claw's steps (Actions.java).
 */
public class Robot extends RobotDefinition {
    public Camera camera;
    public Intake intake;
    public Turret turret;
    public Launcher launcher;
    public Positioner clawFold;
    public Claw claw;

    @Override
    protected void define() {
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheelMm(104)
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // The camera knows this season's goal for each alliance: the tag group's name.
        // autoPipelines: venue lighting. In INIT it tries pipelines 0 (normal light), 1 (bright) and
        // 2 (dim), each set up on the Limelight's web page, and keeps the one that sees the most tags.
        camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));

        intake = add(Intake.motor("intake").onAtStart());

        // 5 motor turns per turret turn. follows(camera): follow mode is on from START.
        turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));

        // Speed from the camera's distance; and a piece is only fed when the turret points at the goal.
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.servo("indexer"))
                .speedFromDistance(camera)
                .requireReady("turret aimed", () -> turret.isAimed()));

        clawFold = add(Positioner.servo("claw_fold"));   // Tunables.ClawFold: stowed, grab, flower
        claw = add(Claw.servo("claw"));                  // Tunables.Claw: open, closed
    }
}
