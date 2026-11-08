package com.crossbotics.ftchelper.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The glitch filter for a camera's distance: the middle of the last pictures, and the jump check. */
public class DistanceFilterTest {
    private long now = 0;

    // One new picture (33 ms after the last: 30 pictures a second) at this distance; the filter's answer.
    private double picture(DistanceFilter f, double distanceIn) {
        now += 33_000_000L;
        return f.add(new TargetObservation(1, "GOAL", 0, distanceIn, now), now);
    }

    // The filter warmed up at 60 in.
    private DistanceFilter at60() {
        DistanceFilter f = new DistanceFilter();
        for (int i = 0; i < 5; i++) picture(f, 60 + (i % 2));   // 60, 61, 60, 61, 60: a camera never repeats exactly
        return f;
    }

    @Test public void itNeedsThreePicturesBeforeItGivesADistance() {
        DistanceFilter f = new DistanceFilter();
        assertTrue(Double.isNaN(picture(f, 60)));
        assertTrue(Double.isNaN(picture(f, 61)));
        assertEquals(60, picture(f, 59), 1e-9);              // the middle of 59, 60, 61
    }

    @Test public void oneOrTwoBadPicturesNeverChangeTheDistance() {
        DistanceFilter f = at60();
        assertEquals(60, picture(f, 140), 1.01);             // one glitch: still about 60
        assertEquals(60, picture(f, 20), 1.01);             // a second one the other way: the middle stays
        assertEquals(60, picture(f, 60), 1.01);
        assertEquals(60, picture(f, 61), 1.01);
    }

    @Test public void theSamePictureAgainCountsOnce() {
        DistanceFilter f = at60();
        now += 33_000_000L;
        TargetObservation bad = new TargetObservation(1, "GOAL", 0, 140, now);
        for (int i = 0; i < 10; i++) f.add(bad, now + i);    // the loop is faster than the camera
        assertEquals("one picture, not ten", 60, f.distance(), 1.01);
    }

    @Test public void aRealJumpIsUsedOnceThreePicturesAgree() {
        DistanceFilter f = at60();
        // The robot drove fast: the camera now says about 90 in (each picture a little different).
        picture(f, 90);
        picture(f, 91);
        double d = picture(f, 89);                           // the middle of 5 moves to 89 now
        assertEquals("the jump waits", 60, d, 1.01);
        assertTrue(f.jumpWaiting());
        picture(f, 90);
        d = picture(f, 92);
        assertEquals("3 pictures within 4 in of each other: used", 90, d, 1.01);
        assertFalse(f.jumpWaiting());
    }

    @Test public void aJumpThatDoesNotSettleIsNeverUsed() {
        DistanceFilter f = at60();
        double[] wild = {100, 130, 95, 140, 100, 135, 98, 140};   // a reflection jumping about
        for (double w : wild) assertEquals(60, picture(f, w), 41);
        assertTrue("never in use: it never agreed", f.distance() < 100);
    }

    @Test public void smallChangesAreUsedAtOnce() {
        DistanceFilter f = at60();
        for (int i = 0; i < 5; i++) picture(f, 66);          // 6 in: under the 12 in jump
        assertEquals(66, f.distance(), 1e-9);
    }

    @Test public void afterAGapTheNewDistanceNeedsNoJumpCheck() {
        DistanceFilter f = at60();
        now += 800_000_000L;                                 // out of view for 0.8 s (a HIVE tip)
        f.add(null, now);
        assertTrue("starts over", Double.isNaN(f.distance()));
        picture(f, 100);
        picture(f, 101);
        assertEquals("3 pictures: used, no jump check", 100, picture(f, 99), 1e-9);
    }

    @Test public void offWithOnePictureAndNoJumpCheck() {
        DistanceFilter f = new DistanceFilter(1, 0, 4);
        assertEquals(60, picture(f, 60), 1e-9);
        assertEquals(140, picture(f, 140), 1e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void medianOfMustBeOneToFifteen() {
        new DistanceFilter(0, 12, 4);
    }
}
