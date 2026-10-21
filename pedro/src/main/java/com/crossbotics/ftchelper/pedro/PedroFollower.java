package com.crossbotics.ftchelper.pedro;

import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.robot.PathFollower;
import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.follower.Follower;
import com.pedropathing.paths.Path;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.qualcomm.robotcore.hardware.DcMotor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pedro's Follower as a part of the robot. Made by {@link Pedro#follower}; Robot.java's
 * {@code follower(...)} line adds it, and {@code Steps.follow(...)} uses it.
 *
 * <p>Every loop it copies the pose from the odometry. It drives only while a path runs, so it never
 * fights TeleOp driving or a {@code driveTo} step.
 */
public class PedroFollower implements PathFollower {
    private final Follower follower;
    private final PedroLocalizer localizer;
    private final List<DcMotor> driveMotors;
    // Each drive motor's run mode before a path: put back after it.
    private final List<DcMotor.RunMode> savedModes = new ArrayList<>();
    // The drive's brake/coast setting before a path (Pedro sets coast while it drives): put back after.
    private DcMotor.ZeroPowerBehavior savedZero = null;
    private boolean running = false;
    // The hold at the end of a path: its time limit, when it started (0: not yet), its point (Pedro's frame).
    private double holdLimitSec = 0;
    private long holdStartNanos = 0;
    private com.pedropathing.math.Pose holdPoint = null;
    // A note for the Driver Station after a hold ran out of time, and until when it shows.
    private String holdNote = "";
    private long holdNoteUntil = 0;
    private final java.util.function.LongSupplier clockNanos;

    public PedroFollower(Follower follower, PedroLocalizer localizer, List<DcMotor> driveMotors) {
        this(follower, localizer, driveMotors, System::nanoTime);
    }

    /** With your own clock (for tests). */
    public PedroFollower(Follower follower, PedroLocalizer localizer, List<DcMotor> driveMotors,
                         java.util.function.LongSupplier clockNanos) {
        this.clockNanos = clockNanos;
        this.follower = follower;
        this.localizer = localizer;
        this.driveMotors = driveMotors;
        // At the end of a path: stop, unless the path asks to hold its end (set per path in follow()).
        follower.holdEnd.set(false);
    }

    /**
     * Pedro's own Follower, for its debug data and settings. It is updated only during a follow step:
     * Pedro commands you start yourself (hold, manual) do not run.
     */
    public Follower pedro() {
        return follower;
    }

    @Override
    public void check(Object path) {
        if (!(path instanceof PedroPath) && !(path instanceof Path)) {
            throw new IllegalArgumentException("give a PedroPath (PedroPath.line(...) or .curve(...)) or a Pedro Path, not "
                    + (path == null ? "null" : path.getClass().getSimpleName()));
        }
        if (path instanceof PedroPath && ((PedroPath) path).hasSpeedLimit() && foresight() == null) {
            throw new IllegalArgumentException("maxSpeed(...) needs Pedro's Foresight follower (Pedro.follower(ForesightConfig))");
        }
    }

    // Pedro's Foresight settings, or null with another algorithm.
    private ForesightConfig foresight() {
        return follower.algorithm() instanceof Foresight ? ((Foresight) follower.algorithm()).config : null;
    }

