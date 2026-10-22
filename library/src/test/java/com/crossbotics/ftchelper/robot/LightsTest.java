package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.Fakes;
import com.qualcomm.hardware.rev.RevBlinkinLedDriver.BlinkinPattern;
import org.junit.Test;

/** The Lights module: rule order, "otherwise", blinking and flashes on a pretend clock, both kinds of light. */
public class LightsTest {
    // The pretend clock, in nanoseconds.
    private long now = 0;

    private void at(double seconds) {
        now = (long) (seconds * 1e9);
    }

    /** A robot with one light (goBILDA or Blinkin). */
    static class LightBot extends RobotDefinition {
        final boolean blinkin;
        Lights lights;

        LightBot(boolean blinkin) {
            this.blinkin = blinkin;
        }

        @Override
        protected void define() {
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            lights = add(blinkin ? Lights.blinkin("blinkin") : Lights.gobilda("light"));
        }
    }

    private LightBot bot;
    private RobotTestKit kit;

    private Lights gobilda() {
        bot = new LightBot(false);
        bot.describe();
        bot.lights.useClock(() -> now);
        kit = new RobotTestKit();
        bot.setup(kit);
        return bot.lights;
    }

    private Fakes.FakeServo servo() {
        return kit.servos.get("light");
    }

    @Test public void theFirstTrueRuleWinsElseOtherwise() {
        Lights l = gobilda();
        final boolean[] full = {false}, follow = {false};
        l.show(LightColor.GREEN).when(() -> full[0]);
        l.show(LightColor.BLUE).when(() -> follow[0]);
        l.otherwise(LightColor.RED);
        bot.all().update();
        assertEquals("no rule true: otherwise", LightColor.RED, l.showing());
        assertEquals(LightColor.RED.gobildaPosition(), servo().position, 1e-9);
        follow[0] = true;
        bot.all().update();
        assertEquals(LightColor.BLUE, l.showing());
        full[0] = true;
        bot.all().update();
        assertEquals("both true: the one written first", LightColor.GREEN, l.showing());
        assertEquals(LightColor.GREEN.gobildaPosition(), servo().position, 1e-9);
    }

    @Test public void offByDefaultAndOnlyChangesAreSent() {
        Lights l = gobilda();
        assertTrue("nothing sent at setup (nothing shows before the first loop)", Double.isNaN(servo().position));
        bot.all().update();
        assertEquals(LightColor.OFF, l.showing());
        assertEquals(0.0, servo().position, 1e-9);
        int writes = servo().writes;
        for (int i = 0; i < 10; i++) bot.all().update();
        assertEquals("the same color is not sent again", writes, servo().writes);
    }

    @Test public void blinkingFollowsTheClockAndStartsOn() {
        Lights l = gobilda();
        final boolean[] jam = {false};
        l.show(LightColor.RED).blinking().when(() -> jam[0]);     // 2 a second: 0.25 s on, 0.25 s off
        at(10);
        bot.all().update();
        assertEquals(LightColor.OFF, l.showing());
        jam[0] = true;
        at(10.1);
        bot.all().update();
        assertEquals("on when the rule starts", LightColor.RED, l.showing());
        at(10.4);
        bot.all().update();
        assertEquals("dark half", LightColor.OFF, l.showing());
        assertEquals("color() still says RED", LightColor.RED, l.color());
        at(10.62);
        bot.all().update();
        assertEquals("on again", LightColor.RED, l.showing());
        at(10.85);
        bot.all().update();
        assertEquals(LightColor.OFF, l.showing());
    }

