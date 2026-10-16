package io.github.jasoncross.ftchelper.drive;

import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import io.github.jasoncross.ftchelper.match.MatchMemory;
import io.github.jasoncross.ftchelper.util.Angles;
import io.github.jasoncross.ftchelper.util.FieldPose;
import io.github.jasoncross.ftchelper.util.PoseStore;
import java.util.function.DoubleSupplier;

/**
 * Which way the robot faces compared with the DRIVER, for field-centric driving.
 * 0 = pointing straight away from the driver. Turning left (counterclockwise) makes it bigger.
 *
 * <p>Why it is needed: TeleOp sets up the IMU (turn sensor) again in INIT, and that makes "0"
 * the way the robot faces at that moment, which is wherever autonomous left it. Drivers may not touch
 * the robot between autonomous and TeleOp. So without help, field-centric "forward" would be wrong
 * until the driver turns the robot and presses a reset button.
 *
 * <p>This class fixes that. Autonomous saves its final heading (EasyAuto does this for you). Made in
 * TeleOp INIT, a DriverHeading reads that saved heading and keeps counting from it, so "forward" on
 * the stick is the same as at the start of autonomous. It is used only if it is less than
 * {@link MatchMemory#MAX_AGE_SEC} old. Otherwise, 0 = the way the robot faces in INIT.
 *
 * <p>Call {@link #forgetSaved()} right after START: from then on the saved heading belongs to this
 * TeleOp run, and a later TeleOp run (after the robot was driven) must not use it. It is not
 * forgotten in INIT, so pressing INIT, STOP, INIT again before a match still works.
 *
 * <pre>{@code
 * // TeleOp INIT, after the IMU is set up:
 * DriverHeading heading = new DriverHeading(imu);
 * controls.bindPress(Pad.G1, Btn.BACK, heading::reset);   // manual reset: face away first
 * waitForStart();
 * heading.forgetSaved();                                  // used by this run now
 *
 * // every loop:
 * drive.driveFieldCentric(forward, strafe, turn, heading.degrees(), speed);
 * }</pre>
 *
 * <p>For autonomous that does not use EasyAuto: at its end, call
 * {@code PoseStore.save(new FieldPose(0, 0, heading))}, where heading is measured from "away from
 * the driver" (for example {@code auto.headingDeg()} if the robot started facing away).
 *
 * Docs: docs/modules/drive.md (section "DriverHeading")
 */
public final class DriverHeading {
    // Reads the IMU's heading (0 = where it was last reset), and resets it. Handed in so tests can fake them.
    private final DoubleSupplier imuHeading;
    private final Runnable resetImu;
    // Degrees added to the IMU's heading: the heading autonomous ended with, or 0.
    private double offsetDeg;
    // True while the offset came from autonomous (until reset()).
    private boolean fromAuto;

    /**
     * Makes the driver heading for this IMU. Call it in TeleOp INIT, AFTER the IMU is set up
     * (setting up the IMU resets it to 0). Takes autonomous's saved heading if there is a recent one.
     *
     * @param imu the IMU from {@code ImuSetup.get(...)}
     */
    public DriverHeading(IMU imu) {
        this(() -> ImuSetup.headingDeg(imu), imu::resetYaw, takeSavedHeading());
    }

    /** For tests: a pretend IMU and a given saved heading (null = none). */
    DriverHeading(DoubleSupplier imuHeading, Runnable resetImu, Double savedHeadingDeg) {
        this.imuHeading = imuHeading;
        this.resetImu = resetImu;
        // "a ? b : c" = if a is true use b, otherwise c. No saved heading: start from 0.
        this.offsetDeg = savedHeadingDeg != null ? savedHeadingDeg : 0;
        this.fromAuto = savedHeadingDeg != null;
    }

    // Reads the heading autonomous saved, if it is from this match. Does not clear it, so a second
    // INIT before the match still finds it; forgetSaved() clears it after START.
    static Double takeSavedHeading() {
        if (PoseStore.ageSec() > MatchMemory.MAX_AGE_SEC) return null;
        FieldPose saved = PoseStore.peek();
        return saved != null ? saved.headingDeg : null;
    }

    /**
     * Clears the heading autonomous saved. Call right after START, so a later TeleOp run (after the
     * robot has been driven) does not use it. This object keeps its own copy.
     */
    public void forgetSaved() {
        PoseStore.clear();
    }

    /** The heading compared with the driver, -180 to 180 degrees. 0 = away from the driver. Call every loop. */
    public double degrees() {
        return Angles.wrapDegrees(imuHeading.getAsDouble() + offsetDeg);
    }

    /**
     * "The robot now faces away from me": makes the current direction 0. For a button (e.g. BACK).
     * Turn the robot to face straight away from the driver first.
     */
    public void reset() {
        resetImu.run();
        offsetDeg = 0;
        fromAuto = false;
    }

    /** True if the heading still counts from autonomous's (no reset since). Handy for telemetry. */
    public boolean isFromAuto() {
        return fromAuto;
    }
}
