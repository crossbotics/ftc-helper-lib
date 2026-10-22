package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.teleop.LibraryButtons;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Cookbook: buttons and gestures (docs/cookbook.md#buttons-and-gestures). Every line goes inside
 * {@code buttons(...)} in Controls.java. That method runs ONCE, at INIT: each line plugs a job into a
 * button, and the library runs the job when the button is pressed. So no if, while or sleep() there.
 *
 * The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class ButtonRecipes {

    /** The robot these buttons use: a mecanum drive, an intake, a lift, a claw and a launcher. */
    public static class Robot extends RobotDefinition {
        public Intake intake;
        public Lift lift;
        public Claw claw;
        public Launcher launcher;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            intake = add(Intake.motor("intake"));
            lift = add(Lift.slide("lift"));
            claw = add(Claw.servo("claw"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer")));
        }
    }

    /** One job per button, gestures, pairs and buzzes. */
    @TeleOp(name = "Cookbook Buttons", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: button-press
            // Once per press. "::" hands over the method; the button runs it later.
            g2.y.press(robot.claw::open).says("open the claw");
            // Several commands: "() ->" starts a small piece of code (a lambda).
            g2.x.press(() -> robot.lift.goTo("low")).says("lift to low");
            // end recipe

            // recipe: button-hold
            // Runs every loop while held; the second job runs once when it is let go.
            g1.a.hold(robot.intake::in, robot.intake::stop).says("intake in (hold)");
            // end recipe

            // recipe: button-toggle
            // Each press switches the intake on or off. rumble(): 2 buzzes = on, 1 buzz = off.
            g1.x.toggle(robot.intake).rumble().says("intake on/off");
            // end recipe

            // recipe: button-gestures
            // Three gestures on one button: tap, hold, double-tap.
            g2.rb.tap(robot.launcher::fire).says("fire one")
                 .hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("keep firing")
                 .doubleTap(() -> robot.launcher.fire(3)).says("fire three");
            // end recipe

            // recipe: button-held-for
            // Runs once, after the button is held for 1 second: a safe "are you sure?".
            g2.dpadLeft.heldFor(1.0, robot.launcher::stop).says("hold 1 s: flywheel off");
            // end recipe

            // recipe: button-pair
            // Hold RB (the "shift key"), then press D-pad up. For big jobs that must not run by accident.
            g1.rb.with(g1.dpadUp, () -> robot.lift.goTo("high")).says("lift to the top");
            // end recipe

            // recipe: rumble-when
            // A buzz on gamepad 2 each time the flywheel gets to its speed.
            g2.rumbleWhen(robot.launcher::isReady);
            // end recipe

            // recipe: stick-use
            // A stick is a number from -1 to 1 (up and right are +). Its value goes to the job every loop.
            g2.rightStickY.use(robot.lift::manual).says("lift by hand");
            // end recipe
        }
    }

    /** The intake moved from the triggers (its standard buttons) to the bumpers. */
    @TeleOp(name = "Cookbook Moved Buttons", group = "Cookbook")
    @Disabled
    public static class MovedButtons extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: move-a-button
            g2.rt.free();   // free(): no job on it, even the one the intake suggests
            g2.lt.free();
            g2.lb.hold(robot.intake::in, robot.intake::stop).says("intake in (hold)");
            g2.rb.hold(robot.intake::out, robot.intake::stop).says("intake out (hold)");
            // end recipe
        }
    }

    /** The library's own buttons moved: field-centric, slow, and the alliance switch turned on. */
    @TeleOp(name = "Cookbook Library Buttons", group = "Cookbook")
    @Disabled
    public static class MovedLibraryButtons extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        // recipe: move-library-buttons
        // The library's own buttons (driving, pause, menus...): move them here; null = no button.
        @Override
        protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
            lib.fieldCentric(g1.x);                  // field-centric on/off: X instead of Y
            lib.slow(g1.rt);                         // slow on the right trigger: LT is free now
            lib.switchAlliance(g1.start, g1.back);   // optional, off by default: hold START, then BACK, 1.5 s
        }
        // end recipe

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            g1.y.press(robot.claw::open).says("open the claw");                  // Y is free now
            g1.lt.hold(robot.intake::in, robot.intake::stop).says("intake in (hold)");
        }
    }

    /** The alliance switch in TeleOp, on its own. */
    @TeleOp(name = "Cookbook Switch Alliance", group = "Cookbook")
    @Disabled
    public static class SwitchAlliance extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        // recipe: switch-alliance
        // Controls.java, inside the class, next to buttons(...) (import
        // com.crossbotics.ftchelper.teleop.LibraryButtons at the top). Off by default.
        @Override
        protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
            // Gamepad 1: hold START, then press and hold BACK too, for 1.5 s: BLUE <-> RED.
            lib.switchAlliance(g1.start, g1.back);
        }
        // end recipe

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
        }
    }

    /** No standard buttons: only the jobs written here. */
    @TeleOp(name = "Cookbook Only My Buttons", group = "Cookbook")
    @Disabled
    public static class OnlyMyButtons extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        // recipe: no-standard-buttons
        // false: the parts bring no buttons of their own. Only the jobs in buttons(...) are used.
        @Override
        protected boolean useStandardButtons() {
            return false;
        }
        // end recipe

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            g2.a.toggle(robot.intake).says("intake on/off");
        }
    }
}
