package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The path lines on the field map and in the 3D view (SimPaths): the plan of a Pedro follow step and
 * the trail of where the robot really went.
 */
public class SimPathLinesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // The robot file made from Robot.java, as ./gradlew :sim:fromRobot writes it.
    private Path generate(Class<?> robotClass) throws Exception {
        com.crossbotics.ftchelper.robot.RobotDefinition r =
                (com.crossbotics.ftchelper.robot.RobotDefinition) robotClass.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, r.simLines(), robotClass.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile(robotClass.getPackage().getName() + ".properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // The field map draws the running path (the plan) and where the robot really went (the trail).
    @Test public void theMapShowsThePlannedPathAndTheTrail() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.pedrobot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Pedro Bot Curve to the Goal");
            // Wait until the simulator has put the robot on the start spot (INIT shows first).
            assertTrue("placed", SimRun.waitFor(() -> {
                com.crossbotics.ftchelper.util.FieldPose at = com.crossbotics.ftchelper.match.MatchMemory.placement();
                return at != null && Math.hypot(at.x - s.robot().x, at.y - s.robot().y) < 1;
            }, 3));
            boolean topWall = s.robot().y > 0;
            double sign = topWall ? -1 : 1;     // BLUE's wall is the far one: the field turned half a turn
            s.robot().paths.clear();
            s.start();
            SimPaths paths = s.robot().paths;
            assertTrue("the plan shows while the path runs",
                    SimRun.waitFor(() -> !paths.plan().isEmpty() && paths.planRunning(), 5));
            List<com.crossbotics.ftchelper.util.FieldPose> plan = paths.plan();
            // From the start spot (-56, -61) to the path's end (-30, -30), seen from the drivers' wall.
            assertEquals(sign * -56, plan.get(0).x, 0.01);
            assertEquals(sign * -61, plan.get(0).y, 0.01);
            assertEquals(sign * -30, plan.get(plan.size() - 1).x, 0.01);
            assertEquals(sign * -30, plan.get(plan.size() - 1).y, 0.01);
            assertTrue("it finishes\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 30));
            // The plan stays after its step (to compare with the trail), shown as not running.
            assertEquals(plan.size(), paths.plan().size());
            assertFalse(paths.planRunning());
            // The follower says so too: a path that ended has no points.
            assertTrue(com.crossbotics.ftchelper.pedro.PedroFollower.lastStarted().plannedPoints().isEmpty());
            // The trail: a point about every 0.1 s of driving, ending where the robot is.
            assertTrue("trail points: " + paths.trailPoints(), paths.trailPoints() > 20);
            List<List<double[]>> trail = paths.trail();
            List<double[]> last = trail.get(trail.size() - 1);
            double[] end = last.get(last.size() - 1);
            assertEquals(s.robot().x, end[0], 1.0);
            assertEquals(s.robot().y, end[1], 1.0);
            // The trail went near the planned end point (-30, -30) on its way.
            double closest = Double.MAX_VALUE;
            for (List<double[]> piece : trail) {
                for (double[] p : piece) closest = Math.min(closest, Math.hypot(p[0] - sign * -30, p[1] - sign * -30));
            }
            assertTrue("closest to the end: " + closest, closest < 3);
            SimServer server = new SimServer(s.sim, 0, false);   // any free port, never opened by a page
            String json = SimJson.state(s.sim, server);
            server.close();
            assertTrue(json.contains("\"plan\":[[") && json.contains("\"trail\":[[["));
            // The clear button.
            paths.clear();
            assertEquals(0, paths.trailPoints());
            assertTrue(paths.plan().isEmpty());
        }
    }

    // A robot put somewhere by hand starts a new piece of trail (no line across the field).
    @Test public void aTrailBreaksWhereTheRobotIsPutByHand() {
        SimPaths p = new SimPaths();
        for (int i = 0; i < 10; i++) p.record(i, 0, SimPaths.TRAIL_STEP_SEC);
        p.jumped();
        for (int i = 0; i < 10; i++) p.record(40, i, SimPaths.TRAIL_STEP_SEC);
        assertEquals(2, p.trail().size());
        // Standing still adds nothing; the trail keeps at most 30 s of driving.
        for (int i = 0; i < 50; i++) p.record(40, 9, SimPaths.TRAIL_STEP_SEC);
        assertEquals(20, p.trailPoints());
        for (int i = 0; i < 400; i++) p.record(i, 50, SimPaths.TRAIL_STEP_SEC);
        assertEquals(SimPaths.TRAIL_POINTS, p.trailPoints());
        assertEquals(1, p.trail().size());
    }
}
