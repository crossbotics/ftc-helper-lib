package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.LightColor;
import com.crossbotics.ftchelper.robot.Lights;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Cookbook: a colored light the drivers can see (docs/cookbook.md#lights).
 *
 * <p>The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class LightRecipes {

    /** A robot with an intake, a launcher and a goBILDA RGB Indicator Light. */
    public static class Robot extends RobotDefinition {
        public Intake intake;
        public Launcher launcher;
        public Lights lights;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            intake = add(Intake.motor("intake"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer")));
            // recipe: light-add
            // A goBILDA RGB Indicator Light on a servo port ("Servo" in the robot configuration):
            lights = add(Lights.gobilda("light"));
            // Or a REV Blinkin LED Driver with an LED strip ("REV Blinkin LED Driver" in the configuration):
            //   lights = add(Lights.blinkin("blinkin"));
            // end recipe
        }
    }

    /** One action that flashes the light green when it is done. */
    public static class Actions {
        // recipe: light-action
        // The last step of an action: a green flash says "done" to the drivers.
        public static Action shootTwo(Robot r) {
            return Action.named("Shoot two")
                    .then(r.launcher::spinUp)
                    .waitUntil("at speed", r.launcher::isReady, 3.0)
                    .then(() -> r.launcher.fire(2))
                    .waitUntil("both gone", () -> !r.launcher.isShooting(), 3.0)
                    .then(() -> r.lights.flash(LightColor.GREEN));
        }
        // end recipe
    }

    @TeleOp(name = "Cookbook Lights", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: light-endgame
            // Yellow, blinking, in the last 20 seconds of TeleOp: time to park. Written first, so it beats
            // every other rule.
            robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);
            // end recipe

            // recipe: light-rules
            // One rule per line. The first true rule wins: put the most important first.
            robot.lights.show(LightColor.RED).blinking().when(robot.intake::isPushingOut);  // pushing out
            robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);           // at speed: fire
            robot.lights.show(LightColor.BLUE).when(robot.intake::isRunning);            // intake on
            robot.lights.otherwise(LightColor.OFF);                                      // none true
            // end recipe

            // recipe: light-flash
            // A flash beats every rule for 0.5 s: each time a shot starts, and from a button.
            robot.lights.show(LightColor.WHITE).whenStarts(robot.launcher::isShooting);
            g2.a.press(() -> robot.lights.flash(LightColor.PURPLE)).says("flash the light (signal the partner)");
            // end recipe

            g2.x.press(Actions.shootTwo(robot)).says("shoot two");
        }
    }
}
