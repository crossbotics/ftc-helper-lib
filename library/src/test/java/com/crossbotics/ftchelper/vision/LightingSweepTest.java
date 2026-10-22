package com.crossbotics.ftchelper.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The venue-lighting test: tries each setting, keeps the one whose pictures see the most tags. */
public class LightingSweepTest {
    // A pretend clock (nanoseconds) and the switches the sweep asked for.
    private long now = 0;
    private final List<Integer> switched = new ArrayList<>();

    private LightingSweep sweep(String... labels) {
        return new LightingSweep(Arrays.asList(labels), switched::add, () -> now)
                .settleMs(100).pictures(5).trialMs(1000).switchTimeoutMs(500).minPictures(3);
    }

    // A pretend camera: switching takes switchMs; then each picture (every 20 ms) has
    // tagsPerSetting[setting] tags. Before the switch lands, pictures are from the setting before.
    private int cameraHas = -1;
    private long switchLandsAt = 0;
    private double switchMs = 0;

    private void run(LightingSweep s, double ms, int[] tagsPerSetting) {
        for (double t = 0; t < ms; t += 20) {
            now += 20_000_000L;
            int want = s.current();
            if (want >= 0 && want != cameraHas && now >= switchLandsAt) {
                cameraHas = want;
                s.switched();
            }
            int tags = cameraHas < 0 ? LightingSweep.NO_PICTURE
                    : cameraHas == want ? tagsPerSetting[cameraHas] : LightingSweep.OTHER_SETTING;
            s.update(tags);
            // a new switch was asked for in this step: it lands switchMs from now
            if (s.current() != want) switchLandsAt = now + (long) (switchMs * 1e6);
        }
    }

    @Test public void keepsTheSettingThatSeesTheMostTags() {
        LightingSweep s = sweep("normal", "bright", "dim");
        run(s, 3000, new int[] {0, 0, 2});
        assertTrue(s.isDone());
        assertEquals(2, s.chosen());
        assertEquals("dim", s.chosenLabel());
        assertEquals("tried each once, then kept dim (already on it: no extra switch)", Arrays.asList(0, 1, 2), switched);
        assertEquals("dim (5 of 5 pictures saw tags)", s.status());
        assertEquals("normal 0/5, bright 0/5, dim 5/5", s.scores());
    }

    @Test public void aTieGoesToTheFirstSettingAndTheCameraGoesBackToIt() {
        LightingSweep s = sweep("normal", "bright");
        run(s, 3000, new int[] {1, 1});
        assertEquals(0, s.chosen());
        assertEquals("switched back to the winner", Arrays.asList(0, 1, 0), switched);
    }

    @Test public void settlingStartsWhenTheSwitchLandsNotWhenItIsAskedFor() {
        // The switch takes 200 ms, then 100 ms settle: no picture before 300 ms may count.
        switchMs = 200;
        LightingSweep s = sweep("a", "b").pictures(100).trialMs(400);
        // Asked for at 20 ms, lands at 220 ms, settled at 320 ms; the trial ends 400 ms after landing.
        run(s, 700, new int[] {1, 1});
        assertEquals("pictures from 320 to 620 ms only", 16, s.picturesOf(0));
    }

    @Test public void aSettingThatNeverLandsGetsNoScoreButTheTestGoesOn() {
        LightingSweep s = sweep("a", "b");
        s.update(LightingSweep.NO_PICTURE);   // asks for a; the camera never says it switched
        for (int i = 0; i < 30; i++) {        // 600 ms without pictures
            now += 20_000_000L;
            s.update(LightingSweep.NO_PICTURE);
        }
        assertEquals("a's switch timed out: on to b", 1, s.current());
        assertFalse("no pictures at all: not marked missing (the camera may be slow)", s.skipped(0));
    }

    @Test public void aMissingSettingIsSkippedInLaterRounds() {
        // Setting 1 ("pipeline 9", not set up): the camera keeps sending pictures from the one before.
        LightingSweep s = sweep("pipeline 0", "pipeline 9");
        for (int i = 0; i < 400; i++) {
            now += 20_000_000L;
            int want = s.current();
            if (want == 0) s.switched();
            s.update(want < 0 ? LightingSweep.NO_PICTURE : want == 0 ? 0 : LightingSweep.OTHER_SETTING);
        }
        assertTrue(s.skipped(1));
        assertEquals("[no pictures from pipeline 9 (missing?)]", s.problems().toString());
        assertEquals("pipeline 0 0/5, pipeline 9 skipped", s.scores());
        assertFalse("still trying pipeline 0 (no tags yet)", s.isDone());
        assertEquals(0, s.current());
    }

