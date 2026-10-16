// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.starter;

// "import" lines list the ready-made pieces this file uses.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.testing.HardwareCheckOpMode;

/**
 * STARTER KIT, file 6 of 6: test every device in the configuration, one at a time.
 * Run it after every wiring change and before every event. See docs/guides/testing.md.
 *
 * Controls (gamepad 1): D-pad up/down chooses a device. Motors: right stick Y = power (at most 50%).
 * Servos: D-pad right/left nudges it. Sensors: just read the screen. PUT THE ROBOT ON A BLOCK first.
 */
// @TeleOp puts it in the Driver Station's TeleOp list under the name "Hardware Check".
@TeleOp(name = "Hardware Check", group = "Starter")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// The whole program is already written in the library (HardwareCheckOpMode). "extends" means this
// class IS that program, so the body can stay empty: { } with nothing inside is on purpose.
public class HardwareCheck extends HardwareCheckOpMode {
}
