package com.crossbotics.ftchelper.robot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Steps that use several parts together, written once (in {@code Actions.java}) and started by a
 * button in TeleOp or used as a step in an autonomous.
 *
 * <pre>{@code
 * public static Action scoreHigh(Robot r) {
 *     return Action.named("Score high")
 *             .then(() -> r.lift.goTo("high"))
 *             .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)
 *             .then(r.claw::open)
 *             .waitSeconds(0.3)
 *             .then(() -> r.lift.goTo("down"));
 * }
 * // Controls.java:  g2.y.press(Actions.scoreHigh(robot));
 * // an autonomous:  s.action(Actions.scoreHigh(robot));
 * }</pre>
 *
 * <ul>
 *   <li><b>Never waits:</b> an action does a little every loop, so the robot keeps driving and every
 *       other button keeps working while it runs.</li>
 *   <li><b>Parts are claimed:</b> the parts an action commands are its own while it runs. Another
 *       action that commands one of them stops it; so does the driver moving that part by hand
 *       (the driver always wins).</li>
 *   <li><b>A wait that times out stops the action</b> (a lift that never reached the top must not
 *       open the claw). The Driver Station says why.</li>
 *   <li>A stopped action (cancelled, taken over, timed out) stops what it started, safely: a lift or
 *       arm holds its height, a turret its angle, waiting shots are dropped, an intake stops. An
 *       action that finishes keeps its last commands.</li>
 *   <li><b>A part that is not on the robot yet</b> ({@code optional()} in Robot.java, not in the
 *       configuration) is skipped: its commands do nothing, the waits right after a step that only
 *       used missing parts are skipped too, and the action goes on with the other parts. Its status
 *       says "skipped IntakeDeploy (not on the robot)"; an autonomous's report says it too.</li>
 * </ul>
 *
 * <p>{@code () -> r.lift.goTo("high")} is a small piece of code (a "lambda") that the action runs
 * later, when it reaches that step. {@code r.claw::open} is the same for a method with no inputs.
 *
 * Docs: docs/modules/robot.md (section "Actions")
 */
public final class Action {

    // One step: something to do now, or something to wait for.
    private static final class Step {
        final Runnable[] doNow;          // null for a wait
        final BooleanSupplier until;     // null for "then" and timed waits
        final double seconds;            // timed wait, or the time limit of a waitUntil
        final String what;               // for the Driver Station
        Step(Runnable[] doNow, BooleanSupplier until, double seconds, String what) {
            this.doNow = doNow;
            this.until = until;
            this.seconds = seconds;
            this.what = what;
        }
    }

    private final String name;
    private final List<Step> steps = new ArrayList<>();
    // While running: which step (from 0), and when it started (seconds on the runner's clock).
    private int index = -1;
    private double stepStart = 0;
    private String result = "";
    // The parts this run has commanded (see ActionRunner.touched), to halt them if it is stopped.
    final java.util.Set<Module<?>> claimed = new java.util.LinkedHashSet<>();
    // The parts this run skipped because they are not on the robot (Module.optional), by name.
    private final java.util.Set<String> skipped = new java.util.LinkedHashSet<>();
    // While a "then" step runs: did it command a part that is there, or one that is not?
    boolean usedPresentPart = false;
    private boolean usedMissingPart = false;
    // True after a "then" step that only commanded missing parts: the waits right after it are skipped.
    private boolean skipWaits = false;

    private Action(String name) {
        this.name = name;
    }

    /**
     * Starts a new action.
     *
     * @param name shown on the Driver Station, e.g. "Score high"
     */
    public static Action named(String name) {
        return new Action(name);
    }

    /**
     * Does these at once, then goes to the next step. Several at once move several parts together:
     * {@code .then(() -> r.arm.goTo("grab"), r.claw::open)}.
     *
     * @param doNow one or more commands
     */
    public Action then(Runnable... doNow) {
        steps.add(new Step(doNow, null, 0, "then"));
        return this;
    }

    /**
     * Waits this long (the robot keeps running), then goes on.
     *
     * @param seconds how long
     */
    public Action waitSeconds(double seconds) {
        steps.add(new Step(null, null, Math.max(0, seconds), fmtSec(seconds)));
        return this;
    }

