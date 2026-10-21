package com.crossbotics.ftchelper.examples.andymarkbot;

import com.crossbotics.ftchelper.robot.Action;

/** AndyMark starter bot, file 4 of 5: steps that use several parts together. */
public class Actions {

    /**
     * Throws the pieces the robot holds (up to 4, the game's limit), one turn each. The hopper feeds
     * the next piece by gravity, so there is a short wait between throws (Tunables.Auto).
     */
    public static Action throwAll(Robot r) {
        Action a = Action.named("Throw all");
        for (int i = 0; i < 4; i++) {
            a.then(r.catapult::feedOne)
             .waitUntil("the catapult", () -> !r.catapult.isBusy(), 3.0)
             .waitSeconds(Tunables.Auto.secondsBetweenThrows);
        }
        return a;
    }
}
