package com.crossbotics.ftchelper.examples.turretbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.LightColor;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * Example robot 2, file 3 of 5: the buttons and the TeleOp. The parts bring their buttons (INIT
 * shows the map); here: X grab, Y release into a FLOWER, the aim buzz, and the light's colors.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/09-shooter-and-camera/#13-a-light-for-the-drivers
 */
@TeleOp(name = "TurretBot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g2.x.press(Actions.grab(robot)).says("grab");
        g2.y.press(Actions.releaseIntoFlower(robot)).says("release into a FLOWER");
        // Gamepad 2 buzzes when the turret is near the goal (Tunables.Aim.rumbleDeg).
        g2.rumbleWhenAimed();

        // The light: the first true rule wins, so the most important comes first.
        robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);     // last 20 s: go park
        // fire now
        robot.lights.show(LightColor.GREEN).when(() -> robot.launcher.isSpinning() && robot.launcher.isReady());
        // Purple and orange, not blue or red: those show the alliance at INIT.
        robot.lights.show(LightColor.PURPLE).when(() -> robot.turret.followMode().isOn());   // following the goal
        robot.lights.otherwise(LightColor.ORANGE);                             // following is off (BACK)
        // a 0.5 s flash per piece fired
        robot.lights.show(LightColor.WHITE).whenCountGoesUp(() -> robot.launcher.part().getShotsFired());
    }
}
