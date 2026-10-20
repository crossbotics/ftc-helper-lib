package io.github.jasoncross.ftchelper.robot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.core.Profile;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import java.util.Arrays;
import java.util.List;

/**
 * The drivetrain in {@code Robot.java}: mecanum or standard (tank), its motor names, the motor and
 * wheel, and how the Control Hub is mounted (for the IMU, the turn sensor).
 *
 * <pre>{@code
 * drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
 *         .motor("gobilda-5203-312").wheelMm(96)
 *         .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
 * drive(Drive.tank("left_drive", "right_drive").motor("gobilda-5203-312").wheelMm(96));
 * }</pre>
 *
 * <p>Its numbers are in {@code Tunables.Drive}: {@code maxPower}, {@code practiceMaxPower} (the
 * PRACTICE profile's limit), {@code slowSpeed} (the slow trigger), and the autonomous ones
 * ({@code strafeCorrection}, {@code turnKp}, {@code turnKd}, {@code moveMinPower}) with
 * {@code calibrated = true} once the Drive Tuner is done (autonomous refuses to run before).
 *
 * <p>Motor directions: the left side is reversed by default (the usual way motors are mounted). If
 * pushing the stick forward drives backward or spins the robot, name the reversed motors with
 * {@link #reversed(String...)}.
 *
 * Docs: docs/modules/robot.md (section "Drive")
 */
public final class Drive {
    final DriveConfig.DriveType type;
    final String frontLeft, frontRight, backLeft, backRight;   // back names are null on a 2-motor tank
    String motorId = "gobilda-5203-312";
    double wheelMm = 96;
    double gearRatio = 1;
    String wheelId = null;                                      // parts catalog id, if given
    double trackMm = Double.NaN, wheelBaseMm = Double.NaN;      // for the simulator; NaN = not given
    String[] reversedNames = null;                              // null = the left side
    String imuName = "imu";
    LogoFacingDirection logo = LogoFacingDirection.UP;
    UsbFacingDirection usb = UsbFacingDirection.FORWARD;

    private Drive(DriveConfig.DriveType type, String fl, String fr, String bl, String br) {
        this.type = type;
        this.frontLeft = fl;
        this.frontRight = fr;
        this.backLeft = bl;
        this.backRight = br;
    }

    /** A four-wheel mecanum drive (it can strafe). */
    public static Drive mecanum(String frontLeft, String frontRight, String backLeft, String backRight) {
        return new Drive(DriveConfig.DriveType.MECANUM, frontLeft, frontRight, backLeft, backRight);
    }

    /** A two-motor standard (tank) drive, one motor per side. */
    public static Drive tank(String left, String right) {
        return new Drive(DriveConfig.DriveType.TANK, left, right, null, null);
    }

    /** A four-motor standard (tank) drive. */
    public static Drive tank(String frontLeft, String frontRight, String backLeft, String backRight) {
        return new Drive(DriveConfig.DriveType.TANK, frontLeft, frontRight, backLeft, backRight);
    }

    /**
     * The drive motor, by its parts-catalog id (see {@link Motors}). Default "gobilda-5203-312".
     *
     * @param id e.g. "gobilda-5203-312", "rev-hd-hex-20"
     */
    public Drive motor(String id) {
        Motors.get(id);   // stops here, with the list, on a typo
        motorId = id;
        return this;
    }

    /** The wheel diameter in millimetres (goBILDA mecanum: 96 or 104). Default 96. */
    public Drive wheelMm(double mm) {
        wheelMm = mm;
        return this;
    }

    /**
     * The wheel by its parts catalog id (see {@link Parts}): its diameter comes from the catalog.
     *
     * @param id e.g. "gobilda-hogback-96"
     */
    public Drive wheel(String id) {
        wheelMm = Parts.wheel(id).diameterMm;
        wheelId = id;
        return this;
    }

    /**
     * Where the wheels sit, for the simulator's turning: wheel centres side to side (track) and front
     * to back (wheel base), in mm.
     */
    public Drive wheelsAtMm(double trackMm, double wheelBaseMm) {
        this.trackMm = trackMm;
        this.wheelBaseMm = wheelBaseMm;
        return this;
    }

    /** Motor turns per wheel turn, if gears or a belt sit between them. Default 1. */
    public Drive gearRatio(double motorTurnsPerWheelTurn) {
        gearRatio = motorTurnsPerWheelTurn;
        return this;
    }

    /**
     * Exactly these motors run reversed (instead of the left side). Use it when the robot drives
     * the wrong way: docs/troubleshooting.md.
     *
     * @param motorNames configuration names of the reversed motors (none = no motor reversed)
     */
    public Drive reversed(String... motorNames) {
        reversedNames = motorNames.clone();
        return this;
    }

