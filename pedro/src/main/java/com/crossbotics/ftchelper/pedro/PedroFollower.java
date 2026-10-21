package com.crossbotics.ftchelper.pedro;

import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.robot.PathFollower;
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

    public PedroFollower(Follower follower, PedroLocalizer localizer, List<DcMotor> driveMotors) {
        this.follower = follower;
        this.localizer = localizer;
        this.driveMotors = driveMotors;
        // At the end of a path: stop, do not hold the end point (the next step decides what to do).
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
    }

    @Override
    public void follow(Object path, boolean mirrorX) {
        check(path);
        // Pedro's own path: used as it is (in Pedro's frame, never mirrored).
        Path p = path instanceof PedroPath ? ((PedroPath) path).build(mirrorX) : (Path) path;
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
        // (Pedro issue #191). The mode says the truth.
        if (!follower.following()) finish();
    }

    @Override
    public boolean isBusy() {
        return running && follower.following();
    }

    @Override
    public void cancel() {
        if (running) finish();
    }

    @Override
    public double remainingIn() {
        return running ? follower.remainingDistance() : 0;
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
        if (running) t.line("Path", String.format(Locale.US, "%.0f in to go", follower.remainingDistance()));
    }
}
