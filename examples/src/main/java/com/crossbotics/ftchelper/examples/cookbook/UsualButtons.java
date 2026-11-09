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
            robot.intake.inOn(g2.rt);
            robot.intake.outOn(g2.lt);
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
            robot.launcher.spinOn(g2.lb);
            robot.launcher.fireOn(g2.rb);
            robot.launcher.speedOn(g2.dpadUp, g2.dpadDown);
            robot.launcher.tableOn(g2.dpadRight);
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
