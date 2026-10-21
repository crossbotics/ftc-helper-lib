package com.crossbotics.ftchelper.pedro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.crossbotics.ftchelper.util.FieldPose;
import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.controllers.Controller;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Matrix;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Vector2D;
import com.pedropathing.paths.Path;
import com.qualcomm.robotcore.hardware.DcMotor;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

/** The Pedro module on a pretend robot: no hardware, a pretend clock. */
public class PedroTest {

    // A pretend odometry: the pose is set by the test (device frame, like a Pinpoint).
    static final class FakeTracker extends PoseTracker {
        double devX, devY, devH;
        FakeTracker() { super("odo"); }
        @Override public boolean isReady() { return true; }
        @Override public String status() { return "ready"; }
        @Override public String kind() { return "fake"; }
        @Override protected double[] read() { return new double[]{devX, devY, devH}; }
        @Override protected void write(double x, double y, double h) { devX = x; devY = y; devH = h; }
    }

    // A pretend mecanum: powers move the robot at up to 40 in/s and 180 deg/s.
    static final class FakeDrive implements Drivetrain {
        DrivePowers last = DrivePowers.zero();
        @Override public void drive(DrivePowers p, boolean manual) { last = p; }
        @Override public void stop() { last = DrivePowers.zero(); }
        @Override public void stop(boolean b) { stop(); }
        @Override public double maxScaling(DrivePowers p, DrivePowers d) { return 1; }
        @Override public double interpolateVelocity(double x, double y, double t) { return x; }
        @Override public Map<String, Object> debug() { return Collections.emptyMap(); }
    }

    static ForesightConfig config() {
        // Made-up numbers for a desktop test (Pedro issue #191 used the same kind); a real robot uses
        // the numbers from Pedro's tuning OpModes.
        return new ForesightConfig(c -> {
            c.headingFeedback.set(Controller.proportional(1.0));
            c.forwardTranslational.set(Controller.proportional(0.1));
            c.strafeTranslational.set(Controller.proportional(0.1));
            c.brake.set(Controller.proportionalFeedforward(0.01));
            c.coast.set(Controller.proportional(0.01));
            c.linearBrakeCoefficients.set(new Matrix(new double[][]{{0.1, 0}, {0, 0.1}}));
            c.quadraticBrakeCoefficients.set(new Matrix(new double[][]{{0.01, 0}, {0, 0.01}}));
            c.headingBrakeCoefficients.set(Vector2D.cartesian(0.1, 0.01));
            c.maxAchievableForwardVelocity.set(40.0);
            c.maxAchievableStrafeVelocity.set(40.0);
            c.naturalForwardDeceleration.set(60.0);
            c.naturalStrafeDeceleration.set(60.0);
        });
    }

    private final FakeTracker tracker = new FakeTracker();
    private final FakeDrive drive = new FakeDrive();
    private long nanos = 1_000_000_000L;
    private final PedroLocalizer localizer = new PedroLocalizer(tracker, () -> nanos);
    private final Follower pedro = new Follower(localizer, drive, new Foresight(config()));
    private final PedroFollower follower = new PedroFollower(pedro, localizer, Collections.<DcMotor>emptyList());

    // One 20 ms loop: the pretend robot moves by last loop's powers, the odometry reads, Pedro runs.
    private void loop() {
        double dt = 0.02;
        FieldPose p = tracker.pose();
        double h = Math.toRadians(p.headingDeg + 90);   // Pedro's heading: the way the front points
        double fwd = drive.last.forward() * 40 * dt, left = drive.last.strafe() * 40 * dt;
        double x = p.x + fwd * Math.cos(h) - left * Math.sin(h);
        double y = p.y + fwd * Math.sin(h) + left * Math.cos(h);
        double heading = p.headingDeg + drive.last.turn() * 180 * dt;
        tracker.write(y, -x, heading);   // our frame to the device's
        nanos += (long) (dt * 1e9);
        tracker.update();
        follower.update();
    }

    @Test public void framesRoundTrip() {
        Pose p = PedroFrame.toPedro(-30, 12, 45);
        assertEquals(-30, p.x(), 1e-9);
        assertEquals(12, p.y(), 1e-9);
        assertEquals(Math.toRadians(135), p.heading(), 1e-9);
        FieldPose back = PedroFrame.fromPedro(p);
        assertEquals(45, back.headingDeg, 1e-9);
        // Pedro heading 180 degrees faces -x (left): ours is +90 (counterclockwise from "away").
        assertEquals(90, PedroFrame.fromPedro(new Pose(0, 0, Math.PI)).headingDeg, 1e-9);
    }

