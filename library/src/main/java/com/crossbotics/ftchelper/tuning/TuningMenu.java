package com.crossbotics.ftchelper.tuning;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.crossbotics.ftchelper.core.Profile;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
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
 * B = the page with the lines to copy (only looking: nothing changes there), Y then Y again = put ALL
 * back to the code values (and delete the saved values; any other button cancels). Copy, install and
 * check that nothing differs before you erase: erasing first goes back to the old installed numbers.
 *
 * <p>{@code menu::toggle} hands over the menu's toggle method itself (a "method reference"), so the
 * button can run it later. Profiles: docs/guides/profiles.md.
 *
 * Docs: docs/tuning/16-live-tuning.md
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
    private int codePage = 0;
    // Y was pressed once: the screen asks, and only a second Y resets.
    private boolean resetAsked = false;
    // Each button's state in the last loop, to spot the loop where it was just pressed
    // (an "edge"), so holding a button does not repeat the action every loop.
    // Same order as "now" in update(): up, down, right, left, A, X, Y, B.
    private final boolean[] was = new boolean[8];
    // The battery (volts, never waits; NaN = unknown), noted with each change. Null = not shown.
    private java.util.function.DoubleSupplier battery = null;

    /** Below this battery voltage the menu warns: numbers tuned then are too strong on a charged battery. */
    public static final double LOW_BATTERY_VOLTS = 12.5;

    /**
     * Makes a menu.
     *
     * @param gamepad the gamepad that runs the menu
     */
    public TuningMenu(Gamepad gamepad) {
        this.gamepad = gamepad;
    }

    /**
     * The battery reader (e.g. {@code Hw.batteryReader(hardwareMap)}): the menu shows the battery, warns
     * when it is low, and notes its volts with each change ("tuned at 12.6 V" in the copy lines).
     *
     * @param volts a reader that never waits
     * @return this menu
     */
    public TuningMenu battery(java.util.function.DoubleSupplier volts) {
        battery = volts;
        return this;
    }

    private double volts() {
        return battery == null ? Double.NaN : battery.getAsDouble();
    }

    /** Opens or closes the menu. In the COMPETITION profile it stays closed. */
    public void toggle() {
        if (open) open = false;
        // Safety: only open in PRACTICE, so tuned numbers cannot be changed by accident at an event.
        else open = Profile.current().isPractice();
        // Opening clears the old note; closing keeps it.
        message = open ? "" : message;
        resetAsked = false;
        showCode = false;
        codePage = 0;
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
            // Not the numbers of a part that is not on the robot: changing them would do nothing.
            List<Tunable> all = Tunable.shown();
            if (resetAsked) {
                // SAFETY: the question is open. A second Y resets, any other button only closes the question.
                if (pressed(now, 6)) {
                    resetAsked = false;
                    showCode = false;
                    message = "Erasing...";
                    Tunable.resetAllInBackground(ok -> message = ok ? "Erased: all use the installed code now."
                            : "Erase FAILED. Try again.");
                } else {
                    for (int i = 0; i < now.length; i++) {
                        if (pressed(now, i)) {
                            resetAsked = false;
                            message = "Canceled: nothing was erased.";
                        }
                    }
                }
            } else if (showCode) {
                // The lines to copy: the D-pad only turns the page (a number must not change out of sight).
                int pages = Math.max(1, (codeScreen().size() + CODE_PAGE - 1) / CODE_PAGE);
                if (pressed(now, 0)) codePage = (codePage + pages - 1) % pages;
                if (pressed(now, 1)) codePage = (codePage + 1) % pages;
                if (pressed(now, 7)) showCode = false;
                if (pressed(now, 6)) resetAsked = true;
            } else if (!all.isEmpty()) {
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
                if (pressed(now, 2)) { for (int i = 0; i < times; i++) t.increase(); t.tunedAt(volts()); }
                if (pressed(now, 3)) { for (int i = 0; i < times; i++) t.decrease(); t.tunedAt(volts()); }
                // A saves, X resets the chosen one (not saved until A), Y resets all and saves.
                // Saving writes a file, which can be slow, so it runs in the background and the
                // robot loop never waits; the message changes when it is done.
                if (pressed(now, 4)) {
                    message = "Saving...";
                    Tunable.saveAllInBackground(ok -> message = ok ? "Saved on the Control Hub."
                            : "Save FAILED: not stored. {B} = lines to copy.");
                }
                if (pressed(now, 5)) { t.reset(); message = t.name() + " is back to the code value."; }
                // B shows the changed values as code lines (and writes them to the robot log, to copy
                // from a laptop); B again goes back to the list.
                if (pressed(now, 7)) {
                    showCode = true;
                    codePage = 0;
                    logCode();
                }
                if (pressed(now, 6)) resetAsked = true;
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
        List<Tunable> all = Tunable.shown();
        if (resetAsked) {
            int lost = Tunable.countChanged();
            t.warn("Erase ALL saved tuning?");
            t.warn("Every number goes back to the INSTALLED code.");
            t.section("TUNING: are you sure?");
            if (lost > 0) {
                t.text(lost + (lost == 1 ? " tuned value" : " tuned values") + " will be LOST.");
                t.text("Keep them? Cancel. Then {B} = lines to copy.");
            } else {
                t.text("Nothing differs: nothing will change.");
            }
            t.text("{Y} again = yes, erase. Any other button = no.");
            return;
        }
        if (showCode) {
            showCodePage(t);
            return;
        }
        t.warn("A change works at once. Hands clear.");
        t.section("TUNING (practice)");
        double v = volts();
        if (!Double.isNaN(v)) {
            if (v < LOW_BATTERY_VOLTS) {
                t.warn(String.format(java.util.Locale.US, "Battery %.1f V: low. Change it before you tune.", v));
            }
            t.text(String.format(java.util.Locale.US, "Battery %.1f V (noted with each change)", v));
        }
        List<String> hidden = Tunable.hiddenGroups();
        if (!hidden.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (String g : hidden) names.append(names.length() == 0 ? "" : ", ").append(g);
            t.text("Not on the robot (hidden): " + names);
        }
        if (all.isEmpty()) {
            t.text("No numbers to tune.");
            t.text("Next: close the menu. (Your own number:");
            t.text("Tunable.number(...) in your code.)");
            return;
        }
        t.text("Next: D-pad up/down = choose, right/left = change.");
        // Show only the page that holds the chosen tunable (8 per page), so the list fits on screen.
        // Whole-number division drops the remainder: e.g. selected 10 gives page start 8.
        int first = (selected / PAGE) * PAGE;
        for (int i = first; i < Math.min(all.size(), first + PAGE); i++) {
            Tunable tu = all.get(i);
            // "> " marks the chosen line. A changed value also shows the code value next to it.
            String mark = i == selected ? "> " : "  ";
            // Not changed and written DEFAULT_FROM_CATALOG in Tunables.java: where the value came from.
            String changed = tu.isChanged() ? "  (code " + tu.defaultValue() + ")"
                    : tu.source().isEmpty() ? "" : "  (" + tu.source() + ")";
            t.text(mark + tu.name() + " = " + tu.display() + changed);
        }
        t.text(String.format(java.util.Locale.US, "%d of %d. Hold {LB} or {RB}: 10 steps a press.", selected + 1, all.size()));
        t.text("{A} = save on the hub. {X} = put this one back.");
        t.text("{B} = lines to copy. {Y} = erase all (asks).");
        if (!message.isEmpty()) t.text(message);
    }

    // How many lines of code fit on one page.
    private static final int CODE_PAGE = 10;

    // The changed values as exact Java, each class once with its lines under it.
    private static List<String> codeScreen() {
        java.util.Map<String, List<Tunable>> byClass = new java.util.LinkedHashMap<>();
        for (Tunable t : Tunable.all()) {
            if (!t.isChanged()) continue;
            List<Tunable> of = byClass.get(t.group());
            if (of == null) {
                of = new java.util.ArrayList<>();
                byClass.put(t.group(), of);
            }
            of.add(t);
        }
        List<String> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, List<Tunable>> e : byClass.entrySet()) {
            boolean named = !e.getKey().isEmpty();
            out.add(named ? "Open Tunables.java, find class " + e.getKey() + "." : "In your code, its Tunable.number(...) line:");
            for (Tunable t : e.getValue()) {
                out.add(named ? "  public static double " + t.fieldName() + " = " + t.asWritten() + ";"
                        : "  " + t.fieldName() + ": start it at " + t.asWritten());
                if (!t.voltsNote().isEmpty()) out.add("  " + t.voltsNote().trim());
            }
        }
        return out;
    }

    // B's page: the steps in the order that keeps the numbers, then the lines.
    private void showCodePage(RobotTelemetry t) {
        List<String> lines = codeScreen();
        if (lines.isEmpty()) {
            t.section("TUNING: lines to copy");
            t.text("Nothing differs from the installed code.");
            t.text("{Y} = erase the saved values (safe now).");
            t.text("{B} = back to the list.");
            return;
        }
        t.warn("Do NOT erase ({Y}) before steps 1 to 3.");
        int pages = (lines.size() + CODE_PAGE - 1) / CODE_PAGE;
        int page = Math.min(codePage, pages - 1);
        t.section("TUNING: lines to copy, page " + (page + 1) + " of " + pages);
        t.text("1. Change these lines on the laptop.");
        t.text("2. Build and install the app.");
        t.text("3. Look here again: nothing may differ.");
        t.text("4. Then {Y} = erase the saved values.");
        for (int i = page * CODE_PAGE; i < Math.min(lines.size(), (page + 1) * CODE_PAGE); i++) t.text(lines.get(i));
        if (pages > 1) t.text("D-pad up/down = another page.");
        t.text("{B} = back to the list.");
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
