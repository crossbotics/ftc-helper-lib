package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Odometry measured from the robot's edges: the library works out the center numbers. */
public class OdometryEdgesTest {

    @Test public void pinpointPodsFromEdgesGiveTheCenterOffsets() {
        // 430 mm long, 400 mm wide: the center is 200 mm from the left edge and 215 mm from the front.
        Odometry o = Odometry.pinpoint("pinpoint").robotSizeMm(430, 400).podsFromEdgesMm(284, 383);
        o.fromEdges();
        assertEquals(-84, o.xOffsetMm, 1e-9);    // 84 mm RIGHT of the center
        assertEquals(-168, o.yOffsetMm, 1e-9);   // 168 mm BEHIND it
        assertTrue(o.podsFromEdges());
    }

    @Test public void theOrderOfTheCallsDoesNotMatter() {
        Odometry o = Odometry.pinpoint("pinpoint").podsFromEdgesMm(100, 100).robotSizeMm(430, 400);
        o.fromEdges();
        assertEquals(100, o.xOffsetMm, 1e-9);
        assertEquals(115, o.yOffsetMm, 1e-9);
    }

    @Test public void theLastOneWrittenWins() {
        Odometry o = Odometry.pinpoint("pinpoint").robotSizeMm(430, 400).podsFromEdgesMm(284, 383).offsetsMm(-84.2, -167.5);
        o.fromEdges();
        assertEquals(-84.2, o.xOffsetMm, 1e-9);   // the Odometry Tuner's exact numbers
        assertFalse(o.podsFromEdges());
    }

    @Test public void otosMountFromEdgesGivesMountIn() {
        Odometry o = Odometry.otos("otos").robotSizeMm(430, 400).mountFromEdgesMm(291, 200, 90);
        o.fromEdges();
        assertEquals(-76 / 25.4, o.forwardIn, 1e-9);
        assertEquals(0, o.leftIn, 1e-9);
        assertEquals(90, o.mountHeadingDeg, 1e-9);
    }

    @Test public void theSimulatorGetsTheCenterNumbers() {
        List<String> out = new ArrayList<>();
        Odometry.pinpoint("pinpoint").robotSizeMm(430, 400).podsFromEdgesMm(284, 383).simLines(out);
        assertTrue(out.toString(), out.contains("odometry.offsetsMm = -84, -168"));
    }

    @Test public void edgesWithoutTheSizeStopWithHelp() {
        try {
            Odometry.pinpoint("pinpoint").podsFromEdgesMm(284, 383).fromEdges();
            fail("no robotSizeMm");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("robotSizeMm"));
        }
    }

    @Test public void aDistanceOutsideTheRobotStops() {
        try {
            Odometry.pinpoint("pinpoint").robotSizeMm(430, 400).podsFromEdgesMm(450, 100).fromEdges();
            fail("450 mm from the left edge of a 400 mm robot");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("outside the robot"));
        }
    }

    @Test public void edgesAreOnlyForTheirKind() {
        try {
            Odometry.otos("otos").podsFromEdgesMm(1, 1);
            fail("pods on an OTOS");
        } catch (IllegalStateException expected) {
            // ok
        }
    }
}
