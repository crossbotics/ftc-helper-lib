package com.crossbotics.ftchelper.robot;

/**
 * Something one button switches on and off: an {@link Intake}, a {@link Launcher}'s spin, a
 * {@link Claw}, or a {@link Mode}. {@code g2.rb.toggle(robot.intake)} presses {@link #toggle()}.
 *
 * <p>The part itself remembers whether it is on, so the button and an action never disagree.
 *
 * Docs: docs/modules/robot.md
 */
public interface Toggleable {
    /** Switches on if off, off if on. */
    void toggle();
}
