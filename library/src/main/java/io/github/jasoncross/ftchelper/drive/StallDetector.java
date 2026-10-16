package io.github.jasoncross.ftchelper.drive;

/**
 * Tells when a move is stuck: the distance left has not gone down enough for a while.
 * Used by {@link AutoDrive}; you can use it for any mechanism with a target.
 *
 * <pre>{@code
 * StallDetector stall = new StallDetector(0.25, 0.4);  // 0.25 in of progress within 0.4 s
 * // every loop:
 * if (stall.update(inchesLeft, seconds)) { ... stuck ... }
 * }</pre>
 *
 * <p>This checks progress over a time window, not per loop, so it works the same at any loop speed.
 * (At 200 loops a second each loop moves only a tiny bit, so "did it move this loop?" would
 * give false alarms; "did it move 0.25 in during the last 0.4 s?" does not.)
 *
 * Docs: docs/modules/drive.md (section "StallDetector")
 */
public final class StallDetector {
    private final double minProgress;
    private final double windowSec;
    // The "remaining" value at the last time real progress was seen, and when that was.
    // NaN ("not a number") means "nothing remembered yet": the first update() starts the count.
    private double refRemaining = Double.NaN;
    private double refTime;

    /**
     * @param minProgress how much closer the move must get within the window (same unit as "remaining")
     * @param windowSec   the window, seconds
     */
    public StallDetector(double minProgress, double windowSec) {
        // abs() so a minus sign typed by mistake still works the same.
        this.minProgress = Math.abs(minProgress);
        this.windowSec = Math.abs(windowSec);
    }

    /**
     * Call every loop.
     *
     * @param remaining how far is left to go (any unit, must go down as you move)
     * @param timeSec   the time now, seconds (any starting point)
     * @return true if stuck. Never true when {@code remaining} is already within {@code minProgress}.
     */
    public boolean update(double remaining, double timeSec) {
        // First call, or we got at least minProgress closer since the remembered point:
        // that is real progress, so remember this new point and time, and start the clock again.
        if (Double.isNaN(refRemaining) || refRemaining - remaining >= minProgress) {
            refRemaining = remaining;
            refTime = timeSec;
            return false;
        }
        // Almost there: the last bit may be smaller than minProgress, so do not call that stuck.
        if (remaining <= minProgress) return false;
        // No real progress since refTime. Stuck once that has lasted the whole window.
        return timeSec - refTime >= windowSec;
    }

    /** Starts over. Call before a new move. */
    public void reset() {
        refRemaining = Double.NaN;
    }
}
