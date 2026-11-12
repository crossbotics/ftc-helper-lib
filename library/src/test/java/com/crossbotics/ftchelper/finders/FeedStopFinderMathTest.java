package com.crossbotics.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.robot.Parts;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** The Feed Stop Finder's two times: the move time from the catalog, and the backup time from the test shots. */
public class FeedStopFinderMathTest {

    @Test public void theMoveTimeIsTheCatalogsWithExtraRoundedUp() {
        Parts.ServoPart speed = Parts.servoPart("gobilda-2000-speed");
        double raw = speed.moveSec(0.15, 0.55, false);
        double got = FeedStopFinderOpMode.moveSecFor(speed, 0.15, 0.55);
        assertTrue("at least the catalog time + 25%", got >= raw * 1.25 - 1e-9);
        assertTrue("less than 0.05 more than that", got < raw * 1.25 + 0.05);
        assertEquals("a step of 0.05", 0, Math.abs(got / 0.05 - Math.round(got / 0.05)), 1e-9);
        assertEquals("a tiny move: 0.05 at least", 0.05, FeedStopFinderOpMode.moveSecFor(speed, 0.5, 0.501), 1e-9);
        assertTrue("no part: not known", Double.isNaN(FeedStopFinderOpMode.moveSecFor(null, 0.15, 0.55)));
        assertTrue("no release yet: not known", Double.isNaN(FeedStopFinderOpMode.moveSecFor(speed, 0.15, Double.NaN)));
    }

    @Test public void theBackupTimeIsTheLongestShotPlusExtra() {
        assertEquals(0.55, FeedStopFinderOpMode.openSecFor(Arrays.asList(0.31, 0.28, 0.35)), 1e-9);
        assertEquals("0.25 + 0.2 stays 0.45", 0.45, FeedStopFinderOpMode.openSecFor(Collections.singletonList(0.25)), 1e-9);
        assertTrue("no shots: not known", Double.isNaN(FeedStopFinderOpMode.openSecFor(Collections.<Double>emptyList())));
    }
}
