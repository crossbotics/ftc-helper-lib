package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.crossbotics.ftchelper.match.InitMenu;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.util.Arrays;
import org.junit.Test;

/**
 * The INIT reads of an autonomous's steps: a start spot or a park time inside an onlyIf follows the
 * drivers' choice (Auto reads the list again when a choice changes). No step runs in INIT.
 */
public class StepsMenuTest {

    // The list of a "choose the start" autonomous, as a team writes it.
    private static void steps(Steps s) {
        s.menuChoice("Start", "Near", "Far")
         .menuYesNo("Cycle", false)
         .onlyIf("Start", "Near")
             .start("NEAR", -36, -63, 0)
         .end()
         .onlyIf("Start", "Far")
             .start("FAR", 36, -63, 90)
         .end()
         .onlyIf("Cycle")
             .parkWithSeconds(5)
         .end()
         .onlyIf("Cycle", "NO")
             .parkWithSeconds(3)
         .end();
    }

    private static void right(Gamepad g, InitMenu menu) {
        g.dpad_right = true;
        menu.update();
        g.dpad_right = false;
        menu.update();
    }

    private static void down(Gamepad g, InitMenu menu) {
        g.dpad_down = true;
        menu.update();
        g.dpad_down = false;
        menu.update();
    }

    @Test public void theFirstReadAddsTheMenuAndUsesTheFirstOptions() {
        InitMenu menu = new InitMenu(new Gamepad());
        Steps first = new Steps(null, menu, null);
        steps(first);
        assertEquals("NEAR", first.where);
        assertEquals(-36, first.spotX, 1e-9);
        assertEquals(0, first.headingDeg, 1e-9);
        assertEquals(3, first.parkSec, 1e-9);
        assertEquals(Arrays.asList("Start", "Cycle"), first.menuLabels);
        assertEquals("Near", menu.get("Start"));
    }

    @Test public void aLaterReadFollowsTheChoicesWithoutAddingLinesAgain() {
        Gamepad g = new Gamepad();
        InitMenu menu = new InitMenu(g);
        steps(new Steps(null, menu, null));
        right(g, menu);                       // Start: Far
        down(g, menu);
        right(g, menu);                       // Cycle: YES
        Steps again = new Steps(null, menu, null, false);
        steps(again);
        assertEquals("FAR", again.where);
        assertEquals(36, again.spotX, 1e-9);
        assertEquals(90, again.headingDeg, 1e-9);
        assertEquals(5, again.parkSec, 1e-9);
        // The menu still has two lines: down twice comes back to the first.
        down(g, menu);
        right(g, menu);
        assertEquals("Near", menu.get("Start"));
    }

    @Test public void aStartInsideAnOnlyIfThatIsNotChosenIsNotUsed() {
        InitMenu menu = new InitMenu(new Gamepad());
        Steps s = new Steps(null, menu, null);
        s.menuYesNo("Far start", false)
         .onlyIf("Far start").start("FAR", 36, -63, 0).end();
        assertNull(s.where);
        // A line read before it is added (a mistake): not chosen in INIT, no crash.
        Steps t = new Steps(null, new InitMenu(new Gamepad()), null);
        t.onlyIf("Later", "A").start("A", 0).end().menuChoice("Later", "A", "B");
        assertNull(t.where);
    }
}
