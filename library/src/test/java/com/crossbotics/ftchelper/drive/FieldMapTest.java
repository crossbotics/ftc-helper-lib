package com.crossbotics.ftchelper.drive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.Test;

/** The route planner: straight when it can, around the boxes when it must, and never through one. */
public class FieldMapTest {
    // Two boxes with a gap between them, like two frames of a field element.
    private static FieldMap twoBoxes() {
        return FieldMap.margin(13)
                .keepOut("near", -19.5, -24.8, 19.5, -15.4)
                .keepOut("far", -19.5, 15.4, 19.5, 24.8);
    }

    private static final double[][] BOXES = {{-19.5, -24.8, 19.5, -15.4}, {-19.5, 15.4, 19.5, 24.8}};

    // How close the line a-b comes to the box, sampled every 0.1 in.
    private static double nearest(double[] a, double[] b, double[] box) {
        double best = Double.POSITIVE_INFINITY;
        int steps = (int) Math.max(1, Math.hypot(b[0] - a[0], b[1] - a[1]) / 0.1);
        for (int i = 0; i <= steps; i++) {
            double x = a[0] + (b[0] - a[0]) * i / steps, y = a[1] + (b[1] - a[1]) * i / steps;
            double dx = Math.max(Math.max(box[0] - x, x - box[2]), 0), dy = Math.max(Math.max(box[1] - y, y - box[3]), 0);
            // The margin is added on each side, so its corners are square: use the larger of the two.
            best = Math.min(best, Math.max(dx, dy));
        }
        return best;
    }

    @Test public void aClearLineIsOneStraightLine() {
        List<double[]> r = twoBoxes().route(-56, -61, -56, -36);
        assertEquals(1, r.size());
        assertEquals(-56, r.get(0)[0], 0);
        assertEquals(-36, r.get(0)[1], 0);
    }

    @Test public void aBlockedLineGoesAroundTheCornerAndEndsAtTheSpot() {
        List<double[]> r = twoBoxes().route(0, -61, 0, -1);
        assertTrue("goes around: " + r.size(), r.size() >= 3);
        assertEquals(0, r.get(r.size() - 1)[0], 0);
        assertEquals(-1, r.get(r.size() - 1)[1], 0);
        double[] from = {0, -61};
        for (double[] p : r) {
            assertTrue(nearest(from, p, BOXES[0]) >= 13 - 1e-6);
            from = p;
        }
    }

    @Test public void itPicksTheShorterSide() {
        // The start is far to the right of the box's middle: going around the right is shorter.
        List<double[]> r = FieldMap.margin(5).keepOut("box", -30, -5, 10, 5).route(5, -20, 5, 20);
        assertTrue("right side: " + r.get(0)[0], r.get(0)[0] > 10);
    }

    @Test public void noRouteEverGoesThroughAMarginFromAnywhere() {
        FieldMap map = twoBoxes();
        Random dice = new Random(8938);
        int routed = 0;
        for (int i = 0; i < 2000; i++) {
            double[] from = {dice.nextDouble() * 120 - 60, dice.nextDouble() * 120 - 60};
            double[] to = {dice.nextDouble() * 120 - 60, dice.nextDouble() * 120 - 60};
            if (!map.whyBadSpot(to[0], to[1]).isEmpty()) continue;
            List<double[]> r = map.route(from[0], from[1], to[0], to[1]);
            assertFalse("every spot here can be reached", r.isEmpty());
            routed++;
            double[] a = from;
            boolean first = true;
            for (double[] p : r) {
                for (double[] box : BOXES) {
                    // The first line of a start inside a margin only leads out of it.
                    boolean startsInside = first && nearest(from, from, box) < 13;
                    // The last line to a spot inside a margin only leads into it.
                    boolean endsInside = p == r.get(r.size() - 1) && nearest(to, to, box) < 13;
                    if (!startsInside && !endsInside) assertTrue("the line to " + p[0] + ", " + p[1], nearest(a, p, box) >= 13 - 1e-6);
                }
                a = p;
                first = false;
            }
            assertEquals(to[0], a[0], 0);
            assertEquals(to[1], a[1], 0);
        }
        assertTrue(routed > 1000);
    }

