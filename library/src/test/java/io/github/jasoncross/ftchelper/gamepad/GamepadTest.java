package io.github.jasoncross.ftchelper.gamepad;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import static io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import static io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Trigger;

import com.qualcomm.robotcore.hardware.Gamepad;
import org.junit.Test;

public class GamepadTest {
    private static final double E = 1e-9;

    @Test public void pressAndHoldShareAButton() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        int[] counts = {0, 0};
        ControllerBindings c = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> counts[0]++)
                .bindHold(Pad.G1, Btn.A, () -> counts[1]++);
        g1.a = true;
        c.update(g1, g2);
        c.update(g1, g2);
        assertArrayEquals(new int[]{1, 2}, counts);
        g1.a = false; c.update(g1, g2);
        g1.a = true; c.update(g1, g2);
        assertArrayEquals(new int[]{2, 3}, counts);
        c.clear(); c.update(g1, g2);
        assertArrayEquals(new int[]{2, 3}, counts);
    }

    @Test public void releaseFiresOnTheWayUp() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        int[] count = {0};
        ControllerBindings c = new ControllerBindings().bindRelease(Pad.G2, Btn.X, () -> count[0]++);
        c.update(g1, g2);
        g2.x = true; c.update(g1, g2);
        assertEquals(0, count[0]);
        g2.x = false; c.update(g1, g2);
        assertEquals(1, count[0]);
    }

    @Test public void triggerAndBumperAreIndependentAndPadsDoNotCross() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        int[] counts = {0, 0};
        ControllerBindings c = new ControllerBindings()
                .bindTriggerPress(Pad.G2, Trigger.RT, () -> counts[0]++)
                .bindPress(Pad.G2, Btn.RB, () -> counts[1]++);
        g1.right_trigger = 1; g1.right_bumper = true; c.update(g1, g2);
        assertArrayEquals(new int[]{0, 0}, counts);
        g2.right_trigger = 0.6f; c.update(g1, g2); c.update(g1, g2);
        assertArrayEquals(new int[]{1, 0}, counts);
        g2.right_bumper = true; c.update(g1, g2);
        assertArrayEquals(new int[]{1, 1}, counts);
        g2.right_trigger = 0.4f; c.update(g1, g2);   // below threshold = released
        g2.right_trigger = 0.6f; c.update(g1, g2);
        assertArrayEquals(new int[]{2, 1}, counts);
    }

    @Test public void triggerThresholdCanChange() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        int[] count = {0};
        ControllerBindings c = new ControllerBindings().setTriggerThreshold(0.8)
                .bindTriggerHold(Pad.G1, Trigger.LT, () -> count[0]++);
        g1.left_trigger = 0.7f; c.update(g1, g2);
        assertEquals(0, count[0]);
        g1.left_trigger = 0.9f; c.update(g1, g2);
        assertEquals(1, count[0]);
    }

    @Test public void triggerAxisGivesTheValue() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        double[] seen = {-1};
        ControllerBindings c = new ControllerBindings().bindTriggerAxis(Pad.G2, Trigger.LT, v -> seen[0] = v);
        g2.left_trigger = 0.25f; c.update(g1, g2);
        assertEquals(0.25, seen[0], 1e-6);
    }

    @Test public void toggleChangesOnlyOnPress() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        int[] counts = {0, 0};
        ControllerBindings c = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.Y, () -> counts[0]++, () -> counts[1]++);
        g1.y = true; c.update(g1, g2); c.update(g1, g2);
        g1.y = false; c.update(g1, g2);
        g1.y = true; c.update(g1, g2);
        assertArrayEquals(new int[]{1, 1}, counts);
    }

    @Test public void buttonHeldThroughStartMustBeReleasedFirst() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        int[] count = {0};
        ControllerBindings c = new ControllerBindings().bindPress(Pad.G1, Btn.L_STICK_BTN, () -> count[0]++);
        g1.left_stick_button = true;
        c.ignoreHeldButtons(g1, g2);
        c.update(g1, g2);
        assertEquals(0, count[0]);
        g1.left_stick_button = false; c.update(g1, g2);
        g1.left_stick_button = true; c.update(g1, g2);
        assertEquals(1, count[0]);
    }

    @Test public void extraPaddlesUseTheirReaders() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        boolean[] paddle = {false};
        int[] count = {0};
        ControllerBindings c = new ControllerBindings()
                .registerExtraButton(Pad.G1, Btn.M1, () -> paddle[0])
                .bindPress(Pad.G1, Btn.M1, () -> count[0]++);
        c.update(g1, g2);
        paddle[0] = true; c.update(g1, g2);
        assertEquals(1, count[0]);
        assertTrue(c.isDown(Pad.G1, Btn.M1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void onlyPaddlesCanBeExtraButtons() {
        new ControllerBindings().registerExtraButton(Pad.G1, Btn.A, () -> true);
    }

    @Test public void stickShaping() {
        assertEquals(0.25, StickShaping.curve(0.5, 2), E);
        assertEquals(-0.25, StickShaping.curve(-0.5, 2), E);
        assertEquals(0.5, StickShaping.curve(0.5, 0.5), E);    // below 1 is treated as 1
        assertEquals(1, StickShaping.curve(3, 2), E);          // clamped
        assertEquals(0, StickShaping.shape(0.04, 0.05, 2), E);
        assertEquals(1, StickShaping.shape(1, 0.05, 2), E);
        assertEquals(1.0, StickShaping.slowScale(0, 0.25), E);
        assertEquals(0.25, StickShaping.slowScale(1, 0.25), E);
        assertEquals(0.625, StickShaping.slowScale(0.5, 0.25), E);
        assertEquals(0.25, StickShaping.slowScale(7, 0.25), E);
        assertEquals(1.0, StickShaping.slowScale(-1, 0.25), E);
    }

    @Test public void toggleRumbleTracksItsOwnDuration() {
        long[] now = {0};
        ToggleRumble rumble = new ToggleRumble(new Gamepad(), () -> now[0]).setPattern(0.8, 100, 50);
        assertFalse(rumble.isPlaying());
        rumble.play(true);                 // 100 + 50 + 100 = 250 ms
        now[0] = 249_000_000L;
        assertTrue(rumble.isPlaying());
        now[0] = 250_000_000L;
        assertFalse(rumble.isPlaying());
        rumble.play(false);                // 100 ms
        now[0] += 99_000_000L;
        assertTrue(rumble.isPlaying());
        now[0] += 1_000_000L;
        assertFalse(rumble.isPlaying());
    }

    @Test public void aimRumbleOnlyInsideThresholdAndAfterCooldown() {
        long[] now = {0};
        AimRumble aim = new AimRumble(new Gamepad(), () -> now[0]).setThreshold(2.5);
        assertFalse(aim.update(3.0));          // outside
        assertFalse(aim.update(Double.NaN));   // no target
        assertTrue(aim.update(0));             // inside: buzz
        now[0] += 100_000_000L;
        assertFalse(aim.update(0));            // cooldown at zero error is 350 ms
        now[0] += 300_000_000L;
        assertTrue(aim.update(0));
        aim.setEnabled(false);
        now[0] += 1_000_000_000L;
        assertFalse(aim.update(0));
    }

    @Test public void heldThroughStartSuppressesHoldAndReleaseToo() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        int[] counts = {0, 0};
        ControllerBindings c = new ControllerBindings()
                .bindHold(Pad.G1, Btn.DPAD_UP, () -> counts[0]++)
                .bindRelease(Pad.G1, Btn.DPAD_UP, () -> counts[1]++);
        g1.dpad_up = true;
        c.ignoreHeldButtons(g1, g2);
        c.update(g1, g2);
        c.update(g1, g2);
        g1.dpad_up = false; c.update(g1, g2);
        assertArrayEquals(new int[]{0, 0}, counts);
        g1.dpad_up = true; c.update(g1, g2);
        g1.dpad_up = false; c.update(g1, g2);
        assertArrayEquals(new int[]{1, 1}, counts);
    }

    @Test public void aSlowButtonActionIsReported() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        ControllerBindings c = new ControllerBindings().bindPress(Pad.G2, Btn.A, () -> {
            long end = System.nanoTime() + 40_000_000L;      // 40 ms: like a sleep() in an action
            while (System.nanoTime() < end) { }
        });
        c.update(g1, g2);
        assertEquals("", c.getSlowActionWarning());
        g2.a = true;
        c.update(g1, g2);
        assertTrue(c.getSlowActionWarning().startsWith("G2:A action took"));
    }

    @Test public void releaseAllRunsReleaseActionsForHeldButtons() {
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        int[] stops = {0};
        ControllerBindings c = new ControllerBindings().bindRelease(Pad.G1, Btn.RB, () -> stops[0]++);
        c.update(g1, g2);
        g1.right_bumper = true; c.update(g1, g2);          // intake started by holding RB
        c.releaseAll();                                    // a menu takes the gamepad
        assertEquals(1, stops[0]);
        g1.right_bumper = false;
        c.ignoreHeldButtons(g1, g2);
        c.update(g1, g2);
        assertEquals("no second release", 1, stops[0]);
    }
}
