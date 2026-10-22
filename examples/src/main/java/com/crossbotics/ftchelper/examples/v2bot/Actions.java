package com.crossbotics.ftchelper.examples.v2bot;

import com.crossbotics.ftchelper.robot.Action;

/**
 * The V2 robot, file 4 of 5: the lifter's steps, in a safe order. Each wait lets a part get there
 * (a servo: about its travelSec in Tunables; the lift: its encoder says it arrived). Every wait has a
 * time limit, so a stuck part never stops the robot for good: the action ends and says which step
 * timed out.
 *
 * The order matters (team 8938's lifter design):
 * 1. Pick up with the slide down and the wrist 20 deg out, so the ball is not under the robot.
 * 2. Swing the wrist out to 90 deg BEFORE lifting, then lift, then tip the wrist over the FLOWER.
 * 3. Coming back: wrist to 90, lift down, then fold.
 *
 * The interlock rule (Robot.java) is always on: while the turret points back, the wrist waits above
 * 47 degrees by itself (the claw would hit the turret lower down). To not wait, {@link #grab} first
 * turns the turret forward; scoring needs nothing (the wrist is out at 90 and more, where the turret
 * may point anywhere). {@link #stow} folds the wrist last: it waits there if the turret points back.
 */
public class Actions {

    /**
     * Reaches down, opens, grabs a ball from the tiles and holds it, ready to carry. The wrist at pick-up
     * (20 deg) is in its interlock zone, so the turret turns forward first; it follows the goal again
     * once the wrist is back out at carry.
     */
    public static Action grab(Robot r) {
        // Was the turret following the goal? Then it follows again at the end.
        final boolean[] wasFollowing = {false};
        return Action.named("Grab")
                .then(() -> wasFollowing[0] = r.turret.followMode().isOn(),
                      () -> r.turret.aimAt(0),                           // forward: out of the claw's way
                      r.claw::open, () -> r.lift.goTo("pickup"), () -> r.wrist.goTo("pickup"))   // all at once
                // The wrist waits above 47 deg until the turret is out of the back (Driver Station:
                // "TurretClaw : Wrist held at ...").
                .waitUntil("down and open", () -> r.lift.isAtTarget() && r.wrist.isDone() && r.claw.isDone(), 3.0)
                .then(r.claw::close)
                .waitUntil("claw closed", r.claw::isDone, 1.0)          // hold it before moving
                .then(() -> r.wrist.goTo("carry"), () -> r.lift.goTo("stowed"))
                .waitUntil("wrist out", r.wrist::isDone, 1.0)
                .then(() -> { if (wasFollowing[0]) r.turret.followMode().on(); });
    }

    /**
     * Lifts the ball over a FLOWER, lets it go, and comes back down folded. Drive the robot to the
     * FLOWER first (back to it, about 28 mm away: the lifter design's release point).
     */
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
                // Folding goes into the wrist's interlock zone: if the turret points back, the wrist
                // waits at carry until it turns away.
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
