package com.crossbotics.ftchelper.teleop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Trigger;
import com.crossbotics.ftchelper.teleop.LibraryButtons.Job;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** The library's own TeleOp buttons: where they are, clashes, and how taps, holds and pairs run. */
public class LibraryButtonsTest {
    private final Fakes.Clock clock = new Fakes.Clock();
    private final Gamepad g1 = new Gamepad(), g2 = new Gamepad();
    private final List<String> ran = new ArrayList<>();

    private static Set<Job> all() {
        return EnumSet.allOf(Job.class);
    }

    // Every job only writes its name into "ran".
    private LibraryKeys keys(LibraryButtons lib, Set<Job> jobs) {
        Map<Job, Runnable> map = LibraryKeys.jobs();
        for (final Job j : Job.values()) map.put(j, () -> ran.add(j.name()));
        return new LibraryKeys(lib.slots(jobs), map, clock::get);
    }

    // One loop after `sec` seconds.
    private void loop(LibraryKeys k, double sec) {
        clock.addSec(sec);
        k.update(g1, g2, false);
    }

    // ----------------------------- the defaults -----------------------------

    @Test public void theDefaultsAreTheOldButtonsPlusPoseAndAlliance() {
        Map<Key, String> w = LibraryButtons.twoDrivers().words(all());
        assertEquals("pause / go on; hold 1 s: forward = away from you now; with START: debug lines; "
                + "hold both 1.5 s: switch alliance (BLUE / RED)", w.get(Key.g1(Btn.BACK)));
        assertEquals("hold 1 s: set pose here; with BACK: debug lines; hold both 1.5 s: switch alliance (BLUE / RED)",
                w.get(Key.g1(Btn.START)));
        assertEquals("field-centric on/off", w.get(Key.g1(Btn.Y)));
        assertEquals("slow (squeeze: the further, the slower)", w.get(Key.g1(Trigger.LT)));
        assertEquals("button map; hold 1 s: tuning menu", w.get(Key.g1(Btn.GUIDE)));
        assertEquals(Key.g1(Btn.X), LibraryButtons.twoDrivers().initBlue());
        assertEquals(Key.g1(Btn.B), LibraryButtons.twoDrivers().initRed());
    }

    @Test public void withoutAPauseJobBackResetsForwardOnATap() {
        Set<Job> jobs = all();
        jobs.remove(Job.PAUSE);
        jobs.remove(Job.BUTTON_MAP);
        Map<Key, String> w = LibraryButtons.twoDrivers().words(jobs);
        assertTrue(w.get(Key.g1(Btn.BACK)), w.get(Key.g1(Btn.BACK)).startsWith("forward = away from you now; with START"));
        assertEquals("tuning menu", w.get(Key.g1(Btn.GUIDE)));
    }

    @Test public void oneDriverHasSlowAndFieldCentricOnTheStickClicks() {
        Map<Key, String> w = LibraryButtons.oneDriver().words(all());
        assertEquals("slow on/off", w.get(Key.g1(Btn.L_STICK_BTN)));
        assertEquals("field-centric on/off", w.get(Key.g1(Btn.R_STICK_BTN)));
        assertNull(w.get(Key.g1(Trigger.LT)));
        assertNull(w.get(Key.g1(Btn.Y)));
    }

    // ----------------------------- moving and freeing -----------------------------

    @Test public void everyJobCanBeMovedOrFreed() {
        LibraryButtons lib = LibraryButtons.twoDrivers()
                .pause(Key.g2(Btn.BACK)).resetForward(Key.g1(Btn.DPAD_DOWN)).fieldCentric(Key.g1(Btn.X))
                .slow(Key.g1(Trigger.RT)).buttonMap(null).tuningMenu(Key.g1(Btn.R_STICK_BTN))
                .debug(Key.g1(Btn.START), null).setPose(null).switchAlliance(null, null).allianceInInit(Key.g2(Btn.X), null);
        Map<Key, String> w = lib.words(all());
        assertEquals("pause / go on", w.get(Key.g2(Btn.BACK)));
        assertEquals("forward = away from you now", w.get(Key.g1(Btn.DPAD_DOWN)));
        assertEquals("field-centric on/off", w.get(Key.g1(Btn.X)));
        assertEquals("slow (squeeze: the further, the slower)", w.get(Key.g1(Trigger.RT)));
        assertEquals("tuning menu", w.get(Key.g1(Btn.R_STICK_BTN)));
        assertEquals("debug lines", w.get(Key.g1(Btn.START)));
        assertNull("BACK is free", w.get(Key.g1(Btn.BACK)));
        assertNull("Y is free", w.get(Key.g1(Btn.Y)));
        assertNull("LT is free", w.get(Key.g1(Trigger.LT)));
        assertNull("GUIDE is free", w.get(Key.g1(Btn.GUIDE)));
        assertEquals(Key.g2(Btn.X), lib.initBlue());
        assertNull(lib.initRed());
    }

