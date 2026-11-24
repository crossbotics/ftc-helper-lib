package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Continuous fire and bang-bang in a whole TeleOp on the simulator: the buttons, the aim, the screens
 * and the real loop, with a speed reading in steps and late.
 */
public class SimContinuousFireTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    public static class PidNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Aim { public static double toleranceDeg = 3; }
        public static class Launcher {
            public static double[][] speedByDistance = {{10, 2400}, {100, 2800}};
            public static double readyBandRpm = 150, continuousMaxSec = 8;
            public static boolean continuousOnHold = true, extendTableEnds = true;
        }
    }

    public static class BangNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Aim { public static double toleranceDeg = 3; }
        public static class Launcher {
            public static String speedControl = "BANG_BANG";
            public static double[][] speedByDistance = {{10, 2400}, {100, 2800}};
            public static double readyBandRpm = 180, bangKv = 0.00213333, continuousMaxSec = 8;
            public static boolean continuousOnHold = true, extendTableEnds = true;
        }
    }

    public static class Bot extends RobotDefinition {
        Class<?> numbers() {
            return PidNumbers.class;
        }

        public Launcher launcher;

        @Override protected void define() {
            tunables(numbers());
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            Camera camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right"))
                    .speedFromDistance(camera).spinAtStart());
        }
    }

    public static class BangBot extends Bot {
        @Override Class<?> numbers() {
            return BangNumbers.class;
        }
    }

    public static class Controls extends ControlMap<Bot> {
        @Override protected void buttons(Bot robot, Pad g1, Pad g2) {
            robot.launcher.fireOn(g2.rb);
        }
    }

    public static class BangControls extends ControlMap<BangBot> {
        @Override protected void buttons(BangBot robot, Pad g1, Pad g2) {
            robot.launcher.fireOn(g2.rb);
        }
    }

    private Path robotFile() throws Exception {
        SimFromRobot.Result res = SimFromRobot.merge(null, new Bot().simLines(), Bot.class.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        StringBuilder text = new StringBuilder();
        for (String line : res.text.split("\n")) if (!line.startsWith("camera.")) text.append(line).append('\n');
        // The same camera as SimCameraTest, which sees BLUE's up CELL from its pose.
        text.append("camera.front = name=Webcam 1, forward=8, up=10, pitch=30\n");
        Path file = tmp.newFile("group.properties").toPath();
        Files.write(file, text.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static double feeder(SimRun s) {
        synchronized (s.robot()) {
            return s.robot().crServos.get("feeder_left")[0];
        }
    }

    private SimRun started(Class<? extends com.qualcomm.robotcore.eventloop.opmode.OpMode> type) throws Exception {
        SimRun s = new SimRun(robotFile().toString());
        s.sim.ds.register("Group", "TeleOp", type);
        s.init("Group");
        s.press(v -> s.g1().x = v);                       // BLUE
        assertEquals("nothing moves in INIT", 0, feeder(s), 0);
        s.start();
        s.robot().setPose(-60, 12.75, 270);
        assertEquals("", s.error());
        return s;
    }

    private static void holdUntilFeeding(SimRun s) throws InterruptedException {
        s.g2().right_bumper = true;
        assertTrue(s.screen().toString(), SimRun.waitFor(() -> feeder(s) > 0, 5));
        assertTrue(s.screen().toString(), SimRun.waitFor(() -> s.screenHas("feeder RUNNING. Let go to stop."), 2));
    }

    @Test public void holdIsOneGroupAndReleaseStopsIt() throws Exception {
        try (SimRun s = started(Controls.class)) {
            SimRun.pause(3);
            holdUntilFeeding(s);
            SimRun.pause(1);
            assertTrue("no feedSec ends it", feeder(s) > 0);
            s.g2().right_bumper = false;
            s.waitForLoops(2);
            assertEquals(0, feeder(s), 0);
            SimRun.pause(0.5);
            assertEquals("no restart", 0, feeder(s), 0);
        }
    }

    @Test public void theButtonListEndsTheGroupAndAFreshPressIsNeeded() throws Exception {
        try (SimRun s = started(Controls.class)) {
            SimRun.pause(3);
            holdUntilFeeding(s);
            s.press(v -> s.g1().guide = v);
            assertEquals(0, feeder(s), 0);
            s.press(v -> s.g1().guide = v);                 // closed again
            assertTrue(s.screen().toString(), SimRun.waitFor(() -> s.screenHas("Firing stopped: button list is open"), 2));
            SimRun.pause(0.6);
            assertEquals("still held: no restart", 0, feeder(s), 0);
            s.g2().right_bumper = false;
            s.waitForLoops(2);
            holdUntilFeeding(s);
        }
    }

    @Test public void pauseEndsTheGroupAndResumeDoesNotBringItBack() throws Exception {
        try (SimRun s = started(Controls.class)) {
            SimRun.pause(3);
            holdUntilFeeding(s);
            s.press(v -> s.g1().back = v);
            assertEquals(0, feeder(s), 0);
            s.press(v -> s.g1().back = v);
            SimRun.pause(0.6);
            assertEquals("still held: no restart", 0, feeder(s), 0);
        }
    }

    @Test public void noGoalIsNoGroup() throws Exception {
        try (SimRun s = started(Controls.class)) {
            s.robot().setPose(0, -40, 180);
            SimRun.pause(1);
            s.g2().right_bumper = true;
            assertTrue(s.screen().toString(), SimRun.waitFor(() -> s.screenHas("goal NOT seen"), 2));
            assertEquals(0, feeder(s), 0);
            assertFalse(s.screen().toString(), s.screenHas("waits for"));
        }
    }

    @Test public void bangBangFromStartWithStepsLagAndSlowLoops() throws Exception {
        try (SimRun s = started(BangControls.class)) {
            s.robot().setSensor("launcher", 43, 0.05);
            s.sim.ds.screen.loopTime(20, 60);
            SimRun.pause(4);
            double rpm = Math.abs(s.robot().motors.get("launcher").velocity()) / 28 * 60;
            assertTrue("spun up from START: " + rpm + " " + s.screen(), rpm > 2000 && rpm < 3500);
            assertFalse(s.screen().toString(), s.screenHas("bad speed reading"));
            assertTrue("never reversed", s.robot().motors.get("launcher").userPower() >= 0);
            holdUntilFeeding(s);
            s.g2().right_bumper = false;
            s.waitForLoops(2);
            assertEquals(0, feeder(s), 0);
            s.sim.ds.stop();
            assertTrue(SimRun.waitFor(() -> s.sim.ds.phase() == SimDriverStation.Phase.STOPPED, 4));
            assertEquals("STOP reaches the motor", 0, s.robot().motors.get("launcher").userPower(), 0);
        }
    }
}
