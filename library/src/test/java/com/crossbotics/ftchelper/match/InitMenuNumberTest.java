package com.crossbotics.ftchelper.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.qualcomm.robotcore.hardware.Gamepad;
import org.junit.Test;

/** The INIT menu's number and angle lines: one step per press, more steps while held, ends and wrap. */
public class InitMenuNumberTest {

    // A menu with a clock we move by hand (milliseconds).
    private static final class TimedMenu extends InitMenu {
        long ms;
        TimedMenu(Gamepad g) { super(g); }
        @Override long nanoTime() { return ms * 1_000_000L; }
    }

    private static void tap(Gamepad g, TimedMenu m, boolean right) {
        set(g, right, true);
        m.update();
        m.ms += 20;
        set(g, right, false);
        m.update();
        m.ms += 20;
    }

    private static void set(Gamepad g, boolean right, boolean on) {
        if (right) g.dpad_right = on; else g.dpad_left = on;
    }

    // Holds the button for ms milliseconds, with a loop every 20 ms, then lets go.
    private static void hold(Gamepad g, TimedMenu m, boolean right, long ms) {
        set(g, right, true);
        for (long t = 0; t <= ms; t += 20) {
            m.update();
            m.ms += 20;
        }
        set(g, right, false);
        m.update();
    }

    @Test public void aTapIsOneStepAndTheEndsHold() {
        Gamepad g = new Gamepad();
        TimedMenu m = new TimedMenu(g);
        m.number("Distance (in)", 0, 96, 1, 24);
        assertEquals("24", m.get("Distance (in)"));
        tap(g, m, true);
        assertEquals(25, m.getNumber("Distance (in)"), 1e-9);
        tap(g, m, false);
        tap(g, m, false);
        assertEquals(23, m.getNumber("Distance (in)"), 1e-9);
        for (int i = 0; i < 100; i++) tap(g, m, true);
        assertEquals(96, m.getNumber("Distance (in)"), 1e-9);
        for (int i = 0; i < 100; i++) tap(g, m, false);
        assertEquals(0, m.getNumber("Distance (in)"), 1e-9);
    }

    @Test public void holdingKeepsSteppingAfterAShortWait() {
        Gamepad g = new Gamepad();
        TimedMenu m = new TimedMenu(g);
        m.number("Distance (in)", 0, 96, 1, 24);
        // Held 0.3 s: still only the first step (the wait is 0.4 s).
        hold(g, m, true, 300);
        assertEquals(25, m.getNumber("Distance (in)"), 1e-9);
        // Held 1.2 s: the first step, then one each 0.08 s after 0.4 s: 1 + 10 or 11.
        hold(g, m, true, 1200);
        double v = m.getNumber("Distance (in)");
        if (v < 35 || v > 37) fail("expected about 36, got " + v);
        // Held left long enough to reach the end: stops at 0.
        hold(g, m, false, 10_000);
        assertEquals(0, m.getNumber("Distance (in)"), 1e-9);
    }

    @Test public void holdingAListLineStillChangesItOnce() {
        Gamepad g = new Gamepad();
        TimedMenu m = new TimedMenu(g);
        m.choice("Route", "Near", "Mid", "Far");
        hold(g, m, true, 2000);
        assertEquals("Mid", m.get("Route"));
    }

    @Test public void anAngleGoesAroundTheCircle() {
        Gamepad g = new Gamepad();
        TimedMenu m = new TimedMenu(g);
        m.angle("Direction (deg)", 5, 0);
        tap(g, m, false);
        assertEquals(-5, m.getNumber("Direction (deg)"), 1e-9);
        for (int i = 0; i < 34; i++) tap(g, m, false);
        assertEquals(-180 + 5, m.getNumber("Direction (deg)"), 1e-9);
        tap(g, m, false);
        assertEquals(180, m.getNumber("Direction (deg)"), 1e-9);
        tap(g, m, true);
        assertEquals(-175, m.getNumber("Direction (deg)"), 1e-9);
    }

