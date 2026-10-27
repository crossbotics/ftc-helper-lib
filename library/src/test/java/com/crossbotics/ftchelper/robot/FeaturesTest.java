package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * robot.features(): what the library does by itself for a robot, ready or not, from Robot.java's parts
 * alone (no hardware). And the launcher's ready-made actions.
 */
public class FeaturesTest {
    /** A tank robot with a launcher and nothing to aim with. */
    public static class TankShooter extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            drive(Drive.tank("left", "right"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
        }
    }

    private static Feature find(List<Feature> list, String name) {
        for (Feature f : list) if (f.name.equals(name)) return f;
        return null;
    }

    @Test public void aLauncherGivesFireActionsAndNoAimWithoutACamera() {
        List<Feature> list = new TankShooter().features();
        for (int i = 1; i <= Pieces.GAME_LIMIT; i++) {
            Feature f = find(list, "Fire " + i);
            assertTrue("Fire " + i, f != null && f.ready() && f.kind == Feature.Kind.ACTION);
            assertEquals("robot.launcher.fireAction(" + i + ")", f.code);
        }
        assertEquals("robot.launcher.fireAllAction()", find(list, "Fire all").code);
        Feature aim = find(list, "Aim at the goal");
        assertFalse("nothing to aim with", aim.ready());
        assertTrue(aim.missing, aim.missing.contains("camera"));
        Feature mode = find(list, "Aim mode (the whole robot turns to the goal)");
        assertTrue(mode.note, mode.note.contains("tank"));
        assertFalse(find(list, "Goal line on the Driver Station: seen, in range, LOCKED").ready());
    }

    @Test public void aCameraWithAGoalMakesAimReady() {
        AimTest.AimBot r = new AimTest.AimBot(AimTest.GoalTunables.class, true);
        r.withTurret = false;
        List<Feature> list = r.features();
        assertTrue(find(list, "Aim at the goal").ready());
        Feature mode = find(list, "Aim mode (the whole robot turns to the goal)");
        assertTrue(mode.ready());
        assertTrue(mode.note, mode.note.contains("strafe"));
        assertTrue("odometry too: shooting while moving", find(list, "Shoot while moving").ready());
        assertNull("no turret", find(list, "Turret follows the goal"));
    }

    @Test public void aFollowingTurretAimsInsteadOfTheRobot() {
        List<Feature> list = new AimTest.AimBot(AimTest.GoalTunables.class, true).features();
        assertTrue(find(list, "Turret follows the goal").ready());
        assertTrue(find(list, "Aim at the goal").note.contains("turret"));
        assertNull("no whole-robot aim with a following turret", find(list, "Aim mode (the whole robot turns to the goal)"));
    }

    @Test public void theFireActionsAreNamedByTheCount() {
        TankShooter r = new TankShooter();
        r.describe();
        assertEquals("Fire 2", r.launcher.fireAction(2).name());
        assertEquals("Fire 1", r.launcher.fireAction(1).name());
        assertEquals("Fire all", r.launcher.fireAllAction().name());
        try {
            r.launcher.fireAction(0);
            throw new AssertionError("fireAction(0) must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("at least 1"));
        }
    }
}