    @Test public void aRefusedSettingGetsNoScoreAndIsSkipped() {
        LightingSweep s = sweep("auto", "6 ms");
        run(s, 20, new int[] {0, 0});
        s.switched();
        s.switchFailed();                     // the camera refused "auto"
        assertTrue(s.skipped(0));
        assertEquals(1, s.current());
        assertEquals("[the camera refused auto]", s.problems().toString());
    }

    @Test public void atStartItStopsAndKeepsTheBestSoFar() {
        LightingSweep s = sweep("normal", "bright", "dim").minPictures(6);   // 5 pictures: never "good"
        run(s, 2500, new int[] {0, 2, 0});
        assertFalse("not good enough yet: still trying in INIT", s.isDone());
        assertTrue(s.status(), s.status().contains("no tags yet: face a tag"));
        s.matchStarted();
        assertTrue("stopped at START", s.isDone());
        assertEquals("bright saw tags: kept, not the first setting", "bright", s.chosenLabel());
        assertFalse(s.gaveUp());
        assertEquals("the camera is on the kept setting", 1, (int) switched.get(switched.size() - 1));
    }

    @Test public void atStartWithNoTagsAtAllItKeepsTheFirstSetting() {
        LightingSweep s = sweep("normal", "dim");
        run(s, 3000, new int[] {0, 0});
        s.matchStarted();
        assertTrue(s.gaveUp());
        assertEquals(0, s.chosen());
        assertTrue(s.status(), s.status().startsWith("NO TAGS SEEN: kept normal (have a tag in view at INIT"));
    }

    @Test public void withTimeAfterStartItKeepsTryingThenKeepsTheBestSoFar() {
        LightingSweep s = sweep("normal", "dim").giveUpAfterStartMs(500);
        run(s, 3000, new int[] {0, 0});
        s.matchStarted();
        assertFalse(s.isDone());
        assertTrue(s.matchHasStarted());
        run(s, 600, new int[] {0, 0});
        assertTrue(s.isDone());
        assertTrue(s.gaveUp());
    }

    @Test public void tooFewPicturesWithTagsIsNotGoodEnough() {
        // Only 1 or 2 of 5 pictures see a tag (minPictures 3): not good enough, try again.
        LightingSweep s = sweep("a");
        int count = 0;
        for (int i = 0; i < 100; i++) {
            now += 20_000_000L;
            if (s.current() == 0) s.switched();
            s.update(s.current() < 0 ? LightingSweep.NO_PICTURE : (count++ % 3 == 0 ? 1 : 0));
        }
        assertFalse("never 3 of 5: keeps trying", s.isDone());
    }

    @Test public void restartInTheMatchGetsItsOwnTimeAndANewSessionIsInitAgain() {
        LightingSweep s = sweep("a", "b").giveUpAfterStartMs(500);
        run(s, 3000, new int[] {3, 0});
        assertEquals(0, s.chosen());
        s.matchStarted();
        now += 10_000_000_000L;               // 10 s into the match
        s.restart();
        assertFalse(s.isDone());
        run(s, 200, new int[] {0, 0});
        assertFalse("the retry has 500 ms from the restart, not from START", s.isDone());
        run(s, 400, new int[] {0, 0});
        assertTrue(s.isDone());
        s.newSession();
        assertFalse("a new camera session is INIT again", s.matchHasStarted());
        assertFalse(s.isDone());
    }

    @Test public void waitingForTheCameraStillEndsAtStart() {
        LightingSweep s = sweep("a", "b").giveUpAfterStartMs(500);
        s.matchStarted();
        now += 600_000_000L;
        s.waitingForCamera();
        assertTrue("a camera that never streamed: the test still ends", s.isDone());
        assertEquals(0, s.chosen());
    }

    @Test public void eachCameraGetsItsOwnKindOfLightingTest() {
        try {
            com.crossbotics.ftchelper.robot.Camera.limelight("limelight").autoExposure();
            org.junit.Assert.fail("a Limelight has pipelines, not exposures");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("autoPipelines"));
        }
        try {
            com.crossbotics.ftchelper.robot.Camera.webcam("Webcam 1").autoPipelines(0, 1);
            org.junit.Assert.fail("a webcam has exposures, not pipelines");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("autoExposure"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void needsAtLeastOneSetting() {
        new LightingSweep(new ArrayList<String>(), i -> { }, () -> 0L);
    }
}
