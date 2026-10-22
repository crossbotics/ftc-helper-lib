package com.crossbotics.ftchelper.examples.v2bot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.LightColor;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * The V2 robot, file 3 of 5: gamepad 2 (the operator). From the modules: RT intake on/off (held 1 s: in
 * even when the count says full), LT push out (hold), LB flywheel spin up/down, RB fire (hold: keep
 * firing), D-pad up/down speed trim, D-pad right the next shot table, left stick Y hood trim, right
 * stick X turret by hand, BACK turret following on/off, B claw open/close, D-pad left the wrist's
 * previous position, left stick button held 1 s piece count = 0.
 *
 * <p>Added here: A grab, Y score in a FLOWER, X stow the lifter (Actions.java), the lift by hand on the
 * right stick Y, a buzz when the turret is on target, and the light's colors.
 *
 * <p>Some module suggestions find their button taken by a part added earlier in Robot.java (the INIT
 * screen lists them, e.g. "Lift by hand: no button (G2 left stick Y is taken by Hood)"). That is the
 * library's check doing its job: one job per button. The jobs below give the lift its own stick.
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
        // The lift by hand. The interlock still holds it below its line while the turret points back.
        g2.rightStickY.use(robot.lift::manual).says("lift by hand");

        g2.rumbleWhenAimed();

        // The light: the first true rule wins, so the most important comes first.
        robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);     // last 20 s: go park
        robot.lights.show(LightColor.RED).when(() -> robot.turretLift.isHolding());   // the interlock holds a part back
        robot.lights.show(LightColor.GREEN).when(() -> robot.launcher.isSpinning() && robot.launcher.isReady());
        robot.lights.show(LightColor.BLUE).when(() -> robot.turret.followMode().isOn());
        robot.lights.otherwise(LightColor.PURPLE);
        robot.lights.show(LightColor.WHITE).whenCountGoesUp(() -> robot.launcher.part().getShotsFired());
    }
}
