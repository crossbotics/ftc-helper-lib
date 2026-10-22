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
import com.qualcomm.hardware.rev.RevBlinkinLedDriver;
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
            // At the top of Robot.java: import com.crossbotics.ftchelper.robot.Lights;  (and in
            // Controls.java, for the colors: import com.crossbotics.ftchelper.robot.LightColor;)
            // A goBILDA RGB Indicator Light on a servo port ("Servo" in the robot configuration):
            lights = add(Lights.gobilda("light"));
            // Or a REV Blinkin LED Driver with an LED strip ("REV Blinkin LED Driver" in the configuration):
            //   lights = add(Lights.blinkin("blinkin"));
            // end recipe
        }
    }

    /** A REV Blinkin with an LED strip, and a pattern of its own for one color. */
    public static class BlinkinRobot extends RobotDefinition {
        public Lights lights;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: light-blinkin
            // At the top of Robot.java: import com.qualcomm.hardware.rev.RevBlinkinLedDriver;
            // A REV Blinkin LED Driver ("REV Blinkin LED Driver" in the robot configuration):
            lights = add(Lights.blinkin("blinkin"));
            // Your strip shows plain blue too dark? Show another Blinkin pattern for BLUE (the
            // pattern names are on REV's Blinkin page). Other lights ignore this line.
            lights.blinkinPattern(LightColor.BLUE, RevBlinkinLedDriver.BlinkinPattern.BLUE_VIOLET);
            // end recipe
        }
    }

    /** One action that flashes the light when it is done. */
    public static class Actions {
        // recipe: light-action
        // In Actions.java (it needs import com.crossbotics.ftchelper.robot.LightColor; at the top).
        // The last step of an action: a 1 s white flash says "done" to the drivers. Pick a color
        // (or a time) your rules do not use: a GREEN flash over the GREEN "ready" rule cannot be seen.
        public static Action shootTwo(Robot r) {
            return Action.named("Shoot two")
                    .then(r.launcher::spinUp)
                    .waitUntil("at speed", r.launcher::isReady, 3.0)
                    .then(() -> r.launcher.fire(2))
                    .waitUntil("both gone", () -> !r.launcher.isShooting(), 3.0)
                    .then(() -> r.lights.flash(LightColor.WHITE, 1.0));
        }
        // end recipe
    }

    /** Other colors for the alliance at INIT (its own TeleOp: the main one keeps the defaults). */
    @TeleOp(name = "Cookbook Alliance Colors", group = "Cookbook")
    @Disabled
    public static class AllianceColors extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: light-alliance
            // At INIT the light shows the alliance chosen (solid RED or BLUE; white blinking until one is
            // chosen) and flashes it for 1 s at START: on by default, nothing to write.
            // Your light shows red badly? Other colors for the alliance at INIT (BLUE, and PURPLE for RED):
            robot.lights.allianceColors(LightColor.BLUE, LightColor.PURPLE);
            // No alliance color at INIT at all (your rules from INIT on): delete the line above and write
            //     robot.lights.allianceAtInit(false);
            // or, in Tunables.java, the light's section: public static class Light {
            //     public static boolean allianceAtInit = false; }   (the line in Controls.java wins)
            // end recipe
        }
    }

    @TeleOp(name = "Cookbook Lights", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: light-flash
            // A flash beats every rule while it lasts, wherever it is written (write it first: it reads
            // that way). A short white flash for each piece fired (the launcher counts the pieces it
            // feeds): 0.2 s, so two quick shots are two flashes, not one long one. And one from a button.
            robot.lights.show(LightColor.WHITE).forSeconds(0.2)
                    .whenCountGoesUp(() -> robot.launcher.part().getShotsFired());
            g2.a.press(() -> robot.lights.flash(LightColor.YELLOW)).says("flash the light (signal the partner)");
            // end recipe

            // recipe: light-endgame
            // Yellow, blinking, in the last 20 seconds of TeleOp: time to park. Written above the other
            // rules, so it beats them.
            robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);
            // end recipe

            // recipe: light-rules
            // One rule per line. The first true rule wins: put the most important first.
            // Red and blue are left for the alliance (shown at INIT), so no rule looks like one.
            robot.lights.show(LightColor.ORANGE).blinking().when(robot.intake::isPushingOut);  // pushing out
            robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);              // at speed: fire
            robot.lights.show(LightColor.PURPLE).when(robot.intake::isRunning);             // intake on
            robot.lights.otherwise(LightColor.OFF);                                         // none true
            // end recipe


            g2.x.press(Actions.shootTwo(robot)).says("shoot two");
        }
    }
}
