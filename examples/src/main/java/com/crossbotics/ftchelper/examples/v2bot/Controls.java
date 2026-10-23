package com.crossbotics.ftchelper.examples.v2bot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.LightColor;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * The V2 robot, file 3 of 5: the TeleOp. The parts bring their buttons (INIT shows the map). Added
 * here: A grab, Y score in a FLOWER, X stow, the lift on the right stick Y, and the light's colors.
 * more: https://crossbotics.com/ftc-helper/docs/guides/interlocks/#the-v2-robots-buttons
 */
@TeleOp(name = "V2 Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // The lifter's steps (each is an Action: it runs over many loops and never blocks the robot).
        g2.a.press(Actions.grab(robot)).says("grab a ball from the tiles");
        g2.y.press(Actions.scoreInFlower(robot)).says("score in a FLOWER");
        g2.x.press(Actions.stow(robot)).says("stow the lifter");
        // The lift by hand (no interlock on the lift: the wrist's rule keeps the claw clear of the turret).
        g2.rightStickY.use(robot.lift::manual).says("lift by hand");

        g2.rumbleWhenAimed();

        // A flash beats every rule; then the first true rule wins. No red or blue: those mean the alliance.
        robot.lights.show(LightColor.WHITE).whenCountGoesUp(() -> robot.launcher.part().getShotsFired());
        robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);     // last 20 s: go park
        // the interlock holds a part back
        robot.lights.show(LightColor.ORANGE).when(() -> robot.turretClaw.isHolding());
        robot.lights.show(LightColor.GREEN).when(() -> robot.launcher.isSpinning() && robot.launcher.isReady());
        robot.lights.show(LightColor.PURPLE).when(() -> robot.turret.followMode().isOn());
        robot.lights.otherwise(LightColor.OFF);                                 // when no rule is true
    }
}
