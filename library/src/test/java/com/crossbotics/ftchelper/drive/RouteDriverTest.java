package com.crossbotics.ftchelper.drive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.auto.AutoRoutine;
import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.FieldPose;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** A route in TeleOp (one loop at a time) and in autonomous (the blocking step), on a pretend robot. */
public class RouteDriverTest {
    private long nanos = 0;
    private double x, y, heading;
    private boolean ready = true, stuck = false;
    private final AutoDriveConfig cfg = AutoDriveSimTest.config();
    private final List<double[]> path = new ArrayList<>();

    private RouteDriver driver() {
        return new RouteDriver(cfg, 1, () -> new FieldPose(x, y, Angles.wrapDegrees(heading)), () -> ready, () -> nanos);
    }

    // One 20 ms loop: the wheels' command moves the pretend robot (60 in/s and 180 deg/s at full power).
    private double[] loop(RouteDriver d) {
        double[] p = d.update();
        nanos += 20_000_000L;
        if (!stuck) {
            double h = Math.toRadians(heading);
            x += (-p[0] * Math.sin(h) + p[1] * Math.cos(h)) * 60 * 0.02;
            y += (p[0] * Math.cos(h) + p[1] * Math.sin(h)) * 60 * 0.02;
            heading -= p[2] * 180 * 0.02;
        }
        path.add(new double[] {x, y});
        return p;
    }

    private void runToEnd(RouteDriver d, int maxLoops) {
        for (int i = 0; i < maxLoops && d.isRunning(); i++) loop(d);
    }

