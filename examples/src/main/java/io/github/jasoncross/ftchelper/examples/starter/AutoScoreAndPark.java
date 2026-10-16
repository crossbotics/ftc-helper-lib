// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.starter;

// "import" lines list the ready-made pieces this file uses.
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import io.github.jasoncross.ftchelper.auto.AutoRoutine;
import io.github.jasoncross.ftchelper.match.InitMenu;

/**
 * STARTER KIT, file 5 of 6: a real autonomous. Copy this file for each new route.
 * Written for BLUE; RED is mirrored automatically. Choose "Score: YES/NO" in INIT.
 *
 * {@code preselectTeleOp} makes the Driver Station pick Main TeleOp after autonomous.
 *
 * Before it runs, set calibrationConfirmed to true in RobotSettings.auto() (after tuning):
 * until then it stops in INIT with a message, on purpose. Writing autos: docs/guides/easy-autos.md
 * and docs/tutorials/16-easy-autos.md.
 */
// @Autonomous puts it in the Driver Station's Autonomous list. Give each route its own clear name.
@Autonomous(name = "Score and Park", group = "Starter", preselectTeleOp = "Main TeleOp")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends StarterAuto" = it gets all the shared setup (drive, IMU, robot, INIT menu) from file 4.
// This file only says where to start, which extra choices to offer, and the steps.
public class AutoScoreAndPark extends StarterAuto {

    // Shown on the INIT screen, so the drive team places the robot the same way every match.
    // "@Override" = "this is my answer to a method StarterAuto/EasyAuto asks for".
    @Override
    protected String startPosition() {
        return "Against the back wall, 2nd tile from the left, facing the field";
    }

    // Extra lines in the INIT menu (the alliance, start delay and profile lines are already there).
    // "Score" starts at YES; the drivers can switch it to NO with the D-pad, e.g. when a partner scores.
    @Override
    protected void addChoices(InitMenu menu) {
        menu.yesNo("Score", true);
    }

    // The scoring steps stop when 4 seconds are left, so parking always gets its time.
    // Time your park on the field and change the 4 to fit.
    @Override
    protected double parkReserveSec() {
        return 4;   // the last 4 s are kept for parking
    }

    // The route: a list of named steps, run in order after START. The names show on the Driver
    // Station while each one runs, and in the report at the end, so make them clear.
    // move(name, inches, direction, power): direction 0 = forward, 90 = right, 180 = back, -90 = left.
    // turn(name, degrees, power): positive = left. Everything is written for BLUE; for RED the turns
    // and sideways moves are flipped for you.
    @Override
    protected void buildRoutine(AutoRoutine r) {
        // First, close the claw on the piece we start with. "() -> ..." is a lambda: a small piece
        // of code the routine runs when it gets to this step.
        // Then pull 4 inches off the wall, slowly (40%), so the robot does not rub the wall when it turns.
        r.run("Hold the piece", () -> robot.claw.goTo("CLOSED"))
         .move("Leave the wall", 4, 0, 0.4);

        // Only if the drivers left "Score" at YES in the INIT menu.
        if (menu("Score")) {
            // startStep starts the lift moving and goes straight on: the lift rises while the robot
            // drives, which saves time.
            r.step("Start the lift", robot.lift.startStep("HIGH"))   // rises during the next moves
             .move("Drive to the goal", 24, 0, 0.6)
             .turn("Face the goal", 45, 0.5)                        // left 45 on BLUE, right 45 on RED
             // Wait until the lift is really at HIGH, but never more than 2 seconds, so a stuck lift
             // cannot use up the whole autonomous. "robot.lift::isAtTarget" is a method reference:
             // it hands over "how to ask the lift if it is there", checked again and again.
             .waitUntil("Lift up", robot.lift::isAtTarget, 2.0)
             // Open the claw and wait until it should be open (from the claw's travel time).
             .step("Drop", robot.claw.goToStep("OPEN"))
             // Start the lift down and go straight on (it comes down during the next steps).
             .step("Lift down", robot.lift.startStep("DOWN"))
             // Turn back to heading 0 (the way the robot faced at the start) before parking.
             .turnTo("Square up", 0, 0.5);
        }

        // Everything after thenPark() is the park: it still runs when the steps above ran out of time.
        // 70% power: parking is a straight, simple move, and time is short here.
        r.thenPark()
         .move("Park", 20, 90, 0.7);                                // right on BLUE, left on RED
    }

    // A small helper: true when the yes/no INIT choice with this name is set to "YES".
    // ("YES".equals(...) also works safely if the choice is missing: then it is just false.)
    private boolean menu(String yesNoChoice) {
        return "YES".equals(choice(yesNoChoice));
    }
}
