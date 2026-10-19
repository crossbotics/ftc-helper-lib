package io.github.jasoncross.ftchelper.examples.gobildabot;

import io.github.jasoncross.ftchelper.robot.Action;

/** goBILDA starter bot, file 4 of 5: steps that use several parts together. */
public class Actions {

    /**
     * Fires the three preloaded pieces: spins up, then feeds them one by one as soon as the wheel is at
     * speed. It uses only the launcher, so the driver can still run the intake (RT) to help push pieces
     * in, as goBILDA's code does while it launches. (An action that also ran the intake would stop the
     * moment the driver touched RT: the driver always wins.)
     */
    public static Action fireAll(Robot r) {
        return Action.named("Fire all")
                .then(() -> r.launcher.fire(3))
                .waitUntil("the shots", () -> !r.launcher.isShooting(), 8.0);
    }

    /** For the autonomous: the intake helps push the pieces in while they are fired. */
    public static Action fireAllWithIntake(Robot r) {
        return Action.named("Fire all")
                .then(r.intake::in, () -> r.launcher.fire(3))
                .waitUntil("the shots", () -> !r.launcher.isShooting(), 8.0)
                .then(r.intake::stop);
    }
}
