package io.github.jasoncross.ftchelper.examples.gobildabot;

import io.github.jasoncross.ftchelper.robot.Action;

/** goBILDA starter bot, file 4 of 5: steps that use several parts together. */
public class Actions {

    /**
     * Fires the three preloaded pieces: spins up, then feeds them one by one as soon as the wheel is at
     * speed. The intake helps push them in (goBILDA's code does the same while it launches).
     */
    public static Action fireAll(Robot r) {
        return Action.named("Fire all")
                .then(r.intake::in, () -> r.launcher.fire(3))
                .waitUntil("the shots", () -> !r.launcher.isShooting(), 8.0)
                .then(r.intake::stop);
    }
}