    @Test public void aMirroredPathFlipsXAndHeadings() {
        Path p = PedroPath.line(-56, -61, -30, -30).turnTo(0, 45).build(true);
        Pose end = p.endPose();
        assertEquals(30, end.x(), 1e-6);
        assertEquals(-30, end.y(), 1e-6);
        assertEquals(-45, PedroFrame.fromPedro(end).headingDeg, 1e-6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aCurveNeedsAControlPoint() {
        PedroPath.curve(0, 0, 10, 10);
    }

    @Test public void theLocalizerWorksOutTheSpeed() {
        tracker.update();
        localizer.refresh();
        tracker.write(1, 0, 0);   // 1 in forward (our y) in 20 ms: 50 in/s
        nanos += 20_000_000L;
        tracker.update();
        localizer.refresh();
        assertEquals(0, localizer.state().velocity().vx, 1e-6);
        assertEquals(50, localizer.state().velocity().vy, 1e-6);
    }

    @Test public void setPoseReachesTheOdometry() {
        localizer.setPose(PedroFrame.toPedro(-56, -61, 0));
        FieldPose p = tracker.pose();
        assertEquals(-56, p.x, 1e-9);
        assertEquals(-61, p.y, 1e-9);
    }

    @Test public void followsALineAndStops() {
        tracker.setPose(new FieldPose(0, 0, 0));
        tracker.update();
        follower.follow(PedroPath.line(0, 0, 0, 24).keepHeading(0), false);
        assertTrue(follower.isBusy());
        int n = 0;
        while (follower.isBusy() && n++ < 500) loop();
        assertFalse("still busy after 10 s", follower.isBusy());
        FieldPose p = tracker.pose();
        assertEquals(0, p.x, 2.0);
        assertEquals(24, p.y, 2.0);
        assertEquals(0, drive.last.forward(), 1e-9);
    }

    @Test public void cancelStopsTheWheels() {
        tracker.update();
        follower.follow(PedroPath.line(0, 0, 0, 48), false);
        for (int i = 0; i < 10; i++) loop();
        follower.cancel();
        assertFalse(follower.isBusy());
        assertEquals(0, drive.last.forward(), 1e-9);
    }

    // Sideways at a fixed heading: checks the heading turn between the frames (a wrong one drives off).
    @Test public void strafesSidewaysToAPoint() {
        tracker.setPose(new FieldPose(0, 0, 0));
        tracker.update();
        follower.follow(PedroPath.line(0, 0, 24, 0).keepHeading(0), false);
        int n = 0;
        while (follower.isBusy() && n++ < 500) loop();
        assertFalse("still busy after 10 s", follower.isBusy());
        FieldPose p = tracker.pose();
        assertEquals(24, p.x, 2.0);
        assertEquals(0, p.y, 2.0);
        assertEquals(0, p.headingDeg, 5.0);
    }

    // A curve that turns on the way, mirrored: ends at the flipped point and heading.
    @Test public void followsAMirroredCurve() {
        tracker.setPose(new FieldPose(0, 0, 0));
        tracker.update();
        follower.follow(PedroPath.curve(0, 0, -20, 10, -24, 30).turnTo(0, 45), true);
        int n = 0;
        while (follower.isBusy() && n++ < 500) loop();
        assertFalse("still busy after 10 s", follower.isBusy());
        FieldPose p = tracker.pose();
        // Within Pedro's own "done" limits (translationalDeviationTolerance, 2.5 in by default):
        // the made-up gains stop a little short on a curve. The point is the mirrored side and heading.
        assertEquals(24, p.x, 2.5);
        assertEquals(30, p.y, 2.5);
        assertEquals(-45, p.headingDeg, 11.25);   // headingDeviationTolerance, 11.25 degrees by default
    }

    // In a tool (Pedro's tuners) there is no robot loop: Pedro's update() reads the odometry itself.
    @Test public void aToolLocalizerReadsTheOdometryItself() {
        PedroLocalizer tool = PedroLocalizer.readingItself(tracker);
        tracker.devX = 10;   // 10 in forward = our y
        tool.update();
        assertEquals(10, tool.state().pose().y(), 1e-9);
        assertEquals(0, tool.state().pose().x(), 1e-9);
    }

    // Facing a point: Pedro's heading points from the robot to it; mirrored, the point flips too.
    @Test public void facePointAimsAtThePoint() {
        Path p = PedroPath.line(0, 0, 0, 24).facePoint(-24, 24).build(false);
        // At the start (0, 0) the point (-24, 24) is up and to the left: our heading +45.
        assertEquals(45, PedroFrame.fromPedro(new Pose(0, 0, p.heading(0))).headingDeg, 1e-6);
        Path m = PedroPath.line(0, 0, 0, 24).facePoint(-24, 24).build(true);
        assertEquals(-45, PedroFrame.fromPedro(new Pose(0, 0, m.heading(0))).headingDeg, 1e-6);
    }
}
