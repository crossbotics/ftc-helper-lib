package com.crossbotics.ftchelper.match;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.crossbotics.ftchelper.core.Profile;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A menu for the INIT screen: choose alliance, start delay, route and more with the gamepad,
 * before pressing START. The robot does not move in INIT, so this is safe.
 *
 * <pre>{@code
 * InitMenu menu = new InitMenu(gamepad1)
 *         .alliance()                       // X = blue, B = red
 *         .delay(10)                        // 0..10 s start delay
 *         .choice("Route", "Near", "Far")   // any list of options
 *         .profile();                       // practice / competition (saved)
 * while (opModeInInit()) {
 *     menu.update();
 *     menu.addTelemetry(t);
 *     t.update();
 * }
 * Alliance a = menu.getAlliance();
 * if (menu.get("Route").equals("Far")) { ... }
 * }</pre>
 *
 * <p>Controls: D-pad up/down = choose a line, D-pad left/right = change it.
 * Shortcuts: X = blue, B = red.
 *
 * <p>Each "add" method returns this same menu, so you can chain them as in the example.
 * {@code update()} never waits: it reads the buttons once and returns, so the INIT loop keeps running.
 *
 * Docs: docs/modules/match.md (section "InitMenu"). {@code EasyAuto} builds this menu for you
 * (Tutorial 16).
 */
public class InitMenu {
    private final Gamepad gamepad;
    // The menu lines, in the order they were added. "List<Item>" is a list that holds only Items.
    private final List<Item> items = new ArrayList<>();
    // Which line the cursor (">") is on, counting from 0.
    private int selected = 0;
    // The six buttons we watch, as they were LAST loop: up, down, right, left, X, B.
    // Comparing with this loop tells us the moment a button goes down (a "press"),
    // so holding a button changes the value only once, not 50 times a second.
    private final boolean[] was = new boolean[6];

    // One line of the menu. "abstract" means it is only a pattern: each kind of line below
    // ("extends Item") fills in how it changes and how it shows itself.
    // "static" here means the class does not need an InitMenu object to exist.
    private abstract static class Item {
        final String label;
        Item(String label) { this.label = label; }
        // direction: +1 = D-pad right, -1 = D-pad left.
        abstract void change(int direction);
        // The text shown after the label, e.g. "BLUE" or "3 s".
        abstract String show();
    }

    // A line with a list of options (alliance, route, yes/no).
    private static final class ListItem extends Item {
        final List<String> options;
        int index;
        ListItem(String label, List<String> options, int start) {
            super(label);
            this.options = options;
            this.index = start;
        }
        // Step through the options and wrap around at the ends (after the last comes the first).
        // Adding options.size() first keeps the number from going negative when stepping left from 0.
        void change(int d) { index = (index + d + options.size()) % options.size(); }
        String show() { return options.get(index); }
    }

    // The start-delay line: 1 second per press, never below 0 or above the maximum.
    private final class DelayItem extends Item {
        final double max;
        double value;
        DelayItem(double max) { super("Start delay"); this.max = max; }
        void change(int d) { value = Math.max(0, Math.min(max, value + d)); }
        // Locale.US makes the number look the same on every phone or hub language setting.
        String show() { return String.format(Locale.US, "%.0f s", value); }
    }

    // The practice/competition line. Either direction flips it, and Profile saves the choice on
    // the hub, so it stays until changed again. See docs/guides/profiles.md.
    private final class ProfileItem extends Item {
        ProfileItem() { super("Profile"); }
        void change(int d) {
            Profile.set(Profile.current() == Profile.PRACTICE ? Profile.COMPETITION : Profile.PRACTICE);
        }
        String show() { return Profile.current().name(); }
    }

    /** @param gamepad the gamepad used to choose (usually gamepad1) */
    public InitMenu(Gamepad gamepad) {
        this.gamepad = gamepad;
    }

    /** Adds the alliance choice (starts at the alliance remembered from earlier, else BLUE). */
    public InitMenu alliance() {
        // Start where the last autonomous left off (see MatchMemory), so the drivers rarely need to change it.
        Alliance start = MatchMemory.allianceOr(Alliance.BLUE);
        items.add(new ListItem("Alliance", Arrays.asList("BLUE", "RED"), start == Alliance.RED ? 1 : 0));
        return this;
    }

