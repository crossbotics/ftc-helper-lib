package com.crossbotics.ftchelper.examples.gobildabot.autos;

import com.crossbotics.ftchelper.examples.gobildabot.Actions;
import com.crossbotics.ftchelper.examples.gobildabot.Robot;
import com.crossbotics.ftchelper.examples.gobildabot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * goBILDA starter bot: every part of the autonomous builder in one program, numbered. The guide
 * docs/guides/autonomous-builder.md explains each number. Try it in the simulator: INIT, choose in
 * the menu with the D-pad, START, and read the report at the end.
 */
@Autonomous(name = "goBILDA Bot Auto Cookbook", group = "Examples", preselectTeleOp = "goBILDA Bot TeleOp")
@Disabled
public class AutoCookbook extends Auto<Robot> {

    // BIOBUZZ's field is turned half a turn between the alliances, so the same route works for both:
    // no left/right mirroring for RED.
    @Override
    protected boolean mirrorForRed() {
        return false;
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        // 1. Where to put the robot: the note for the drive team (shown in INIT) and the spot (the
        //    simulator puts the robot there). Heading 0 = facing away from the drivers.
        s.start("Back against our goal, facing away from it", -56, -61.1, 0)

         // 2. The INIT menu: the drivers choose with the D-pad before START (alliance, start delay
         //    and profile come with every autonomous).
         .menuChoice("Then", "Leave", "Collect more", "Stay")
         .menuYesNo("Spin up early", true)

         // 3. A command that starts something and goes on at once.
         .onlyIf("Spin up early")
             .run("Spin up the flywheel", robot.launcher::spinUp)
         .end()

         // 4. An action from Actions.java, waited for (at most 8 s; the report says if it timed out).
         .action(Actions.fireAllWithIntake(robot), 8)

         // 5. A branch: these steps run only when "Collect more" was chosen.
         .onlyIf("Then", "Collect more")
             .run("Intake in", robot.intake::in)
             .move("To the pieces", Tunables.Auto.collectInches, 0, 0.5)
             .waitSeconds("Let them in", 0.5)
             .move("Back to the goal", Tunables.Auto.collectInches, 180, 0.5)
             .run("Intake off", robot.intake::stop)
             .action(Actions.fireAllWithIntake(robot), 8)
         .end()

         // 6. Your own step, for anything the list cannot say. Wait with ctx, never sleep().
         .step("Clear the intake", ctx -> {
             robot.intake.out();
             ctx.waitFor(0.3);
             robot.intake.stop();
         })

         // 7. Parking: the steps below always get the last 3 seconds of autonomous.
         .parkWithSeconds(3)
         .onlyIf("Then", "Leave")
             .move("Leave the launch zone", Tunables.Auto.leaveInches, 0, 0.7)
         .end();
    }
}
