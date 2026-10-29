package com.crossbotics.ftchelper.examples.flywheelbot;

import com.crossbotics.ftchelper.robot.Action;

/**
 * Example robot 1, file 4 of 5: steps that use parts together, used by a button, an autonomous,
 * or at START.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/05-actions/
 */
public class Actions {

    /** Pushes the intake down into its working position, then pulls the servo back. Runs at START. */
    public static Action deployIntake(Robot r) {
        return Action.named("Deploy intake")
                .then(() -> r.intakeDeploy.goTo("deployed"))   // push it down
                // isDone() guesses from Tunables.IntakeDeploy.travelSec (a servo cannot say where it is).
                .waitUntil("the deploy servo", r.intakeDeploy::isDone, 2.0)
                .then(() -> r.intakeDeploy.goTo("stowed"));    // and back out of the way
    }
}
