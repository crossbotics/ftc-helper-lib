package com.crossbotics.ftchelper.examples.turretbot;

import com.crossbotics.ftchelper.robot.Action;

/**
 * Example robot 2, file 4 of 5: the claw's steps.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/05-actions/
 */
public class Actions {

    /** Folds out, grabs a piece, and folds back in with it. */
    public static Action grab(Robot r) {
        return Action.named("Grab")
                .then(() -> r.clawFold.goTo("grab"), r.claw::open)   // both at once
                // isDone(): the servo should be there by now (its travelSec in Tunables).
                .waitUntil("folded out", r.clawFold::isDone, 2.0)
                .then(r.claw::close)
                .waitUntil("claw closed", r.claw::isDone, 1.0)       // hold it before moving
                .then(() -> r.clawFold.goTo("stowed"));
    }

    /** Lifts the claw over a FLOWER, lets the piece go, and folds back in. */
    public static Action releaseIntoFlower(Robot r) {
        return Action.named("Release into FLOWER")
                .then(() -> r.clawFold.goTo("flower"))
                .waitUntil("over the FLOWER", r.clawFold::isDone, 2.0)
                .then(r.claw::open)
                .waitUntil("claw open", r.claw::isDone, 1.0)
                .then(() -> r.clawFold.goTo("stowed"));
    }
}
