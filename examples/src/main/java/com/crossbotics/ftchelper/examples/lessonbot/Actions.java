package com.crossbotics.ftchelper.examples.lessonbot;

import com.crossbotics.ftchelper.robot.Action;

/**
 * The lesson robot, file 4 of 5: moves that use several parts (lesson 5). A button starts one in
 * TeleOp (press again to cancel); an autonomous uses the same one as a step.
 */
public class Actions {

    /** Lift up, open the claw at the top, then lift down. */
    public static Action scoreHigh(Robot r) {
        return Action.named("Score high")
                .then(() -> r.lift.goTo("high"))
                .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)   // gives up after 2 s: the claw stays shut
                .then(r.claw::open)
                .waitSeconds(0.3)
                .then(() -> r.lift.goTo("down"));
    }

    /** Close the claw on a piece, then lift it a little off the tiles. */
    public static Action grabAndLift(Robot r) {
        return Action.named("Grab and lift")
                .then(r.claw::close)
                .waitUntil("claw closed", r.claw::isDone, 1.0)
                .then(() -> r.lift.goTo("low"));
    }
}
