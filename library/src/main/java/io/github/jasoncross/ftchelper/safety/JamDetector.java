package io.github.jasoncross.ftchelper.safety;

/**
 * Tells when a motor is jammed: it is asked to spin but barely turns (or draws too much current)
 * for a while. No hardware here; {@code SpinMotor} and {@code Feeder} use it.
 *
 * <pre>{@code
 * JamDetector jam = new JamDetector(50, 0.3);   // slower than 50 ticks/s for 0.3 s = jammed
 * // every loop:
 * if (jam.update(power, motor.getVelocity(), getRuntime())) { ...back it out... }
 * }</pre>
 *
 * Docs: docs/modules/safety.md
 */
public final class JamDetector {
    private final double minSpeed;
    private final double timeSec;
    private double minPower = 0.15;
    private double maxCurrent = Double.POSITIVE_INFINITY;
    private double badSince = Double.NaN;

    /**
     * @param minSpeed slowest normal speed while powered (ticks per second, any sign)
     * @param timeSec  how long it must be too slow before it counts as jammed
     */
    public JamDetector(double minSpeed, double timeSec) {
        this.minSpeed = Math.abs(minSpeed);
        this.timeSec = Math.abs(timeSec);
    }

    /** Below this power the motor is not really trying, so it is never "jammed". Default 0.15. */
    public JamDetector setMinPower(double power) {
        minPower = Math.abs(power);
        return this;
    }

    /** Also counts as jammed when the current is above this many amps (for the same time). */
    public JamDetector setMaxCurrent(double amps) {
        maxCurrent = Math.abs(amps);
        return this;
    }

    /** Checks speed only. @return true while jammed */
    public boolean update(double power, double speed, double nowSec) {
        return update(power, speed, 0, nowSec);
    }

    /**
     * Checks speed and current.
     *
     * @param power   the power you are sending
     * @param speed   the measured speed (ticks per second)
     * @param amps    the measured current (0 if you do not measure it)
     * @param nowSec  the time now, in seconds
     * @return true while jammed
     */
    public boolean update(double power, double speed, double amps, double nowSec) {
        boolean trying = Math.abs(power) >= minPower;
        boolean bad = trying && (Math.abs(speed) < minSpeed || Math.abs(amps) > maxCurrent);
        if (!bad) {
            badSince = Double.NaN;
            return false;
        }
        if (Double.isNaN(badSince)) badSince = nowSec;
        return nowSec - badSince >= timeSec;
    }

    /** Starts over (after clearing a jam). */
    public void reset() {
        badSince = Double.NaN;
    }
}
