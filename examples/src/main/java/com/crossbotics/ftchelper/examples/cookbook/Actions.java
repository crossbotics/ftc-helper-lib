package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Action;

/*
 * Cookbook (docs/cookbook.md): a whole file, as it looks in your TeamCode. The lines between
 * "// recipe: name" and "// end recipe" are shown on the page; tools/check_docs.py keeps them the same.
 */
// recipe: file-actions
public class Actions {
    // One method per action. r = your robot. Use it in Controls.java and in autos.
    public static Action scoreHigh(Robot r) {
        return Action.named("Score high")
                .then(() -> r.lift.goTo("high"))
                .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)
                .then(r.claw::open)
                .waitSeconds(0.5)              // wait half a second (this is "sleep", done safely)
                .then(() -> r.lift.goTo("down"));
    }
}
// end recipe
