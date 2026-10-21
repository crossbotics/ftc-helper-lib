package com.crossbotics.ftchelper.examples.revbot;

import com.crossbotics.ftchelper.robot.Action;

/** REV starter bot, file 4 of 5: steps that use several parts together. */
public class Actions {

    /**
     * Fires the pieces the robot holds (up to 4, the game's limit): spins up, then feeds them one by
     * one as soon as the wheel is at speed. It uses only the launcher, so the driver can still run the
     * intake. If the robot holds fewer, the feeder only turns a few more times.
     */
    public static Action fireAll(Robot r) {
        return Action.named("Fire all")
                .then(() -> r.launcher.fire(4))
                .waitUntil("the shots", () -> !r.launcher.isShooting(), 10.0);
    }

    /** Combs POLLEN out of a FLOWER: poker down, wait for it, poker up. */
    public static Action comb(Robot r) {
        return Action.named("Comb a FLOWER")
                .then(() -> r.poker.goTo("down"))
                .waitUntil("the poker", r.poker::isDone, 2.0)
                .waitSeconds(0.3)
                .then(() -> r.poker.goTo("up"));
    }
}
