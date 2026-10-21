package io.github.jasoncross.ftchelper.tuning;

import com.qualcomm.robotcore.hardware.Gamepad;
import io.github.jasoncross.ftchelper.core.Profile;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.List;

/**
 * A menu on the Driver Station to change {@link Tunable} numbers with a gamepad, while the robot runs.
 * Only works in the PRACTICE profile, so it can never be opened by accident at an event.
 *
 * <pre>{@code
 * TuningMenu menu = new TuningMenu(gamepad1);
 * controls.bindPress(Pad.G1, Btn.GUIDE, menu::toggle);   // open/close (any button you like)
 * // every loop:
 * menu.update();
 * if (menu.isOpen()) { menu.addTelemetry(t); }            // the menu takes over the screen
 * else { ...normal driving with gamepad1... }
 * }</pre>
 *
 * <p>While open: D-pad up/down = choose, D-pad right/left = change by one step,
 * right/left bumper = step x10, A = save on the hub, X = put the chosen one back to the code value,
 * Y = put ALL back to the code values (and delete the saved values).
 *
 * <p>{@code menu::toggle} hands over the menu's toggle method itself (a "method reference"), so the
 * button can run it later. Profiles: docs/guides/profiles.md.
 *
 * Docs: docs/guides/tuning.md
 */
public class TuningMenu {
    // How many tunables fit on one screen page.
    private static final int PAGE = 8;

    private final Gamepad gamepad;
    private boolean open = false;
    // Which tunable is chosen, counted from 0.
    private int selected = 0;
    // A short note for the driver, e.g. "Saved on the hub.".
    // "volatile": a background save writes it, the loop reads it.
    private volatile String message = "";
    // B: show the changed values as lines to copy into the code, instead of the list.
    private boolean showCode = false;
    // Each button's state in the last loop, to spot the loop where it was just pressed
    // (an "edge"), so holding a button does not repeat the action every loop.
    // Same order as "now" in update(): up, down, right, left, A, X, Y, B.
    private final boolean[] was = new boolean[8];

    /**
     * Makes a menu.
     *
     * @param gamepad the gamepad that runs the menu
     */
    public TuningMenu(Gamepad gamepad) {
        this.gamepad = gamepad;
    }

    /** Opens or closes the menu. In the COMPETITION profile it stays closed. */
    public void toggle() {
        if (open) open = false;
        // Safety: only open in PRACTICE, so tuned numbers cannot be changed by accident at an event.
        else open = Profile.current().isPractice();
        // Opening clears the old note; closing keeps it.
        message = open ? "" : message;
    }

    /** True while the menu is open. Do not drive with the same gamepad while it is open. */
    public boolean isOpen() {
        return open;
    }

    /** Reads the gamepad. Call every loop (it does nothing while closed). */
    public void update() {
        // The buttons we use, read once.
        boolean[] now = {gamepad.dpad_up, gamepad.dpad_down, gamepad.dpad_right, gamepad.dpad_left,
                gamepad.a, gamepad.x, gamepad.y, gamepad.b};
        if (open) {
            List<Tunable> all = Tunable.all();
            if (!all.isEmpty()) {
                // Keep the choice inside the list (new tunables may have been made, or some forgotten).
                selected = Math.max(0, Math.min(selected, all.size() - 1));
                Tunable t = all.get(selected);
                // Holding a bumper makes each press count 10 steps, for big changes.
                int times = (gamepad.right_bumper || gamepad.left_bumper) ? 10 : 1;
                // Up/down move the choice. "%" (remainder) wraps around: up from the first goes to
                // the last, down from the last goes to the first. Adding all.size() before
                // taking 1 away keeps the number from going below 0.
                if (pressed(now, 0)) selected = (selected + all.size() - 1) % all.size();
                if (pressed(now, 1)) selected = (selected + 1) % all.size();
                // Right/left change the chosen value by one step (or ten with a bumper).
                if (pressed(now, 2)) for (int i = 0; i < times; i++) t.increase();
                if (pressed(now, 3)) for (int i = 0; i < times; i++) t.decrease();
                // A saves, X resets the chosen one (not saved until A), Y resets all and saves.
                // Saving writes a file, which can be slow, so it runs in the background and the
                // robot loop never waits; the message changes when it is done.
                if (pressed(now, 4)) {
                    message = "Saving...";
                    Tunable.saveAllInBackground(ok -> message = ok ? "Saved on the hub." : "SAVE FAILED.");
                }
                if (pressed(now, 5)) { t.reset(); message = t.name() + " back to the code value."; }
                // B shows the changed values as code lines (and writes them to the robot log, to copy
                // from a laptop); B again goes back to the list.
                if (pressed(now, 7)) {
                    showCode = !showCode;
                    if (showCode) logCode();
                }
                if (pressed(now, 6)) {
                    message = "Resetting...";
                    Tunable.resetAllInBackground(ok -> message = ok ? "All back to code values." : "RESET FAILED.");
                }
            }
        }
        // Remember this loop's buttons for the next loop, even while closed, so a button held
        // when the menu opens does not count as a fresh press.
        System.arraycopy(now, 0, was, 0, was.length);
    }

    /**
     * Shows the menu (only when open).
     *
     * @param t the telemetry to add the menu lines to
     */
    public void addTelemetry(RobotTelemetry t) {
        if (!open) return;
        List<Tunable> all = Tunable.all();
        t.section("TUNING (practice)");
        if (all.isEmpty()) {
            t.text("No tunables. Make some with Tunable.number(...).");
            return;
        }
        if (showCode) {
            List<String> lines = Tunable.changedAsCode();
            t.text("Copy into Tunables.java (then Y = reset all):");
            if (lines.isEmpty()) t.text("  nothing differs from the code");
            for (String line : lines) t.text("  " + line);
            t.text("B back to the list");
            return;
        }
        // Show only the page that holds the chosen tunable (8 per page), so the list fits on screen.
        // Whole-number division drops the remainder: e.g. selected 10 gives page start 8.
        int first = (selected / PAGE) * PAGE;
        for (int i = first; i < Math.min(all.size(), first + PAGE); i++) {
            Tunable tu = all.get(i);
            // "> " marks the chosen line. A changed value also shows the code value next to it.
            String mark = i == selected ? "> " : "  ";
            String changed = tu.isChanged() ? "  (code " + tu.defaultValue() + ")" : "";
            t.text(mark + tu.name() + " = " + tu.display() + changed);
        }
        t.text(String.format(java.util.Locale.US, "%d/%d  D-pad: choose/change  bumper: x10", selected + 1, all.size()));
        t.text("A save   X reset this   Y reset all   B as code");
        if (!message.isEmpty()) t.text(message);
    }

    // The changed values in the robot log (logcat, tag "ftc-helper"), to copy from a laptop.
    private static void logCode() {
        try {
            for (String line : Tunable.changedAsCode()) com.qualcomm.robotcore.util.RobotLog.ii("ftc-helper", "tuned " + line);
        } catch (RuntimeException | LinkageError e) {
            // No robot log (a test without the SDK): the screen still shows them.
        }
    }

    // True only in the loop where button i went from up to down.
    private boolean pressed(boolean[] now, int i) {
        return now[i] && !was[i];
    }
}
