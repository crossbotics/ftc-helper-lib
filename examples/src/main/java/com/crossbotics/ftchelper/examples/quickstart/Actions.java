package com.crossbotics.ftchelper.examples.quickstart;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode;

import com.crossbotics.ftchelper.robot.Action;

/** Quick start, file 4 of 5: moves that use several parts or steps (lesson 5). */
public class Actions {

    /** Pushes a piece out for half a second, then stops the intake. */
    public static Action spitOut(Robot r) {
        return Action.named("Spit out")
                .then(r.intake::out)
                .waitSeconds(0.5)
                .then(r.intake::stop);
    }
}