    @Test public void aStartInsideAMarginLeavesByTheNearestSide() {
        // 3 in below the near box (margin 13): the nearest way out is 10 in straight down, and 2.5 more.
        List<double[]> r = twoBoxes().route(0, -27.8, -56, -36);
        assertEquals(-27.8 - 12.5, r.get(0)[1], 1e-6);
        assertEquals(0, r.get(0)[0], 1e-6);
    }

    @Test public void aBadSpotHasNoRouteAndSaysWhy() {
        FieldMap map = twoBoxes();
        assertTrue(map.route(0, -61, 0, -20).isEmpty());
        assertTrue(map.whyBadSpot(0, -20), map.whyBadSpot(0, -20).contains("\"near\""));
        assertTrue(map.whyBadSpot(0, 80), map.whyBadSpot(0, 80).contains("off the field"));
        assertEquals("", map.whyBadSpot(0, 0));
    }

    @Test public void aSpotInsideAMarginIsReachedByAStraightLastLine() {
        // Right next to the near frame, on the far side from the robot: around, out, then straight in.
        FieldMap map = twoBoxes();
        assertEquals("", map.whyBadSpot(0, -27));
        List<double[]> r = map.route(-40, -50, 0, -27);
        assertEquals(2, r.size());
        assertEquals(0, r.get(0)[0], 1e-6);
        assertEquals(-24.8 - 13 - 2.5, r.get(0)[1], 1e-6);
        assertEquals(-27, r.get(1)[1], 0);
    }

    @Test public void aSpotInAMarginThatIsBoxedInHasNoRouteAndSaysWhy() {
        // Inside a ring of four boxes, too small for a robot: every way out meets a box or a margin.
        FieldMap map = FieldMap.margin(6).keepOut("top", -6, 5, 6, 6).keepOut("bottom", -6, -6, 6, -5)
                .keepOut("left", -6, -6, -5, 6).keepOut("right", 5, -6, 6, 6);
        assertTrue(map.whyBadSpot(0, 0), map.whyBadSpot(0, 0).contains("cannot drive in"));
        assertTrue(map.route(-40, 0, 0, 0).isEmpty());
    }

    @Test public void aRobotAlreadyAtASpotInAMarginDoesNotDriveOutAndBack() {
        List<double[]> r = twoBoxes().route(1, -27, 0, -27);
        assertEquals(1, r.size());
    }

    @Test public void aCutCornerStaysOutsideTheMargin() {
        // The robot leaves a corner up to 2 in early. Walk each line from the worst such place.
        FieldMap map = FieldMap.margin(0).keepOut("box", 0, 0, 20, 20);
        double[] box = {0, 0, 20, 20};
        List<double[]> r = map.route(-10, 10, 30, 10);
        assertTrue(r.size() >= 3);
        double[] a = {-10, 10};
        for (int i = 0; i < r.size() - 1; i++) {
            double[] c = r.get(i), next = r.get(i + 1);
            double len = Math.hypot(c[0] - a[0], c[1] - a[1]);
            double[] early = {c[0] + (a[0] - c[0]) * 2 / len, c[1] + (a[1] - c[1]) * 2 / len};
            assertTrue("from the early place to the next", nearest(early, next, box) > 0);
            a = c;
        }
    }

    @Test public void aWayOutDoesNotCrossAnotherBox() {
        // Left: B's margin. Right: through A. Down: nearest of what is left, but C is in the way. So up.
        FieldMap map = FieldMap.margin(1).keepOut("A", 0, 0, 20, 20).keepOut("B", -3, 0, -2, 20).keepOut("C", -1, 3, 0, 4);
        List<double[]> r = map.route(-0.5, 10, 0, -10);
        assertEquals(-0.5, r.get(0)[0], 1e-6);
        assertTrue("up: " + r.get(0)[1], r.get(0)[1] > 20);
    }

    @Test public void aWayOutDoesNotGoTowardAWallButMayGoAlongIt() {
        // Near the wall, in the margin of a box along it: down is the wall, up is the box. So sideways.
        List<double[]> r = FieldMap.margin(10).keepOut("box", -20, -66, 20, -60).route(0, -70, 40, -50);
        assertEquals(-70, r.get(0)[1], 1e-6);
        assertEquals(32.5, Math.abs(r.get(0)[0]), 1e-6);
    }

