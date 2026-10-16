package io.github.jasoncross.ftchelper.core;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;

/**
 * The robot's drivetrain described ONCE: motor names and directions, autonomous tuning, and how the
 * Control Hub is mounted. {@code EasyTeleOp} and {@code EasyAuto} both read it, so TeleOp and every
 * autonomous always match. Change a motor name here and every program gets it.
 *
 * <pre>{@code
 * public class RobotSettings extends RobotConfig {
 *     @Override public DriveConfig drive() {
 *         DriveConfig c = DriveConfig.mecanum();
 *         c.frontLeft = "front_left";   // ... the other names
 *         return c;
 *     }
 *     @Override public AutoDriveConfig auto() { ... }     // optional: only for autonomous
 *     @Override public IMU imu(HardwareMap hardwareMap) {
 *         return ImuSetup.get(hardwareMap, "imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD);
 *     }
 * }
 * }</pre>
 *
 * <p>The mechanisms (lift, claw, ...) are not in here: they go in your own Robot class
 * (see docs/starter-kit.md), because your programs need to reach them by name.
 *
 * <p>"abstract" means you cannot use this class as it is: you make your own class that
 * {@code extends} it and fills in the methods marked abstract.
 *
 * Docs: docs/modules/core.md (section "RobotConfig")
 */
public abstract class RobotConfig {

    /** The drivetrain: type, motor names, directions, power limit. Called each time a program starts. */
    public abstract DriveConfig drive();

    /**
     * Autonomous tuning (wheel size, encoder counts, ...). Only autonomous uses it. The default is an
     * untuned config, which autonomous refuses to run with, on purpose, until you tune it (tutorial 09).
     */
    public AutoDriveConfig auto() {
        return new AutoDriveConfig();
    }

    /** The IMU (turn sensor), set up for how the hub is mounted. Usually one {@code ImuSetup.get(...)} line. */
    public abstract IMU imu(HardwareMap hardwareMap);
}
