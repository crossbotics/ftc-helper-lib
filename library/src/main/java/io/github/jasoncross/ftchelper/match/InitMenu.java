package io.github.jasoncross.ftchelper.match;

import com.qualcomm.robotcore.hardware.Gamepad;
import io.github.jasoncross.ftchelper.core.Profile;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
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
 * Docs: docs/modules/match.md
 */
public class InitMenu {
    private final Gamepad gamepad;
    private final List<Item> items = new ArrayList<>();
    private int selected = 0;
    private final boolean[] was = new boolean[6];

    private abstract static class Item {
        final String label;
        Item(String label) { this.label = label; }
        abstract void change(int direction);
        abstract String show();
    }

    private static final class ListItem extends Item {
        final List<String> options;
        int index;
        ListItem(String label, List<String> options, int start) {
            super(label);
            this.options = options;
            this.index = start;
        }
        void change(int d) { index = (index + d + options.size()) % options.size(); }
        String show() { return options.get(index); }
    }

    private final class DelayItem extends Item {
        final double max;
        double value;
        DelayItem(double max) { super("Start delay"); this.max = max; }
        void change(int d) { value = Math.max(0, Math.min(max, value + d)); }
        String show() { return String.format(Locale.US, "%.0f s", value); }
    }

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
        Alliance start = MatchMemory.allianceOr(Alliance.BLUE);
        items.add(new ListItem("Alliance", Arrays.asList("BLUE", "RED"), start == Alliance.RED ? 1 : 0));
        return this;
    }

    /** Adds a start delay choice, 0 to {@code maxSeconds}, in 1-second steps. */
    public InitMenu delay(double maxSeconds) {
        items.add(new DelayItem(maxSeconds));
        return this;
    }

    /** Adds a choice from a list. The first option is the default. */
    public InitMenu choice(String label, String... options) {
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
        boolean[] now = {gamepad.dpad_up, gamepad.dpad_down, gamepad.dpad_right, gamepad.dpad_left,
                gamepad.x, gamepad.b};
        if (!items.isEmpty()) {
            if (pressed(now, 0)) selected = (selected + items.size() - 1) % items.size();
            if (pressed(now, 1)) selected = (selected + 1) % items.size();
            if (pressed(now, 2)) items.get(selected).change(1);
            if (pressed(now, 3)) items.get(selected).change(-1);
            ListItem a = find("Alliance");
            if (a != null && pressed(now, 4)) a.index = 0;
            if (a != null && pressed(now, 5)) a.index = 1;
        }
        System.arraycopy(now, 0, was, 0, was.length);
    }

    /** Shows the menu. */
    public void addTelemetry(RobotTelemetry t) {
        t.section("Choose (D-pad), then START");
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
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
        for (Item i : items) if (i instanceof DelayItem) return ((DelayItem) i).value;
        return 0;
    }

    /** The chosen option of a choice line, e.g. {@code get("Route")}. */
    public String get(String label) {
        ListItem item = find(label);
        if (item == null) throw new IllegalArgumentException("No choice called \"" + label + "\" in the menu");
        return item.show();
    }

    /** True if a yes/no line is YES. */
    public boolean isYes(String label) {
        return "YES".equals(get(label));
    }

    private ListItem find(String label) {
        for (Item i : items) if (i instanceof ListItem && i.label.equals(label)) return (ListItem) i;
        return null;
    }

    private boolean pressed(boolean[] now, int i) {
        return now[i] && !was[i];
    }
}