    @Test public void blinkingFasterThanFiveIsRefused() {
        Lights l = gobilda();
        try {
            l.show(LightColor.RED).blinking(8);
            fail("8 blinks a second is against R202 J");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("R202"));
        }
        l.show(LightColor.RED).blinking(5).when(() -> true);   // 5 is fine
    }

    @Test public void aFlashBeatsEveryRuleThenEnds() {
        Lights l = gobilda();
        l.show(LightColor.GREEN).when(() -> true);
        at(1);
        bot.all().update();
        assertEquals(LightColor.GREEN, l.showing());
        l.flash(LightColor.WHITE);                              // 0.5 s
        bot.all().update();
        assertEquals(LightColor.WHITE, l.showing());
        assertTrue(l.isFlashing());
        at(1.45);
        bot.all().update();
        assertEquals(LightColor.WHITE, l.showing());
        at(1.55);
        bot.all().update();
        assertEquals("back to the rules", LightColor.GREEN, l.showing());
        assertFalse(l.isFlashing());
        l.flash(LightColor.PURPLE, 2);
        at(3.5);
        bot.all().update();
        assertEquals(LightColor.PURPLE, l.showing());
    }

    @Test public void whenStartsFlashesOnlyOnTheChangeToTrue() {
        Lights l = gobilda();
        final boolean[] shooting = {true};
        l.show(LightColor.WHITE).forSeconds(0.3).whenStarts(() -> shooting[0]);
        at(0);
        bot.all().update();
        assertEquals("already true at the first look: no flash", LightColor.OFF, l.showing());
        shooting[0] = false;
        at(0.1);
        bot.all().update();
        shooting[0] = true;
        at(0.2);
        bot.all().update();
        assertEquals("false -> true: a flash", LightColor.WHITE, l.showing());
        at(0.45);
        bot.all().update();
        assertEquals("still true, no new flash", LightColor.WHITE, l.showing());
        at(0.55);
        bot.all().update();
        assertEquals("0.3 s later: over", LightColor.OFF, l.showing());
    }

    @Test public void lastSecondsCountFromStart() {
        Lights l = gobilda();
        l.show(LightColor.YELLOW).inLastSeconds(20);
        at(0);
        bot.all().update();
        assertEquals("before START: never", LightColor.OFF, l.showing());
        at(5);
        bot.started();                                          // START at 5 s
        at(5 + 99);
        bot.all().update();
        assertEquals("21 s left", LightColor.OFF, l.showing());
        at(5 + 100.5);
        bot.all().update();
        assertEquals("19.5 s left", LightColor.YELLOW, l.showing());
    }

    @Test public void aRuleThatFailsCountsAsFalseAndWarns() {
        Lights l = gobilda();
        l.show(LightColor.RED).when(() -> { throw new IllegalStateException("not set up"); });
        l.show(LightColor.GREEN).when(() -> true);
        bot.all().update();
        assertEquals(LightColor.GREEN, l.showing());
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        com.crossbotics.ftchelper.telemetry.RobotTelemetry t = new com.crossbotics.ftchelper.telemetry.RobotTelemetry(ft.telemetry, () -> 0L);
        l.addTelemetry(t);
        t.update();
        String screen = String.join("\n", ft.lastScreen);
        assertTrue(screen, screen.contains("a light rule failed"));
        assertTrue(screen, screen.contains("GREEN (rule 2)"));
    }

    @Test public void stopTurnsItOff() {
        Lights l = gobilda();
        l.otherwise(LightColor.BLUE);
        bot.all().update();
        assertEquals(LightColor.BLUE.gobildaPosition(), servo().position, 1e-9);
        bot.all().stop();
        assertEquals(0.0, servo().position, 1e-9);
    }

    @Test public void blinkinGetsTheSolidColorPatterns() {
        bot = new LightBot(true);
        bot.describe();
        bot.lights.useClock(() -> now);
        kit = new RobotTestKit();
        bot.setup(kit);
        bot.lights.otherwise(LightColor.GREEN);
        bot.all().update();
        // The SDK sends pattern n as servo position 0.2525 + 0.005 n (1005 + 10 n microseconds).
        double sent = kit.blinkins.get("blinkin")[0];
        assertEquals(0.2525 + 0.005 * BlinkinPattern.GREEN.ordinal(), sent, 1e-9);
        assertEquals(BlinkinPattern.VIOLET, LightColor.PURPLE.blinkinPattern());
        assertEquals(BlinkinPattern.BLACK, LightColor.OFF.blinkinPattern());
    }

    @Test public void goBildaPositionsAreInsideTheColorWindow() {
        // The hub's normal servo signal: 600 us at 0, 2400 us at 1. The light: off below 1100 us,
        // white above 1900 us (goBILDA 3118-0808-0002, Product Insight #4).
        for (LightColor c : LightColor.values()) {
            double us = 600 + 1800 * c.gobildaPosition();
            if (c == LightColor.OFF) assertTrue(us < 1100);
            else if (c == LightColor.WHITE) assertTrue(us > 1900);
            else assertTrue(c + " at " + us + " us", us >= 1100 && us <= 1890);
        }
    }

    @Test public void theSimulatorFileGetsALightLine() {
        bot = new LightBot(false);
        assertTrue(bot.simLines().toString(), bot.simLines().contains("light.light = gobilda"));
        assertTrue(new LightBot(true).simLines().contains("light.blinkin = blinkin"));
    }
}
