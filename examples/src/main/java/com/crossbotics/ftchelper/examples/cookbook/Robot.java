package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/*
 * Cookbook (docs/cookbook.md): a whole file, as it looks in your TeamCode. The lines between
 * "// recipe: name" and "// end recipe" are shown on the page; tools/check_docs.py keeps them the same.
 */
// recipe: file-robot
public class Robot extends RobotDefinition {
    public Intake intake;          // one field per part: "public", the kind of part, a name
    public Lift lift;
    public Claw claw;
    public Camera camera;
    public Launcher launcher;

    @Override
    protected void define() {      // the drive line, then one line per part
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
        odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
        intake = add(Intake.motor("intake"));
        lift = add(Lift.slide("lift").bottomSwitch("lift_bottom"));
        claw = add(Claw.servo("claw"));
        camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
                .speedFromDistance(camera));
    }
}
// end recipe
