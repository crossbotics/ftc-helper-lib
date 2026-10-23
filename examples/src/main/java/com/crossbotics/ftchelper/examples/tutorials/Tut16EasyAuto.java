package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.auto.AutoRoutine;
import com.crossbotics.ftchelper.auto.EasyAuto;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.match.InitMenu;
import com.crossbotics.ftchelper.mechanism.PositionMotor;
import com.crossbotics.ftchelper.mechanism.PositionServo;

/**
 * Tutorial 16: an easy autonomous. The whole program is the list of steps; EasyAuto does the
 * INIT menu, the timer, mirroring for red, stopping, and the report. No runOpMode(), no loop.
 * Needs: the drivetrain from RobotSettings, motor "lift", servo "claw". Start with the lift DOWN.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/16-easy-autos/
 */
// preselectTeleOp = the TeleOp's exact name: the Driver Station gets it ready after autonomous.
@Autonomous(name = "Tutorial 16: Easy Auto", group = "Tutorials", preselectTeleOp = "Tutorial 20: Easy TeleOp")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut16EasyAuto extends EasyAuto {

    private PositionMotor lift;
    private PositionServo claw;

    // ---- 1. The robot ----
    // drive, autonomous tuning and IMU, all from RobotSettings: the same as TeleOp (tutorial 20)
    @Override protected RobotConfig robotConfig() { return RobotSettings.config(); }

    // EasyAuto updates these during EVERY step and stops them at the end, even after an error.
    @Override
    protected Subsystems createSubsystems() {
        lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600).pid(0.004, 0, 0.0002, 0.05)
                .position("DOWN", 0).position("HIGH", 2400);
        // travelTime: about 0.3 s for a full move (a servo cannot say where it is).
        claw = PositionServo.create(hardwareMap, "claw")
                .position("OPEN", 0.2).position("CLOSED", 0.75).travelTime(0.3);
        return new Subsystems().add(lift, claw);
    }

    // ---- 2. INIT: where to put the robot, and what the drivers can choose ----
    // The distances in the steps only work from this exact spot.
    @Override
    protected String startPosition() {
        return "Back wall, touching the left edge of the 2nd tile, facing out";
    }

    // EasyAuto already adds alliance, start delay and profile.
    @Override
    protected void addChoices(InitMenu menu) {
        menu.choice("Route", "Score", "Park only");
    }

    // The last 4 s are for parking: late main steps are cut short.
    @Override
    protected double parkReserveSec() {
        return 4;
    }

    // ---- 3. The steps (written for BLUE; RED is mirrored) ----
    // move(name, inches, direction, power): 0 = forward, 90 = right. Turns: positive = left.
    @Override
    protected void buildRoutine(AutoRoutine r) {
        r.step("Grab", claw.goToStep("CLOSED"))
         .move("Off the wall", 4, 0, 0.4);

        if (choice("Route").equals("Score")) {
            // startStep only STARTS the lift: it rises while we drive.
            r.step("Lift starts up", lift.startStep("HIGH"))
             .move("To the goal", 24, 0, 0.6)
             .turn("Face the goal", 45, 0.5)
             .waitUntil("Lift is up", lift::isAtTarget, 2.0)
             .step("Drop", claw.goToStep("OPEN"))
             .waitFor("Let it fall", 0.3)
             .step("Lift down", lift.startStep("DOWN"))
             .turnTo("Square up", 0, 0.5);
        }

        // The park part runs even if the main steps ran out of time.
        r.thenPark()
         .move("Park", 20, 90, 0.7);
    }
}
