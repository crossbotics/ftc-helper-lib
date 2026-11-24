package com.crossbotics.ftchelper.launcher;

import com.qualcomm.robotcore.hardware.DcMotorEx;

/** For tests in other packages: a pretend follower motor on a flywheel (no hardware map). */
public final class FlywheelTestAccess {
    private FlywheelTestAccess() { }

    /** Puts a pretend follower on the flywheel, as {@code follower(hardwareMap, name, reversed)} would. */
    public static void attachFollower(Flywheel f, DcMotorEx follower) {
        f.attachFollowerForTests(follower);
    }
}