    @Test public void aNarrowGapNeedsASafePointAtEachEnd() {
        FieldMap plain = FieldMap.margin(1).keepOut("a", -72, -5, -1.2, 5).keepOut("b", 1.2, -5, 72, 5);
        assertTrue("no corner fits: no route, and the robot stays", plain.route(-10, -10, 10, 10).isEmpty());
        FieldMap with = FieldMap.margin(1).keepOut("a", -72, -5, -1.2, 5).keepOut("b", 1.2, -5, 72, 5)
                .safePoint(0, -7).safePoint(0, 7);
        assertFalse(with.route(-10, -10, 10, 10).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class) public void aSafePointTooNearAWallStopsAtInit() {
        FieldMap.margin(13).safePoint(0, 60);
    }

    @Test(expected = IllegalArgumentException.class) public void aSafePointOffAFieldMadeSmallerStopsAtInit() {
        FieldMap.margin(13).safePoint(0, 50).fieldSize(100);
    }

    @Test public void aWallToWallBarrierHasNoRoute() {
        // Boxes from wall to wall: nothing goes past them, so the robot must not set off.
        FieldMap map = FieldMap.margin(5).keepOut("barrier", -72, -2, 72, 2);
        assertTrue(map.route(0, -30, 0, 30).isEmpty());
    }

    @Test public void cornersTooCloseToAWallAreNotUsed() {
        // The box reaches the left wall: its left corners would put the robot in the wall.
        List<double[]> r = FieldMap.margin(13).keepOut("box", -72, -5, 0, 5).route(-40, -30, -40, 30);
        for (double[] p : r) assertTrue("right of the box: " + p[0], p[0] > 0 || p[1] == 30);
    }

    @Test public void aSafePointCanMakeTheRouteShorter() {
        // Nothing but a safe point in the wide gap between two boxes that touch their corners' limits.
        FieldMap map = FieldMap.margin(2).keepOut("a", -60, -5, -1, 5).keepOut("b", 1, -5, 60, 5);
        assertFalse(map.route(0, -10, 0, 10).isEmpty());
        FieldMap with = FieldMap.margin(2).keepOut("a", -60, -5, -4, 5).keepOut("b", 4, -5, 60, 5).safePoint(0, 0);
        List<double[]> r = with.route(0, -10, 0, 10);
        assertEquals("straight through the gap", 1, r.size());
    }

    @Test(expected = IllegalArgumentException.class) public void aSafePointOnABoxStopsAtInit() {
        twoBoxes().safePoint(0, -20);
    }

    @Test(expected = IllegalArgumentException.class) public void aBoxLaterAddedOverASafePointStopsAtInit() {
        FieldMap.margin(5).safePoint(0, 0).keepOut("box", -1, -1, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class) public void aNegativeMarginStopsAtInit() {
        FieldMap.margin(-1);
    }

    @Test(expected = IllegalArgumentException.class) public void anEmptyNameStopsAtInit() {
        FieldMap.margin(1).keepOut(" ", 0, 0, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class) public void aNumberThatIsNotANumberStopsAtInit() {
        FieldMap.margin(1).keepOut("box", 0, 0, Double.NaN, 1);
    }

    @Test public void cornersGivenInAnyOrderAreTheSameBox() {
        FieldMap map = FieldMap.margin(0).keepOut("box", 10, 10, -10, -10);
        assertFalse(map.whyBadSpot(0, 0).isEmpty());
        assertEquals(1, map.boxCount());
        assertEquals(0, map.marginIn(), 0);
    }

    @Test public void aLineAlongABoxsSideIsClear() {
        assertEquals(1, FieldMap.margin(0).keepOut("box", 0, 0, 10, 10).route(0, -5, 0, 15).size());
    }

    @Test public void aFieldOfAnotherSizeChangesWhatIsOff() {
        assertFalse(FieldMap.margin(0).fieldSize(100).whyBadSpot(60, 0).isEmpty());
    }
}
