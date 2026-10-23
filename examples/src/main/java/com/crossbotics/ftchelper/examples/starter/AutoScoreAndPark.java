// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.starter;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.crossbotics.ftchelper.auto.AutoRoutine;
import com.crossbotics.ftchelper.match.InitMenu;

/**
 * STARTER KIT, file 5 of 6: a real autonomous. Copy this file for each new route. Written for BLUE;
 * RED is mirrored automatically. Choose "Score: YES/NO" in INIT. It stops in INIT until
 * calibrationConfirmed is true in RobotSettings.java (after tuning), on purpose.
 * more: https://crossbotics.com/ftc-helper/docs/starter-kit/#autoscoreandparkjava-an-autonomous
 */
// Give each route its own clear name. preselectTeleOp = the TeleOp the Driver Station picks next.
@Autonomous(name = "Score and Park", group = "Starter", preselectTeleOp = "Main TeleOp")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// The shared setup (drive, IMU, robot, INIT menu) comes from StarterAuto.java (file 4).
public class AutoScoreAndPark extends StarterAuto {

    // Shown on the INIT screen, so the drive team places the robot the same way every match.
    @Override
    protected String startPosition() {
        return "Against the back wall, 2nd tile from the left, facing the field";
    }

    // Degrees from "straight away from your driver" (left = +): facing the field = 0.
    // TeleOp's field-centric "forward" uses it. Change it if you start turned.
    @Override
    protected double startHeadingDeg() {
        return 0;
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

    // The route, run in order after START. Written for BLUE: RED flips the turns and sideways moves.
    // move(name, inches, direction, power): direction 0 = forward, 90 = right, 180 = back, -90 = left.
    // turn(name, degrees, power): positive = left.
    @Override
    protected void buildRoutine(AutoRoutine r) {
        // Close the claw on the preloaded piece, then pull 4 in off the wall so the turn does not rub it.
        r.run("Hold the piece", () -> robot.claw.goTo("CLOSED"))
         .move("Leave the wall", 4, 0, 0.4);

        // Only if the drivers left "Score" at YES in the INIT menu.
        if (menu("Score")) {
            r.step("Start the lift", robot.lift.startStep("HIGH"))   // rises during the next moves
             .move("Drive to the goal", 24, 0, 0.6)
             .turn("Face the goal", 45, 0.5)                        // left 45 on BLUE, right 45 on RED
             // At most 2 s, so a stuck lift cannot use up the whole autonomous.
             .waitUntil("Lift up", robot.lift::isAtTarget, 2.0)
             // Open the claw and wait until it should be open (from the claw's travel time).
             .step("Drop", robot.claw.goToStep("OPEN"))
             // Start the lift down and go straight on (it comes down during the next steps).
             .step("Lift down", robot.lift.startStep("DOWN"))
             // Turn back to heading 0 (the way the robot faced at the start) before parking.
             .turnTo("Square up", 0, 0.5);
        }

        // Everything after thenPark() is the park: it still runs when the steps above ran out of time.
        r.thenPark()
         .move("Park", 20, 90, 0.7);                                // right on BLUE, left on RED
    }

    // True when the yes/no INIT choice with this name is "YES" (false if it is missing).
    private boolean menu(String yesNoChoice) {
        return "YES".equals(choice(yesNoChoice));
    }
}
