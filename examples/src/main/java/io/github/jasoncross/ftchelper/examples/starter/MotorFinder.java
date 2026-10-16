// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.starter;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.finders.MotorFinderOpMode;

/**
 * STARTER KIT finder: finds a lift's, arm's or turret's limits, positions and top speed.
 * Nothing to fill in: it lists the devices in the robot configuration. It shows the code lines to
 * copy, and saves them on the hub (/sdcard/FIRST/ftc-helper/finder-results.txt) when you press STOP.
 * How to use it: docs/guides/finders.md.
 */
@TeleOp(name = "Motor Finder", group = "Finders")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
public class MotorFinder extends MotorFinderOpMode {
}
