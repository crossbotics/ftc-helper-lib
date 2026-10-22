package com.crossbotics.ftchelper.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The math of shooting while moving: the picture's age, the lead, and where the shooter is. */
public class MovingShotTest {
    private static double deg(double y, double x) {
        return Math.toDegrees(Math.atan2(y, x));
    }

    @Test public void aStillRobotChangesNothing() {
        MovingShot turret = new MovingShot().onTurret().flightSec(0.8, 0.002);
        MovingShot.Result r = turret.solve(60, 10, 0, 0, 0, 0.05);
        assertEquals(deg(10, 60), r.aimDeg, 1e-9);
        assertEquals(Math.hypot(60, 10), r.distanceIn, 1e-9);
        assertEquals(0, r.leadDeg, 1e-9);
        assertEquals(0, r.leadIn, 1e-9);
        // The whole robot (a fixed shooter at the centre): the same.
        MovingShot.Result w = new MovingShot().flightSec(0.8, 0).solve(60, 10, 0, 0, 0, 0.05);
        assertEquals(deg(10, 60), w.aimDeg, 1e-9);
        assertEquals(Math.hypot(60, 10), w.distanceIn, 1e-9);
    }

    @Test public void strafingRightLeadsToTheLeft() {
        // The goal straight ahead 60 in; the robot strafes RIGHT at 30 in/s (left speed -30); 0.7 s flight.
        MovingShot shot = new MovingShot().onTurret().flightSec(0.7, 0).maxLeadIn(0);
        MovingShot.Result r = shot.solve(60, 0, 0, -30, 0, 0);
        // The piece drifts 21 in right, so aim 21 in left: atan(21 / 60) = 19.29 deg left.
        assertEquals(deg(21, 60), r.aimDeg, 1e-9);
        assertEquals(Math.hypot(60, 21), r.distanceIn, 1e-9);
        assertEquals(21, r.leadIn, 1e-9);
        assertEquals(r.aimDeg, r.leadDeg, 1e-9);
        // Strafing LEFT: the same lead to the right.
        assertEquals(-r.aimDeg, shot.solve(60, 0, 0, 30, 0, 0).aimDeg, 1e-9);
    }

    @Test public void drivingTowardTheGoalShootsShorter() {
        MovingShot.Result r = new MovingShot().onTurret().flightSec(0.5, 0).maxLeadIn(0).solve(60, 0, 40, 0, 0, 0);
        assertEquals(0, r.aimDeg, 1e-9);
        assertEquals("the piece also flies 20 in on the robot's speed", 40, r.distanceIn, 1e-9);
    }

    @Test public void turningWhileThePictureIsOldTurnsTheGoalBack() {
        // Turning LEFT at 90 deg/s, a 50 ms old picture: the robot turned 4.5 deg left since, so the
        // goal is 4.5 deg less to the left now. No flight time: no lead.
        double[] g = MovingShot.seenFrom(10, 48, 0, 0, 0);
        MovingShot.Result r = new MovingShot().onTurret().solve(g[0], g[1], 0, 0, 90, 0.050);
        assertEquals(5.5, r.aimDeg, 1e-9);
        assertEquals(48, r.distanceIn, 1e-9);
        assertEquals(-4.5, r.leadDeg, 1e-9);
    }

    @Test public void movingWhileThePictureIsOldMovesTheGoal() {
        // Driving forward at 40 in/s, a 100 ms old picture: the goal is 4 in closer now.
        MovingShot.Result r = new MovingShot().onTurret().solve(48, 0, 40, 0, 0, 0.1);
        assertEquals(44, r.distanceIn, 1e-9);
    }

    @Test public void theLeadIsCapped() {
        MovingShot.Result r = new MovingShot().onTurret().flightSec(1.0, 0).maxLeadIn(24).solve(60, 0, 0, -60, 0, 0);
        assertEquals(24, r.leadIn, 1e-9);
        assertEquals(deg(24, 60), r.aimDeg, 1e-9);
    }

