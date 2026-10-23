// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.starter;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.testing.HardwareCheckOpMode;

/**
 * STARTER KIT, file 6 of 6: test every device in the configuration, one at a time.
 * Run it after every wiring change and before every event. See docs/guides/testing.md.
 *
 * Controls (gamepad 1): D-pad up/down chooses a device. Motors: right stick Y = power (at most 50%).
 * Servos: D-pad right/left nudges it. Sensors: just read the screen. PUT THE ROBOT ON A BLOCK first.
 */
@TeleOp(name = "Hardware Check", group = "Starter")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// The whole program is in the library (HardwareCheckOpMode): the empty { } is on purpose.
public class HardwareCheck extends HardwareCheckOpMode {
}
