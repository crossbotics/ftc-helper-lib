package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.odometry.DeadWheelOdometry;
import com.crossbotics.ftchelper.odometry.OtosOdometry;
import com.crossbotics.ftchelper.odometry.PinpointOdometry;
import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.hardware.sparkfun.SparkFunOTOS;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

/**
 * The robot's odometry, one line in {@code Robot.java}: what tells the robot where it is on the field.
 *
 * <pre>{@code
 * odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));          // goBILDA Pinpoint, 4-bar pods
 * odometry(Odometry.otos("otos").mountIn(0, -3, 0));                     // SparkFun OTOS
 * odometry(Odometry.threeWheels("par_left", "par_right", "perp")          // three dead wheels
 *         .trackWidthIn(13.2).perpForwardIn(-6));
 * odometry(Odometry.twoWheels("par", "perp").parallelLeftIn(5).perpForwardIn(-6));   // two + the IMU
 * }</pre>
 *
 * <p>Then {@code robot.pose()} is the robot's place every loop, autonomous can drive to a field point
 * ({@code s.driveTo(...)}), and TeleOp starts where autonomous ended. Its numbers (the OTOS scales, and
 * {@code checked}) are in {@code Tunables.Odometry}. The Odometry Tuner finds the offsets.
 *
 * Docs: docs/modules/odometry.md
 */
public final class Odometry {
    enum Kind { PINPOINT, OTOS, THREE, TWO, CUSTOM }

    // goBILDA pods, in encoder counts per mm (the Pinpoint driver's own numbers).
    static final double FOUR_BAR_TICKS_PER_MM = 19.89436789;
    static final double SWINGARM_TICKS_PER_MM = 13.26291192;

    final Kind kind;
    final List<String> names;
    double xOffsetMm = 0, yOffsetMm = 0;              // Pinpoint
    double ticksPerMm = FOUR_BAR_TICKS_PER_MM;
    boolean swingarm = false, customPods = false;
    double forwardIn = 0, leftIn = 0, mountHeadingDeg = 0;   // OTOS
    double trackWidthIn = Double.NaN, parallelLeftIn = 0, perpForwardIn = 0;   // dead wheels
    final List<String> reversed = new ArrayList<>();

    private Odometry(Kind kind, String... names) {
        for (String n : names) {
            if (n == null || n.trim().isEmpty()) throw new IllegalArgumentException("Robot.java: an odometry device name is empty");
        }
        this.kind = kind;
        this.names = Arrays.asList(names);
    }

    // CUSTOM: makes the team's own PoseTracker at INIT.
    private java.util.function.Function<Devices, PoseTracker> custom;

    /**
     * Your own pose source (e.g. odometry fused with AprilTags, or another library's localizer), made at
     * INIT from the robot's devices. It must use the library's frame (x right, y away, heading 0 = away,
     * counterclockwise +). driveTo, robot.pose() and TeleOp use it like the built-in kinds.
     */
    public static Odometry custom(java.util.function.Function<Devices, PoseTracker> make) {
        if (make == null) throw new IllegalArgumentException("Robot.java: Odometry.custom(...) needs a maker");
        Odometry o = new Odometry(Kind.CUSTOM, "custom");
        o.custom = make;
        return o;
    }

    /** A goBILDA Pinpoint (I2C port), with goBILDA 4-bar pods unless you say otherwise. */
    public static Odometry pinpoint(String name) {
        return new Odometry(Kind.PINPOINT, name);
    }

    /** A SparkFun OTOS (I2C port). */
    public static Odometry otos(String name) {
        return new Odometry(Kind.OTOS, name);
    }

    /**
     * Three dead wheels on encoder ports: two that measure forward (left and right of the centre) and
     * one that measures sideways. The names are the motor ports' configuration names.
     */
    public static Odometry threeWheels(String leftParallel, String rightParallel, String perpendicular) {
        return new Odometry(Kind.THREE, leftParallel, rightParallel, perpendicular);
    }

    /** Two dead wheels (one forward, one sideways) and the Control Hub's IMU for the heading. */
    public static Odometry twoWheels(String parallel, String perpendicular) {
        return new Odometry(Kind.TWO, parallel, perpendicular);
    }

