package com.crossbotics.ftchelper.finders;

/**
 * The arithmetic behind the finders, kept apart so it can be tested on a laptop.
 * Every method is plain math: no hardware.
 *
 * Docs: docs/modules/finders.md
 */
public final class FinderMath {
    private FinderMath() { }

    /**
     * Starting speed-control gains for a flywheel from its top speed (the hub's velocity PIDF).
     * F makes the motor send the right power for a speed by itself: the hub's full power is 32767,
     * so F = 32767 / (top speed in counts per second). P = F / 10 and I = P / 10 fix what is left;
     * D = 0. A common starting point for REV hubs; fine-tune F with the flywheel finder.
     *
     * @param topTicksPerSec the speed at full power, encoder counts per second (sign ignored)
     * @return {P, I, D, F}, or all zeros if the top speed is 0
     */
    public static double[] flywheelPidf(double topTicksPerSec) {
        double top = Math.abs(topTicksPerSec);
        if (top < 1) return new double[] {0, 0, 0, 0};
        double f = 32767 / top;
        double p = f / 10;
        double i = p / 10;
        return new double[] {p, i, 0, f};
    }

    /** Counts per second to RPM (turns per minute), for this many counts per turn. */
    public static double ticksPerSecToRpm(double ticksPerSec, double countsPerRev) {
        return countsPerRev == 0 ? 0 : ticksPerSec / countsPerRev * 60.0;
    }

    /**
     * The wheel diameter that matches a "push" test: the robot was pushed {@code inches} and the
     * wheels counted {@code ticks}. One wheel turn = pi x diameter inches = ticksPerRev x gearRatio ticks.
     *
     * @return the diameter in inches, or NaN if the numbers cannot give one
     */
    public static double wheelDiameterFromPush(double ticks, double inches, double ticksPerRev, double gearRatio) {
        if (Math.abs(ticks) < 1 || inches <= 0) return Double.NaN;
        double ticksPerInch = Math.abs(ticks) / inches;
        return ticksPerRev * gearRatio / (Math.PI * ticksPerInch);
    }

    /**
     * A corrected wheel diameter after a drive test: the robot was asked for {@code asked} inches and
     * really went {@code measured}. Went too far = the wheels are really bigger than the setting.
     *
     * @return the new diameter, or NaN if the numbers cannot give one
     */
    public static double correctedDiameter(double oldDiameter, double asked, double measured) {
        if (asked <= 0 || measured <= 0) return Double.NaN;
        return oldDiameter * measured / asked;
    }

    /**
     * A corrected strafe correction after a sideways test: asked for {@code asked} inches, really
     * went {@code measured}. Went too short = the correction must be bigger.
     *
     * @return the new correction, or NaN if the numbers cannot give one
     */
    public static double correctedStrafe(double oldCorrection, double asked, double measured) {
        if (asked <= 0 || measured <= 0) return Double.NaN;
        return oldCorrection * asked / measured;
    }

    /**
     * Turns two marked encoder counts ("low end" and "high end") into limits for a mechanism whose
     * code counts UP as it goes UP. If the high end has the smaller count, the motor counts down as
     * it goes up: then it needs {@code .reversed()}, and every count changes sign.
     *
     * @return {min, max, reversed (1 or 0)}
     */
    public static double[] limits(double lowEndTicks, double highEndTicks) {
        boolean reversed = highEndTicks < lowEndTicks;
        // "0.0 - x" instead of "-x": it never gives "-0", which would show as "-0" in the code line.
        double low = reversed ? 0.0 - lowEndTicks : lowEndTicks;
        double high = reversed ? 0.0 - highEndTicks : highEndTicks;
        return new double[] {Math.min(low, high), Math.max(low, high), reversed ? 1 : 0};
    }

    /**
     * The wheel count of a push test from each wheel's change in counts. All wheels must agree: the
     * same sign, and none less than half of the biggest (a wheel turning the wrong way, or not
     * counting, would spoil the average).
     *
     * @return the average count (negative if every wheel counted down), or NaN if the wheels disagree
     */
    public static double pushTicks(int[] deltas) {
        double biggest = 0;
        for (int d : deltas) biggest = Math.max(biggest, Math.abs(d));
        if (biggest < 1) return Double.NaN;
        int sign = 0;
        double sum = 0;
        for (int d : deltas) {
            int s = d > 0 ? 1 : d < 0 ? -1 : 0;
            if (Math.abs(d) < biggest / 2) return Double.NaN;
            if (sign == 0) sign = s;
            else if (s != sign) return Double.NaN;
            sum += d;
        }
        return sum / deltas.length;
    }

    /** Keeps a servo position inside 0..1, the only range a servo accepts. NaN becomes 0.5. */
    public static double servoPosition(double position) {
        if (Double.isNaN(position)) return 0.5;
        return Math.max(0, Math.min(1, position));
    }
}