    @Test public void aFlightTimeThatGrowsWithDistanceUsesTheVirtualGoalsDistance() {
        MovingShot.Result r = new MovingShot().onTurret().flightSec(0.3, 0.008).maxLeadIn(0).solve(60, 0, 0, -30, 0, 0);
        assertEquals("t = 0.3 + 0.008 x the virtual distance", 0.3 + 0.008 * r.distanceIn, r.flightSec, 1e-4);
        assertEquals(30 * r.flightSec, r.leadIn, 1e-9);
    }

    @Test public void aTurretBehindTheCentreAimsFromItsOwnPlace() {
        // The turret 6 in behind the centre and 4 in left; the goal 60 in ahead of the centre, 4 in left.
        MovingShot.Result r = new MovingShot().shooterAt(-6, 4).onTurret().solve(60, 4, 0, 0, 0, 0);
        assertEquals("straight ahead from the turret", 0, r.aimDeg, 1e-9);
        assertEquals("66 in from the turret, not 60 from the centre", 66, r.distanceIn, 1e-9);
    }

    @Test public void aFixedShooterToOneSideTurnsTheRobotSoItsLinePassesThroughTheGoal() {
        // A launcher 5 in LEFT of the centre, fixed facing forward; the goal 50 in straight ahead of the centre.
        MovingShot.Result r = new MovingShot().shooterAt(3, 5).solve(50, 0, 0, 0, 0, 0);
        // Turn RIGHT by asin(5 / 50) so the launcher's line (5 in left of the centre line) meets the goal.
        assertEquals(-Math.toDegrees(Math.asin(0.1)), r.aimDeg, 1e-9);
        assertEquals("along the line, from the launcher", Math.sqrt(2500 - 25) - 3, r.distanceIn, 1e-9);
        // Check: turned by aimDeg, the goal sits on the launcher's line (5 in left, ahead).
        double a = Math.toRadians(-r.aimDeg);
        assertEquals(5, 50 * Math.sin(a), 1e-9);
    }

    @Test public void turningSwingsAnOffCentreShooterSideways() {
        // A turret 10 in in front of the centre; the robot turns LEFT at 90 deg/s and does not drive:
        // the turret moves LEFT at 10 x pi/2 = 15.7 in/s, so the shot must lead to the RIGHT.
        MovingShot shot = new MovingShot().shooterAt(10, 0).onTurret().flightSec(0.5, 0).maxLeadIn(0);
        MovingShot.Result r = shot.solve(70, 0, 0, 0, 90, 0);
        assertEquals(10 * Math.PI / 2 * 0.5, r.leadIn, 1e-9);
        assertEquals(deg(-10 * Math.PI / 2 * 0.5, 60), r.aimDeg, 1e-9);
        assertTrue(r.aimDeg < 0);
        // At the centre, turning alone gives no lead.
        assertEquals(0, new MovingShot().onTurret().flightSec(0.5, 0).solve(70, 0, 0, 0, 90, 0).leadIn, 1e-9);
    }

    @Test public void aCameraOnTheTurretOrOnTheBodySeesTheSameGoal() {
        // The goal at (60, 20) from the centre. A body camera 8 in forward, 2 in right; a turret camera
        // at the turret's pivot (-4, 0), the turret turned 30 deg left.
        double[] body = MovingShot.seenFrom(deg(22, 52), Math.hypot(52, 22), 8, -2, 0);
        assertEquals(60, body[0], 1e-9);
        assertEquals(20, body[1], 1e-9);
        double[] onTurret = MovingShot.seenFrom(deg(20, 64) - 30, Math.hypot(64, 20), -4, 0, 30);
        assertEquals(60, onTurret[0], 1e-9);
        assertEquals(20, onTurret[1], 1e-9);
    }

    @Test public void fieldSpeedTurnsIntoTheRobotsFrame() {
        // Facing away (0): moving right (+x) is to the robot's right (left speed negative).
        double[] v = MovingShot.toRobotFrame(10, 0, 0);
        assertEquals(0, v[0], 1e-9);
        assertEquals(-10, v[1], 1e-9);
        // Facing left (90): moving left (-x) is forward.
        v = MovingShot.toRobotFrame(-10, 0, 90);
        assertEquals(10, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
        // Facing away, moving away: forward.
        v = MovingShot.toRobotFrame(0, 7, 0);
        assertEquals(7, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
    }
}
