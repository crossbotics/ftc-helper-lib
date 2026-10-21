package com.crossbotics.ftchelper.examples.flywheelbot;

import com.crossbotics.ftchelper.robot.Action;

/**
 * Example robot 1, file 4 of 5: steps that use parts together, written once and used by a button,
 * an autonomous, or at START.
 *
 * <p>An action never waits: it does a little every loop, so the robot keeps driving while it runs.
 * Moving one of its parts by hand stops it (the driver always wins).
 */
public class Actions {

    /**
     * Pushes the intake down into its working position, then pulls the servo back out of the way.
     * Runs once at START (see Robot.java's atStart).
     */
    public static Action deployIntake(Robot r) {
        return Action.named("Deploy intake")
                .then(() -> r.intakeDeploy.goTo("deployed"))   // push it down
                // A servo cannot say where it is: isDone() guesses from Tunables.IntakeDeploy.travelSec.
                .waitUntil("the deploy servo", r.intakeDeploy::isDone, 2.0)
                .then(() -> r.intakeDeploy.goTo("stowed"));    // and back out of the way
    }

    /** Fires every piece the robot holds (three in a full robot), one after another. */
    public static Action fireThree(Robot r) {
        return Action.named("Fire three")
                .then(() -> r.launcher.fire(3))
                // isShooting() is true until the last piece is fed. 6 s is plenty for three.
                .waitUntil("the shots", () -> !r.launcher.isShooting(), 6.0);
    }
}
