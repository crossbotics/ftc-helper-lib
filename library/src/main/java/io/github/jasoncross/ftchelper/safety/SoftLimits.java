package io.github.jasoncross.ftchelper.safety;

import io.github.jasoncross.ftchelper.util.MathUtil;

/**
 * The rule that keeps a mechanism inside its safe range. No hardware here; {@link SafeMotor}
 * uses it.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>Power that pushes past a limit (position or limit switch) becomes 0.
 *       Power that moves AWAY from the limit is always allowed.</li>
 *   <li>Inside the "slow zone" near a limit, power toward that limit is scaled down,
 *       so the mechanism does not slam into the end.</li>
 * </ol>
 *
 * Docs: docs/modules/safety.md
 */
public final class SoftLimits {
    private SoftLimits() { }

    /**
     * Applies the limits to a power.
     *
     * @param power         the power you want, -1 to 1 (positive = toward max)
     * @param position      where the mechanism is now
     * @param min           lowest allowed position
     * @param max           highest allowed position
     * @param slowZone      distance from a limit where power toward it is reduced (0 = no slow zone)
     * @param slowZonePower the largest power allowed right at the limit edge of the slow zone, 0 to 1
     * @param atLowerSwitch true if a lower limit switch is pressed (false if there is none)
     * @param atUpperSwitch true if an upper limit switch is pressed (false if there is none)
     * @return the safe power
     */
    public static double limit(double power, double position, double min, double max,
                               double slowZone, double slowZonePower,
                               boolean atLowerSwitch, boolean atUpperSwitch) {
        power = MathUtil.clamp(power, -1, 1);
        if (power < 0) {
            if (atLowerSwitch || position <= min) return 0;
            return -scaleNear(-power, position - min, slowZone, slowZonePower);
        }
        if (power > 0) {
            if (atUpperSwitch || position >= max) return 0;
            return scaleNear(power, max - position, slowZone, slowZonePower);
        }
        return 0;
    }

    /** Scales a positive power down when {@code distance} to the limit is inside the slow zone. */
    private static double scaleNear(double power, double distance, double slowZone, double slowZonePower) {
        if (slowZone <= 0 || distance >= slowZone) return power;
        double floor = MathUtil.clamp(slowZonePower, 0, 1);
        double cap = floor + (1 - floor) * (distance / slowZone);
        return Math.min(power, cap);
    }
}
