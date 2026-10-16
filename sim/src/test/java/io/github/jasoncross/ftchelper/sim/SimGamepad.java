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
            Long last = lastSeqBySession.get(session);
            if (last != null && seq <= last) return;
            lastSeqBySession.put(session, seq);
        }
        // Two pages for one gamepad (a phone and the laptop, or a second tab): the page that last
        // pressed something owns it; while it is active, "nothing pressed" from another page is ignored.
        boolean blank = isBlank(s);
        long now = System.nanoTime();
        if (!blank) {
            owner = session;
            ownerFrom = s.getOrDefault("_from", "");
            ownerSeen = now;
        } else if (!session.equals(owner) && (now - ownerSeen) / 1e9 < 1.0) {
            return;
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

    private final Map<String, Long> lastSeqBySession = new java.util.HashMap<>();
    private String owner = "", ownerFrom = "";
    private long ownerSeen = 0;

    /** The page that last pressed something on this gamepad: its session id and address. */
    synchronized String owner() { return owner; }
    synchronized String ownerFrom() { return ownerFrom; }

    private static boolean isBlank(Map<String, String> s) {
        for (Map.Entry<String, String> e : s.entrySet()) {
            String k = e.getKey();
            if (k.equals("session") || k.equals("seq") || k.startsWith("_")) continue;
            String v = e.getValue();
            if (v == null) continue;
            try {
                if (Math.abs(Double.parseDouble(v)) > 0.02) return false;
            } catch (NumberFormatException ex) {
                if (v.equals("true")) return false;
            }
        }
        return true;
    }

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
