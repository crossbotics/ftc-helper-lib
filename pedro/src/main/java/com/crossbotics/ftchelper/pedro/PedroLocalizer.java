package com.crossbotics.ftchelper.pedro;

import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.crossbotics.ftchelper.util.FieldPose;
import com.pedropathing.localization.Localizer;
import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Velocity;

import java.util.function.LongSupplier;

/**
 * Gives Pedro the pose from Robot.java's odometry(...) line, so the robot has one odometry, set up
 * and tuned once (the Odometry Tuner). Pedro does not read the device itself.
 *
 * <p>The robot reads the odometry once per loop, first. {@link #refresh()} then copies that pose and
 * works out the speed (field frame, inches and radians per second) from the change since last loop.
 */
public class PedroLocalizer implements Localizer {
    private final PoseTracker tracker;
    private final LongSupplier clockNanos;
    private Pose pose = Pose.zero();
    private Velocity velocity = Velocity.zero();
    private long lastNanos = 0;
    // True for a tool with no robot loop (Pedro's tuners): update() then reads the odometry itself.
    private boolean readsOdometry = false;

    public PedroLocalizer(PoseTracker tracker) {
        this(tracker, System::nanoTime);
    }

    /** With your own clock (for tests). */
    public PedroLocalizer(PoseTracker tracker, LongSupplier clockNanos) {
        this.tracker = tracker;
        this.clockNanos = clockNanos;
        pose = PedroFrame.toPedro(tracker.pose());
    }

    /**
     * For a tool with no robot loop around it (Pedro's tuners): Pedro's update() then reads the
     * odometry too. See {@link PedroTuning}.
     */
    public static PedroLocalizer readingItself(PoseTracker tracker) {
        PedroLocalizer l = new PedroLocalizer(tracker);
        l.readsOdometry = true;
        return l;
    }

    /** Copies this loop's pose from the odometry and works out the speed. Once per loop. */
    public void refresh() {
        Pose now = PedroFrame.toPedro(tracker.pose());
        long t = clockNanos.getAsLong();
        if (lastNanos != 0) {
            double dt = (t - lastNanos) / 1e9;
            if (dt < 0.001) return;   // called twice in one loop: keep the speed from the first call
            if (dt < 0.5) {
                double dh = Math.atan2(Math.sin(now.heading() - pose.heading()), Math.cos(now.heading() - pose.heading()));
                velocity = new Velocity((now.x() - pose.x()) / dt, (now.y() - pose.y()) / dt, dh / dt);
            } else {
                velocity = Velocity.zero();   // a long pause: the change says nothing about the speed now
            }
        }
        pose = now;
        lastNanos = t;
    }

    /**
     * Pedro calls this from its own update(). On the robot: nothing to do, {@link #refresh()} already
     * ran this loop. In a tool ({@link #readingItself}): reads the odometry, then refreshes.
     */
    @Override public void update() {
        if (!readsOdometry) return;
        tracker.update();
        refresh();
    }

    @Override public MotionState state() {
        return MotionState.ofVelocity(pose, velocity);
    }

    /** Pedro says "the robot is here now": passed on to the odometry. */
    @Override public void setPose(Pose p) {
        tracker.setPose(PedroFrame.fromPedro(p));
        pose = p;
        velocity = Velocity.zero();
    }

    /** Back to (0, 0), heading 0 (ours). */
    @Override public void reset() {
        setPose(PedroFrame.toPedro(new FieldPose(0, 0, 0)));
    }

    /** True when the odometry works (not calibrating, no fault). */
    public boolean isReady() {
        return tracker.isReady();
    }
}
