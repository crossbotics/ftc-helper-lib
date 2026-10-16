// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.starter;

// "import" lines list the ready-made pieces this file uses.
import io.github.jasoncross.ftchelper.auto.EasyAuto;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.core.Subsystems;

/**
 * STARTER KIT, file 4 of 6: what every autonomous shares. Each real autonomous (file 5) extends
 * this and only lists its steps. Nothing to change here for a new season except the robot class.
 *
 * EasyAuto (the library class this builds on) does the rest: the INIT menu (alliance, start delay,
 * profile), the 30-second timer, mirroring for RED, and stopping everything at the end.
 * See docs/guides/easy-autos.md.
 */
// "abstract" = this is not a whole program on its own (it has no steps and no @Autonomous), so it
// never shows on the Driver Station. Real autonomous files "extend" it and fill in the steps.
// "extends EasyAuto" = it gets everything EasyAuto already does.
public abstract class StarterAuto extends EasyAuto {
    // The robot's mechanisms. "protected" = the autonomous files built on this one can use it
    // (as robot.lift, robot.claw...). It is filled in by createSubsystems() below, during INIT.
    protected Robot robot;

    // EasyAuto asks for two things. "@Override" = "this is my answer to a method EasyAuto asks for".
    // The robot's settings (drive, autonomous tuning, IMU): the SAME object type TeleOp uses,
    // so every autonomous matches TeleOp.
    @Override protected RobotConfig robotConfig() { return new RobotSettings(); }

    // The mechanisms. EasyAuto keeps every one of them working during every step (for example
    // the lift keeps holding its height while the robot drives), and stops them all at the end.
    @Override
    protected Subsystems createSubsystems() {
        robot = new Robot(hardwareMap);
        return robot.all;
    }
}
