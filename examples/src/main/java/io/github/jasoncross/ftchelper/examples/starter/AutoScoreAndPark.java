package io.github.jasoncross.ftchelper.examples.starter;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import io.github.jasoncross.ftchelper.auto.AutoRoutine;
import io.github.jasoncross.ftchelper.match.InitMenu;

/**
 * STARTER KIT, file 5 of 6: a real autonomous. Copy this file for each new route.
 * Written for BLUE; RED is mirrored automatically. Choose "Score: YES/NO" in INIT.
 *
 * {@code preselectTeleOp} makes the Driver Station pick Main TeleOp after autonomous.
 */
@Autonomous(name = "Score and Park", group = "Starter", preselectTeleOp = "Main TeleOp")
@Disabled
public class AutoScoreAndPark extends StarterAuto {

    @Override
    protected String startPosition() {
        return "Against the back wall, 2nd tile from the left, facing the field";
    }

    @Override
    protected void addChoices(InitMenu menu) {
        menu.yesNo("Score", true);
    }

    @Override
    protected double parkReserveSec() {
        return 4;   // the last 4 s are kept for parking
    }

    @Override
    protected void buildRoutine(AutoRoutine r) {
        r.run("Hold the piece", () -> robot.claw.goTo("CLOSED"))
         .move("Leave the wall", 4, 0, 0.4);

        if (menu("Score")) {
            r.step("Start the lift", robot.lift.startStep("HIGH"))   // rises during the next moves
             .move("Drive to the goal", 24, 0, 0.6)
             .turn("Face the goal", 45, 0.5)                        // left 45 on BLUE, right 45 on RED
             .waitUntil("Lift up", robot.lift::isAtTarget, 2.0)
             .step("Drop", robot.claw.goToStep("OPEN"))
             .step("Lift down", robot.lift.startStep("DOWN"))
             .turnTo("Square up", 0, 0.5);
        }

        r.thenPark()
         .move("Park", 20, 90, 0.7);                                // right on BLUE, left on RED
    }

    private boolean menu(String yesNoChoice) {
        return "YES".equals(choice(yesNoChoice));
    }
}
