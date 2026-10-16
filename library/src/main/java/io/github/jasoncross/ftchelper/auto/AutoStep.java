package io.github.jasoncross.ftchelper.auto;

/**
 * One step of an autonomous routine: "drive 24 inches", "raise the lift", "wait 1 second".
 *
 * <p>A step BLOCKS on purpose: it runs from start to finish, then the next step starts. While a step waits, it must call
 * {@code ctx.loop()} (or {@code ctx.waitUntil}/{@code ctx.waitFor}) so the mechanisms keep working
 * and the routine can stop it when the match ends.
 *
 * <pre>{@code
 * // A custom step, as a lambda:
 * AutoStep dropPiece = ctx -> {
 *     claw.open();
 *     ctx.waitFor(0.3);                            // keeps every mechanism updating
 * };
 * AutoStep liftUp = ctx -> {
 *     lift.goTo("HIGH");
 *     ctx.waitUntil(lift::isAtTarget, 2.0);        // done, or 2 s, or the match ends
 * };
 * }</pre>
 *
 * <p>Mechanism helpers give you ready-made steps, e.g. {@code lift.goToStep("HIGH")}.
 *
 * Docs: docs/modules/auto.md
 */
public interface AutoStep {
    /** Does the step. Returns when it is finished, or as soon as {@code ctx.isActive()} turns false. */
    void run(AutoContext ctx);
}
