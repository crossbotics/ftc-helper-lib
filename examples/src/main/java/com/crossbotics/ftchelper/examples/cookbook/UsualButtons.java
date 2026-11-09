package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Arm;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.Pieces;
import com.crossbotics.ftchelper.robot.Turret;
import com.crossbotics.ftchelper.robot.Wrist;

/**
 * Each part's usual buttons, the lines to copy into Controls.java's buttons(...) (docs/modules/robot.md, each
 * part's "Buttons to add"). They compile here; the example robots' tests (AllButtonsTest) check that the same
 * lines make the same buttons the parts used to add by themselves. The lines between "// recipe: name" and
 * "// end recipe" are shown in the docs: change both together (tools/check_docs.py checks them).
 */
public class UsualButtons {

    /** The parts, by the names the lines use (robot.intake, robot.lift...). */
    static class Parts {
        Intake intake;
        Lift lift;
        Arm arm;
        Claw claw;
        Wrist wrist;
        Turret turret;
        Launcher launcher;
        Indexer feeder;
        Pieces pieces;
    }

    // Never run: each class holds one part's lines (in buttons(...), as in Controls.java), so they compile.
    static class IntakeLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-intake
            g2.rt.hold(robot.intake::holdIn, robot.intake::stop).says("in (hold)");
            g2.lt.hold(robot.intake::out, robot.intake::stop).says("out (hold)");
            // end recipe
        }
    }

    static class IntakeOnAtStartLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-intake-on-at-start
            g2.rt.toggle(robot.intake).says("in on/off");
            g2.lt.hold(robot.intake::reverse, robot.intake::endReverse).says("out (hold)");
            // end recipe
        }
    }

    static class LiftLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-lift
            g2.a.press(() -> robot.lift.goTo("down")).says("down");
            g2.x.press(() -> robot.lift.goTo("low")).says("low");
            g2.y.press(() -> robot.lift.goTo("high")).says("high");
            g2.dpadUp.press(robot.lift::stepUp).says("next height up");
            g2.dpadDown.press(robot.lift::stepDown).says("next height down");
            g2.leftStickY.use(robot.lift::manual).says("by hand");
            // end recipe
        }
    }

    static class ArmLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-arm
            g2.rightStickY.use(robot.arm::manual).says("by hand");
            // end recipe
        }
    }

    static class ClawLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-claw
            g2.b.toggle(robot.claw).says("open / close");
            // end recipe
        }
    }

    static class WristLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-wrist
            g2.dpadLeft.press(robot.wrist::previous).says("previous position");
            g2.dpadRight.press(robot.wrist::next).says("next position");
            // end recipe
        }
    }

    static class TurretLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-turret
            g2.rightStickX.use(v -> robot.turret.manual(-v)).says("by hand (right = clockwise)");
            g2.back.toggle(robot.turret.followMode()).says("follow the goal on/off");   // only with follows(...)
            // end recipe
        }
    }

    static class LauncherLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-launcher
            g2.lb.toggle(robot.launcher).says("spin up / down");
            g2.rb.hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("fire one; hold: keep firing");
            g2.dpadUp.hold(() -> robot.launcher.repeatStep(1), robot.launcher::repeatEnd).says("speed up, 4 a second")
                    .tap(() -> robot.launcher.step(1)).says("speed up");
            g2.dpadDown.hold(() -> robot.launcher.repeatStep(-1), robot.launcher::repeatEnd).says("speed down, 4 a second")
                    .tap(() -> robot.launcher.step(-1)).says("speed down")
                    .doubleTap(robot.launcher::resetTrim).says("back to speedRpm");
            // end recipe
        }
    }

    static class LauncherWithTablesLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-launcher-tables
            g2.lb.toggle(robot.launcher).says("spin up / down");
            g2.rb.hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("fire one; hold: keep firing");
            g2.dpadUp.hold(() -> robot.launcher.repeatStep(1), robot.launcher::repeatEnd).says("speed trim +1%, 4 a second")
                    .tap(() -> robot.launcher.step(1)).says("speed trim +1%");
            g2.dpadDown.hold(() -> robot.launcher.repeatStep(-1), robot.launcher::repeatEnd).says("speed trim -1%, 4 a second")
                    .tap(() -> robot.launcher.step(-1)).says("speed trim -1%")
                    .doubleTap(robot.launcher::resetTrim).says("speed trim 0");
            g2.dpadRight.press(robot.launcher::nextTable).says("next shot table");   // with two tables or more
            // end recipe
        }
    }

    static class HoodLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-hood
            g2.leftStickY.use(robot.launcher.hood()::byHand).says("hood angle (up = bigger angle)");
            // end recipe
        }
    }

    static class FeederLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-indexer
            g2.rb.press(robot.feeder::feedOne).says("feed one");   // an indexer on its own, not in a launcher
            // end recipe
        }
    }

    static class PiecesLines {
        void buttons(Parts robot, Pad g1, Pad g2) {
            // recipe: buttons-pieces
            g2.leftStickButton.heldFor(1.0, robot.pieces::clear).says("piece count = 0");
            // end recipe
        }
    }
}
