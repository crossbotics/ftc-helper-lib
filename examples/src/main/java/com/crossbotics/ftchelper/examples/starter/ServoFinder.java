// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.starter;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.finders.ServoFinderOpMode;

/**
 * STARTER KIT finder: finds a servo's safe ends and positions (claw open/closed...).
 * Nothing to fill in: it lists the devices in the robot configuration. It shows the code lines to
 * copy, and saves them on the hub (/sdcard/FIRST/ftc-helper/finder-results.txt) when you press STOP.
 * How to use it: docs/guides/finders.md.
 */
@TeleOp(name = "Servo Finder", group = "Finders")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
public class ServoFinder extends ServoFinderOpMode {
}
