package com.crossbotics.ftchelper.odometry;

import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.FieldPose;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Where the robot is on the field, from odometry: small wheels ("pods") or a sensor that measure how
 * far the robot moved. One class for every kind: a goBILDA Pinpoint, a SparkFun OTOS, or three or two
 * dead wheels on motor encoder ports.
 *
 * <p><b>The frame</b> (the same as autonomous start spots, {@code Steps.start}): seen from your
 * drivers' wall, x inches to the right, y inches away from you, heading 0 = facing away from you,
 * counterclockwise positive. Where (0, 0) is: wherever you set it ({@link #setPose}); autonomous sets
 * it from the start spot.
 *
 * <p>It is a {@link Subsystem}: {@link #update()} runs once per loop, first, so every part reads this
 * loop's pose. It never waits.
 *
 * Docs: docs/modules/odometry.md
 */
public abstract class PoseTracker implements Subsystem {
    // The pose in our frame (x right, y away, heading CCW from "away"), this loop.
    private final FieldPose pose = new FieldPose();
    // Inches per second and degrees per second, this loop (for telemetry and "has it stopped?").
    private double speedInPerSec = 0, turnDegPerSec = 0;
    // The robot's speed over the field, inches per second, in our frame (x right, y away): which WAY it
    // moves, not only how fast. Shooting on the move needs it (the piece keeps the robot's speed).
    private double velX = 0, velY = 0;
    // For devices that do not tell their speed (dead wheels): the last pose and when it was read, to
    // work the speed out from how far the robot moved. haveLast = false: no last pose yet.
    private boolean haveLast = false;
    private long lastNanos = 0;
    private double lastX, lastY, lastH;
    // The time, in nanoseconds. Tests use a pretend clock (useClock).
    private LongSupplier clock = System::nanoTime;
    // How much one new finite-difference speed counts (a short smoothing time, seconds): encoder counts
    // between two loops are few, so a raw difference jumps around.
    private static final double SMOOTH_SEC = 0.06;
    private final String name;
    // A pose set while the device was not ready yet (a Pinpoint calibrating): written once it is.
    private FieldPose pending = null;

    protected PoseTracker(String name) {
        this.name = name;
    }

    /**
     * Reads the device once and works out the new pose. Called once per loop for you.
     */
    @Override
    public final void update() {
        if (pending != null) {
            // Read anyway: a Pinpoint's status is only fresh after a read (getDeviceStatus() gives the
            // value its last update() saved). Without it, "calibrating" would never change.
            read();
            if (!isReady()) return;   // keep the pose we were given until the device can take it
            write(pending.y, -pending.x, pending.headingDeg);
            pending = null;
            return;   // the device has the new pose; the next loop reads from it
        }
        double[] p = read();
        if (p == null) return;
        // The device's own frame: X forward and Y left at heading 0 (goBILDA, SparkFun and dead
        // wheels all use it). Ours is turned a quarter turn: our x (right) = -Y, our y (away) = X.
        pose.x = -p[1];
        pose.y = p[0];
        pose.headingDeg = Angles.wrapDegrees(p[2]);
        long now = clock.getAsLong();
        if (p.length >= 5) {
            // A Pinpoint and an OTOS give their speed over the FIELD (not turned with the robot), in
            // their own frame like the pose: our x = -their Y, our y = their X.
            velX = -p[4];
            velY = p[3];
        } else {
            // Dead wheels: the speed from how far the robot moved since the last loop, smoothed.
            double dt = !haveLast ? 0 : (now - lastNanos) / 1e9;
            if (dt > 1e-4 && dt < 0.5) {
                double k = dt / (SMOOTH_SEC + dt);
                velX += k * ((pose.x - lastX) / dt - velX);
                velY += k * ((pose.y - lastY) / dt - velY);
                if (p.length < 6) {
                    turnDegPerSec += k * (Angles.wrapDegrees(pose.headingDeg - lastH) / dt - turnDegPerSec);
                }
            } else if (dt >= 0.5) {
                velX = velY = 0;   // a long pause: start again rather than average over it
                if (p.length < 6) turnDegPerSec = 0;
            }
        }
        speedInPerSec = Math.hypot(velX, velY);
        if (p.length >= 6) turnDegPerSec = p[5];
        haveLast = true;
        lastNanos = now;
        lastX = pose.x;
        lastY = pose.y;
        lastH = pose.headingDeg;
    }

    /** This loop's pose (a copy: changing it does not move the robot). */
    public final FieldPose pose() {
        return pose.copy();
    }

    /**
     * Inches per second (how fast the robot moves). A Pinpoint or OTOS tells it; with dead wheels it is
     * worked out from how far the robot moved between loops (smoothed).
     */
    public final double speedInPerSec() {
        return speedInPerSec;
    }

    /**
     * Degrees per second (how fast it turns, counterclockwise +). From the device when it tells,
     * otherwise worked out from the heading change between loops (smoothed).
     */
    public final double turnDegPerSec() {
        return turnDegPerSec;
    }

    /**
     * The robot's speed toward +x (to the RIGHT, seen from your drivers' wall), inches per second. With
     * {@link #velocityY()} it says which way the robot moves over the field, whichever way it faces.
     * From a Pinpoint or an OTOS directly; with dead wheels, from how far it moved between loops.
     * Shooting on the move uses it ({@code Tunables.Aim.shootOnMove}, docs/modules/robot.md#aim).
     */
    public final double velocityX() {
        return velX;
    }

    /** The robot's speed toward +y (AWAY from your drivers' wall), inches per second. See {@link #velocityX()}. */
    public final double velocityY() {
        return velY;
    }

    /**
     * Uses this clock instead of {@code System.nanoTime()} for the dead-wheel speed (tests: a pretend
     * clock). Nanoseconds.
     */
    public final void useClock(LongSupplier nanos) {
        clock = nanos;
        haveLast = false;
    }

    /**
     * Says "the robot is here now". Autonomous does this from its start spot; TeleOp from where
     * autonomous ended. Our frame (see the class).
     */
    public final void setPose(FieldPose p) {
        place(p);
        poseSet = true;
    }

    /**
     * Makes the way the robot faces now this heading, keeping x and y (TeleOp's "forward = away from
     * you now"). It does NOT count as setting the pose ({@link #isPoseSet()} stays as it was): x and y
     * are still only as good as before.
     */
    public final void setHeading(double headingDeg) {
        place(new FieldPose(pose.x, pose.y, headingDeg));
    }

    /**
     * True once {@link #setPose} was called: autonomous's start spot, the pose autonomous left for
     * TeleOp, or your own {@code setPose}. False means the pose is only "where the robot was turned
     * on = (0, 0)", which is NOT a place on the field: aiming by odometry stays off until then.
     */
    public final boolean isPoseSet() {
        return poseSet;
    }

    /**
     * Says "the pose is not known any more" ({@link #isPoseSet()} becomes false), keeping the numbers.
     * Aiming by odometry then stays off until the pose is set again ({@link #setPose}, TeleOp's "set
     * pose here"). TeleOp does this when the alliance is switched on a field that is not mirrored: the
     * pose from an autonomous run as the wrong alliance cannot be trusted. Never waits.
     */
    public final void unsetPose() {
        poseSet = false;
    }

    // Writes a pose to the device (or later, while it calibrates) and to this loop's pose.
    private void place(FieldPose p) {
        // Not ready yet (calibrating after INIT): written as soon as it is (see update()).
        if (!isReady()) pending = p.copy();
        // Back to the device's frame: X = our y, Y = -our x.
        else write(p.y, -p.x, p.headingDeg);
        pose.x = p.x;
        pose.y = p.y;
        pose.headingDeg = Angles.wrapDegrees(p.headingDeg);
        // A jump to a new pose is not a speed: start the dead-wheel difference again.
        haveLast = false;
        velX = velY = 0;
        speedInPerSec = 0;
    }

    /**
     * Moves the pose by a small correction (inches, our frame), keeping the heading, the speed and
     * {@link #isPoseSet()} as they are: the camera fix uses it, so a correction is not read as a jump
     * (setPose would set the speed to 0 and restart the dead-wheel difference).
     */
    public final void nudge(double dxIn, double dyIn) {
        if (!isReady()) return;
        write(pose.y + dyIn, -(pose.x + dxIn), pose.headingDeg);
        pose.x += dxIn;
        pose.y += dyIn;
        // The last position moves with it, so the next loop's difference is the robot's own motion.
        lastX += dxIn;
        lastY += dyIn;
    }

    // True once setPose was called (see isPoseSet()).
    private boolean poseSet = false;

    /** True when the device works and gives a pose (not calibrating, no fault). */
    public abstract boolean isReady();

    /** "ready", "calibrating", "Y pod not found"... for the Driver Station. */
    public abstract String status();

    /** What kind it is, e.g. "Pinpoint". */
    public abstract String kind();

    /**
     * The device's pose in its own frame: {X forward in, Y left in, heading CCW deg}, plus, if it
     * knows, {vX in/s, vY in/s, turn deg/s}. Null if there is nothing new.
     */
    protected abstract double[] read();

    /** Sets the device's pose in its own frame (X forward in, Y left in, heading CCW deg). */
    protected abstract void write(double xIn, double yIn, double headingDeg);

    @Override
    public String name() {
        return name;
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!isReady()) t.warn(name + " (" + kind() + "): " + status());
        t.line("Pose", String.format(Locale.US, "x %.1f, y %.1f in, heading %.1f", pose.x, pose.y, pose.headingDeg));
    }
}
