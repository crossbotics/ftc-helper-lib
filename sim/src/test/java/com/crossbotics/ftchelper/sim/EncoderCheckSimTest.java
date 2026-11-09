package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.finders.TuneAndCheckOpMode;
import com.crossbotics.ftchelper.robot.Arm;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.util.Collections;
import org.junit.After;
import org.junit.Test;

/**
 * The Encoder Check in "Tune and check", on the simulator's starter robot: with two encoder cables swapped
 * (the lift's and the flywheel's) and one missing (the arm's), it names each fault; every other motor is OK.
 * It runs only while A is held, and nothing moves after it ends.
 */
public class EncoderCheckSimTest {

    /** A robot in five files with the starter robot file's motors (sim/robots/starter.properties). */
    public static class StarterMotors extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            add(Intake.motor("intake"));
            add(Lift.slide("lift"));
            add(Arm.motor("arm", "gobilda-5203-117"));
            add(Launcher.flywheel("flywheel", "gobilda-5203-6000").feeder(Indexer.motor("feeder")));
        }
    }

    @After public void findRobotsAgain() {
        TuneAndCheckOpMode.useRobots(null);
    }

    @Test public void findsASwappedPairAndAMissingCable() throws Exception {
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>singletonList(StarterMotors.class));
        try (SimRun s = new SimRun()) {
            // The wiring faults (the scenario command "set encoder" does the same).
            s.robot().setEncoder("lift", "flywheel");
            s.robot().setEncoder("flywheel", "lift");
            s.robot().setEncoder("arm", "none");
            s.init(TuneAndCheckOpMode.NAME);
            for (int i = 0; i < 15 && !s.screenHas("> Encoder Check"); i++) s.press(v -> s.g1().dpad_down = v);
            assertTrue(String.join("\n", s.screen()), s.screenHas("> Encoder Check"));
            s.press(v -> s.g1().a = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Put the robot on a block"), 2));
            s.start();
            // Not held: nothing moves.
            SimRun.pause(0.3);
            for (SimMotor m : s.robot().motors.values()) assertEquals(m.name, 0, m.userPower(), 1e-9);
            s.g1().a = true;
            boolean done = SimRun.waitFor(() -> s.screenHas("problem(s)") || s.screenHas("Done:"), 40);
            s.g1().a = false;
            String screen = String.join("\n", s.screen());
            assertTrue(screen, done);
            assertTrue(screen, line(s, "\"lift\"").contains("counts on \"flywheel\""));
            assertTrue(screen, line(s, "\"flywheel\"").contains("counts on \"lift\""));
            assertTrue(screen, line(s, "\"arm\"").contains("NO COUNTS"));
            for (String ok : new String[] {"\"front_left\"", "\"front_right\"", "\"back_left\"", "\"back_right\""}) {
                assertTrue(screen, line(s, ok).contains("OK"));
            }
            assertTrue(screen, s.screenHas("3 problem(s)"));
            assertTrue("every motor stopped", SimRun.waitFor(() -> {
                for (SimMotor m : s.robot().motors.values()) if (m.userPower() != 0) return false;
                return true;
            }, 1));
            assertEquals("", s.error());
        }
    }

    @Test public void lettingGoOfAStopsAtOnce() throws Exception {
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>singletonList(StarterMotors.class));
        try (SimRun s = new SimRun()) {
            s.init(TuneAndCheckOpMode.NAME);
            for (int i = 0; i < 15 && !s.screenHas("> Encoder Check"); i++) s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().a = v);
            assertTrue(SimRun.waitFor(() -> s.screenHas("Put the robot on a block"), 2));
            s.start();
            SimMotor fl = s.robot().motors.get("front_left");
            s.g1().a = true;
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> fl.userPower() > 0, 5));
            s.g1().a = false;
            assertTrue("let go: it stops", SimRun.waitFor(() -> fl.userPower() == 0, 0.5));
            // The screen comes at the end of the same loop: wait for it.
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> !s.screenHas("PUSHING"), 1));
            assertEquals("", s.error());
        }
    }

    // The screen line that starts with this motor's name.
    private static String line(SimRun s, String start) {
        for (String l : s.screen()) if (l.trim().startsWith(start)) return l;
        return "";
    }
}
