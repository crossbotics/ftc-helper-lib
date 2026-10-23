package com.crossbotics.ftchelper.examples.v2bot;

import com.crossbotics.ftchelper.robot.Action;

/**
 * The V2 robot, file 4 of 5: the lifter's steps, in a safe order: swing the wrist out BEFORE lifting,
 * and fold it last. Every wait has a time limit. The interlock rule is always on.
 * more: https://crossbotics.com/ftc-helper/docs/guides/interlocks/#the-lifters-steps
 */
public class Actions {

    /** Grabs a ball from the tiles, ready to carry. The turret turns forward first (the interlock). */
    public static Action grab(Robot r) {
        // Was the turret following the goal? Then it follows again at the end.
        final boolean[] wasFollowing = {false};
        return Action.named("Grab")
                .then(() -> wasFollowing[0] = r.turret.followMode().isOn(),
                      () -> r.turret.aimAt(0),                           // forward: out of the claw's way
                      r.claw::open, () -> r.lift.goTo("pickup"), () -> r.wrist.goTo("pickup"))   // all at once
                // The wrist waits above 47 deg until the turret is out of the back.
                .waitUntil("down and open", () -> r.lift.isAtTarget() && r.wrist.isDone() && r.claw.isDone(), 3.0)
                .then(r.claw::close)
                .waitUntil("claw closed", r.claw::isDone, 1.0)          // hold it before moving
                .then(() -> r.wrist.goTo("carry"), () -> r.lift.goTo("stowed"))
                .waitUntil("wrist out", r.wrist::isDone, 1.0)
                .then(() -> { if (wasFollowing[0]) r.turret.followMode().on(); });
    }

    /** Lifts the ball over a FLOWER and lets it go. Drive there first: back to it, about 28 mm away. */
    public static Action scoreInFlower(Robot r) {
        return Action.named("Score in FLOWER")
                .then(() -> r.wrist.goTo("carry"))                       // swing out first
                .waitUntil("wrist out", r.wrist::isDone, 1.0)
                .then(() -> r.lift.goTo("flower"))
                .waitUntil("lift up", r.lift::isAtTarget, 4.0)
                .then(() -> r.wrist.goTo("flower"))
                .waitUntil("over the FLOWER", r.wrist::isDone, 1.0)
                .then(r.claw::open)
                .waitUntil("let go", r.claw::isDone, 1.0)
                .waitSeconds(0.3)                                        // the ball drops clear
                .then(() -> r.wrist.goTo("carry"))
                .waitUntil("wrist back", r.wrist::isDone, 1.0)
                .then(() -> r.lift.goTo("stowed"))
                .waitUntil("lift down", r.lift::isAtTarget, 3.0)
                // If the turret points back, the wrist waits at carry until it turns away (the interlock).
                .then(() -> r.wrist.goTo("folded"), r.claw::close);
    }

    /** Folds everything in (for the start of a match, or to drive under something): wrist, lift, claw. */
    public static Action stow(Robot r) {
        return Action.named("Stow lifter")
                .then(() -> r.wrist.goTo("carry"))
                .waitUntil("wrist clear", r.wrist::isDone, 1.0)
                .then(() -> r.lift.goTo("stowed"))
                .waitUntil("lift down", r.lift::isAtTarget, 3.0)
                .then(() -> r.wrist.goTo("folded"), r.claw::close);
    }
}
