package com.crossbotics.ftchelper.safety;

/**
 * Tells when a motor is jammed: it is asked to spin but barely turns (or draws too much current)
 * for a while. No hardware here; {@code SpinMotor} and {@code MotorFeeder} use it.
 *
 * <p>Why it matters: a jammed motor gets hot, drains the battery and can strip gears or burn out.
 * Noticing the jam lets your code back the game piece out instead.
 *
 * <pre>{@code
 * JamDetector jam = new JamDetector(50, 0.3);   // slower than 50 ticks/s for 0.3 s = jammed
 * // every loop:
 * if (jam.update(power, motor.getVelocity(), getRuntime())) { ...back it out... }
 * }</pre>
 *
 * Docs: docs/modules/safety.md (section "JamDetector")
 */
public final class JamDetector {
    private final double minSpeed;
    private final double timeSec;
    private double minPower = 0.15;
    // Infinity = no current limit until setMaxCurrent() is called.
    private double maxCurrent = Double.POSITIVE_INFINITY;
    // When the motor first looked jammed, in seconds. NaN ("not a number") = it looks fine now.
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

    /**
     * Checks speed only. Call every loop.
     *
     * @param power   the power you are sending
     * @param speed   the measured speed (ticks per second)
     * @param nowSec  the time now, in seconds
     * @return true while jammed
     */
    public boolean update(double power, double speed, double nowSec) {
        return update(power, speed, 0, nowSec);
    }

    /**
     * Checks speed and current. Call every loop.
     *
     * @param power   the power you are sending
     * @param speed   the measured speed (ticks per second)
     * @param amps    the measured current (0 if you do not measure it)
     * @param nowSec  the time now, in seconds
     * @return true while jammed
     */
    public boolean update(double power, double speed, double amps, double nowSec) {
        // A motor at low power is allowed to be slow (or stopped): that is not a jam.
        boolean trying = Math.abs(power) >= minPower;
        // Looks jammed: trying, but too slow, or pulling too much current.
        boolean bad = trying && (Math.abs(speed) < minSpeed || Math.abs(amps) > maxCurrent);
        // Looks fine: forget any earlier bad moment.
        if (!bad) {
            badSince = Double.NaN;
            return false;
        }
        // Remember when the trouble started...
        if (Double.isNaN(badSince)) badSince = nowSec;
        // ...and call it a jam only once it has lasted timeSec. A motor is also slow for a moment
        // when it starts up, so one slow reading must not count.
        return nowSec - badSince >= timeSec;
    }

    /** Starts over (after clearing a jam). */
    public void reset() {
        badSince = Double.NaN;
    }
}
