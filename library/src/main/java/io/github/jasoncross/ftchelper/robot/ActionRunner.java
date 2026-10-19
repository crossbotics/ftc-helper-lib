package io.github.jasoncross.ftchelper.robot;

import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Runs the robot's {@link Action}s, a little every loop, and keeps the rule "one part, one owner":
 * each module remembers the action that commanded it last. One per robot ({@code robot.runner}).
 */
final class ActionRunner implements Subsystem {
    /** How long a finished action's result stays on the Driver Station, seconds. */
    static final double SHOW_RESULT_SEC = 3;

    private final List<Action> running = new ArrayList<>();
    private LongSupplier clock = System::nanoTime;
    // The action whose "then" step is running its commands now (null = the driver's commands).
    private Action current = null;
    // The last finished action, for the Driver Station.
    private Action lastFinished = null;
    private double lastFinishedAt = -1e9;

    /** For tests: a pretend clock. */
    void useClock(LongSupplier clockNanos) {
        clock = clockNanos;
    }

    private double now() {
        return clock.getAsLong() / 1e9;
    }

    /** Starts the action (from its first step again if it was running). */
    void start(Action a) {
        if (a.isRunning()) stop(a, "restarted");
        running.add(a);
        a.begin(this, now());
        collectFinished();
    }

    /** Starts it, or stops it if it is running ("press again to cancel"). */
    void toggle(Action a) {
        if (a.isRunning()) stop(a, "cancelled");
        else start(a);
    }

    /** Stops one action, leaving its parts where they are. */
    void stop(Action a, String why) {
        if (!a.isRunning()) return;
        a.finish(why);
        halt(a);
        remember(a);
        running.remove(a);
    }

    // An action that did not finish ("cancelled", "stopped: ...", "timed out ..."): each part it still
    // owns stops what the action told it to do, safely (a lift holds where it is, queued shots are
    // dropped, an intake stops). A part the driver or another action has taken is left alone: its new
    // command comes right after this. A finished action ("done") keeps its last commands.
    private void halt(Action a) {
        for (Module<?> part : a.claimed) {
            if (part.owner == a) {
                part.owner = null;
                part.halt();
            }
        }
        a.claimed.clear();
    }

    /** Stops every action (end of the OpMode, or a "cancel all" button). */
    void stopAll(String why) {
        for (Action a : new ArrayList<>(running)) stop(a, why);
    }

    /** True if any action is running. */
    boolean isBusy() {
        return !running.isEmpty();
    }

    // Runs one "then" step's commands as this action: each part they command is claimed by it.
    void runAs(Action a, Runnable[] commands) {
        Action before = current;
        current = a;
        try {
            for (Runnable r : commands) {
                if (!a.isRunning()) break;
                r.run();
            }
        } finally {
            current = before;
        }
    }

    // A module was commanded. See Module.touch().
    void touched(Module<?> part) {
        Action previous = part.owner;
        if (current != null) {
            // Inside an action: it claims the part. Another action holding it stops.
            if (previous != null && previous != current && previous.isRunning()) {
                stop(previous, "stopped: " + part.name() + " taken by " + current.name());
            }
            part.owner = current;
            current.claimed.add(part);
        } else {
            // The driver (a button or stick): the driver wins.
            if (previous != null && previous.isRunning()) stop(previous, "stopped: " + part.name() + " moved by hand");
            part.owner = null;
        }
    }

    @Override
    public void update() {
        double t = now();
        // A copy: an action may stop another one while it runs.
        for (Action a : new ArrayList<>(running)) a.update(this, t);
        collectFinished();
    }

    // Takes finished actions off the list.
    private void collectFinished() {
        for (Action a : new ArrayList<>(running)) {
            if (!a.isRunning()) {
                running.remove(a);
                if ("done".equals(a.result())) a.claimed.clear();
                else halt(a);   // timed out: stop what it started
                remember(a);
            }
        }
    }

    private void remember(Action a) {
        lastFinished = a;
        lastFinishedAt = now();
    }

    @Override
    public void stop() {
        stopAll("stopped (end of the program)");
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        for (Action a : running) t.line("Action", a.status());
        if (running.isEmpty() && lastFinished != null && now() - lastFinishedAt < SHOW_RESULT_SEC) {
            t.line("Action", lastFinished.status());
        }
    }

    @Override
    public String name() {
        return "Actions";
    }
}
