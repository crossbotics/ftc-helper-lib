package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.vision.MovingShot;
import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Shooting on the move in the simulator's physics: a piece shot from a moving robot keeps the robot's
 * speed (SimPieces adds it), so a shot aimed straight at the goal misses to the side; aimed at the
 * virtual goal ({@link MovingShot}, the math Tunables.Aim.shootOnMove uses), it lands much closer.
 */
public class SimMovingShotTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private SimRobot robot() throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File f = tmp.newFile();
        // A fixed exit speed (a well-tuned launcher), 45 degrees up.
        Files.write(f.toPath(), (base + "\nshoot.launcher = shape=flywheel, angle=45, speed=150\n").getBytes("UTF-8"));
        return SimRobot.load(f.toPath());
    }

    private static void run(SimRobot r, double sec) {
        for (double t = 0; t < sec; t += 0.01) r.step(0.01);
    }

    /** One shot: where the robot was when it fired, its speed, where the piece came down, and when. */
    private static final class Shot {
        double[] robotAt, robotVel, landed;
        double flightSec;
    }

    // Starts at (x, y) facing headingDeg, drives over the field at (vx, vy) in/s for a moment (0 = stands
    // still), fires one piece, and follows it until it comes back down to the tiles.
    private Shot shoot(double x, double y, double headingDeg, double vx, double vy) throws Exception {
        SimRobot r = robot();
        r.setPose(x, y, headingDeg);
        r.motors.get("flywheel").device.setPower(1);
        run(r, 1.5);                                                  // spin up, standing
        // The push is in the robot's own frame: forward and right.
        double h = Math.toRadians(headingDeg);
        double fwd = -vx * Math.sin(h) + vy * Math.cos(h), right = vx * Math.cos(h) + vy * Math.sin(h);
        if (vx != 0 || vy != 0) {
            r.push(fwd, right, 3);
            run(r, 0.6);                                              // up to speed
        }
        int before = r.pieces.launched();
        r.motors.get("feeder").device.setPower(1);
        Shot s = new Shot();
        double t0 = 0;
        for (int i = 0; i < 100 && r.pieces.launched() == before; i++) {
            run(r, 0.01);
            t0 = r.world.time();
            s.robotAt = new double[] {r.x, r.y};
            s.robotVel = r.world.robotVel();
        }
        r.motors.get("feeder").device.setPower(0);
        assertEquals("one shot", before + 1, r.pieces.launched());
        SimWorld.Ball b = r.world.balls().get(r.world.balls().size() - 1);
        double radius = b.type.diameterIn / 2;
        for (int i = 0; i < 400; i++) {
            run(r, 0.005);
            double[] p = r.world.ballPos(b), v = r.world.ballVel(b);
            if (v[2] < 0 && p[2] <= radius + 0.3) {
                s.landed = p;
                s.flightSec = r.world.time() - t0;
                break;
            }
        }
        assertTrue("the piece came down", s.landed != null);
        return s;
    }

    @Test public void aMovingShotLandsCloserWithTheLead() throws Exception {
        // 1. Standing still: where a straight shot lands is "the goal", and how long it flew is flightSec
        //    (on a real robot: a slow-motion video).
        Shot still = shoot(-30, -40, 0, 0, 0);
        double range = Math.hypot(still.landed[0] - still.robotAt[0], still.landed[1] - still.robotAt[1]);
        assertTrue("it flies forward: " + range, range > 30);
        assertTrue("a sensible flight time: " + still.flightSec, still.flightSec > 0.3 && still.flightSec < 2);

        // 2. Strafing RIGHT at 30 in/s, aimed straight ahead at a goal the same range ahead: it misses right.
        Shot plain = shoot(-50, -40, 0, 30, 0);
        double[] goal = {plain.robotAt[0], plain.robotAt[1] + range};
        double missPlain = Math.hypot(plain.landed[0] - goal[0], plain.landed[1] - goal[1]);

        // 3. The same, aimed with the lead: the robot faces the virtual goal (whole-robot aim, the
        //    launcher about 1 in behind the centre). The robot's speed from step 2 (the same drive).
        double[] v = MovingShot.toRobotFrame(plain.robotVel[0], plain.robotVel[1], 0);
        MovingShot.Result lead = new MovingShot().shooterAt(-1, 0).flightSec(still.flightSec, 0).maxLeadIn(0)
                .solve(range, 0, v[0], v[1], 0, 0);
        assertTrue("it leads to the LEFT (+): " + lead, lead.aimDeg > 5);
        Shot led = shoot(-50, -40, lead.aimDeg, 30, 0);
        double[] goal2 = {led.robotAt[0], led.robotAt[1] + range};
        double missLed = Math.hypot(led.landed[0] - goal2[0], led.landed[1] - goal2[1]);

        String says = String.format(java.util.Locale.US, "range %.1f in, flight %.2f s, robot %.1f in/s, lead %s: "
                + "miss %.1f in without, %.1f in with", range, still.flightSec, Math.hypot(v[0], v[1]), lead, missPlain, missLed);
        System.out.println("SimMovingShotTest: " + says);
        assertTrue("without the lead it misses by about speed x flight time: " + says, missPlain > 15);
        assertTrue("with the lead it lands much closer: " + says, missLed < missPlain / 3);
    }
}