    /**
     * Pinpoint: where the pods sit, in mm (the Odometry Tuner measures them). {@code xPodLeftMm}: how
     * far LEFT of the robot's centre the forward-measuring pod is (right = negative);
     * {@code yPodForwardMm}: how far FORWARD of the centre the sideways-measuring pod is (back = negative).
     */
    public Odometry offsetsMm(double xPodLeftMm, double yPodForwardMm) {
        need(Kind.PINPOINT, "offsetsMm");
        xOffsetMm = xPodLeftMm;
        yOffsetMm = yPodForwardMm;
        return this;
    }

    /** goBILDA swingarm pods instead of 4-bar pods. */
    public Odometry podsSwingarm() {
        swingarm = true;
        ticksPerMm = SWINGARM_TICKS_PER_MM;
        return this;
    }

    /** Other pods: encoder counts per mm of wheel travel. */
    public Odometry ticksPerMm(double ticks) {
        if (!(ticks > 0)) throw new IllegalArgumentException("Robot.java: ticksPerMm must be more than 0");
        customPods = true;
        ticksPerMm = ticks;
        return this;
    }

    /**
     * Pods that count the wrong way (the Odometry Tuner says which): for a Pinpoint "x" and/or "y";
     * for dead wheels the motor port names.
     */
    public Odometry reversed(String... which) {
        // Added to what is there (two calls keep both). A name that is not a pod stops INIT (see build).
        for (String w : which) {
            String n = kind == Kind.PINPOINT && w != null ? w.toLowerCase(java.util.Locale.US) : w;
            if (!reversed.contains(n)) reversed.add(n);
        }
        return this;
    }

    // A reversed(...) name that names no pod: a typo that would quietly leave a pod counting backward.
    private void checkReversed() {
        for (String r : reversed) {
            boolean ok = kind == Kind.PINPOINT ? r.equals("x") || r.equals("y") : names.contains(r);
            if (!ok) {
                throw new IllegalStateException("Robot.java: odometry .reversed(\"" + r + "\"): "
                        + (kind == Kind.PINPOINT ? "a Pinpoint's pods are \"x\" and \"y\"" : "the pods are " + names));
            }
        }
    }

    /** OTOS: where it sits: inches forward of the centre, inches left, and its arrow's direction (CCW deg). */
    public Odometry mountIn(double forwardIn, double leftIn, double headingDeg) {
        need(Kind.OTOS, "mountIn");
        this.forwardIn = forwardIn;
        this.leftIn = leftIn;
        this.mountHeadingDeg = headingDeg;
        return this;
    }

    /** Three dead wheels: the distance between the left and right forward-measuring pods, inches. */
    public Odometry trackWidthIn(double inches) {
        need(Kind.THREE, "trackWidthIn");
        trackWidthIn = inches;
        return this;
    }

    /** Two dead wheels: how far LEFT of the centre the forward-measuring pod is, inches (right = negative). */
    public Odometry parallelLeftIn(double inches) {
        need(Kind.TWO, "parallelLeftIn");
        parallelLeftIn = inches;
        return this;
    }

    /** Dead wheels: how far FORWARD of the centre the sideways-measuring pod is, inches (back = negative). */
    public Odometry perpForwardIn(double inches) {
        if (kind != Kind.THREE && kind != Kind.TWO) throw new IllegalStateException("Robot.java: perpForwardIn is for dead wheels");
        perpForwardIn = inches;
        return this;
    }

    private void need(Kind k, String what) {
        if (kind != k) throw new IllegalStateException("Robot.java: " + what + "(...) is not for this kind of odometry (" + kindName() + ")");
    }

    /** "pinpoint", "otos", "threeWheels" or "twoWheels" (for the Odometry Tuner). */
    public String kindName() {
        switch (kind) {
            case CUSTOM: return "custom";
            case PINPOINT: return "pinpoint";
            case OTOS: return "otos";
            case THREE: return "threeWheels";
            default: return "twoWheels";
        }
    }

    /** The configuration names, in the order written. */
    public List<String> names() {
        return java.util.Collections.unmodifiableList(names);
    }

    /** Pod encoder counts per mm. */
    public double podTicksPerMm() {
        return ticksPerMm;
    }

    /** True if this pod ("x"/"y" for a Pinpoint, a port name for dead wheels) is in {@code reversed(...)}. */
    public boolean isReversed(String which) {
        return reversed.contains(which);
    }

    /** The configuration names it uses (checked with the rest at INIT). */
    List<String> deviceNames() {
        return names;
    }

