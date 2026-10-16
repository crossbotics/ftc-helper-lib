package io.github.jasoncross.ftchelper.sim;

import com.qualcomm.robotcore.hardware.Gamepad;
import java.util.Map;

/**
 * Copies a gamepad's state (from the web page: on-screen, keyboard, a real controller or a phone)
 * into the SDK's Gamepad object that the OpMode reads.
 */
final class SimGamepad {
    final Gamepad gamepad = new Gamepad();
    // When the page last sent this gamepad (System.nanoTime). Silence = let go of everything.
    private long lastSeen = 0;

    /** Sets every button and axis from "name -> value" (buttons: 0/1; sticks and triggers: numbers). */
    synchronized void apply(Map<String, String> s) {
        // Updates from the page arrive on several threads: an older one must not undo a newer one.
        String session = s.getOrDefault("session", "");
        long seq = -1;
        try { seq = Long.parseLong(s.getOrDefault("seq", "-1")); } catch (NumberFormatException ignored) { }
        if (seq >= 0) {
            if (session.equals(lastSession) && seq <= lastSeq) return;
            lastSession = session;
            lastSeq = seq;
        }
        // Built on a fresh Gamepad, then copied in with the SDK's own copy(), so the SDK's
        // "was pressed" methods and the PlayStation names (cross, circle...) work too.
        Gamepad g = new Gamepad();
        g.a = on(s, "a");
        g.b = on(s, "b");
        g.x = on(s, "x");
        g.y = on(s, "y");
        g.left_bumper = on(s, "lb");
        g.right_bumper = on(s, "rb");
        g.dpad_up = on(s, "up");
        g.dpad_down = on(s, "down");
        g.dpad_left = on(s, "left");
        g.dpad_right = on(s, "right");
        g.back = on(s, "back");
        g.start = on(s, "start");
        g.guide = on(s, "guide");
        g.left_stick_button = on(s, "ls");
        g.right_stick_button = on(s, "rs");
        g.left_stick_x = axis(s, "lx");
        g.left_stick_y = axis(s, "ly");
        g.right_stick_x = axis(s, "rx");
        g.right_stick_y = axis(s, "ry");
        g.left_trigger = Math.max(0, axis(s, "lt"));
        g.right_trigger = Math.max(0, axis(s, "rt"));
        gamepad.copy(g);
        lastSeen = System.nanoTime();
    }

    private String lastSession = "";
    private long lastSeq = -1;

    /** No news from the page for a while (a closed tab): let go of everything, like a pulled cable. */
    synchronized void releaseIfSilent(double seconds) {
        if (lastSeen != 0 && (System.nanoTime() - lastSeen) / 1e9 > seconds) {
            gamepad.copy(new Gamepad());
            lastSeen = 0;
        }
    }

    private static boolean on(Map<String, String> s, String key) {
        String v = s.get(key);
        return v != null && (v.equals("1") || v.equals("true"));
    }

    private static float axis(Map<String, String> s, String key) {
        String v = s.get(key);
        if (v == null) return 0;
        try {
            float f = Float.parseFloat(v);
            if (Float.isNaN(f) || Float.isInfinite(f)) return 0;
            return Math.max(-1, Math.min(1, f));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
