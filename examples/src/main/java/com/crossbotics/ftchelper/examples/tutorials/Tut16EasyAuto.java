package com.crossbotics.ftchelper.examples.tutorials;

// "import" lines tell Java where to find the classes we use: FTC SDK classes (com.qualcomm...)
// and helper-library classes (com.crossbotics.ftchelper...).
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
 * INIT menu, the timer, mirroring for red, stopping, and the report.
 * Needs: the drivetrain from RobotSettings, motor "lift", servo "claw".
 *
 * There is no runOpMode() and no loop here: EasyAuto has them already. We only answer its
 * questions (which drive? which mechanisms? which steps?) by filling in the methods below.
 *
 * Before you use it: remove the {@code @Disabled} line, make "lift" and "claw" match your
 * configuration, fill in RobotSettings.java, and change the distances to fit your field plan.
 *
 * Tutorial: docs/tutorials/16-easy-autos.md (and docs/guides/easy-autos.md)
 */
// @Autonomous puts this program in the Driver Station's autonomous list. preselectTeleOp: when
// autonomous ends, the Driver Station gets this TeleOp ready, so the drivers only press INIT.
// The name must match the TeleOp's name exactly (and that TeleOp must not be @Disabled either).
@Autonomous(name = "Tutorial 16: Easy Auto", group = "Tutorials", preselectTeleOp = "Tutorial 20: Easy TeleOp")
// @Disabled hides it from the Driver Station. Delete this line to use the program.
@Disabled
// "extends EasyAuto": this program is an EasyAuto (which is itself an FTC LinearOpMode) and gets
// everything it does for free. We only fill in the parts that are about OUR robot.
public class Tut16EasyAuto extends EasyAuto {

    // Fields: they belong to the whole program, so createSubsystems() can make the mechanisms and
    // buildRoutine() (which runs later) can use them in its steps.
    private PositionMotor lift;
    private PositionServo claw;

    // ---- 1. The robot ----
    // Each "@Override" line fills in a method that EasyAuto asks for. Java checks that the names
    // match what EasyAuto expects, so a typo is caught when you build.
    // The drive, autonomous tuning and IMU, all from RobotSettings.java: the same as TeleOp (tutorial 20).
    @Override protected RobotConfig robotConfig() { return RobotSettings.config(); }

    // The mechanisms. EasyAuto keeps them updated during EVERY step (moves and waits included),
    // and stops them all at the end, even after an error.
    @Override
    protected Subsystems createSubsystems() {
        // The lift: positions in encoder ticks, safe range 0..2600, PID gains like tutorial 14.
        // Start the robot with the lift DOWN: that position counts as 0.
        lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600).pid(0.004, 0, 0.0002, 0.05)
                .position("DOWN", 0).position("HIGH", 2400);
        // The claw: servo positions 0 to 1 (find yours by testing). travelTime(0.3): the servo
        // needs about 0.3 s for a full move. A servo cannot say where it is, so this is how
        // the step knows when the claw should have finished moving.
        claw = PositionServo.create(hardwareMap, "claw")
                .position("OPEN", 0.2).position("CLOSED", 0.75).travelTime(0.3);
        return new Subsystems().add(lift, claw);
    }

    // ---- 2. INIT: where to put the robot, and what the drivers can choose ----
    // Shown on the Driver Station in INIT, so the drive team places the robot the same way
    // every match. The distances in the steps only work from this exact spot.
    @Override
    protected String startPosition() {
        return "Back wall, touching the left edge of the 2nd tile, facing out";
    }

    // An extra choice in the INIT menu (EasyAuto already adds alliance, start delay and profile).
    // "Route" can be "Score" (the first one, chosen at the start) or "Park only".
    @Override
    protected void addChoices(InitMenu menu) {
        menu.choice("Route", "Score", "Park only");
    }

    // Keep the last 4 seconds of the 30-second autonomous for parking: if the main steps run
    // late, they are cut short so the park steps still run in time (parking scores points).
    @Override
    protected double parkReserveSec() {
        return 4;
    }

    // ---- 3. The steps (written for BLUE; RED is mirrored) ----
    // Mirrored = for RED, every turn and sideways move flips (left becomes right).
    // Each step has a name (the first text) shown on the screen while it runs and in the report.
    // Steps run one after the other. Moves: (inches, direction, top power), direction
    // 0 = forward, 90 = right, 180 = back, -90 = left. Turns: positive degrees = left.
    @Override
    protected void buildRoutine(AutoRoutine r) {
        // Close the claw on the preloaded piece (waits until it should be closed), then drive
        // 4 inches forward off the wall so the robot can turn without scraping it.
        r.step("Grab", claw.goToStep("CLOSED"))
         .move("Off the wall", 4, 0, 0.4);

        // Only score if the drivers chose "Score" in INIT. equals() compares two pieces of text.
        if (choice("Route").equals("Score")) {
            // startStep only STARTS the lift and goes straight on: the lift rises while we drive.
            r.step("Lift starts up", lift.startStep("HIGH"))
             .move("To the goal", 24, 0, 0.6)
             .turn("Face the goal", 45, 0.5)
             // Wait until the lift is really up, but never more than 2.0 seconds (a stuck lift
             // must not waste the whole autonomous). "lift::isAtTarget" is a method reference:
             // short for "() -> lift.isAtTarget()", a question the routine asks over and over.
             .waitUntil("Lift is up", lift::isAtTarget, 2.0)
             .step("Drop", claw.goToStep("OPEN"))
             // Give the piece 0.3 s to fall before the lift moves. The lift keeps holding meanwhile.
             .waitFor("Let it fall", 0.3)
             .step("Lift down", lift.startStep("DOWN"))
             // Turn back to heading 0 (the direction the robot faced at the start).
             .turnTo("Square up", 0, 0.5);
        }

        // Everything after thenPark() is the park part: it runs even if the main steps ran out
        // of time. Drive 20 inches to the right (90) at up to 0.7 power.
        r.thenPark()
         .move("Park", 20, 90, 0.7);
    }
}
