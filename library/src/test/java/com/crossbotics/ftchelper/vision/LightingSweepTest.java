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
                .settleMs(100).pictures(5).trialMs(1000).minPictures(3).giveUpAfterStartMs(500);
    }

    // Runs the sweep for ms milliseconds, a picture every 20 ms; seesTags says, per setting tried, how
    // many tags each picture has.
    private void run(LightingSweep s, double ms, int[] tagsPerSetting) {
        for (double t = 0; t < ms; t += 20) {
            now += 20_000_000L;
            int tags = s.current() < 0 ? LightingSweep.NO_PICTURE : tagsPerSetting[s.current()];
            s.update(tags);
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

    @Test public void picturesDuringTheSettleTimeDoNotCount() {
        // 100 ms settle at 20 ms a picture: the first 4 pictures after a switch are skipped.
        LightingSweep s = sweep("only").pictures(100).trialMs(300);
        run(s, 20, new int[] {1});            // first step: switches, counts nothing
        run(s, 300, new int[] {1});
        assertEquals("pictures from 100 ms to 300 ms after the switch", 11, s.picturesOf(0));
    }

    @Test public void noPictureMeansNothingCountsButTheTimeLimitStillEnds() {
        LightingSweep s = sweep("a", "b");
        s.update(LightingSweep.NO_PICTURE);   // switch to a
        for (int i = 0; i < 60; i++) {        // 1.2 s without pictures (e.g. the pipeline switch is not done)
            now += 20_000_000L;
            s.update(LightingSweep.NO_PICTURE);
        }
        assertEquals("a's time ran out: on to b", 1, s.current());
        assertEquals(0, s.picturesOf(0));
    }

    @Test public void withNoTagsItKeepsTryingThenGivesUpAfterStartOnTheFirstSetting() {
        LightingSweep s = sweep("normal", "dim");
        run(s, 10_000, new int[] {0, 0});
        assertFalse("still trying in INIT (the robot may not face a tag yet)", s.isDone());
        assertTrue(s.status(), s.status().contains("no tags yet"));
        s.matchStarted();
        run(s, 600, new int[] {0, 0});
        assertTrue(s.isDone());
        assertTrue(s.gaveUp());
        assertEquals(0, s.chosen());
        assertEquals("the camera is back on the first setting", 0, (int) switched.get(switched.size() - 1));
        assertTrue(s.status(), s.status().startsWith("NO TAGS SEEN: kept normal"));
    }

    @Test public void tooFewPicturesWithTagsIsNotGoodEnough() {
        // Only 2 of 5 pictures see a tag (minPictures 3): not good enough, try again.
        LightingSweep s = sweep("a").pictures(5);
        int[] count = {0};
        for (int i = 0; i < 40; i++) {
            now += 20_000_000L;
            int tags = s.current() < 0 ? LightingSweep.NO_PICTURE : (count[0]++ % 3 == 0 ? 1 : 0);
            s.update(tags);
        }
        assertFalse("never 3 of 5: keeps trying", s.isDone());
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

    @Test public void afterStartAFoundSettingIsKept() {
        LightingSweep s = sweep("a", "b");
        run(s, 3000, new int[] {0, 3});
        assertEquals(1, s.chosen());
        s.matchStarted();
        run(s, 2000, new int[] {0, 0});
        assertEquals("found settings are never dropped after START", 1, s.chosen());
        assertFalse(s.gaveUp());
    }

    @Test public void restartTriesEverythingAgain() {
        LightingSweep s = sweep("a", "b");
        run(s, 3000, new int[] {3, 0});
        assertEquals(0, s.chosen());
        s.restart();
        assertFalse(s.isDone());
        run(s, 3000, new int[] {0, 3});
        assertEquals(1, s.chosen());
    }

    @Test(expected = IllegalArgumentException.class)
    public void needsAtLeastOneSetting() {
        new LightingSweep(new ArrayList<String>(), i -> { }, () -> 0L);
    }
}
