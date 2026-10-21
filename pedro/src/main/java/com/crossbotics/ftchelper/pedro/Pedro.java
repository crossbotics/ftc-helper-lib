package com.crossbotics.ftchelper.pedro;

import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.PathFollower;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.drivetrains.MecanumConfig;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Pedro Pathing in Robot.java. One line, next to {@code odometry(...)}:
 *
 * <pre>
 * odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
 * follower(Pedro.follower(PedroConstants.foresight));
 * </pre>
 *
 * The drive motors come from the {@code drive(...)} line (names and which ones are reversed). The pose
 * comes from the {@code odometry(...)} line. No wheel gets more than the drive's power limit
 * ({@code Tunables.Drive.maxPower}, or {@code practiceMaxPower} in the PRACTICE profile). {@code PedroConstants.foresight} holds the numbers from
 * Pedro's own tuning OpModes. Docs: docs/modules/pedro.md
 */
public final class Pedro {
    private Pedro() { }

    /** Pedro's Foresight follower on the robot's mecanum drive and odometry. */
    public static PathFollower.Maker follower(final ForesightConfig foresight) {
        if (foresight == null) throw new IllegalArgumentException("Pedro.follower: the ForesightConfig is null");
        return (devices, robot) -> {
            final Drive d = robot.drive();
            if (d == null || !d.isMecanum()) {
                throw new IllegalStateException("Robot.java: Pedro.follower needs a mecanum drive(...) line");
            }
            final MecanumConfig mc = mecanumConfig(d);
            final double limit = robot.robotConfig().drive().powerLimit;
            return follower((hw, localizer) -> {
                // Pedro's Mecanum sets the motors to coast (FLOAT) when made: keep the robot's setting.
                DcMotor.ZeroPowerBehavior before = hw.get(DcMotor.class, d.motorNames().get(0)).getZeroPowerBehavior();
                Mecanum m = new LimitedMecanum(hw, mc, limit);
                m.setZeroPowerBehavior(before);
                return new Follower(localizer, m, new Foresight(foresight));
            }).make(devices, robot);
        };
    }

    /**
     * Your own Pedro Follower (another drivetrain or algorithm). You get the hardwareMap and a
     * Localizer that reads the robot's odometry; give back a Follower made with that Localizer.
     */
    public static PathFollower.Maker follower(final BiFunction<HardwareMap, Localizer, Follower> make) {
        return (devices, robot) -> {
            HardwareMap hw = devices.hardwareMap();
            if (hw == null) throw new IllegalStateException("Pedro.follower: no hardwareMap here");
            PedroLocalizer localizer = new PedroLocalizer(robot.odometry());
            Follower f = make.apply(hw, localizer);
            if (f == null) throw new IllegalStateException("Pedro.follower: made no Follower (null)");
            if (f.localizer != localizer) {
                throw new IllegalStateException("Pedro.follower: make the Follower with the Localizer you are given");
            }
            List<DcMotor> motors = new ArrayList<>();
            Drive d = robot.drive();
            if (d != null) for (String name : d.motorNames()) motors.add(hw.get(DcMotor.class, name));
            return new PedroFollower(f, localizer, motors);
        };
    }

    /**
     * Pedro's own Follower on this robot (after setup), for what this module does not cover (its debug
     * data, its settings). Null if the robot has no Pedro follower line.
     */
    public static Follower of(RobotDefinition robot) {
        return robot.follower() instanceof PedroFollower ? ((PedroFollower) robot.follower()).pedro() : null;
    }

    /** Pedro's MecanumConfig from Robot.java's mecanum drive(...) line: the names and directions. */
    public static MecanumConfig mecanumConfig(final Drive d) {
        if (d == null || !d.isMecanum()) throw new IllegalStateException("Robot.java: Pedro needs a mecanum drive(...) line");
        final List<String> n = d.motorNames();
        return new MecanumConfig(c -> {
            c.frontLeftName.set(n.get(0));
            c.frontRightName.set(n.get(1));
            c.backLeftName.set(n.get(2));
            c.backRightName.set(n.get(3));
            c.frontLeftDirection.set(dir(d, n.get(0)));
            c.frontRightDirection.set(dir(d, n.get(1)));
            c.backLeftDirection.set(dir(d, n.get(2)));
            c.backRightDirection.set(dir(d, n.get(3)));
        });
    }

    private static DcMotorSimple.Direction dir(Drive d, String name) {
        return d.isReversed(name) ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD;
    }
}
