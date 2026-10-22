package com.crossbotics.ftchelper.examples.turretbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * Example robot 2, file 3 of 5. From the modules (gamepad 2): RT intake on/off, LT push out (hold),
 * LB flywheel spin up/down, RB fire one, hold to keep firing (each waits for the speed for this distance and for the turret
 * to be aimed), right stick X turret by hand (following pauses while the stick moves), BACK turret
 * following on/off, D-pad right the next shot table (POLLEN / NECTAR), B claw open/close. Added here:
 * X grab, Y release into a FLOWER, and a buzz on gamepad 2 while the turret is on target.
 */
@TeleOp(name = "TurretBot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g2.x.press(Actions.grab(robot)).says("grab");
        g2.y.press(Actions.releaseIntoFlower(robot)).says("release into a FLOWER");
        // The closer the turret is to the goal, the stronger gamepad 2 buzzes (Tunables.Aim.rumbleDeg).
        g2.rumbleWhenAimed();
    }
}
