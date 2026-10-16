package io.github.jasoncross.ftchelper.hardware;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

/**
 * Starts the IMU (the hub's built-in turn sensor) in one line, and reads the heading.
 *
 * <p>The "heading" is which way the robot is pointing, in degrees.
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
 * Why heading is counterclockwise-positive while turning is clockwise-positive: docs/conventions.md.
 */
public final class ImuSetup {
    // "final": no other class can extend this one. It is only a box of helper methods.
    // Private constructor: nobody makes an ImuSetup object; call ImuSetup.get(...) instead.
    private ImuSetup() { }

    /**
     * Gets the IMU, tells it how the hub is mounted, and sets the heading to 0.
     * Call it once in INIT: starting the IMU takes a short moment.
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
        // Hw.get gives a clear message (with the real names) if "imu" is misspelled.
        IMU imu = Hw.get(hardwareMap, IMU.class, name);
        // Tell the IMU how the hub sits on the robot, so "turning left" really reads as left.
        // initialize(...) answers true if the sensor started.
        boolean ok = imu.initialize(new IMU.Parameters(new RevHubOrientationOnRobot(logo, usb)));
        if (!ok) {
            // Stop now, in INIT: driving with a dead turn sensor would make every turn wrong.
            throw new IllegalStateException("The IMU \"" + name + "\" did not start. Check that it is in the active "
                    + "configuration, then restart the robot.");
        }
        // Make the direction the robot faces right now count as heading 0.
        imu.resetYaw();
        return imu;
    }

    /**
     * The robot's heading in degrees, from -180 to 180. Turning left (counterclockwise) makes it bigger.
     * It is 0 where the robot pointed at the last {@code resetYaw()}.
     *
     * <p>"Yaw" is the sensor word for turning flat on the floor, like a spinning top.
     *
     * @param imu the IMU from {@link #get}
     * @return the heading in degrees, -180 to 180, counterclockwise = positive
     */
    public static double headingDeg(IMU imu) {
        return imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES);
    }
}
