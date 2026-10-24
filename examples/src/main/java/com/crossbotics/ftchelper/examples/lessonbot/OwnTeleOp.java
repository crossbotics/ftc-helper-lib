package com.crossbotics.ftchelper.examples.lessonbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * The lesson robot in a plain FTC OpMode: your own INIT, START, loop and STOP, with the same
 * Robot.java, Tunables.java and Actions.java as the library's TeleOp (Controls.java).
 * more: https://crossbotics.com/ftc-helper/docs/guides/your-own-code/
 */
@TeleOp(name = "Lesson Bot: own OpMode", group = "Examples")
@Disabled
public class OwnTeleOp extends LinearOpMode {

    @Override
    public void runOpMode() {
        Robot robot = new Robot();
        robot.init(this);                       // INIT: makes the parts, checks the numbers; moves nothing
        int scored = 0;
        boolean aBefore = false;

        while (opModeInInit()) {
            robot.show("Press START", "A scores high");
            robot.initLoop();                   // sends the screen
        }
        robot.start();

        try {
            while (opModeIsActive()) {
                robot.driveWith(gamepad1);

                // A: only when it goes down, not every loop while it is held
                if (gamepad1.a && !aBefore) {
                    robot.run(Actions.scoreHigh(robot));   // runs a little each loop: never waits
                    scored++;
                }
                aBefore = gamepad1.a;
                if (gamepad1.right_trigger > 0.5) robot.intake.in();
                else robot.intake.stop();

                robot.show("Scored", scored);
                robot.update();                 // every part moves a little, then the screen is sent
            }
        } finally {
            robot.stop();                       // always: stops the wheels and every part
        }
    }
}