    @Test public void twoLibraryJobsOnOneButtonStopWithItsName() {
        expectClash(LibraryButtons.twoDrivers().fieldCentric(Key.g1(Btn.BACK)), "G1 BACK has two library jobs");
        expectClash(LibraryButtons.twoDrivers().setPose(Key.g1(Btn.BACK)), "G1 BACK has two long (held) library jobs");
        expectClash(LibraryButtons.twoDrivers().fieldCentric(Key.g1(Trigger.LT)), "G1 LT is the slow trigger");
        expectClash(LibraryButtons.twoDrivers().tuningMenu(Key.g1(Btn.A)), "G1 A cannot open the tuning menu");
        expectClash(LibraryButtons.twoDrivers().debug(Key.g1(Btn.A), Key.g2(Btn.B)), "on two gamepads");
        expectClash(LibraryButtons.twoDrivers().switchAlliance(Key.g1(Btn.A), Key.g1(Btn.A)), "twice");
        try {
            LibraryButtons.twoDrivers().allianceInInit(Key.g1(Btn.X), Key.g1(Btn.X));
            fail("expected an error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("two different buttons"));
        }
    }

    private static void expectClash(LibraryButtons lib, String words) {
        try {
            lib.slots(all());
            fail("expected an error: " + words);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(words));
            assertTrue(e.getMessage(), e.getMessage().contains("libraryButtons"));
        }
    }

    @Test public void oneDriverMovesGamepadTwoJobsToGamepadOne() {
        LibraryButtons lib = LibraryButtons.oneDriver().pause(Key.g2(Btn.A)).moveAllToGamepad1();
        assertEquals(Key.g1(Btn.A), lib.key(Job.PAUSE));
        // And a clash that appears there is caught.
        expectClash(LibraryButtons.oneDriver().fieldCentric(Key.g2(Btn.L_STICK_BTN)).moveAllToGamepad1(),
                "G1 left stick click has two library jobs");
    }

    // ----------------------------- running them -----------------------------

    @Test public void aTapOfBackPausesOnReleaseAndAHoldResetsForwardInstead() {
        LibraryKeys k = keys(LibraryButtons.twoDrivers(), all());
        g1.back = true;
        loop(k, 0.02);
        assertTrue("nothing yet: it may be a hold", ran.isEmpty());
        g1.back = false;
        loop(k, 0.2);
        assertEquals("[PAUSE]", ran.toString());
        ran.clear();
        g1.back = true;
        loop(k, 0.02);
        loop(k, 0.5);
        loop(k, 0.6);
        assertEquals("[RESET_FORWARD]", ran.toString());
        g1.back = false;
        loop(k, 0.02);
        assertEquals("the release does not pause too", "[RESET_FORWARD]", ran.toString());
    }

    @Test public void startAndBackTogetherAreDebugAndHeldTheySwitchTheAlliance() {
        LibraryKeys k = keys(LibraryButtons.twoDrivers(), all());
        g1.back = true;
        loop(k, 0.02);
        g1.start = true;
        loop(k, 0.1);
        g1.start = false;
        g1.back = false;
        loop(k, 0.02);
        assertEquals("a quick pair: debug only (no pause, no pose)", "[DEBUG]", ran.toString());
        ran.clear();
        g1.start = true;
        g1.back = true;
        loop(k, 0.02);
        loop(k, 1.0);
        assertTrue("not yet at 1 s (and START's own hold is cancelled by the pair)", ran.isEmpty());
        loop(k, 0.6);
        assertEquals("[SWITCH_ALLIANCE]", ran.toString());
        loop(k, 2.0);
        g1.start = false;
        loop(k, 0.02);
        g1.back = false;
        loop(k, 0.02);
        assertEquals("once, and nothing on the release", "[SWITCH_ALLIANCE]", ran.toString());
    }

    @Test public void startHeldAloneSetsThePoseButNotWhilePairingAGamepad() {
        LibraryKeys k = keys(LibraryButtons.twoDrivers(), all());
        g1.start = true;
        loop(k, 0.02);
        loop(k, 1.01);
        assertEquals("[SET_POSE]", ran.toString());
        g1.start = false;
        loop(k, 0.02);
        ran.clear();
        // START + A pairs a gamepad on the Driver Station: not "set pose".
        g1.start = true;
        loop(k, 0.02);
        g1.a = true;
        loop(k, 0.5);
        g1.a = false;
        loop(k, 1.0);
        g1.start = false;
        loop(k, 0.02);
        assertTrue(ran.toString(), ran.isEmpty());
    }

