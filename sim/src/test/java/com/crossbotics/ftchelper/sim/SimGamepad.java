package com.crossbotics.ftchelper.sim;

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
    /** True while a test runs on the page: the test holds the gamepad, the page's updates are ignored. */
    volatile boolean pageLocked = false;
    /** Told about each update from the page that was used (the test recorder): the buttons and sticks. */
    volatile java.util.function.Consumer<Map<String, String>> onPage = null;

    /** Sets every button and axis from "name -> value" (buttons: 0/1; sticks and triggers: numbers). */
    synchronized void apply(Map<String, String> s) {
        apply(s, true);
    }

    /**
     * fromPage: true for the web page (a silent page lets go after a while, like a pulled cable);
     * false for a scenario or a test (what it holds stays held until it lets go).
     */
    synchronized void apply(Map<String, String> s, boolean fromPage) {
        if (fromPage && pageLocked) return;
        // Updates from the page arrive on several threads: an older one must not undo a newer one.
        String session = s.getOrDefault("session", "");
        if (session.length() > 64) session = session.substring(0, 64);
        if (fromPage && session.equals(owner)) ownerHeard = System.nanoTime();
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
            ownerHeard = now;
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
        lastSeen = fromPage ? System.nanoTime() : 0;
        java.util.function.Consumer<Map<String, String>> tell = onPage;
        if (fromPage && tell != null) tell.accept(s);
    }

    // the newest numbers of the last 32 pages (a page makes one session; older ones are forgotten, so a
    // device on the network cannot fill the memory with made-up sessions)
    private final Map<String, Long> lastSeqBySession = new java.util.LinkedHashMap<String, Long>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e) { return size() > 32; }
    };
    private String owner = "", ownerFrom = "";
    private long ownerSeen = 0;
    private long ownerHeard = 0;     // when the owner's page last sent anything: a page sends many times a second
    /** A page silent this long has gone (closed, or reloaded: a reloaded page has a new session). */
    static final double OWNER_GONE_SEC = 3;

    // When the latest buzz the OpMode asked for ends (System.nanoTime), 0 = none yet.
    private long rumbleUntil = 0;
    // The latest buzz pattern: a number that goes up with each new one, and its steps
    // (milliseconds, and 1 = buzzing / 0 = a gap), so the page plays "two buzzes" as two.
    private int rumbleSeq = 0;
    private final java.util.List<int[]> rumbleSteps = new java.util.ArrayList<>();

    /**
     * Seconds of buzzing left: the OpMode's buzzes (gamepad.rumble, runRumbleEffect) wait in the
     * SDK's rumble queue; each one is taken out here and its length added up. The page shakes the
     * gamepad picture (and a real controller or a phone buzzes) while this is more than 0.
     */
    synchronized double rumbleLeft() {
        long now = System.nanoTime();
        Gamepad.RumbleEffect e;
        while (gamepad.rumbleQueue != null && (e = gamepad.rumbleQueue.poll()) != null) {
            long ms = 0;
            rumbleSteps.clear();
            for (Gamepad.RumbleEffect.Step s : e.steps) {
                int d = Math.max(0, Math.min(s.duration, 10_000));
                ms += d;
                if (rumbleSteps.size() < 20) rumbleSteps.add(new int[] {d, s.large > 0 || s.small > 0 ? 1 : 0});
            }
            // A new effect replaces the one playing (as on a real gamepad).
            rumbleUntil = now + Math.min(ms, 10_000) * 1_000_000L;
            rumbleSeq++;
        }
        return Math.max(0, (rumbleUntil - now) / 1e9);
    }

    /** The latest buzz pattern as JSON: {"seq":n,"steps":[[ms,on],...]} (call after rumbleLeft()). */
    synchronized String rumbleJson() {
        StringBuilder b = new StringBuilder("{\"seq\":").append(rumbleSeq).append(",\"steps\":[");
        for (int i = 0; i < rumbleSteps.size(); i++) {
            if (i > 0) b.append(',');
            b.append('[').append(rumbleSteps.get(i)[0]).append(',').append(rumbleSteps.get(i)[1]).append(']');
        }
        return b.append("]}").toString();
    }

    /** The page that last pressed something on this gamepad, while it is still open: its session id, or "". */
    synchronized String owner() { return owner(System.nanoTime()); }
    /** {@link #owner()} at this time (System.nanoTime). */
    synchronized String owner(long nowNanos) {
        return (nowNanos - ownerHeard) / 1e9 > OWNER_GONE_SEC ? "" : owner;
    }
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
            java.util.function.Consumer<Map<String, String>> tell = onPage;
            if (tell != null) tell.accept(new java.util.HashMap<String, String>());
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