    // Makes the real thing at INIT.
    PoseTracker build(Devices devices, TunableSection t, Drive drive) {
        checkReversed();
        switch (kind) {
            case CUSTOM: {
                PoseTracker p = custom.apply(devices);
                if (p == null) throw new IllegalStateException("Robot.java: Odometry.custom(...) made nothing (null)");
                return p;
            }
            case PINPOINT: {
                GoBildaPinpointDriver p = devices.get(GoBildaPinpointDriver.class, names.get(0));
                GoBildaPinpointDriver.GoBildaOdometryPods pods = customPods ? null
                        : swingarm ? GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_SWINGARM_POD
                        : GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD;
                return new PinpointOdometry(names.get(0), p, xOffsetMm, yOffsetMm, pods, ticksPerMm,
                        reversed.contains("x"), reversed.contains("y"));
            }
            case OTOS: {
                SparkFunOTOS o = devices.get(SparkFunOTOS.class, names.get(0));
                return new OtosOdometry(names.get(0), o, forwardIn, leftIn, mountHeadingDeg,
                        t.number("linearScalar", 1.0), t.number("angularScalar", 1.0));
            }
            case THREE: {
                if (Double.isNaN(trackWidthIn)) throw new IllegalStateException("Robot.java: threeWheels(...) needs .trackWidthIn(...)");
                return DeadWheelOdometry.threeWheels("odometry", encoder(devices, 0), encoder(devices, 1), encoder(devices, 2),
                        ticksPerMm * 25.4, trackWidthIn, perpForwardIn,
                        reversed.contains(names.get(0)), reversed.contains(names.get(1)), reversed.contains(names.get(2)));
            }
            default: {
                if (drive.imuName == null) throw new IllegalStateException("Robot.java: twoWheels(...) needs the IMU (drive(...).imu(...))");
                final IMU imu = devices.get(IMU.class, drive.imuName);
                imu.initialize(new IMU.Parameters(new RevHubOrientationOnRobot(drive.logo, drive.usb)));
                imu.resetYaw();
                return DeadWheelOdometry.twoWheels("odometry", encoder(devices, 0), encoder(devices, 1),
                        () -> imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES),
                        ticksPerMm * 25.4, parallelLeftIn, perpForwardIn,
                        reversed.contains(names.get(0)), reversed.contains(names.get(1)));
            }
        }
    }

    // A dead wheel's encoder: the motor port, read only (its motor, if any, is someone else's).
    private DcMotorEx encoder(Devices devices, int i) {
        return devices.get(DcMotorEx.class, names.get(i));
    }

    /** Lines for the simulator's robot file. */
    void simLines(List<String> out) {
        switch (kind) {
            case CUSTOM:
                out.add("# odometry: a custom pose source (the simulator has no pretend device for it)");
                return;
            case PINPOINT:
                out.add("odometry = pinpoint " + names.get(0));
                out.add(Module.fmt("odometry.offsetsMm = %s, %s", Shape.n(xOffsetMm), Shape.n(yOffsetMm)));
                break;
            case OTOS:
                out.add("odometry = otos " + names.get(0));
                out.add(Module.fmt("odometry.mountIn = %s, %s, %s", Shape.n(forwardIn), Shape.n(leftIn), Shape.n(mountHeadingDeg)));
                break;
            case THREE:
                out.add("odometry = threeWheels " + commaList(names));
                out.add("odometry.trackWidthIn = " + Shape.n(trackWidthIn));
                out.add("odometry.perpForwardIn = " + Shape.n(perpForwardIn));
                out.add(Module.fmt("odometry.ticksPerIn = %.3f", ticksPerMm * 25.4));
                break;
            default:
                out.add("odometry = twoWheels " + commaList(names));
                out.add("odometry.parallelLeftIn = " + Shape.n(parallelLeftIn));
                out.add("odometry.perpForwardIn = " + Shape.n(perpForwardIn));
                out.add(Module.fmt("odometry.ticksPerIn = %.3f", ticksPerMm * 25.4));
                break;
        }
        if (!reversed.isEmpty()) out.add("odometry.reversed = " + commaList(reversed));
    }

    // "a, b, c" (not String.join: the blocking check reads ".join(" as a thread wait).
    private static String commaList(java.util.List<String> items) {
        StringBuilder b = new StringBuilder();
        for (String item : items) b.append(b.length() == 0 ? "" : ", ").append(item);
        return b.toString();
    }
}
