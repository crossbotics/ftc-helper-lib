package io.github.jasoncross.ftchelper.robot;

import io.github.jasoncross.ftchelper.auto.AutoRoutine;
import io.github.jasoncross.ftchelper.auto.AutoStep;
import io.github.jasoncross.ftchelper.match.InitMenu;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/**
 * The steps of one autonomous, read top to bottom (see {@link Auto}). Each method adds a step and
 * hands the list back, so they chain:
 *
 * <pre>{@code
 * s.start("Back wall, 2nd tile from the left, facing the field", 0)
 *  .menuYesNo("Score", true)
 *  .run("Hold the piece", robot.claw::close)
 *  .move("Leave the wall", 4, 0, 0.4)
 *  .onlyIf("Score")
 *      .run("Lift up", () -> robot.lift.goTo("high"))       // rises during the next moves
 *      .move("Drive to the goal", 24, 0, 0.6)
 *      .turn("Face the goal", 45, 0.5)                      // mirrored for RED
 *      .waitUntil("Lift at the top", robot.lift::isAtTarget, 2.0)
 *      .run("Drop", robot.claw::open)
 *  .end()
 *  .parkWithSeconds(4)
 *  .move("Park", 20, 90, 0.7);
 * }</pre>
 *
 * <p>Moves and turns: directions in degrees, clockwise from forward for moves (0 forward, 90 right),
 * counterclockwise + for turns; written for BLUE and mirrored for RED. Distances in inches, powers
 * 0 to 1. Each move or turn waits until it is done; {@code run} starts something and goes on at once
 * (the mechanisms keep moving during the next steps).
 *
 * <p>The list is read twice: once in INIT (to learn the menu and the start position: no step runs
 * then) and once after START. So write only steps here, no other code with side effects.
 *
 * Docs: docs/modules/robot.md (section "Autos")
 */
public final class Steps {
    private final AutoRoutine routine;   // null in INIT (only the menu and start are read)
    private final InitMenu menu;
    private final RobotDefinition robot;
    // The onlyIf blocks we are inside: true = add the steps, false = skip them.
    private final Deque<Boolean> blocks = new ArrayDeque<>();
    String where = null;
    double headingDeg = 0;
    double parkSec = 0;

    Steps(AutoRoutine routine, InitMenu menu, RobotDefinition robot) {
        this.routine = routine;
        this.menu = menu;
        this.robot = robot;
    }

    // True when steps are being added (after START, and not inside a skipped onlyIf).
    private boolean adding() {
        return routine != null && !blocks.contains(false);
    }

    /**
     * Where to place the robot (shown in INIT) and which way it faces, degrees from "straight away
     * from your driver", counterclockwise + (TeleOp's field-centric forward uses it).
     */
    public Steps start(String whereToPlace, double headingDeg) {
        where = whereToPlace;
        this.headingDeg = headingDeg;
        return this;
    }

    // ----------------------------- the INIT menu -----------------------------

    /** A yes/no choice in the INIT menu (D-pad), read with {@link #onlyIf} or {@link #isYes}. */
    public Steps menuYesNo(String label, boolean startYes) {
        if (routine == null) menu.yesNo(label, startYes);
        return this;
    }

    /** A choice between options in the INIT menu, read with {@link #choice}. */
    public Steps menuChoice(String label, String... options) {
        if (routine == null) menu.choice(label, options);
        return this;
    }

    /** True if the yes/no choice is "yes". */
    public boolean isYes(String label) {
        return menu.isYes(label);
    }

    /** The option chosen for this label. */
    public String choice(String label) {
        return menu.get(label);
    }

    /** The steps up to {@link #end()} run only if the yes/no choice is "yes". */
    public Steps onlyIf(String yesNoLabel) {
        blocks.push(routine == null || menu.isYes(yesNoLabel));
        return this;
    }

    /** The steps up to {@link #end()} run only if this option was chosen. */
    public Steps onlyIf(String label, String option) {
        blocks.push(routine == null || option.equals(menu.get(label)));
        return this;
    }

    /** Ends the latest {@link #onlyIf}. */
    public Steps end() {
        if (blocks.isEmpty()) throw new IllegalStateException("Steps: end() without an onlyIf(...)");
        blocks.pop();
        return this;
    }

    // ----------------------------- steps -----------------------------

    /** Drives a distance in a direction (clockwise from forward: 0 forward, 90 right, 180 back). Waits until done. */
    public Steps move(String label, double inches, double directionDeg, double maxPower) {
        if (adding()) routine.move(label, inches, directionDeg, maxPower);
        return this;
    }

    /** Turns by an angle (counterclockwise +). Waits until done. */
    public Steps turn(String label, double degrees, double maxPower) {
        if (adding()) routine.turn(label, degrees, maxPower);
        return this;
    }

    /** Turns to a heading (0 = the way it faced at the start, counterclockwise +). Waits until done. */
    public Steps turnTo(String label, double headingDeg, double maxPower) {
        if (adding()) routine.turnTo(label, headingDeg, maxPower);
        return this;
    }

    /** Runs a command now and goes on at once (e.g. {@code () -> robot.lift.goTo("high")}). */
    public Steps run(String label, Runnable command) {
        if (adding()) routine.run(label, command);
        return this;
    }

    /** Waits this long (the mechanisms keep working). */
    public Steps waitSeconds(String label, double seconds) {
        if (adding()) routine.waitFor(label, seconds);
        return this;
    }

    /** Waits until something is true, at most {@code timeoutSec} (the report says if it timed out). */
    public Steps waitUntil(String label, BooleanSupplier condition, double timeoutSec) {
        if (adding()) routine.waitUntil(label, condition, timeoutSec);
        return this;
    }

    /** Runs an action from Actions.java and waits until it is done (at most {@code timeoutSec}). */
    public Steps action(final Action action, double timeoutSec) {
        if (adding()) {
            routine.step(action.name(), ctx -> {
                robot.run(action);
                ctx.waitUntil(() -> !action.isRunning(), timeoutSec);
                if (action.isRunning()) {
                    // Out of time (or autonomous is over): stop it, and say so in the report.
                    robot.cancel(action);
                    if (ctx.isActive()) ctx.note("timed out");
                } else if (!"done".equals(action.result())) {
                    ctx.note(action.result());   // e.g. "timed out waiting for lift at the top"
                }
            });
        }
        return this;
    }

    /** {@link #action(Action, double)} with up to 10 seconds. */
    public Steps action(Action action) {
        return action(action, 10);
    }

    /** Starts an action and goes on at once: it runs during the next steps. */
    public Steps alongside(final Action action) {
        if (adding()) routine.run(action.name() + " (alongside)", () -> robot.run(action));
        return this;
    }

    /** Any other step (a mechanism's step, or your own: see {@code AutoStep}). */
    public Steps step(String label, AutoStep step) {
        if (adding()) routine.step(label, step);
        return this;
    }

    /**
     * The steps after this are PARK steps, and they get the last {@code seconds} of autonomous: the
     * steps before stop in time for them.
     */
    public Steps parkWithSeconds(double seconds) {
        parkSec = seconds;
        if (adding()) routine.thenPark();
        return this;
    }
}