    /** Adds a start delay choice, 0 to {@code maxSeconds}, in 1-second steps. */
    public InitMenu delay(double maxSeconds) {
        items.add(new DelayItem(maxSeconds));
        return this;
    }

    /**
     * Adds a choice from a list. The first option is the default.
     * ({@code String... options} means you can pass as many text options as you like.)
     */
    public InitMenu choice(String label, String... options) {
        // A list with nothing in it could never show anything: stop with a clear message instead.
        if (options.length == 0) throw new IllegalArgumentException("choice \"" + label + "\" needs options");
        items.add(new ListItem(label, Arrays.asList(options), 0));
        return this;
    }

    /** Adds a yes/no choice. */
    public InitMenu yesNo(String label, boolean startYes) {
        items.add(new ListItem(label, Arrays.asList("NO", "YES"), startYes ? 1 : 0));
        return this;
    }

    /** Adds the practice/competition profile choice (saved on the hub when changed). */
    public InitMenu profile() {
        items.add(new ProfileItem());
        return this;
    }

    /** Reads the gamepad. Call every loop during INIT. */
    public void update() {
        // The six buttons right now, in the same order as "was".
        boolean[] now = {gamepad.dpad_up, gamepad.dpad_down, gamepad.dpad_right, gamepad.dpad_left,
                gamepad.x, gamepad.b};
        if (!items.isEmpty()) {
            // Up / down move the cursor, wrapping around from the top line to the bottom one and back.
            if (pressed(now, 0)) selected = (selected + items.size() - 1) % items.size();
            if (pressed(now, 1)) selected = (selected + 1) % items.size();
            // Right / left change the line under the cursor.
            if (pressed(now, 2)) items.get(selected).change(1);
            if (pressed(now, 3)) items.get(selected).change(-1);
            // X and B set the alliance directly, from any line (X is blue, B is red, like the
            // button colors on most gamepads).
            ListItem a = find("Alliance");
            if (a != null && pressed(now, 4)) a.index = 0;
            if (a != null && pressed(now, 5)) a.index = 1;
        }
        // Remember this loop's buttons, to spot new presses next loop.
        System.arraycopy(now, 0, was, 0, was.length);
    }

    /** Shows the menu. */
    public void addTelemetry(RobotTelemetry t) {
        t.section("Choose (D-pad), then START");
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            // "> " marks the line the cursor is on.
            t.text((i == selected ? "> " : "  ") + item.label + ": " + item.show());
        }
        if (find("Alliance") != null) t.text("X = BLUE   B = RED");
    }

    /** The chosen alliance (BLUE if there is no alliance line). */
    public Alliance getAlliance() {
        ListItem a = find("Alliance");
        return a != null && a.index == 1 ? Alliance.RED : Alliance.BLUE;
    }

    /** The chosen start delay in seconds (0 if there is no delay line). */
    public double getDelaySec() {
        // "instanceof" asks "is this line a DelayItem?"; "(DelayItem) i" then treats it as one.
        for (Item i : items) if (i instanceof DelayItem) return ((DelayItem) i).value;
        return 0;
    }

    /** The chosen option of a choice line, e.g. {@code get("Route")}. */
    public String get(String label) {
        ListItem item = find(label);
        // A misspelled label is a bug in the program: say so clearly instead of guessing.
        if (item == null) throw new IllegalArgumentException("No choice called \"" + label + "\" in the menu");
        return item.show();
    }

    /** True if a yes/no line is YES. */
    public boolean isYes(String label) {
        return "YES".equals(get(label));
    }

    // The list line with this label, or null if there is none.
    private ListItem find(String label) {
        for (Item i : items) if (i instanceof ListItem && i.label.equals(label)) return (ListItem) i;
        return null;
    }

    // True only on the loop when button i goes from up to down.
    private boolean pressed(boolean[] now, int i) {
        return now[i] && !was[i];
    }
}
