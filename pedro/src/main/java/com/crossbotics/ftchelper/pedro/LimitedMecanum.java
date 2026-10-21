package com.crossbotics.ftchelper.pedro;

import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.drivetrains.MecanumConfig;
import com.qualcomm.robotcore.hardware.HardwareMap;

/**
 * Pedro's Mecanum, with the robot's drive power limit (Tunables.Drive maxPower, or practiceMaxPower
 * in the PRACTICE profile): no wheel gets more than the limit, as with every other drive in the library.
 */
class LimitedMecanum extends Mecanum {
    private final double limit;

    LimitedMecanum(HardwareMap hardwareMap, MecanumConfig config, double limit) {
        super(hardwareMap, config);
        this.limit = Math.max(0, Math.min(1, limit));
    }

    @Override
    public void drive(DrivePowers p, boolean manual) {
        if (limit < 1) {
            // The biggest wheel power Pedro asks for; scale all three parts so it is at most the limit.
            double max = 0;
            for (double w : computeWheelPowersUnnormalized(p)) max = Math.max(max, Math.abs(w));
            double s = limit / Math.max(1, max);
            p = new DrivePowers(p.forward() * s, p.strafe() * s, p.turn() * s);
        }
        super.drive(p, manual);
    }
}