    private static List<double[]> points(double... xy) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < xy.length; i += 2) out.add(new double[] {xy[i], xy[i + 1]});
        return out;
    }

    @Test public void itDrivesThePointsInOrderAndStopsAtTheLast() {
        RouteDriver d = driver();
        assertTrue(d.start(points(20, 0, 20, 30), 0, 0.6, 1));
        assertTrue(d.isRunning());
        assertNull(d.result());
        runToEnd(d, 2000);
        assertEquals(RouteDriver.Result.DONE, d.result());
        assertEquals(20, x, 1.0);
        assertEquals(30, y, 1.0);
        // It first went to (20, 0): while going up, it was already near x 20, not cutting the corner.
        double xAtY10 = 0;
        for (double[] p : path) if (p[1] < 10) xAtY10 = p[0];
        assertTrue("first the corner: x " + xAtY10, xAtY10 > 15);
        assertArrayEquals(new double[3], d.update(), 0);
    }

    @Test public void aRobotThatSlidPastAPointGoesOnAndDoesNotGoBack() {
        // Set off north for the point (0, 30), then slide past it: 3 in past, 2 in to the side.
        RouteDriver d = driver();
        d.start(points(0, 30, 40, 30), 0, 0.6, 1);
        loop(d);
        x = 2;
        y = 33;
        double[] p = loop(d);
        assertTrue("heads for the next point, to the right: " + p[1], p[1] > 0);
        // Beside a point and not past it, the same distance away, it still goes to that point.
        RouteDriver e = driver();
        x = 0;
        y = 0;
        e.start(points(0, 30, 40, 30), 0, 0.6, 1);
        loop(e);
        x = -3;
        y = 28;
        p = loop(e);
        assertTrue("to the point, forward: " + p[0], p[0] > 0);
    }

    @Test public void itTurnsToTheHeadingOnTheWay() {
        RouteDriver d = driver();
        d.start(points(10, 20), 90, 0.6, 1);
        runToEnd(d, 2000);
        assertEquals(RouteDriver.Result.DONE, d.result());
        assertEquals(90, Angles.wrapDegrees(heading), 2.5);
    }

    @Test public void aRouteOfNoPointsDoesNotStart() {
        RouteDriver d = driver();
        assertFalse(d.start(new ArrayList<double[]>(), 0, 0.6, 1));
        assertFalse(d.start(null, 0, 0.6, 1));
        assertFalse(d.isRunning());
        assertArrayEquals(new double[3], d.update(), 0);
    }

    @Test public void stuckEndsTheRoute() {
        stuck = true;
        RouteDriver d = driver();
        d.start(points(0, 40), 0, 0.6, 1);
        runToEnd(d, 2000);
        assertEquals(RouteDriver.Result.STALLED, d.result());
        assertArrayEquals(new double[3], loop(d), 0);
    }

    @Test public void tooLongOnOneLineEndsTheRoute() {
        cfg.actionTimeoutSec = 0.5;
        RouteDriver d = driver();
        d.start(points(0, 200), 0, 0.2, 1);
        runToEnd(d, 2000);
        assertEquals(RouteDriver.Result.TIMED_OUT, d.result());
    }

    @Test public void eachLineGetsItsOwnTimeLimit() {
        cfg.actionTimeoutSec = 1.5;
        RouteDriver d = driver();
        d.start(points(0, 30, 30, 30, 30, 60), 0, 1, 1);   // each line takes about 1 s; all of it, longer than the limit
        runToEnd(d, 2000);
        assertEquals(RouteDriver.Result.DONE, d.result());
    }

    @Test public void aLostOdometryEndsTheRouteAndAGlitchDoesNot() {
        RouteDriver d = driver();
        d.start(points(0, 60), 0, 0.6, 1);
        for (int i = 0; i < 10; i++) loop(d);
        double before = y;
        ready = false;
        for (int i = 0; i < 5; i++) assertArrayEquals("holds still", new double[3], loop(d), 0);
        assertTrue("0.1 s is a glitch", d.isRunning());
        ready = true;
        for (int i = 0; i < 10; i++) loop(d);
        assertTrue("it goes on", y > before);
        ready = false;
        for (int i = 0; i < 20; i++) loop(d);
        assertEquals(RouteDriver.Result.NO_ODOMETRY, d.result());
    }

    @Test public void cancelEndsItAtOnce() {
        RouteDriver d = driver();
        d.start(points(0, 60), 0, 0.6, 1);
        loop(d);
        d.cancel();
        assertFalse(d.isRunning());
        assertEquals(RouteDriver.Result.CANCELLED, d.result());
        assertArrayEquals(new double[3], d.update(), 0);
    }

    @Test public void aLowPowerLimitDoesNotStopItShortOfThePoint() {
        // The practice profile's limit: the slowest powers are raised to make up for it.
        RouteDriver d = new RouteDriver(cfg, 0.4, () -> new FieldPose(x, y, Angles.wrapDegrees(heading)), () -> ready, () -> nanos);
        d.start(points(0, 5), 0, 0.6, 1);
        double[] p = d.update();
        assertEquals(0.3, p[0], 1e-9);   // 0.12 / 0.4
    }

    // ---------------- the autonomous step, on AutoDriveSimTest's pretend wheels ----------------

    private final AutoDriveSimTest.SimRobot wheels = new AutoDriveSimTest.SimRobot();
    private double ax, ay;
    private final double[] last = {0, 0, 0, 0};
    private final List<double[]> trail = new ArrayList<>();

    private FieldPose wheelPose() {
        return new FieldPose(ax, ay, Angles.wrapDegrees(wheels.heading));
    }

    private void wheelLoop() {
        wheels.step();
        double tpi = cfg.ticksPerInch();
        double[] now = {wheels.fl.position, wheels.fr.position, wheels.bl.position, wheels.br.position};
        double dFl = (now[0] - last[0]) / tpi, dFr = (now[1] - last[1]) / tpi, dBl = (now[2] - last[2]) / tpi, dBr = (now[3] - last[3]) / tpi;
        System.arraycopy(now, 0, last, 0, 4);
        double forward = (dFl + dFr + dBl + dBr) / 4, right = (dFl - dFr - dBl + dBr) / 4;
        double h = Math.toRadians(wheels.heading);
        ax += -forward * Math.sin(h) + right * Math.cos(h);
        ay += forward * Math.cos(h) + right * Math.sin(h);
        trail.add(new double[] {ax, ay});
    }

    private List<String> runStep(Alliance mirror, FieldMap map, double toX, double toY, boolean poseKnown) {
        AutoDrive auto = new AutoDrive(cfg, false, 1, wheels.fl.motor, wheels.fr.motor, wheels.bl.motor, wheels.br.motor,
                () -> wheels.heading, () -> wheels.heading = 0, () -> wheels.active, this::wheelLoop, s -> { }, () -> wheels.nanos);
        AutoRoutine routine = new AutoRoutine(() -> wheels.active, () -> { }, new Fakes.FakeTelemetry().telemetry).drive(auto);
        if (mirror != null) routine.mirrorFor(mirror);
        routine.driveRoute("Home", this::wheelPose, () -> true, () -> poseKnown, map, toX, toY, 0, 0.6, 1);
        routine.runAll();
        return routine.getReport();
    }

    @Test public void theStepGoesAroundTheBoxFromWhereTheRobotIsWhenItStarts() {
        ax = 2;
        ay = -30;
        FieldMap map = FieldMap.margin(6).keepOut("box", -20, -10, 10, 10);
        assertEquals(Arrays.asList("Home: done"), runStep(null, map, 2, 30, true));
        assertEquals(2, ax, 1.2);
        assertEquals(30, ay, 1.2);
        for (double[] p : trail) {
            assertFalse("not through the box: " + p[0] + ", " + p[1], p[0] > -20 && p[0] < 10 && p[1] > -10 && p[1] < 10);
        }
    }

    @Test public void redMirrorsTheBoxesAndTheSpot() {
        // BLUE: a box left of the line. RED (x flips): the same box stands right of it.
        ax = -2;      // RED's real x; BLUE's x 2
        ay = -30;
        FieldMap map = FieldMap.margin(6).keepOut("box", -20, -10, 10, 10);
        assertEquals(Arrays.asList("Home: done"), runStep(Alliance.RED, map, 2, 30, true));
        assertEquals(-2, ax, 1.2);
        assertEquals(30, ay, 1.2);
        double far = 0;
        for (double[] p : trail) {
            assertFalse(p[0] > -10 && p[0] < 20 && p[1] > -10 && p[1] < 10);
            far = Math.min(far, p[0]);
        }
        assertTrue("around the mirrored box's nearer side, x -16: " + far, far < -14);
    }

    @Test public void noRouteAndNoPoseLeaveTheRobotWhereItIs() {
        ay = -30;
        FieldMap wall = FieldMap.margin(5).keepOut("barrier", -72, -2, 72, 2);
        assertEquals(Arrays.asList("Home: no route: the robot stayed where it was"), runStep(null, wall, 0, 30, true));
        assertEquals(Arrays.asList("Home: no pose: the robot stayed where it was (give start(...) a spot)"),
                runStep(null, FieldMap.margin(5), 0, 30, false));
        assertEquals(0, ax, 0);
        assertEquals(-30, ay, 0);
    }

    @Test public void aLineThatFailsEndsTheStep() {
        // Time runs out on the first line: the report says so, and the rest of the route is not driven.
        cfg.actionTimeoutSec = 0.05;
        ax = 2;
        ay = -30;
        List<String> report = runStep(null, FieldMap.margin(6).keepOut("box", -20, -10, 10, 10), 2, 30, true);
        assertEquals(1, report.size());
        assertTrue(report.get(0), report.get(0).equals("Home: TIMED_OUT") || report.get(0).equals("Home: STALLED"));
        assertTrue("it stopped on the way", ay < -5);
    }
}
