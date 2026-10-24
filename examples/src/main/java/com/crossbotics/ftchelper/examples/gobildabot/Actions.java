package com.crossbotics.ftchelper.examples.gobildabot;

import com.crossbotics.ftchelper.robot.Action;

/**
 * goBILDA starter bot, file 4 of 5: steps that use several parts together.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/01-robot-in-five-files/#4-actionsjava-steps-that-use-several-parts
 */
public class Actions {

    /** Fires the four preloaded pieces (every robot starts with 4: manual G304.G). Launcher only, so the driver can run the intake (RT) too. */
    public static Action fireAll(Robot r) {
        return Action.named("Fire all")
                .then(() -> r.launcher.fire(4))
                .waitUntil("the shots", () -> !r.launcher.isShooting(), 8.0);
    }

    /** For the autonomous: the intake helps push the pieces in while they are fired. */
    public static Action fireAllWithIntake(Robot r) {
        return Action.named("Fire all")
                .then(r.intake::in, () -> r.launcher.fire(4))
                .waitUntil("the shots", () -> !r.launcher.isShooting(), 8.0)
                .then(r.intake::stop);
    }
}
