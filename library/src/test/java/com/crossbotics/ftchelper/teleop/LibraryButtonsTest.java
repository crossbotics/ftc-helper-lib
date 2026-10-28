package com.crossbotics.ftchelper.teleop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test public void theDefaultsAreTheOldButtonsPlusPose() {
        Map<Key, String> w = LibraryButtons.twoDrivers().words(all());
        assertEquals("pause / go on; hold 1 s: forward = away from you now; after START: debug lines", w.get(Key.g1(Btn.BACK)));
        assertEquals("hold 1 s: set pose here; then BACK: debug lines", w.get(Key.g1(Btn.START)));
        assertNull("switching the alliance in TeleOp is off until the team turns it on",
                LibraryButtons.twoDrivers().key(Job.SWITCH_ALLIANCE));
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
        expectClash(LibraryButtons.twoDrivers().fieldCentric(Key.g1(Btn.BACK)), "Two library features are on G1 BACK");
        expectClash(LibraryButtons.twoDrivers().setPose(Key.g1(Btn.BACK)), "Two hold-down library features are on G1 BACK");
        expectClash(LibraryButtons.twoDrivers().fieldCentric(Key.g1(Trigger.LT)), "G1 LT is the slow trigger");
        expectClash(LibraryButtons.twoDrivers().tuningMenu(Key.g1(Btn.A)), "G1 A cannot open the tuning menu");
        // Each says the line that fixes it.
        expectClash(LibraryButtons.twoDrivers().fieldCentric(Key.g1(Btn.BACK)), "turn it off: lib.fieldCentric(null)");
        expectClash(LibraryButtons.twoDrivers().tuningMenu(Key.g1(Btn.A)), "lib.tuningMenu(g1.guide)");
        expectClash(LibraryButtons.twoDrivers().debug(Key.g1(Btn.A), Key.g2(Btn.B)), "on two gamepads");
        expectClash(LibraryButtons.twoDrivers().switchAlliance(Key.g1(Btn.A), Key.g1(Btn.A)), "it needs two different buttons");
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
                "Two library features are on G1 left stick click");
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

    @Test public void theAllianceSwitchIsOneLineToTurnOn() {
        LibraryButtons lib = LibraryButtons.twoDrivers().switchAlliance(Key.g1(Btn.BACK), Key.g1(Btn.START));
        assertEquals("pause / go on; hold 1 s: forward = away from you now; after START: debug lines; "
                + "hold both 1.5 s: switch alliance (BLUE / RED)", lib.words(all()).get(Key.g1(Btn.BACK)));
        assertEquals(Key.g1(Btn.START), lib.with(Job.SWITCH_ALLIANCE));
    }

    // ----------------------------- pause at the press -----------------------------

    // A pretend robot that pauses: PAUSE flips it, like Controls.java's robot.togglePause().
    private boolean paused = false;

    private LibraryKeys pausingKeys(LibraryButtons lib) {
        Map<Job, Runnable> map = LibraryKeys.jobs();
        for (final Job j : Job.values()) map.put(j, () -> ran.add(j.name()));
        map.put(Job.PAUSE, () -> {
            paused = !paused;
            ran.add(paused ? "PAUSE" : "GO_ON");
        });
        return new LibraryKeys(lib.slots(all()), map, clock::get).pauseAtPress(() -> paused);
    }

    // While paused, nothing that changes how the robot drives runs: G1 Y (field-centric) waits.
    @Test public void whilePausedFieldCentricDoesNotToggle() {
        LibraryKeys k = pausingKeys(LibraryButtons.twoDrivers());
        g1.back = true;
        loop(k, 0.02);
        g1.back = false;
        loop(k, 0.1);
        assertTrue(paused);
        ran.clear();
        g1.y = true;
        loop(k, 0.02);
        g1.y = false;
        loop(k, 0.1);
        assertEquals("[]", ran.toString());
        // Going on: Y works again.
        g1.back = true;
        loop(k, 0.02);
        g1.back = false;
        loop(k, 0.1);
        ran.clear();
        g1.y = true;
        loop(k, 0.02);
        g1.y = false;
        loop(k, 0.1);
        assertEquals("[FIELD_CENTRIC]", ran.toString());
    }

    @Test public void aPressOfBackPausesAtOnceAndAHoldAlsoResetsForward() {
        LibraryKeys k = pausingKeys(LibraryButtons.twoDrivers());
        g1.back = true;
        loop(k, 0.02);
        assertTrue("paused at the press, not at the release", paused);
        g1.back = false;
        loop(k, 0.1);
        assertTrue("the release does not go on again", paused);
        assertEquals("[PAUSE]", ran.toString());
        // Paused: a tap goes on (at the release, so holding never starts the robot).
        g1.back = true;
        loop(k, 0.02);
        assertTrue(paused);
        g1.back = false;
        loop(k, 0.1);
        assertFalse(paused);
        assertEquals("[PAUSE, GO_ON]", ran.toString());
        ran.clear();
        // Held 1 s: paused at once, then forward is reset, and it stays paused.
        g1.back = true;
        loop(k, 0.02);
        assertTrue(paused);
        loop(k, 0.5);
        loop(k, 0.6);
        g1.back = false;
        loop(k, 0.02);
        assertTrue("stays paused until the next tap", paused);
        assertEquals("[PAUSE, RESET_FORWARD]", ran.toString());
        ran.clear();
        // Paused and held 1 s: forward is reset, still paused.
        g1.back = true;
        loop(k, 0.02);
        loop(k, 1.1);
        g1.back = false;
        loop(k, 0.02);
        assertTrue(paused);
        assertEquals("[RESET_FORWARD]", ran.toString());
    }

    @Test public void backThenStartStaysAPauseAndIsNeverThePair() {
        LibraryKeys k = pausingKeys(LibraryButtons.twoDrivers().switchAlliance(Key.g1(Btn.BACK), Key.g1(Btn.START)));
        g1.back = true;
        loop(k, 0.02);
        assertTrue("BACK first: paused at once", paused);
        g1.start = true;
        loop(k, 0.05);
        assertTrue("START joined later: still paused (nothing is taken back)", paused);
        g1.back = false;
        g1.start = false;
        loop(k, 0.02);
        assertEquals("a plain pause: no debug lines", "[PAUSE]", ran.toString());
        assertTrue(paused);
        ran.clear();
        // Go on again (a tap), then BACK first and START held with it 2 s: no alliance switch, no pose;
        // BACK's own hold still resets "forward".
        g1.back = true;
        loop(k, 0.02);
        g1.back = false;
        loop(k, 0.02);
        assertFalse(paused);
        ran.clear();
        g1.back = true;
        loop(k, 0.02);
        g1.start = true;
        loop(k, 0.1);
        loop(k, 1.0);
        loop(k, 1.0);
        g1.start = false;
        loop(k, 0.02);
        g1.back = false;
        loop(k, 0.02);
        assertEquals("[PAUSE, RESET_FORWARD]", ran.toString());
        assertTrue("stays paused", paused);
    }

    @Test public void startThenBackIsThePairAndNeverPauses() {
        LibraryKeys k = pausingKeys(LibraryButtons.twoDrivers().switchAlliance(Key.g1(Btn.BACK), Key.g1(Btn.START)));
        g1.start = true;
        loop(k, 0.02);
        g1.back = true;
        loop(k, 0.05);
        assertFalse("START first: BACK joining is the pair, not a pause", paused);
        g1.back = false;
        g1.start = false;
        loop(k, 0.02);
        assertEquals("[DEBUG]", ran.toString());
        ran.clear();
        // Held 1.5 s: the alliance switch, still no pause.
        g1.start = true;
        loop(k, 0.02);
        g1.back = true;
        loop(k, 0.02);
        loop(k, 1.0);
        loop(k, 0.6);
        assertEquals("[SWITCH_ALLIANCE]", ran.toString());
        g1.back = false;
        g1.start = false;
        loop(k, 0.02);
        assertEquals("[SWITCH_ALLIANCE]", ran.toString());
        assertFalse(paused);
        ran.clear();
        // Both in the same loop: the pair too (BACK had not paused yet).
        g1.back = true;
        g1.start = true;
        loop(k, 0.02);
        g1.back = false;
        g1.start = false;
        loop(k, 0.02);
        assertEquals("[DEBUG]", ran.toString());
        assertFalse(paused);
        ran.clear();
        // START let go, BACK still held, START again: BACK was first this time, so no pair.
        g1.start = true;
        loop(k, 0.02);
        g1.back = true;
        loop(k, 0.02);
        g1.start = false;
        loop(k, 0.02);
        g1.start = true;
        loop(k, 0.02);
        g1.start = false;
        g1.back = false;
        loop(k, 0.02);
        assertEquals("one pair (the first START), no pause", "[DEBUG]", ran.toString());
        assertFalse(paused);
    }

    @Test public void aTapOfBackAloneStillPauses() {
        LibraryKeys k = pausingKeys(LibraryButtons.twoDrivers());
        g1.back = true;
        loop(k, 0.02);
        g1.back = false;
        loop(k, 0.02);
        assertTrue(paused);
        assertEquals("[PAUSE]", ran.toString());
    }

    @Test public void startAndBackTogetherAreDebugAndHeldTheySwitchTheAlliance() {
        LibraryKeys k = keys(LibraryButtons.twoDrivers().switchAlliance(Key.g1(Btn.BACK), Key.g1(Btn.START)), all());
        g1.start = true;
        loop(k, 0.02);
        g1.back = true;
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

    @Test public void aFreedPauseOrFieldCentricButtonIsSaidAtInit() {
        assertNull(EasyTeleOp.noPauseWarning(LibraryButtons.twoDrivers(), all()));
        String w = EasyTeleOp.noPauseWarning(LibraryButtons.twoDrivers().pause(null), all());
        assertTrue(w, w.startsWith("No pause button"));
        assertTrue(w, w.contains("libraryButtons"));
        Set<Job> noPauseJob = all();
        noPauseJob.remove(Job.PAUSE);
        assertNull("a TeleOp that cannot pause says nothing", EasyTeleOp.noPauseWarning(LibraryButtons.twoDrivers().pause(null), noPauseJob));
        assertNull(EasyTeleOp.noFieldCentricNote(LibraryButtons.twoDrivers(), false));
        assertEquals("No field-centric button (libraryButtons): driving stays robot-centric",
                EasyTeleOp.noFieldCentricNote(LibraryButtons.twoDrivers().fieldCentric(null), false));
    }

    @Test public void aJobTheRobotDoesNotHaveLeavesItsButtonFree() {
        Set<Job> jobs = all();
        jobs.remove(Job.SET_POSE);
        Map<Key, String> w = LibraryButtons.twoDrivers().words(jobs);
        assertEquals("then BACK: debug lines", w.get(Key.g1(Btn.START)));
        LibraryKeys k = keys(LibraryButtons.twoDrivers(), jobs);
        g1.start = true;
        loop(k, 0.02);
        loop(k, 1.5);
        g1.start = false;
        loop(k, 0.02);
        assertTrue(ran.toString(), ran.isEmpty());
    }
}
