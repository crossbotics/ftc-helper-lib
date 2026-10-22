package com.crossbotics.ftchelper.mechanism;

import com.crossbotics.ftchelper.core.Subsystem;

/**
 * Anything that goes to an angle and can say when it is there: a servo with degrees set up
 * ({@link PositionServo}), a launcher's hood on a servo or a motor with an encoder
 * ({@code robot.Hood}), or your own part (a CR servo with an absolute encoder). A launcher's hood is one
 * of these, so the launcher does not care what moves it.
 *
 * <pre>{@code
 * public class MyHood implements AngleDevice {
 *     public void setDegrees(double deg) { ... }           // start moving there (never wait)
 *     public double getTargetDegrees() { ... }              // where it is going (NaN = never sent)
 *     public boolean isDone() { ... }                       // there (by a sensor, or a time estimate)
 *     public void update() { ... }                          // every loop
 * }
 * launcher.hood(new MyHood(...));
 * }</pre>
 *
 * <p>"Angle" is the device's own scale: degrees if you measured them, or plain servo positions or
 * encoder ticks if you did not (then a shot table's hood column holds those). Docs: docs/modules/mechanism.md
 */
public interface AngleDevice extends Subsystem {
    /** Starts moving to this angle (kept inside its limits). Quick: never waits. */
    void setDegrees(double degrees);

    /** The angle it is going to (after its limits), or NaN before the first command. */
    double getTargetDegrees();

    /** True when it is (or, without a sensor, should be) at its target. */
    boolean isDone();
}
