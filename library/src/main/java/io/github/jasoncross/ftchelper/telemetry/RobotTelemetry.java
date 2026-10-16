package io.github.jasoncross.ftchelper.telemetry;

import io.github.jasoncross.ftchelper.core.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * Telemetry with two levels: STANDARD lines for the drivers, DEBUG lines for programmers.
 * Warnings always go to the top.
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
 * Docs: docs/modules/telemetry.md
 */
public class RobotTelemetry {
    private final Telemetry telemetry;
    private final LongSupplier clockNanos;
    private final List<String> warnings = new ArrayList<>();
    private final List<String[]> lines = new ArrayList<>();
    private boolean debug;
    private int minIntervalMs = 0;
    private long lastSentNanos = Long.MIN_VALUE / 2;

    /** Wraps the OpMode's telemetry. Debug starts ON in the PRACTICE profile and OFF in COMPETITION. */
    public RobotTelemetry(Telemetry telemetry) {
        this(telemetry, System::nanoTime);
        this.debug = Profile.current().isPractice();
    }

    /** For tests: your own clock; debug starts off. */
    public RobotTelemetry(Telemetry telemetry, LongSupplier clockNanos) {
        this.telemetry = telemetry;
        this.clockNanos = clockNanos;
    }

    /** A heading line: {@code --- title ---}. Shown at the standard level. */
    public RobotTelemetry section(String title) {
        lines.add(new String[]{"--- " + title + " ---", null});
        return this;
    }

    /** A heading that only shows when debug is on. */
    public RobotTelemetry debugSection(String title) {
        if (debug) section(title);
        return this;
    }

    /** A driver line: "label : value". */
    public RobotTelemetry line(String label, Object value) {
        lines.add(new String[]{label, String.valueOf(value)});
        return this;
    }

    /** A driver line with a format, e.g. {@code line("Speed", "%.2f", speed)}. */
    public RobotTelemetry line(String label, String format, Object... args) {
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

    /** A warning: always shown, at the top, with a "!!" in front. Same text twice in one loop shows once. */
    public RobotTelemetry warn(String text) {
        String line = "!! " + text;
        if (!warnings.contains(line)) warnings.add(line);
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
     * makes the loop faster; 100 ms (10 per second) is plenty for people to read.
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
        boolean send = now - lastSentNanos >= minIntervalMs * 1_000_000L;
        if (send) {
            for (String w : warnings) telemetry.addLine(w);
            for (String[] l : lines) {
                if (l[1] == null) telemetry.addLine(l[0]);
                else telemetry.addData(l[0], l[1]);
            }
            telemetry.update();
            lastSentNanos = now;
        }
        warnings.clear();
        lines.clear();
        return send;
    }

    /** The lines waiting to be sent, as text (used by tests and the hardware check). */
    public List<String> pending() {
        List<String> out = new ArrayList<>(warnings);
        for (String[] l : lines) out.add(l[1] == null ? l[0] : l[0] + " : " + l[1]);
        return out;
    }

    /** The SDK telemetry inside. */
    public Telemetry raw() {
        return telemetry;
    }
}
