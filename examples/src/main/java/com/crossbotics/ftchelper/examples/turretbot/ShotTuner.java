package com.crossbotics.ftchelper.examples.turretbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.finders.ShotTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;

/**
 * The turret robot's Shot Tuner: fills the shot tables (POLLEN and NECTAR) and shows the lines to
 * copy into Tunables.Launcher. KEEP PEOPLE OUT OF THE LINE OF FIRE.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/09-shooter-and-camera/#8-two-game-pieces-two-shot-tables
 */
@TeleOp(name = "TurretBot Shot Tuner", group = "Finders")
@Disabled // hides it on the Driver Station: delete this line in your copy
public class ShotTuner extends ShotTunerOpMode {
    // The robot from Robot.java, with its numbers from Tunables.java.
    @Override
    protected RobotDefinition robot() {
        return new Robot();
    }
}
