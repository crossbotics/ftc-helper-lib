package org.octobots.ftchelper.hardware;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

/**
 * Starts the IMU (the hub's built-in turn sensor) in one line, and reads the heading.
 *
 * <pre>{@code
 * IMU imu = ImuSetup.get(hardwareMap, "imu",
 *         RevHubOrientationOnRobot.LogoFacingDirection.UP,
 *         RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
 * double heading = ImuSetup.headingDeg(imu);   // counterclockwise is positive
 * }</pre>
 *
 * <p>You MUST give the directions the hub's logo and USB port face on YOUR robot.
 * If they are wrong, the heading is wrong. See docs/modules/hardware.md for a picture.
 */
public final class ImuSetup {
    private ImuSetup() { }

    /**
     * Gets the IMU, tells it how the hub is mounted, and sets the heading to 0.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @param name        the IMU's configuration name (usually "imu")
     * @param logo        which way the REV logo on the hub faces
     * @param usb         which way the hub's USB port faces
     * @return the IMU, ready to read
     * @throws IllegalStateException if the IMU does not start 
     */
    public static IMU get(HardwareMap hardwareMap, String name,
                          RevHubOrientationOnRobot.LogoFacingDirection logo,
                          RevHubOrientationOnRobot.UsbFacingDirection usb) {
        IMU imu = Hw.get(hardwareMap, IMU.class, name);
        boolean ok = imu.initialize(new IMU.Parameters(new RevHubOrientationOnRobot(logo, usb)));
        if (!ok) {
            throw new IllegalStateException("The IMU \"" + name + "\" did not start. Check that it is in the active "
                    + "configuration, then restart the robot.");
        }
        imu.resetYaw();
        return imu;
    }

    /**
     * The robot's heading in degrees, from -180 to 180. Turning left (counterclockwise) makes it bigger.
     * It is 0 where the robot pointed at the last {@code resetYaw()}.
     */
    public static double headingDeg(IMU imu) {
        return imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES);
    }
}
