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
 * Docs: docs/guides/tuning.md
 */
public class TuningMenu {
    private static final int PAGE = 8;

    private final Gamepad gamepad;
    private boolean open = false;
    private int selected = 0;
    private String message = "";
    private final boolean[] was = new boolean[8];

    /** @param gamepad the gamepad that runs the menu */
    public TuningMenu(Gamepad gamepad) {
        this.gamepad = gamepad;
    }

    /** Opens or closes the menu. In the COMPETITION profile it stays closed. */
    public void toggle() {
        if (open) open = false;
        else open = Profile.current().isPractice();
        message = open ? "" : message;
    }

    /** True while the menu is open. Do not drive with the same gamepad while it is open. */
    public boolean isOpen() {
        return open;
    }

    /** Reads the gamepad. Call every loop (it does nothing while closed). */
    public void update() {
        boolean[] now = {gamepad.dpad_up, gamepad.dpad_down, gamepad.dpad_right, gamepad.dpad_left,
                gamepad.a, gamepad.x, gamepad.y, false};
        if (open) {
            List<Tunable> all = Tunable.all();
            if (!all.isEmpty()) {
                selected = Math.max(0, Math.min(selected, all.size() - 1));
                Tunable t = all.get(selected);
                int times = (gamepad.right_bumper || gamepad.left_bumper) ? 10 : 1;
                if (pressed(now, 0)) selected = (selected + all.size() - 1) % all.size();
                if (pressed(now, 1)) selected = (selected + 1) % all.size();
                if (pressed(now, 2)) for (int i = 0; i < times; i++) t.increase();
                if (pressed(now, 3)) for (int i = 0; i < times; i++) t.decrease();
                if (pressed(now, 4)) message = Tunable.saveAll() ? "Saved on the hub." : "SAVE FAILED.";
                if (pressed(now, 5)) { t.reset(); message = t.name() + " back to the code value."; }
                if (pressed(now, 6)) message = Tunable.resetAll() ? "All back to code values." : "RESET FAILED.";
            }
        }
        System.arraycopy(now, 0, was, 0, was.length);
    }

    /** Shows the menu (only when open). */
    public void addTelemetry(RobotTelemetry t) {
        if (!open) return;
        List<Tunable> all = Tunable.all();
        t.section("TUNING (practice)");
        if (all.isEmpty()) {
            t.text("No tunables. Make some with Tunable.number(...).");
            return;
        }
        int first = (selected / PAGE) * PAGE;
        for (int i = first; i < Math.min(all.size(), first + PAGE); i++) {
            Tunable tu = all.get(i);
            String mark = i == selected ? "> " : "  ";
            String changed = tu.isChanged() ? "  (code " + tu.defaultValue() + ")" : "";
            t.text(mark + tu.name() + " = " + tu.display() + changed);
        }
        t.text(String.format(java.util.Locale.US, "%d/%d  D-pad: choose/change  bumper: x10", selected + 1, all.size()));
        t.text("A save   X reset this   Y reset all");
        if (!message.isEmpty()) t.text(message);
    }

    private boolean pressed(boolean[] now, int i) {
        return now[i] && !was[i];
    }
}
