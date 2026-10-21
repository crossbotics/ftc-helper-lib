package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.auto.AutoRoutine;
import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.crossbotics.ftchelper.match.Alliance;

import org.junit.Test;

/** Steps.follow(...) with a pretend path follower (the pedro module's job, without Pedro). */
public class FollowStepTest {

    static final class StillTracker extends PoseTracker {
        boolean ready = true;
        StillTracker() { super("odo"); }
        @Override public boolean isReady() { return ready; }
        @Override public String status() { return ready ? "ready" : "lost"; }
        @Override public String kind() { return "test"; }
        @Override protected double[] read() { return new double[]{0, 0, 0}; }
        @Override protected void write(double x, double y, double h) { }
    }

    // Busy for a number of loops; the distance left goes down by `step` each loop.
    static final class FakeFollower implements PathFollower {
        Object path;
        Boolean mirror;
        int loopsLeft;
        double remaining, step, floor = Double.NEGATIVE_INFINITY;
        boolean busy, cancelled;
        final StillTracker tracker;
        FakeFollower(StillTracker tracker, int loops, double step) {
            this.tracker = tracker;
            this.loopsLeft = loops;
            this.step = step;
        }
        @Override public void follow(Object path, boolean mirrorX) {
            this.path = path;
            mirror = mirrorX;
            busy = true;
            remaining = 48;
        }
        @Override public void update() {
            if (!busy) return;
            remaining = Math.max(floor, remaining - step);
            if (--loopsLeft <= 0) busy = false;
        }
        @Override public boolean isBusy() { return busy; }
        @Override public void cancel() { busy = false; cancelled = true; }
        @Override public double remainingIn() { return remaining; }
        @Override public boolean isReady() { return tracker.isReady(); }
        @Override public void check(Object path) {
            if (!(path instanceof String)) throw new IllegalArgumentException("not a path: " + path);
        }
    }

    // Odometry checked (the Odometry Tuner was run): follow steps run.
    public static class Checked {
        public static class Odometry {
            public static boolean checked = true;
        }
    }

    public static class NotChecked {
        public static class Odometry {
            public static boolean checked = false;
        }
    }

    private final StillTracker tracker = new StillTracker();
    private FakeFollower fake;

    private RobotDefinition robot(final int loops, final double step) {
        return robot(loops, step, Checked.class);
    }

    private RobotDefinition robot(final int loops, final double step, final Class<?> tunables) {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(tunables);
                drive(Drive.mecanum("fl", "fr", "bl", "br"));
                odometry(Odometry.custom(d -> tracker));
                follower((devices, robot) -> fake = new FakeFollower(tracker, loops, step));
            }
        };
        r.setup(new RobotTestKit());
        return r;
    }

    private String run(RobotDefinition r, Alliance mirror, double timeoutSec) {
        AutoRoutine routine = new AutoRoutine(() -> true, () -> { }, new Fakes.FakeTelemetry().telemetry).subsystems(r.all());
        if (mirror != null) routine.mirrorFor(mirror);
        new Steps(routine, null, r).follow("Path", "the path", timeoutSec);
        routine.runAll();
        return routine.getReport().toString();
    }

    @Test public void followsUntilTheFollowerIsDone() {
        RobotDefinition r = robot(20, 1);
        assertTrue(r.hasFollower());
        assertEquals("[Path: done]", run(r, null, 5));
        assertEquals("the path", fake.path);
        assertFalse(fake.mirror);
        assertFalse(fake.cancelled);
    }

    @Test public void onRedWithMirroringThePathIsMirrored() {
        RobotDefinition r = robot(5, 1);
        run(r, Alliance.RED, 5);
        assertTrue(fake.mirror);
    }

    @Test public void aTimeLimitCancelsThePath() {
        RobotDefinition r = robot(1_000_000, 0.01);
        assertEquals("[Path: timed out]", run(r, null, 0.1));
        assertTrue(fake.cancelled);
    }

    @Test public void noOdometryDoesNotStartThePath() {
        RobotDefinition r = robot(20, 1);
        tracker.ready = false;
        assertEquals("[Path: NO_ODOMETRY]", run(r, null, 5));
        assertEquals(null, fake.path);
    }

    @Test(expected = IllegalStateException.class)
    public void followWithoutAFollowerStopsAtInit() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.mecanum("fl", "fr", "bl", "br"));
            }
        };
        r.setup(new RobotTestKit());
        AutoRoutine routine = new AutoRoutine(() -> true, () -> { }, new Fakes.FakeTelemetry().telemetry);
        new Steps(routine, null, r).follow("Path", "the path");
    }

    @Test public void anUncheckedOdometrySkipsThePath() {
        RobotDefinition r = robot(20, 1, NotChecked.class);
        assertEquals("[Path: skipped: Tunables.Odometry.checked is false (run the Odometry Tuner first)]", run(r, null, 5));
        assertEquals(null, fake.path);
    }

    @Test public void aWrongPathStopsAtInit() {
        RobotDefinition r = robot(20, 1);
        AutoRoutine routine = new AutoRoutine(() -> true, () -> { }, new Fakes.FakeTelemetry().telemetry).subsystems(r.all());
        try {
            new Steps(routine, null, r).follow("Path", 42);
            org.junit.Assert.fail("expected an error");
        } catch (IllegalStateException e) {
            assertEquals("Steps: follow(\"Path\"): not a path: 42", e.getMessage());
        }
    }

    // Codex: the distance read before the first update is no baseline. Alex: in the last 3 in the
    // follower may still be turning; neither may end the step as STALLED.
    @Test public void finishingATurnAtTheEndIsNotStalled() {
        RobotDefinition r = robot(Integer.MAX_VALUE, 0);
        // 48 in at the start (set by follow), then it sits 2 in from the end: only the time limit ends it.
        fake.step = 23;
        fake.floor = 2;
        AutoRoutine routine = new AutoRoutine(() -> true, () -> { }, new Fakes.FakeTelemetry().telemetry).subsystems(r.all());
        new Steps(routine, null, r).follow("Path", "the path", 2.0);
        routine.runAll();
        assertEquals("[Path: timed out]", routine.getReport().toString());
    }
}
