package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.util.FieldPose;
import org.junit.After;
import org.junit.Test;

/**
 * An autonomous whose start spot is only in words: the page remembers where the team started it, once per
 * autonomous, and uses the same spot for the other alliance turned half a turn (the field is turned, not
 * mirrored, between the alliances).
 */
public class SimStartSpotMemoryTest {

    @After public void clean() {
        SimEngine.spotsFile().delete();
    }

    @Test public void aSpotTurnsHalfATurnForTheOtherAllianceAndBack() {
        FieldPose red = new FieldPose(-12, -60, 90);
        FieldPose blue = SimEngine.forAlliance(red, Alliance.BLUE);
        assertEquals(12, blue.x, 1e-9);
        assertEquals(60, blue.y, 1e-9);
        assertEquals(270, blue.headingDeg, 1e-9);
        FieldPose back = SimEngine.forAlliance(blue, Alliance.BLUE);
        assertEquals(-12, back.x, 1e-9);
        assertEquals(-60, back.y, 1e-9);
        assertEquals(450, back.headingDeg, 1e-9);   // the same way as 90
        FieldPose same = SimEngine.forAlliance(red, Alliance.RED);
        assertEquals(red.x, same.x, 1e-9);
    }

    @Test public void aSavedSpotComesBackForTheSameAutonomousOnly() {
        SimEngine.spotsFile().delete();
        assertNull(SimEngine.loadSpot("Score and Park"));
        SimEngine.saveSpot("Score and Park", new FieldPose(-36.5, -62.25, 0));
        FieldPose got = SimEngine.loadSpot("Score and Park");
        assertEquals(-36.5, got.x, 1e-9);
        assertEquals(-62.25, got.y, 1e-9);
        assertEquals(0, got.headingDeg, 1e-9);
        assertNull("another autonomous has its own", SimEngine.loadSpot("Score Far"));
    }
}
