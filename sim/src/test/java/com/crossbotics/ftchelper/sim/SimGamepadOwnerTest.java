package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** "Another page is using this gamepad": only while that page is open. A reloaded page has a new session. */
public class SimGamepadOwnerTest {
    private static Map<String, String> update(String session, long seq, String ly) {
        Map<String, String> m = new HashMap<>();
        m.put("session", session);
        m.put("seq", Long.toString(seq));
        m.put("_from", "127.0.0.1");
        m.put("ly", ly);
        return m;
    }

    @Test public void aPageThatStopsSendingIsNoLongerTheOwner() {
        SimGamepad g = new SimGamepad();
        assertEquals("", g.owner());
        g.apply(update("old", 1, "-1"));
        assertEquals("old", g.owner());
        // Let go: the page still sends, so it is still there.
        g.apply(update("old", 2, "0"));
        long later = System.nanoTime() + (long) ((SimGamepad.OWNER_GONE_SEC + 1) * 1e9);
        assertEquals("silent for a while: gone", "", g.owner(later));
    }

    @Test public void aNewPageWithNothingPressedDoesNotKeepTheOldOwnerAlive() {
        SimGamepad g = new SimGamepad();
        g.apply(update("old", 1, "-1"));
        g.apply(update("new", 1, "0"));
        long later = System.nanoTime() + (long) ((SimGamepad.OWNER_GONE_SEC + 1) * 1e9);
        assertEquals("", g.owner(later));
    }
}
