package com.crossbotics.ftchelper.pedro;

import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.localization.Localizer;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Pedro's own tuners (the "procedures" folder from Pedro's Quickstart) on Robot.java's odometry and
 * drive lines, so nothing is set up twice. In the Quickstart's Tuning.java:
 *
 * <pre>
 * &#64;Tuner
 * public static Procedure foresightTuner() {
 *     return new ForesightTuner(PedroTuning.localizer(Robot::new), PedroTuning.drivetrain(Robot::new));
 * }
 * </pre>
 *
 * Tune the odometry first, with the library's Odometry Tuner. Docs: docs/modules/pedro.md
 */
public final class PedroTuning {
    private PedroTuning() { }

    /** A Pedro Localizer that reads Robot.java's odometry(...) line by itself (a new robot each time). */
    public static Function<HardwareMap, Localizer> localizer(final Supplier<? extends RobotDefinition> robot) {
        return hardwareMap -> PedroLocalizer.readingItself(robot.get().makeOdometry(hardwareMap));
    }

    /** Pedro's Mecanum drivetrain with Robot.java's drive(...) names and directions. */
    public static Function<HardwareMap, Drivetrain> drivetrain(final Supplier<? extends RobotDefinition> robot) {
        return hardwareMap -> new Mecanum(hardwareMap, Pedro.mecanumConfig(robot.get().drive()));
    }
}