    @Test public void aQuickJobAloneRunsAtThePress() {
        LibraryKeys k = keys(LibraryButtons.twoDrivers(), all());
        g1.y = true;
        loop(k, 0.02);
        assertEquals("[FIELD_CENTRIC]", ran.toString());
        loop(k, 2);
        g1.y = false;
        loop(k, 0.02);
        assertEquals("[FIELD_CENTRIC]", ran.toString());
    }

    @Test public void guideTapShowsTheMapAndAHoldOpensTheTuningMenu() {
        LibraryKeys k = keys(LibraryButtons.twoDrivers(), all());
        g1.guide = true;
        loop(k, 0.02);
        g1.guide = false;
        loop(k, 0.1);
        assertEquals("[BUTTON_MAP]", ran.toString());
        g1.guide = true;
        loop(k, 0.02);
        loop(k, 1.0);
        assertEquals("[BUTTON_MAP, TUNING_MENU]", ran.toString());
    }

    @Test public void whileTheMenuIsOpenOnlyItsButtonWorks() {
        LibraryKeys k = keys(LibraryButtons.twoDrivers(), all());
        g1.y = true;
        clock.addSec(0.02);
        k.update(g1, g2, true);
        g1.y = false;
        clock.addSec(0.02);
        k.update(g1, g2, true);
        g1.guide = true;
        clock.addSec(0.02);
        k.update(g1, g2, true);
        g1.guide = false;
        clock.addSec(0.02);
        k.update(g1, g2, true);
        assertEquals("[BUTTON_MAP]", ran.toString());
        // Y held when the menu closes waits until let go.
        g1.y = true;
        clock.addSec(0.02);
        k.update(g1, g2, true);
        loop(k, 0.02);
        assertEquals("[BUTTON_MAP]", ran.toString());
        g1.y = false;
        loop(k, 0.02);
        g1.y = true;
        loop(k, 0.02);
        assertEquals("[BUTTON_MAP, FIELD_CENTRIC]", ran.toString());
    }

    @Test public void aButtonHeldThroughStartWaitsUntilLetGo() {
        LibraryKeys k = keys(LibraryButtons.twoDrivers(), all());
        g1.y = true;
        g1.back = true;
        k.ignoreHeld(g1, g2);
        loop(k, 2);
        g1.y = false;
        g1.back = false;
        loop(k, 0.02);
        assertTrue(ran.toString(), ran.isEmpty());
    }

    @Test public void movedButtonsWorkOnGamepadTwo() {
        LibraryButtons lib = LibraryButtons.twoDrivers().pause(Key.g2(Btn.A)).switchAlliance(Key.g2(Btn.LB), Key.g2(Btn.RB));
        LibraryKeys k = keys(lib, all());
        g2.a = true;
        loop(k, 0.02);
        assertEquals("alone on its button: at the press", "[PAUSE]", ran.toString());
        g2.a = false;
        g2.left_bumper = true;
        g2.right_bumper = true;
        loop(k, 0.02);
        loop(k, 1.6);
        assertEquals("[PAUSE, SWITCH_ALLIANCE]", ran.toString());
        ran.clear();
        g2.left_bumper = false;
        g2.right_bumper = false;
        g1.back = true;
        loop(k, 0.02);
        loop(k, 1.1);
        assertTrue("G1 BACK alone now: reset forward is a tap (on the release: BACK is in the debug pair)", ran.isEmpty());
        g1.back = false;
        loop(k, 0.02);
        assertEquals("[RESET_FORWARD]", ran.toString());
    }

    @Test public void aJobTheRobotDoesNotHaveLeavesItsButtonFree() {
        Set<Job> jobs = all();
        jobs.remove(Job.SET_POSE);
        Map<Key, String> w = LibraryButtons.twoDrivers().words(jobs);
        assertEquals("with BACK: debug lines; hold both 1.5 s: switch alliance (BLUE / RED)", w.get(Key.g1(Btn.START)));
        LibraryKeys k = keys(LibraryButtons.twoDrivers(), jobs);
        g1.start = true;
        loop(k, 0.02);
        loop(k, 1.5);
        g1.start = false;
        loop(k, 0.02);
        assertTrue(ran.toString(), ran.isEmpty());
    }
}
