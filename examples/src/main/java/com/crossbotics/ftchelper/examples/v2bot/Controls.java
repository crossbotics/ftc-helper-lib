package com.crossbotics.ftchelper.examples.v2bot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.LightColor;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * The V2 robot, file 3 of 5: the TeleOp. Each part's buttons are written out below (INIT shows the map). Added
 * here: A grab, Y score in a FLOWER, X stow, the lift on the right stick Y, and the light's colors.
 * more: https://crossbotics.com/ftc-helper/docs/guides/interlocks/#the-v2-robots-buttons
 */
@TeleOp(name = "V2 Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;

    // Always pre-assigned on gamepad 1 (the library's): sticks: drive; LT: slow; Y: field-centric (double
    // tap); BACK: pause; GUIDE: button map (held: tuning menu); START held 1 s: set pose; START + BACK: debug
    // lines; X / B in INIT: alliance.

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // The intake's own buttons.
        robot.intake.inOn(g2.rt);
        robot.intake.outOn(g2.lt);

        // The launcher's own buttons.
        robot.launcher.spinOn(g2.lb);
        robot.launcher.fireOn(g2.rb);
        robot.launcher.speedOn(g2.dpadUp, g2.dpadDown);
        robot.launcher.tableOn(g2.dpadRight);

        // The claw's own button.
        g2.b.toggle(robot.claw).says("open / close");

        // The wrist's and the piece count's own buttons.
        g2.dpadLeft.press(robot.wrist::previous).says("previous position");
        g2.leftStickButton.heldFor(1.0, robot.pieces::clear).says("piece count = 0");

        // The hood's and the turret's own buttons.
        g2.leftStickY.use(robot.launcher.hood()::byHand).says("hood trim while it follows the table, else the hood (up = bigger angle)");
        g2.rightStickX.use(v -> robot.turret.manual(-v)).says("by hand (right = clockwise)");
        g2.back.toggle(robot.turret.followMode()).says("follow the goal on/off");

        // Ours.
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
