package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.auto.AutoRoutine;
import io.github.jasoncross.ftchelper.auto.EasyAuto;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.match.InitMenu;
import io.github.jasoncross.ftchelper.mechanism.PositionMotor;
import io.github.jasoncross.ftchelper.mechanism.PositionServo;

/**
 * Tutorial 16: an easy autonomous. The whole program is the list of steps; EasyAuto does the
 * INIT menu, the timer, mirroring for red, stopping, and the report.
 * Needs: the drivetrain from RobotSettings, motor "lift", servo "claw".
 *
 * Tutorial: docs/tutorials/16-easy-autos.md
 */
@Autonomous(name = "Tutorial 16: Easy Auto", group = "Tutorials", preselectTeleOp = "Tutorial 12: Main TeleOp")
@Disabled
public class Tut16EasyAuto extends EasyAuto {

    private PositionMotor lift;
    private PositionServo claw;

    // ---- 1. The robot ----
    @Override protected DriveConfig driveConfig() { return RobotSettings.drive(); }
    @Override protected AutoDriveConfig autoDriveConfig() { return RobotSettings.auto(); }
    @Override protected IMU createImu() { return RobotSettings.imu(hardwareMap); }

    @Override
    protected Subsystems createSubsystems() {
        lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600).pid(0.004, 0, 0.0002, 0.05)
                .position("DOWN", 0).position("HIGH", 2400);
        claw = PositionServo.create(hardwareMap, "claw")
                .position("OPEN", 0.2).position("CLOSED", 0.75).travelTime(0.3);
        return new Subsystems().add(lift, claw);
    }

    // ---- 2. INIT: where to put the robot, and what the drivers can choose ----
    @Override
    protected String startPosition() {
        return "Back wall, touching the left edge of the 2nd tile, facing out";
    }

    @Override
    protected void addChoices(InitMenu menu) {
        menu.choice("Route", "Score", "Park only");
    }

    @Override
    protected double parkReserveSec() {
        return 4;
    }

    // ---- 3. The steps (written for BLUE; RED is mirrored) ----
    @Override
    protected void buildRoutine(AutoRoutine r) {
        r.step("Grab", claw.goToStep("CLOSED"))
         .move("Off the wall", 4, 0, 0.4);

        if (choice("Route").equals("Score")) {
            r.step("Lift starts up", lift.startStep("HIGH"))
             .move("To the goal", 24, 0, 0.6)
             .turn("Face the goal", 45, 0.5)
             .waitUntil("Lift is up", lift::isAtTarget, 2.0)
             .step("Drop", claw.goToStep("OPEN"))
             .waitFor("Let it fall", 0.3)
             .step("Lift down", lift.startStep("DOWN"))
             .turnTo("Square up", 0, 0.5);
        }

        r.thenPark()
         .move("Park", 20, 90, 0.7);
    }
}
