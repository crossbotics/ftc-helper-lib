package com.crossbotics.ftchelper.examples.flywheelbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * Example robot 1, file 3 of 5: the buttons, and the TeleOp itself.
 *
 * <p>Each part already brings its standard buttons, so this robot needs almost nothing here:
 * <ul>
 *   <li>Gamepad 1 drives: left stick, right stick X turns, LT slow. Added here: RB switches
 *       whole-robot aim on and off: the robot turns to face the goal by itself while the driver
 *       drives forward and back (moving the turn stick still turns it by hand).</li>
 *   <li>Gamepad 2, from the modules: RT intake on/off (it is on from the start), LT push pieces out
 *       while held, LB flywheel spin up/down, RB fire one (hold: keep firing), D-pad up/down launcher speed +/- 100 RPM.</li>
 * </ul>
 * The Driver Station shows the whole map during INIT.
 */
@TeleOp(name = "FlywheelBot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    // true: one driver does everything on gamepad 1 (the operator's buttons move there).
    public static boolean oneDriver = false;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // RB: whole-robot aim on/off (two buzzes = on, one = off). Numbers: Tunables.Aim.
        g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();
        // The operator fires: gamepad 2 buzzes while the robot points at the goal, the closer the stronger.
        g2.rumbleWhenAimed();
        // Y: fire everything the robot holds (an action from Actions.java; press again to cancel).
        g2.y.press(Actions.fireThree(robot)).says("fire three");
        // Free buttons stay free until you need them; the help page lists them.
        g2.a.free();
        g2.x.free();
        g2.b.free();
    }
}
