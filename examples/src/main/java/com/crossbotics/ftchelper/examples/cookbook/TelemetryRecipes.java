package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import java.util.Locale;

/**
 * Cookbook: telemetry, the lines on the Driver Station (docs/cookbook.md#telemetry).
 *
 * <p>The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class TelemetryRecipes {

    /** A robot with an intake, a lift and a launcher. */
    public static class Robot extends RobotDefinition {
        public Intake intake;
        public Lift lift;
        public Launcher launcher;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            intake = add(Intake.motor("intake"));
            lift = add(Lift.slide("lift"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer")));
        }
    }

    @TeleOp(name = "Cookbook Telemetry", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: show-value
            // A line of its own in buttons(...). The value is read again every loop.
            show("Lift ticks", robot.lift::position);
            show("Intake on", robot.intake::isRunning);                 // shows "Intake on : true"
            show("Flywheel ready", () -> robot.launcher.isReady());
            // end recipe

            // recipe: show-format
            // Round a number: %.0f = no decimals, %.1f = one decimal. Locale.US keeps the dot (not a comma).
            show("Target RPM", () -> String.format(Locale.US, "%.0f", robot.launcher.targetRpm()));
            // end recipe
        }
    }

    @Autonomous(name = "Cookbook Telemetry Auto", group = "Cookbook")
    @Disabled
    public static class TelemetryAuto extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            s.start("Against our wall, facing the field", 0);
            // recipe: auto-telemetry
            // In your own step: lines on the Driver Station while it runs, and a note in the report.
            s.step("Spin up", ctx -> {
                robot.launcher.spinUp();
                double giveUpAt = ctx.secondsLeft() - 3;    // a time limit: at most 3 s
                while (ctx.isActive() && !robot.launcher.isReady() && ctx.secondsLeft() > giveUpAt) {
                    ctx.telemetry().line("Flywheel", "spinning up to %.0f RPM", robot.launcher.targetRpm());
                    ctx.loop();          // updates every part once: the robot keeps running
                }
                ctx.note(robot.launcher.isReady() ? "at speed" : "not at speed after 3 s");
            });
            // end recipe
        }
    }
}
