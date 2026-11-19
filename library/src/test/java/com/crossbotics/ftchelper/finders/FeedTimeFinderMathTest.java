package com.crossbotics.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** The Feed Time Finder's numbers: feedSec from the longest test shot, and the shortest, average and longest. */
public class FeedTimeFinderMathTest {

    @Test public void feedSecIsTheLongestShotPlusExtraRoundedUp() {
        assertEquals("0.42 + 0.1 = 0.52: up to 0.55", 0.55, FeedTimeFinderOpMode.feedSecFor(Arrays.asList(0.31, 0.42, 0.28)), 1e-9);
        assertEquals("0.25 + 0.1 stays 0.35", 0.35, FeedTimeFinderOpMode.feedSecFor(Collections.singletonList(0.25)), 1e-9);
        assertEquals("never more than feedSec's range (3 s)", 3.0, FeedTimeFinderOpMode.feedSecFor(Collections.singletonList(2.95)), 1e-9);
        assertTrue("no shots: not known", Double.isNaN(FeedTimeFinderOpMode.feedSecFor(Collections.<Double>emptyList())));
    }

    @Test public void shortestAverageLongest() {
        java.util.List<Double> t = Arrays.asList(0.30, 0.40, 0.50);
        assertEquals(0.30, FeedTimeFinderOpMode.shortest(t), 1e-9);
        assertEquals(0.40, FeedTimeFinderOpMode.average(t), 1e-9);
        assertEquals(0.50, FeedTimeFinderOpMode.longest(t), 1e-9);
        assertTrue(Double.isNaN(FeedTimeFinderOpMode.average(Collections.<Double>emptyList())));
    }
}
