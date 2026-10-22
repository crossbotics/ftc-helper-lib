package com.crossbotics.ftchelper.examples.v2bot;

import com.crossbotics.ftchelper.robot.Action;

/**
 * The V2 robot, file 4 of 5: the lifter's steps, in a safe order. Each wait lets a part get there
 * (a servo: about its travelSec in Tunables; the lift: its encoder says it arrived). Every wait has a
 * time limit, so a stuck part never stops the robot for good: the action ends and says which step
 * timed out.
 *
 * <p>The order matters (team 8938's lifter design):
 * <ol>
 *   <li>Pick up with the slide down and the wrist 20 deg out, so the ball is not under the robot.</li>
 *   <li>Swing the wrist out to 90 deg BEFORE lifting, then lift, then tip the wrist over the FLOWER.</li>
 *   <li>Coming back: wrist to 90, lift down, then fold.</li>
 * </ol>
 *
 * <p>The interlock rule (Robot.java) is always on: if the turret points back, the lift waits below its
 * line by itself. To not wait, {@link #scoreInFlower} first turns the turret forward.
 */
public class Actions {

    /** Reaches down, opens, grabs a ball from the tiles and holds it, ready to carry. */
    public static Action grab(Robot r) {
        return Action.named("Grab")
                .then(r.claw::open, () -> r.lift.goTo("pickup"), () -> r.wrist.goTo("pickup"))   // all at once
                .waitUntil("down and open", () -> r.lift.isAtTarget() && r.wrist.isDone() && r.claw.isDone(), 2.0)
                .then(r.claw::close)
                .waitUntil("claw closed", r.claw::isDone, 1.0)          // hold it before moving
                .then(() -> r.wrist.goTo("carry"), () -> r.lift.goTo("stowed"));
    }

    /**
     * Lifts the ball over a FLOWER, lets it go, and comes back down folded. Drive the robot to the
     * FLOWER first (back to it, about 28 mm away: the lifter design's release point).
     */
    public static Action scoreInFlower(Robot r) {
        // Was the turret following the goal? Then it follows again once the lift is up (the interlock
        // keeps it out of the back while the lift is up, so following is safe).
        final boolean[] wasFollowing = {false};
        return Action.named("Score in FLOWER")
                .then(() -> wasFollowing[0] = r.turret.followMode().isOn(),
                      () -> r.turret.aimAt(0),                           // forward: out of the lift's way
                      () -> r.wrist.goTo("carry"))                       // swing out first
                .waitUntil("wrist out", r.wrist::isDone, 1.0)
                .then(() -> r.lift.goTo("flower"))
                // The lift is held below its line until the turret is out of the back (Driver Station:
                // "TurretLift : Lift held at ...").
                .waitUntil("lift up", r.lift::isAtTarget, 4.0)
                .then(() -> { if (wasFollowing[0]) r.turret.followMode().on(); },
                      () -> r.wrist.goTo("flower"))
                .waitUntil("over the FLOWER", r.wrist::isDone, 1.0)
                .then(r.claw::open)
                .waitUntil("let go", r.claw::isDone, 1.0)
                .waitSeconds(0.3)                                        // the ball drops clear
                .then(() -> r.wrist.goTo("carry"))
                .waitUntil("wrist back", r.wrist::isDone, 1.0)
                .then(() -> r.lift.goTo("stowed"))
                .waitUntil("lift down", r.lift::isAtTarget, 3.0)
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
