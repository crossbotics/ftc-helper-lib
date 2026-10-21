package com.crossbotics.ftchelper.examples.studicabot;

import com.crossbotics.ftchelper.robot.Action;

/** Studica starter bot, file 4 of 5: steps that use several parts together. */
public class Actions {

    /**
     * Fires the pieces the robot holds (up to 4, the game's limit): spins up, then pushes them in one
     * by one with the flap as soon as the wheel is at speed. It uses only the launcher, so the driver
     * can still run the intake.
     */
    public static Action fireAll(Robot r) {
        return Action.named("Fire all")
                .then(() -> r.launcher.fire(4))
                .waitUntil("the shots", () -> !r.launcher.isShooting(), 10.0);
    }
}
