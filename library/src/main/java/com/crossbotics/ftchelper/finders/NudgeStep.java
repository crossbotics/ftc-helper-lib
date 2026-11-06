package com.crossbotics.ftchelper.finders;

/**
 * The step size for nudging a servo in a finder: D-pad up/down moves the servo by the step, D-pad
 * left/right makes the step smaller/bigger. Big steps get close fast; the smallest (0.001) finds an exact
 * spot, such as the gap two wheels squeeze a game piece through.
 *
 * <p>The Servo Finder, Feed Stop Finder and Hood Finder all use it, so the buttons are the same in each.
 * <pre>{@code
 * NudgeStep step = new NudgeStep();       // starts at 0.05
 * step.smaller();                         // 0.01
 * double p = step.move(0.500, +1);        // 0.510
 * }</pre>
 *
 * Docs: docs/modules/finders.md. Guide: docs/guides/finders.md
 */
public final class NudgeStep {

    /** The step sizes, biggest first. 0.001 is about a quarter of a degree on a 270 degree servo. */
    public static final double[] SIZES = {0.1, 0.05, 0.01, 0.005, 0.001};

    private static final int START = 1;   // 0.05: the old D-pad up/down step, so the first moves feel the same
    private int index = START;

    /** @return the step now, servo position units (0..1 is the whole range) */
    public double size() {
        return SIZES[index];
    }

    /** D-pad left: the next smaller step (stays at the smallest). */
    public void smaller() {
        if (index < SIZES.length - 1) index++;
    }

    /** D-pad right: the next bigger step (stays at the biggest). */
    public void bigger() {
        if (index > 0) index--;
    }

    /** Back to the first step (0.05), for example when another servo is chosen. */
    public void reset() {
        index = START;
    }

    /**
     * The position one step away.
     *
     * @param position  the servo position now, 0..1
     * @param direction +1 = up (bigger number), -1 = down
     * @return the new position, rounded to 0.001 (so 0.1 + 0.05 shows as 0.150) and kept inside 0..1
     */
    public double move(double position, int direction) {
        double next = position + Math.signum(direction) * size();
        return FinderMath.servoPosition(Math.round(next * 1000) / 1000.0);
    }

    /** @return the step for the screen, e.g. "0.005" */
    public String label() {
        return FinderResults.num(size(), 3);
    }
}