    @Override
    public void follow(Object path, boolean mirrorX) {
        check(path);
        // Pedro's own path: used as it is (in Pedro's frame, never mirrored).
        Path p;
        double hold = 0;
        if (path instanceof PedroPath) {
            PedroPath pp = (PedroPath) path;
            final ForesightConfig cfg = foresight();
            p = pp.build(mirrorX, cfg == null ? null : speed -> cfg.maxPathSpeed.at(speed));
            hold = pp.holdSeconds();
        } else {
            p = (Path) path;
        }
        // Hold the end point only if the path asks for it (PedroPath.holdEnd()).
        follower.holdEnd.set(hold > 0);
        holdLimitSec = hold;
        holdStartNanos = 0;
        holdPoint = p.endPose();
        // Pedro sets plain power. Autonomous runs the drive motors at a set speed (RUN_USING_ENCODER),
        // which would change what Pedro's power means: plain power while the path runs.
        if (!running) {
            savedModes.clear();
            for (DcMotor m : driveMotors) {
                savedModes.add(m.getMode());
                if (m.getMode() != DcMotor.RunMode.RUN_WITHOUT_ENCODER) m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            }
            savedZero = driveMotors.isEmpty() ? null : driveMotors.get(0).getZeroPowerBehavior();
            // Tell Pedro's motor cache what is really set, so its own changes are really sent.
            setZero(savedZero);
        }
        running = true;
        follower.follow(p);
    }

    @Override
    public void update() {
        localizer.refresh();
        if (!running) return;
        follower.update();
        // following(), not isBusy(): Pedro 3.0.1 kept isBusy() true after a path with holdEnd off
        // (Pedro issue #191). The mode says the truth. While it holds the end point, it keeps running.
        if (!follower.following() && !follower.holding()) {
            finish();
            return;
        }
        if (follower.holding()) {
            long now = clockNanos.getAsLong();
            if (holdStartNanos == 0) holdStartNanos = now;
            // A time limit: a wedged robot must not push for the rest of autonomous.
            if ((now - holdStartNanos) / 1e9 > holdLimitSec) {
                finish();
                holdNote = String.format(Locale.US, "Path: the hold ended after %.0f s (its limit)", holdLimitSec);
                holdNoteUntil = now + 3_000_000_000L;
            }
        }
    }

    /** How far the robot is from the point it holds, inches (NaN when not holding). */
    public double holdErrorIn() {
        if (!isHolding() || holdPoint == null) return Double.NaN;
        com.pedropathing.math.Pose at = localizer.state().pose();
        return Math.hypot(at.x() - holdPoint.x(), at.y() - holdPoint.y());
    }

    @Override
    public boolean isBusy() {
        return running && follower.following();
    }

    @Override
    public boolean isHolding() {
        return running && follower.holding();
    }

    @Override
    public void cancel() {
        if (running) finish();
    }

    @Override
    public double remainingIn() {
        return running && follower.following() ? follower.remainingDistance() : 0;
    }

    @Override
    public boolean isReady() {
        return localizer.isReady();
    }

    @Override
    public void stop() {
        cancel();
    }

    // Stops the wheels and puts the run modes back.
    private void finish() {
        follower.stop();
        // Pedro's stop() only ends the path; its drivetrain keeps the last powers until told.
        follower.drivetrain.stop();
        setZero(savedZero);
        for (DcMotor m : driveMotors) m.setPower(0);
        for (int i = 0; i < savedModes.size() && i < driveMotors.size(); i++) {
            if (driveMotors.get(i).getMode() != savedModes.get(i)) driveMotors.get(i).setMode(savedModes.get(i));
        }
        savedModes.clear();
        running = false;
    }

    // Sets brake/coast through Pedro's Mecanum when there is one (its cache stays right), else directly.
    private void setZero(DcMotor.ZeroPowerBehavior b) {
        if (b == null) return;
        if (follower.drivetrain instanceof Mecanum) ((Mecanum) follower.drivetrain).setZeroPowerBehavior(b);
        else for (DcMotor m : driveMotors) m.setZeroPowerBehavior(b);
    }

    @Override
    public String name() {
        return "Pedro";
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!holdNote.isEmpty() && clockNanos.getAsLong() < holdNoteUntil) t.warn(holdNote);
        if (isHolding()) t.line("Path", String.format(Locale.US, "holding the end (%.1f in off)", holdErrorIn()));
        else if (running) t.line("Path", String.format(Locale.US, "%.0f in to go", follower.remainingDistance()));
    }
}
