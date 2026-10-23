package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Cookbook: driving (docs/cookbook.md#driving). Each small robot below shows one way to write the
 * drive line in Robot.java. In your own code there is only ONE Robot.java with ONE drive line.
 *
 * The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class DrivingRecipes {

    /** A mecanum drive (four wheels that can also drive sideways). */
    public static class Robot extends RobotDefinition {
        @Override
        protected void define() {
            // recipe: drive-mecanum
            // The four motor names, in this order: front left, front right, back left, back right.
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                    .motor("gobilda-5203-312").wheelMm(96)       // the motor (catalog id) and wheel size
                    .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
            // end recipe
        }
    }

    /** A standard (tank) drive: one motor per side. */
    public static class TankRobot extends RobotDefinition {
        @Override
        protected void define() {
            // recipe: drive-tank
            drive(Drive.tank("left_drive", "right_drive")
                    .motor("gobilda-5203-312").wheelMm(96)
                    .reversed("right_drive")                     // only the motors that run backward
                    .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
            // end recipe
        }
    }

    /** A mecanum drive whose right motors run backward (not the usual left side). */
    public static class ReversedRobot extends RobotDefinition {
        @Override
        protected void define() {
            // recipe: drive-reversed
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                    .motor("gobilda-5203-312").wheelMm(96)
                    // List EVERY reversed drive motor. Without this line the left side is reversed.
                    .reversed("front_right", "back_right")
                    .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
            // end recipe
        }
    }

    /** A Control Hub mounted on its side: the logo faces left, the USB ports face up. */
    public static class SidewaysHubRobot extends RobotDefinition {
        @Override
        protected void define() {
            // recipe: drive-imu
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                    .motor("gobilda-5203-312").wheelMm(96)
                    // Which way the hub's LOGO faces, then which way its USB PORTS face, on the robot.
                    .imu("imu", LogoFacingDirection.LEFT, UsbFacingDirection.UP));
            // end recipe
        }
    }

    /** One driver does everything on gamepad 1, with slow on the left trigger. */
    @TeleOp(name = "Cookbook One Driver", group = "Cookbook")
    @Disabled
    public static class OneDriverControls extends ControlMap<Robot> {
        // recipe: one-driver
        // true: every button is on gamepad 1 (the operator's buttons move there).
        public static boolean oneDriver = true;
        // end recipe

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: slow-trigger
            // One driver: slow on LT. The further it is pulled, the slower (down to slowSpeed).
            g1.lt.slow().says("slow");
            // end recipe
        }
    }
}
