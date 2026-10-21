package com.crossbotics.ftchelper.examples.tutorials;

// "import" lines tell Java where to find the classes we use.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.testing.HardwareCheckOpMode;

/**
 * Tutorial 19: the hardware check. The whole OpMode is these few lines: the library does the rest.
 *
 * It lists every device in the robot configuration and lets you test them one at a time with
 * gamepad 1: D-pad up/down chooses a device; for a motor, the right stick sets the power (half
 * power at most, unless you hold the right bumper); for a servo, the D-pad and bumpers move it;
 * sensors just show their readings. Put the robot on a block first, so the wheels cannot drive it
 * off the table. Use it after wiring changes, before every event, and when something "does not work".
 *
 * Before you use it: remove the {@code @Disabled} line. Nothing else to change: it reads the
 * names from the active configuration by itself.
 *
 * Tutorial: docs/tutorials/19-testing-procedures.md (steps: docs/guides/testing.md)
 */
// @TeleOp puts this program in the Driver Station's TeleOp list, with this name and group.
@TeleOp(name = "Tutorial 19: Hardware Check", group = "Tutorials")
// @Disabled hides it from the Driver Station. Delete this line to use the program.
@Disabled
// "extends HardwareCheckOpMode": this program IS the library's ready-made hardware check, so it
// gets all of its code. The empty { } means we add nothing of our own. A program must still be
// its own class in TeamCode, with its own @TeleOp name, for the Driver Station to list it.
public class Tut19HardwareCheck extends HardwareCheckOpMode {
}