    /**
     * The IMU's name and how the Control Hub is mounted. Default "imu", logo UP, USB FORWARD.
     *
     * @param name the IMU's configuration name (usually "imu")
     * @param logo which way the REV logo on the hub faces
     * @param usb  which way the hub's USB port faces
     */
    public Drive imu(String name, LogoFacingDirection logo, UsbFacingDirection usb) {
        imuName = name;
        this.logo = logo;
        this.usb = usb;
        return this;
    }

    /** No IMU: TeleOp drives robot-centric only, and autonomous refuses to start. */
    public Drive noImu() {
        imuName = null;
        return this;
    }

    /** True for mecanum (it can strafe). */
    public boolean isMecanum() {
        return type == DriveConfig.DriveType.MECANUM;
    }

    /** Every drive motor name, front left first. */
    public List<String> motorNames() {
        return backLeft == null ? Arrays.asList(frontLeft, frontRight)
                : Arrays.asList(frontLeft, frontRight, backLeft, backRight);
    }

    // True if this motor runs reversed.
    boolean isReversed(String name) {
        if (reversedNames == null) return name.equals(frontLeft) || name.equals(backLeft);
        for (String n : reversedNames) if (n.equals(name)) return true;
        return false;
    }

    /** The settings EasyTeleOp and EasyAuto use, with the numbers from {@code Tunables.Drive}. */
    RobotConfig config(final TunableSection t) {
        // Read once here (not live): the drive is made in INIT. Each is still a Tunable, so the
        // tuning menu can change it and the next INIT uses the new value.
        final double maxPower = t.tunable("maxPower", 1.0, 0.05).range(0.1, 1).get();
        final double practiceMax = t.tunable("practiceMaxPower", 0.6, 0.05).range(0.1, 1).get();
        final double strafe = t.tunable("strafeCorrection", 1.15, 0.01).range(0.5, 2).get();
        final double turnKp = t.tunable("turnKp", 0.012, 0.001).range(0, 1).get();
        final double turnKd = t.tunable("turnKd", 0.003, 0.0005).range(0, 1).get();
        final double moveMin = t.tunable("moveMinPower", 0.30, 0.02).range(0, 1).get();
        final boolean calibrated = t.flag("calibrated", false);
        return new RobotConfig() {
            @Override public DriveConfig drive() {
                DriveConfig c = type == DriveConfig.DriveType.MECANUM ? DriveConfig.mecanum() : DriveConfig.tank();
                c.frontLeft = frontLeft;
                c.frontRight = frontRight;
                c.backLeft = backLeft;
                c.backRight = backRight;
                c.frontLeftReversed = isReversed(frontLeft);
                c.frontRightReversed = isReversed(frontRight);
                c.backLeftReversed = backLeft != null && isReversed(backLeft);
                c.backRightReversed = backRight != null && isReversed(backRight);
                c.powerLimit = Profile.pick(practiceMax, maxPower);
                return c;
            }

            @Override public AutoDriveConfig auto() {
                AutoDriveConfig c = new AutoDriveConfig();
                c.ticksPerRev = Motors.get(motorId).countsPerRev;
                c.wheelDiameterIn = wheelMm / 25.4;
                c.gearRatio = gearRatio;
                c.strafeCorrection = strafe;
                c.turnKp = turnKp;
                c.turnKd = turnKd;
                c.moveMinPower = moveMin;
                c.calibrationConfirmed = calibrated;
                return c;
            }

            @Override public IMU imu(HardwareMap hardwareMap) {
                return imuName == null ? null : ImuSetup.get(hardwareMap, imuName, logo, usb);
            }
        };
    }

    /** The slow trigger's speed from {@code Tunables.Drive.slowSpeed}. */
    static double slowSpeed(TunableSection t) {
        return t.tunable("slowSpeed", 0.3, 0.05).range(0.05, 1).get();
    }

    /** The simulator robot file's drive lines. */
    void simLines(List<String> out) {
        out.add("drive.type = " + (isMecanum() ? "mecanum" : "tank"));
        out.add("drive.part = " + motorId);
        out.add(Module.fmt("drive.wheelDiameterIn = %.3f", wheelMm / 25.4) + (wheelId != null ? "   # " + wheelId : ""));
        if (!Double.isNaN(trackMm)) {
            out.add("drive.trackWidthIn = " + Shape.n(trackMm / 25.4));
            out.add("drive.wheelBaseIn = " + Shape.n(wheelBaseMm / 25.4));
        }
        if (gearRatio != 1) out.add(Module.fmt("drive.gearRatio = %s", gearRatio));
        out.add("drive.motors = " + join(motorNames()));
        // Which side the code reverses; the simulator mirrors that side so forward is forward.
        boolean leftReversed = isReversed(frontLeft);
        boolean rightReversed = isReversed(frontRight);
        if (leftReversed != rightReversed) out.add("drive.mirrored = " + (leftReversed ? "left" : "right"));
        if (imuName != null) out.add("imu = " + imuName);
    }

    private static String join(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) sb.append(sb.length() == 0 ? "" : ", ").append(n);
        return sb.toString();
    }
}
