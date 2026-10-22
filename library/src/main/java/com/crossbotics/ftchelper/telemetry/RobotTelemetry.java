package com.crossbotics.ftchelper.telemetry;

import com.crossbotics.ftchelper.core.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * Telemetry with two levels: STANDARD lines for the drivers, DEBUG lines for programmers.
 * Warnings always go to the top.
 *
 * <p>"Telemetry" is the text the robot sends to the Driver Station screen.
 *
 * <pre>{@code
 * RobotTelemetry t = new RobotTelemetry(telemetry);       // debug ON in PRACTICE, OFF in COMPETITION
 * controls.bindPress(Pad.G1, Btn.BACK, t::toggleDebug);   // let the driver switch it
 * // every loop:
 * t.section("Lift");
 * t.line("Height", "%s", lift.getTargetName());           // always shown
 * t.debug("Power", "%.2f", lift.getAppliedPower());       // only when debug is on
 * if (battery < 11.5) t.warn("Battery low!");             // always on top
 * t.update();                                             // once, at the end of the loop
 * }</pre>
 *
 * <p>How it works: during the loop the lines are only collected in a list. {@link #update()}
 * sends them all at once, warnings first, and empties the list for the next loop.
 * Tutorial 15 (docs/tutorials/15-telemetry-profiles-tuning.md) shows it in a full program.
 *
 * Docs: docs/modules/telemetry.md
 */
public class RobotTelemetry {
    // The SDK's telemetry that really sends to the Driver Station.
    private final Telemetry telemetry;
    // A clock in nanoseconds (billionths of a second). Tests pass a fake one.
    private final LongSupplier clockNanos;
    // This loop's warnings, and this loop's other lines, waiting for update().
    private final List<String> warnings = new ArrayList<>();
    // How many of the warnings were put at the very top with warnFirst (they stay above the others).
    private int firstCount = 0;
    // Each line is a pair of texts: {label, value}. A null value means "plain text line".
    private final List<String[]> lines = new ArrayList<>();
    // True = show the programmer (debug) lines too.
    private boolean debug;
    // Send at most once per this many milliseconds (0 = every loop).
    private int minIntervalMs = 0;
    // When we last sent. It starts far in the past (but only half the smallest number, so the
    // subtraction in update() cannot overflow) so the first update() always sends.
    private long lastSentNanos = Long.MIN_VALUE / 2;

    /**
     * Wraps the OpMode's telemetry. Debug starts ON in the PRACTICE profile and OFF in COMPETITION.
     * (Profiles are explained in docs/guides/profiles.md.)
     *
     * @param telemetry the OpMode's {@code telemetry}
     */
    public RobotTelemetry(Telemetry telemetry) {
        // "this(...)" runs the other constructor first, then we choose debug from the profile.
        this(telemetry, System::nanoTime);
        this.debug = Profile.current().isPractice();
    }

    /**
     * For tests: your own clock; debug starts off.
     *
     * @param telemetry  the telemetry to send to
     * @param clockNanos gives the current time in nanoseconds
     */
    public RobotTelemetry(Telemetry telemetry, LongSupplier clockNanos) {
        this.telemetry = telemetry;
        this.clockNanos = clockNanos;
    }

    /**
     * A heading line: {@code --- title ---}. Shown at the standard level.
     *
     * @return this RobotTelemetry, so you can chain more lines
     */
    public RobotTelemetry section(String title) {
        // "new String[]{a, b}" makes a pair of texts; null as the value means "no value, just text".
        lines.add(new String[]{"--- " + title + " ---", null});
        return this;
    }

    /** A heading that only shows when debug is on. */
    public RobotTelemetry debugSection(String title) {
        if (debug) section(title);
        return this;
    }

    /**
     * A driver line: "label : value".
     *
     * @param value any value; it is turned into text
     */
    public RobotTelemetry line(String label, Object value) {
        lines.add(new String[]{label, String.valueOf(value)});
        return this;
    }

    /**
     * A driver line with a format, e.g. {@code line("Speed", "%.2f", speed)}.
     * "%.2f" means "a number with 2 decimals"; "%d" a whole number; "%s" any text.
     *
     * @param args the values for the format; "Object..." means "any number of values"
     */
    public RobotTelemetry line(String label, String format, Object... args) {
        // Locale.US: always "." for decimals, whatever the phone's language setting.
        lines.add(new String[]{label, String.format(Locale.US, format, args)});
        return this;
    }

    /** A plain text line for drivers. */
    public RobotTelemetry text(String text) {
        lines.add(new String[]{text, null});
        return this;
    }

    /** A programmer line: only shown when debug is on. */
    public RobotTelemetry debug(String label, Object value) {
        if (debug) line(label, value);
        return this;
    }

    /** A programmer line with a format: only shown when debug is on. */
    public RobotTelemetry debug(String label, String format, Object... args) {
        if (debug) line(label, format, args);
        return this;
    }

    /**
     * A warning: always shown, at the top, with a "!!" in front. Same text twice in one loop shows once.
     * Call it every loop while the problem lasts: warnings are cleared after each {@link #update()}
     * that sends (with {@link #setMinIntervalMs}, a warning from a skipped loop waits for the next send).
     */
    public RobotTelemetry warn(String text) {
        String line = "!! " + text;
        // Skip repeats, so a warning raised by two parts of the code does not fill the screen.
        if (!warnings.contains(line)) warnings.add(line);
        return this;
    }

    /**
     * A warning above every other warning, even ones added before it: for the one thing the drivers must
     * see first (the library uses it for a part that is OFF in the COMPETITION profile). Several stay in
     * the order they were added. Same text twice in one loop shows once.
     */
    public RobotTelemetry warnFirst(String text) {
        String line = "!! " + text;
        int had = warnings.indexOf(line);
        if (had >= 0 && had < firstCount) return this;   // already at the top
        if (had >= 0) warnings.remove(had);
        warnings.add(firstCount++, line);
        return this;
    }

    /** Turns debug lines on or off. */
    public void setDebug(boolean on) {
        debug = on;
    }

    /** Flips debug on/off. Bind it to a button. */
    public void toggleDebug() {
        debug = !debug;
    }

    /** True when debug lines are shown. */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Sends at most once per this many milliseconds (0 = every loop, the default). Sending less often
     * makes the loop faster; 100 ms (10 per second) is plenty for people to read. Warnings raised in
     * the skipped loops are kept and sent with the next send, so none are lost.
     *
     * @param ms milliseconds between sends; below 0 counts as 0
     * @return this RobotTelemetry, so you can chain more settings
     */
    public RobotTelemetry setMinIntervalMs(int ms) {
        minIntervalMs = Math.max(0, ms);
        return this;
    }

    /**
     * Sends the lines to the Driver Station (warnings first) and starts a fresh screen for the next loop.
     *
     * @return true if it sent this time (false when skipped by {@link #setMinIntervalMs})
     */
    public boolean update() {
        long now = clockNanos.getAsLong();
        // Has enough time passed since the last send? (1 ms = 1,000,000 ns; "L" = a long whole number.)
        boolean send = now - lastSentNanos >= minIntervalMs * 1_000_000L;
        if (send) {
            // Warnings first, so the drivers see them at the top of the screen.
            for (String w : warnings) telemetry.addLine(w);
            for (String[] l : lines) {
                // No value: a plain text line. Otherwise "label : value".
                if (l[1] == null) telemetry.addLine(l[0]);
                else telemetry.addData(l[0], l[1]);
            }
            telemetry.update();
            lastSentNanos = now;
        }
        // Always start the next loop with no lines, even when we skipped sending, so old lines never
        // pile up. Warnings are only cleared once they were sent: a warning raised in a skipped loop
        // still reaches the screen on the next send. (Repeats are skipped, so they cannot pile up.)
        if (send) {
            warnings.clear();
            firstCount = 0;
        }
        lines.clear();
        return send;
    }

    /** The lines waiting to be sent, as text (used by tests and the hardware check). */
    public List<String> pending() {
        // Start with a copy of the warnings, then add each line as "label : value" (or plain text).
        List<String> out = new ArrayList<>(warnings);
        for (String[] l : lines) out.add(l[1] == null ? l[0] : l[0] + " : " + l[1]);
        return out;
    }

    /** The SDK telemetry inside. */
    public Telemetry raw() {
        return telemetry;
    }
}
