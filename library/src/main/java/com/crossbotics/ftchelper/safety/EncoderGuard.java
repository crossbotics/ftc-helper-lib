package com.crossbotics.ftchelper.safety;

/**
 * Notices a flywheel encoder that cannot be trusted: the arithmetic only (no motor). WHY: speed control
 * that reads 0 from a broken cable gives full power for as long as it runs.
 *
 * <p>Three ways to fail:
 * <ul>
 * <li>a power of {@link #MIN_POWER} or more, and under {@link #STILL_RPM} read, for {@link #STILL_SEC};</li>
 * <li>the wheel reads {@link #STILL_RPM} or more the other way than the power, two readings in a row
 *     (one odd reading is not enough);</li>
 * <li>a reading that is not a number: at once.</li>
 * </ul>
 * It cannot see every failure: a believable wrong speed passes.
 *
 * <pre>{@code
 * String why = guard.update(powerSentLastLoop, measuredRpm, nowSec);
 * if (!why.isEmpty()) motor.setPower(0);      // and keep it at 0 until switched off and on
 * }</pre>
 *
 * Docs: docs/modules/safety.md
 */
public final class EncoderGuard {
    /** The power from which a wheel must turn. */
    public static final double MIN_POWER = 0.3;
    /** Under this speed a wheel counts as not turning, RPM. */
    public static final double STILL_RPM = 60;
    /** How long it may read "not turning" under power, seconds. */
    public static final double STILL_SEC = 0.5;

    private double stillSince = Double.NaN;
    private int backwardReadings = 0;

    /**
     * One reading.
     *
     * @param power  the power the motor had since the last reading, -1 to 1
     * @param rpm    the speed read now, with its sign
     * @param nowSec the time now, seconds
     * @return "" while it looks right, else what is wrong, in one short plain line for the Driver Station
     */
    public String update(double power, double rpm, double nowSec) {
        if (Double.isNaN(rpm) || Double.isInfinite(rpm)) return "Speed reading is not a number.";
        boolean backward = power != 0 && Math.abs(rpm) >= STILL_RPM && Math.signum(rpm) != Math.signum(power);
        backwardReadings = backward ? backwardReadings + 1 : 0;
        if (backwardReadings >= 2) return "Speed reads backward.";
        if (Math.abs(power) >= MIN_POWER && Math.abs(rpm) < STILL_RPM) {
            if (Double.isNaN(stillSince)) stillSince = nowSec;
            // (The tiny margin: 0.5 s of small steps must not add up to 0.4999999.)
            if (nowSec - stillSince >= STILL_SEC - 1e-9) return "Speed stays near 0 with the power on.";
        } else {
            stillSince = Double.NaN;
        }
        return "";
    }

    /** Starts over (stopped, or switched off and on). */
    public void reset() {
        stillSince = Double.NaN;
        backwardReadings = 0;
    }
}