    @Test public void halfStepsShowTheirDecimal() {
        Gamepad g = new Gamepad();
        TimedMenu m = new TimedMenu(g);
        m.number("Distance (in)", 0, 10, 0.5, 2);
        tap(g, m, true);
        assertEquals("2.5", m.get("Distance (in)"));
        tap(g, m, true);
        assertEquals("3", m.get("Distance (in)"));
    }

    @Test public void rightAndLeftTogetherChangeNothing() {
        Gamepad g = new Gamepad();
        TimedMenu m = new TimedMenu(g);
        m.number("Distance (in)", 0, 96, 1, 96);
        g.dpad_right = true;
        g.dpad_left = true;
        for (int i = 0; i < 50; i++) {
            m.update();
            m.ms += 20;
        }
        assertEquals(96, m.getNumber("Distance (in)"), 1e-9);
    }

    @Test public void movingTheCursorWhileHoldingChangesNoOtherLine() {
        Gamepad g = new Gamepad();
        TimedMenu m = new TimedMenu(g);
        m.number("Distance (in)", 0, 96, 1, 24);
        m.angle("Direction (deg)", 5, 0);
        g.dpad_right = true;
        for (int i = 0; i < 30; i++) { m.update(); m.ms += 20; }   // 0.6 s on Distance: it repeats
        g.dpad_down = true;                                          // a diagonal: down while right is held
        for (int i = 0; i < 30; i++) { m.update(); m.ms += 20; }
        g.dpad_right = false;
        g.dpad_down = false;
        m.update();
        assertEquals(0, m.getNumber("Direction (deg)"), 1e-9);
    }

    @Test public void notANumberIsRefused() {
        InitMenu m = new InitMenu(new Gamepad());
        try { m.number("D", 0, 10, 1, Double.NaN); fail(); } catch (IllegalArgumentException e) { /* NaN start */ }
        try { m.number("D", Double.NaN, 10, 1, 5); fail(); } catch (IllegalArgumentException e) { /* NaN min */ }
        try { m.angle("A", 5, Double.NaN); fail(); } catch (IllegalArgumentException e) { /* NaN start */ }
        m.number("Distance (in)", 0, 96, 1, 24);
        try { m.isYes("Distance (in)"); fail(); } catch (IllegalArgumentException e) {
            assertEquals(true, e.getMessage().contains("getNumber"));
        }
    }

    @Test public void roundingNeverPassesTheEnds() {
        Gamepad g = new Gamepad();
        TimedMenu m = new TimedMenu(g);
        m.number("N", 0, 0.1234567, 0.1, 0.1);
        tap(g, m, true);
        assertEquals(true, m.getNumber("N") <= 0.1234567);
        m.angle("A", 0.001, -179.9989996); // one step left rounds to -180, which must show as 180
        m.update();
        g.dpad_down = true;
        m.update();
        g.dpad_down = false;
        m.update();
        tap(g, m, false);
        double a = m.getNumber("A");
        assertEquals(180, a, 1e-9);
    }

    @Test public void badSettingsAndMissingLinesSayWhy() {
        InitMenu m = new InitMenu(new Gamepad());
        try { m.number("D", 0, 10, 0, 5); fail(); } catch (IllegalArgumentException e) { /* step 0 */ }
        try { m.number("D", 0, 10, 0.0000001, 5); fail(); } catch (IllegalArgumentException e) { /* step too small */ }
        try { m.number("D", 0, 10, 1, 11); fail(); } catch (IllegalArgumentException e) { /* start past max */ }
        try { m.angle("A", 5, -180); fail(); } catch (IllegalArgumentException e) { /* -180 is the same as 180 */ }
        m.number("Distance (in)", 0, 96, 1, 24);
        try {
            m.getNumber("Distance");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(true, e.getMessage().contains("Distance (in)"));
        }
    }
}
