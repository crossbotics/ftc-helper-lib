package io.github.jasoncross.ftchelper.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.qualcomm.robotcore.hardware.Gamepad;
import io.github.jasoncross.ftchelper.Fakes;
import io.github.jasoncross.ftchelper.core.SettingsFile;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MatchTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "s.properties")));
        MatchMemory.clear();
    }

    @After public void back() {
        SettingsFile.useForTests(null);
        MatchMemory.clear();
    }

    @Test public void allianceMirrorsForRedOnly() {
        assertEquals(90, Alliance.BLUE.mirror(90), 1e-12);
        assertEquals(-90, Alliance.RED.mirror(90), 1e-12);
        assertEquals(Alliance.BLUE, Alliance.RED.other());
        assertEquals(24, Alliance.RED.pick(20, 24));
        assertEquals("b", Alliance.BLUE.pick("b", "r"));
        assertEquals(-1, Alliance.RED.sign());
    }

    @Test public void timerCountsDownAndRunsCuesOnce() {
        Fakes.Clock c = new Fakes.Clock();
        List<String> fired = new ArrayList<>();
        MatchTimer timer = new MatchTimer(120, c::get)
                .cueAt(60, () -> fired.add("60"))
                .cueAt(20, () -> fired.add("20"))
                .cueAt(500, () -> fired.add("never"));
        assertEquals(120, timer.secondsLeft(), 1e-9);
        assertFalse(timer.isRunning());
        timer.start();
        c.addSec(30); timer.update();
        assertEquals("1:30", timer.format());
        assertTrue(fired.isEmpty());
        c.addSec(31); timer.update(); timer.update();
        assertEquals(1, fired.size());
        c.addSec(45); timer.update();
        assertEquals("0:14", timer.format());
        assertEquals(2, fired.size());
        c.addSec(100); timer.update();
        assertTrue(timer.isOver());
        assertEquals("0:00", timer.format());
        assertFalse(fired.contains("never"));
    }

    @Test public void initMenuChoosesWithTheDpad() {
        Gamepad g = new Gamepad();
        InitMenu menu = new InitMenu(g).alliance().delay(5).choice("Route", "Near", "Far").yesNo("Park", true);
        assertEquals(Alliance.BLUE, menu.getAlliance());
        press(g, menu, "b");
        assertEquals(Alliance.RED, menu.getAlliance());
        press(g, menu, "down");                // to delay
        press(g, menu, "right"); press(g, menu, "right");
        assertEquals(2, menu.getDelaySec(), 1e-12);
        press(g, menu, "left"); press(g, menu, "left"); press(g, menu, "left");
        assertEquals(0, menu.getDelaySec(), 1e-12);      // not below 0
        press(g, menu, "down");                // to route
        press(g, menu, "right");
        assertEquals("Far", menu.get("Route"));
        press(g, menu, "right");
        assertEquals("Near", menu.get("Route"));          // wraps
        assertTrue(menu.isYes("Park"));
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        menu.addTelemetry(t);
        t.update();
        assertTrue(ft.lastScreen.contains("> Route: Near"));
    }

    @Test public void memoryHandsTheAllianceToTeleOp() {
        assertEquals(Alliance.BLUE, MatchMemory.allianceOr(Alliance.BLUE));
        MatchMemory.remember(Alliance.RED, null);
        assertEquals(Alliance.RED, MatchMemory.allianceOr(Alliance.BLUE));
        assertEquals(Alliance.RED, new InitMenu(new Gamepad()).alliance().getAlliance());
    }

    private static void press(Gamepad g, InitMenu menu, String button) {
        set(g, button, true);
        menu.update();
        set(g, button, false);
        menu.update();
    }

    private static void set(Gamepad g, String button, boolean v) {
        switch (button) {
            case "up": g.dpad_up = v; break;
            case "down": g.dpad_down = v; break;
            case "left": g.dpad_left = v; break;
            case "right": g.dpad_right = v; break;
            case "b": g.b = v; break;
            case "x": g.x = v; break;
            default: throw new IllegalArgumentException(button);
        }
    }

    @Test public void aCueAtTheFullLengthRunsAtStart() {
        Fakes.Clock c = new Fakes.Clock();
        List<String> fired = new ArrayList<>();
        MatchTimer timer = new MatchTimer(120, c::get).cueAt(120, () -> fired.add("start"));
        timer.start();
        timer.update();
        assertEquals(1, fired.size());
    }

    @Test public void aNegativeClockStillCountsAsStarted() {
        long[] now = {-5_000_000_000L};
        MatchTimer timer = new MatchTimer(30, () -> now[0]);
        timer.start();
        assertTrue(timer.isRunning());
        now[0] += 10_000_000_000L;
        assertEquals(20, timer.secondsLeft(), 1e-9);
    }
}