    /**
     * Waits until something is true. If it is not true in {@code timeoutSec}, the action STOPS and
     * the Driver Station says "timed out waiting for {what}".
     *
     * @param what       a few words for the Driver Station, e.g. "lift at the top"
     * @param condition  e.g. {@code r.lift::isAtTarget}
     * @param timeoutSec the most to wait, seconds
     */
    public Action waitUntil(String what, BooleanSupplier condition, double timeoutSec) {
        steps.add(new Step(null, condition, Math.max(0, timeoutSec), what));
        return this;
    }

    /** {@link #waitUntil(String, BooleanSupplier, double)} with "the condition" as its words. */
    public Action waitUntil(BooleanSupplier condition, double timeoutSec) {
        return waitUntil("the condition", condition, timeoutSec);
    }

    /** The name given to {@link #named}. */
    public String name() {
        return name;
    }

    /** True while it runs. */
    public boolean isRunning() {
        return index >= 0;
    }

    /** How the last run ended: "done", "cancelled", "stopped: Lift moved by hand"... ("" before the first). */
    public String result() {
        return result;
    }

    /**
     * The parts this run (or the last one) skipped because they are not on the robot (marked
     * {@code optional()} in Robot.java and missing from the configuration), e.g. ["IntakeDeploy"].
     * Empty when every part was there.
     */
    public java.util.List<String> skipped() {
        return java.util.Collections.unmodifiableList(new ArrayList<>(skipped));
    }

    /** "" or the words for the skipped parts: "skipped IntakeDeploy (not on the robot)". */
    String skippedWords() {
        if (skipped.isEmpty()) return "";
        StringBuilder names = new StringBuilder();
        for (String n : skipped) names.append(names.length() == 0 ? "" : ", ").append(n);
        return "skipped " + names + " (not on the robot)";
    }

    /** E.g. "Score high: step 2 of 5 (lift at the top)", or its last result when not running. */
    public String status() {
        String skip = skippedWords();
        if (!isRunning()) return name + ": " + (result.isEmpty() ? "not run" : result) + (skip.isEmpty() ? "" : "; " + skip);
        Step s = steps.get(index);
        return String.format(java.util.Locale.US, "%s: step %d of %d (%s)", name, index + 1, steps.size(), s.what);
    }

    // ---------------------------------------------------------------------------------------
    // Run by the ActionRunner (one per robot), a little every loop
    // ---------------------------------------------------------------------------------------

    void begin(ActionRunner runner, double now) {
        index = 0;
        stepStart = now;
        result = "";
        claimed.clear();
        skipped.clear();
        skipWaits = false;
        advance(runner, now);
    }

    void update(ActionRunner runner, double now) {
        if (isRunning()) advance(runner, now);
    }

    // After a pause: the current step's clock starts later, so a wait does not time out because of it.
    void shift(double seconds) {
        stepStart += seconds;
    }

    void finish(String why) {
        index = -1;
        result = why;
    }

    // A part that is not on the robot was commanded by this action (see ActionRunner.skipped).
    void skipped(Module<?> part) {
        skipped.add(part.name());
        usedMissingPart = true;
    }

    // Goes through the steps that can finish now: all "then" steps and every wait that is over.
    // Stops at the first wait that is still waiting. Never waits for time: at most one pass per step.
    // A wait right after a step that only commanded parts that are not on the robot is skipped: there
    // is nothing to wait for (a deploy servo that is not there never "arrives").
    private void advance(ActionRunner runner, double now) {
        for (int pass = 0; pass <= steps.size() && isRunning(); pass++) {
            if (index >= steps.size()) {
                finish("done");
                return;
            }
            Step s = steps.get(index);
            if (s.doNow != null) {
                // The commands claim their parts for this action (see Module.touch).
                usedPresentPart = false;
                usedMissingPart = false;
                runner.runAs(this, s.doNow);
                if (!isRunning()) return;   // a command stopped this action (for example its own cancel)
                skipWaits = usedMissingPart && !usedPresentPart;
            } else if (skipWaits) {
                // Nothing to wait for: the step before only told missing parts what to do.
            } else if (s.until != null) {
                if (!s.until.getAsBoolean()) {
                    if (now - stepStart >= s.seconds) finish("timed out waiting for " + s.what);
                    return;
                }
            } else if (now - stepStart < s.seconds) {
                return;
            }
            index++;
            stepStart = now;
        }
    }

    private static String fmtSec(double s) {
        return String.format(java.util.Locale.US, "wait %.1f s", s);
    }
}
