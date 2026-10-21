package com.crossbotics.ftchelper.examples.turretbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.finders.ShotTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * The turret robot's Shot Tuner (lesson 7, docs/tutorials/start/07-shooter-and-camera.md). It uses
 * the robot's launcher, camera and drive, and shows the line to copy into Tunables.java:
 * {@code Tunables.Launcher.speedByDistance = {{48, 2550}, {72, 2900}};}. The turret stays still and
 * the "turret aimed" check is off: you point the robot at the goal with the sticks.
 * How to use it: docs/guides/finders.md. KEEP PEOPLE OUT OF THE LINE OF FIRE.
 */
@TeleOp(name = "TurretBot Shot Tuner", group = "Finders")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
public class ShotTuner extends ShotTunerOpMode {
    // The robot from Robot.java, with its numbers from Tunables.java.
    @Override
    protected RobotDefinition robot() {
        return new Robot();
    }
}
