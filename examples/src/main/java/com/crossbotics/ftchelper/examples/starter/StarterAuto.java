// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.starter;

import com.crossbotics.ftchelper.auto.EasyAuto;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;

/**
 * STARTER KIT, file 4 of 6: what every autonomous shares. Each real autonomous (file 5) extends
 * this and only lists its steps. EasyAuto does the rest: the INIT menu, the 30-second timer,
 * mirroring for RED, and stopping everything at the end.
 * more: https://crossbotics.com/ftc-helper/docs/guides/easy-autos/
 */
// Not a whole program on its own ("abstract"): the autonomous files extend it and add the steps.
public abstract class StarterAuto extends EasyAuto {
    // The mechanisms, for the autonomous files (robot.lift, robot.claw...). Filled in during INIT.
    protected Robot robot;

    // The robot's settings (drive, autonomous tuning, IMU): the same as TeleOp's.
    @Override protected RobotConfig robotConfig() { return new RobotSettings(); }

    // The mechanisms: EasyAuto updates them during every step and stops them all at the end.
    @Override
    protected Subsystems createSubsystems() {
        robot = new Robot(hardwareMap);
        return robot.all;
    }
}
