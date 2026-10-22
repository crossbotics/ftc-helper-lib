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
        double x = 0;   // where it sits, inches right of the centre line
        StillTracker() { super("odo"); }
        @Override public boolean isReady() { return ready; }
        @Override public String status() { return ready ? "ready" : "lost"; }
        @Override public String kind() { return "test"; }
        @Override protected double[] read() { return new double[]{0, -x, 0}; }   // device frame: its y = our -x
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
        // A 48 in path in two parts of 24 in (the "basic" one uses the defaults: it knows neither).
        boolean basic;
        @Override public double totalIn() { return basic ? Double.NaN : 48; }
        @Override public int partNow() { return basic || !busy ? 0 : remaining > 24 ? 1 : 2; }
        @Override public int parts(Object path) { return basic ? 0 : 2; }
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

    // ----------------------------- PathAt: things to do along the path -----------------------------

    // Runs one follow step with these PathAts; `fired` gets "name@inches left" as each one fires.
    private AutoRoutine runWith(RobotDefinition r, Alliance mirror, double timeoutSec, PathAt... along) {
        AutoRoutine routine = new AutoRoutine(() -> true, () -> { }, new Fakes.FakeTelemetry().telemetry).subsystems(r.all());
        if (mirror != null) routine.mirrorFor(mirror);
        new Steps(routine, null, r).follow("Path", "the path", timeoutSec, along);
        routine.runAll();
        return routine;
    }

    private final java.util.List<String> fired = new java.util.ArrayList<>();

    private Runnable mark(final String name) {
        return () -> fired.add(name + "@" + Math.round(fake.remaining));
    }

    // 48 in, 2 in a loop for 20 loops: it ends 8 in short (like Pedro's "done" distance, larger).
    @Test public void eachFiresOnceInOrderWhereTheRobotIs() {
        RobotDefinition r = robot(20, 2);
        AutoRoutine routine = runWith(r, null, 5,
                PathAt.inchesDone(10, "Ten in", mark("ten")),
                PathAt.fraction(0.5, "Halfway", mark("half")),
                PathAt.part(2, "Part 2", mark("part2")),
                PathAt.inchesLeft(12, "Spin up", mark("spin")),
                PathAt.inchesLeft(1, "Last inch", mark("last")));
        assertEquals("[Path: done]", routine.getReport().toString());
        // Checked before each loop's update, with the distance after the last one. "Last inch" is never
        // reached on the way: it fires at the end of the path.
        assertEquals("[ten@38, half@24, part2@24, spin@12, last@8]", fired.toString());
    }

    @Test public void theOrderWrittenWinsWhenTwoAreReachedTogether() {
        RobotDefinition r = robot(20, 2);
        runWith(r, null, 5, PathAt.part(2, "B", mark("b")), PathAt.fraction(0.5, "A", mark("a")));
        assertEquals("[b@24, a@24]", fired.toString());
    }

    @Test public void aFollowerThatDoesNotKnowTheLengthUsesTheDistanceAtTheStart() {
        RobotDefinition r = robot(20, 2);
        fake.basic = true;
        runWith(r, null, 5, PathAt.fraction(0.5, "Halfway", mark("half")), PathAt.part(2, "Part 2", mark("part2")));
        // The first distance read (after the first update) is 46 in: halfway is 23 in left. Part 2: at the end.
        assertEquals("[half@22, part2@8]", fired.toString());
    }

    @Test public void anEarlyEndListsWhatDidNotFire() {
        RobotDefinition r = robot(1_000_000, 0.01);
        fake.floor = 30;   // it never gets closer than 30 in (a fast computer runs many loops in 0.1 s)
        AutoRoutine routine = runWith(r, null, 0.1,
                PathAt.inchesDone(0, "At once", mark("now")),
                PathAt.inchesLeft(12, "Spin up", mark("spin")),
                PathAt.fraction(0.9, "Almost", mark("almost")));
        assertEquals("[Path: timed out, not fired: Spin up, Almost]", routine.getReport().toString());
        assertEquals(1, fired.size());
    }

    @Test public void noOdometryFiresNothing() {
        RobotDefinition r = robot(20, 1);
        tracker.ready = false;
        AutoRoutine routine = runWith(r, null, 5, PathAt.part(1, "Start", mark("start")));
        assertEquals("[Path: NO_ODOMETRY, not fired: Start]", routine.getReport().toString());
        assertTrue(fired.isEmpty());
    }

    @Test public void aMirroredPathFiresAtTheSamePlaces() {
        RobotDefinition r = robot(20, 2);
        runWith(r, Alliance.RED, 5, PathAt.fraction(0.5, "Halfway", mark("half")), PathAt.inchesLeft(12, "Spin up", mark("spin")));
        assertTrue(fake.mirror);
        assertEquals("[half@24, spin@12]", fired.toString());
    }

    // near(...): the point is mirrored for RED like driveTo's. The pretend robot sits at x = 3.
    @Test public void nearAFieldPointIsMirroredForRed() {
        tracker.x = 3;
        RobotDefinition r = robot(5, 1);
        AutoRoutine blue = runWith(r, null, 5, PathAt.near(-3, 0, 1, "By the wall", mark("near")));
        assertEquals("[Path: done, not fired: By the wall]", blue.getReport().toString());
        r = robot(5, 1);
        AutoRoutine red = runWith(r, Alliance.RED, 5, PathAt.near(-3, 0, 1, "By the wall", mark("near")));
        assertEquals("[Path: done]", red.getReport().toString());
        assertEquals(1, fired.size());
    }

    // An action keeps running on the robot's action runner after it starts, like alongside.
    @Test public void anActionStartsOnTheRobot() {
        RobotDefinition r = robot(20, 2);
        final int[] ran = {0};
        Action spin = Action.named("Spin up").then(() -> ran[0]++).waitSeconds(60);
        AutoRoutine routine = runWith(r, null, 5, PathAt.inchesLeft(12, spin));
        assertEquals("[Path: done]", routine.getReport().toString());
        assertEquals(1, ran[0]);
        assertTrue("still running after the step", spin.isRunning());
    }

    @Test public void mistakesStopInit() {
        RobotDefinition r = robot(20, 1);
        AutoRoutine routine = new AutoRoutine(() -> true, () -> { }, new Fakes.FakeTelemetry().telemetry).subsystems(r.all());
        try {
            new Steps(routine, null, r).follow("Path", "the path", PathAt.part(3, "Gate", () -> { }));
            org.junit.Assert.fail("expected an error");
        } catch (IllegalStateException e) {
            assertEquals("Steps: follow(\"Path\"): PathAt.part(3), but the path has 2 parts", e.getMessage());
        }
        try {
            new Steps(routine, null, r).driveTo("Go", 0, 24, 0, 0.5, PathAt.part(2, "Gate", () -> { }));
            org.junit.Assert.fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().endsWith("the path has 1 part (driveTo is one straight line)"));
        }
        assertBad("PathAt.fraction: give a part of the path from 0 to 1, e.g. 0.5 = halfway (not 50.0)",
                () -> PathAt.fraction(50, "Half", () -> { }));
        assertBad("PathAt.part: parts count from 1 (not 0)", () -> PathAt.part(0, "Gate", () -> { }));
        assertBad("PathAt.inchesLeft: give 0 inches or more (not -12.0)", () -> PathAt.inchesLeft(-12, "Spin", () -> { }));
        assertBad("PathAt.near: give a distance more than 0 inches (not 0.0)", () -> PathAt.near(0, 0, 0, "Near", () -> { }));
        assertBad("PathAt.inchesLeft: give a name for the report, e.g. \"Spin up\"", () -> PathAt.inchesLeft(12, "", () -> { }));
        assertBad("PathAt.fraction: give something to do (it is null)", () -> PathAt.fraction(0.5, (Action) null));
    }

    private static void assertBad(String message, Runnable make) {
        try {
            make.run();
            org.junit.Assert.fail("expected an error: " + message);
        } catch (IllegalArgumentException e) {
            assertEquals(message, e.getMessage());
        }
    }
}
