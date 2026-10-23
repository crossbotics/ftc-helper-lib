package com.crossbotics.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.testing.HardwareCheckOpMode;

/**
 * Tutorial 19: the hardware check. The whole OpMode is these few lines: the library does the rest.
 * It lists every device in the active configuration; test them one at a time with gamepad 1.
 * Put the robot on a block first. Use it after wiring changes, before every event.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/19-testing-procedures/
 */
@TeleOp(name = "Tutorial 19: Hardware Check", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut19HardwareCheck extends HardwareCheckOpMode {
}
