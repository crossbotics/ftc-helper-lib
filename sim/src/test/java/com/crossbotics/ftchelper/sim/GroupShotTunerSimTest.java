package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.finders.FlywheelTuneResults;
import com.crossbotics.ftchelper.finders.GroupShotTunerOpMode;
import com.crossbotics.ftchelper.finders.TuneAndCheckOpMode;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.tuning.Tunable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The Group Shot Tuner, run whole in the simulator from Tune and check, on a robot with a camera that sees the
 * goal and two spinning feed wheels: nothing moves in INIT, a held button starts nothing, the sticks drive only
 * on the "place the robot" screen and at 30%, any button and help stop a shot, the check shots and a group
 * really feed (the group without a break), the power is off while the person enters where the balls went, and a
 * saved aim offset that is not the code's stops every shot from counting.
 */
public class GroupShotTunerSimTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    public static class Numbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Aim { public static double toleranceDeg = 3; }
        public static class Webcam1 { public static double pitchDeg = 35; }   // the camera's tilt: the distance along the floor
        public static class Launcher {
            public static double[][] speedByDistance = {{30, 2450}, {35, 2500}, {40, 2550}};
            public static double readyBandRpm = 150, continuousMaxSec = 2;
        }
    }

    public static class Bot extends RobotDefinition {
        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            Camera camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right"))
                    .speedFromDistance(camera));
        }
    }

    // The same robot with a feed stop in front of the flywheel and a feed wheel that runs whenever the flywheel is
    // spun up (alwaysOn): the tuners set the robot up but do not start it, so they must start the feed themselves.
    public static class StopNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Aim { public static double toleranceDeg = 3; }
        public static class Webcam1 { public static double pitchDeg = 35; }
        public static class Stage { public static boolean alwaysOn = true; public static double power = 1; }
        public static class Launcher {
            public static double[][] speedByDistance = {{30, 2450}, {35, 2500}, {40, 2550}};
            public static double readyBandRpm = 150, continuousMaxSec = 2, feedStopHold = 0.2, feedStopRelease = 0.7;
        }
    }

    public static class StopBot extends RobotDefinition {
        @Override protected void define() {
            tunables(StopNumbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            Camera camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right"))
                    .feedStop("stop").feedWheel(Indexer.crServo("stage"))
                    .speedFromDistance(camera));
        }
    }

    // The first robot with bang-bang speed control (its own fault check for a bad speed reading).
    public static class BangNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Aim { public static double toleranceDeg = 3; }
        public static class Webcam1 { public static double pitchDeg = 35; }
        public static class Launcher {
            public static String speedControl = "BANG_BANG";
            public static double[][] speedByDistance = {{30, 2450}, {35, 2500}, {40, 2550}};
            public static double readyBandRpm = 150, continuousMaxSec = 2;
        }
    }

    public static class BangBot extends RobotDefinition {
        @Override protected void define() {
            tunables(BangNumbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            Camera camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right"))
                    .speedFromDistance(camera));
        }
    }

    @Before @After public void noSavedRuns() throws Exception {
        TuneAndCheckOpMode.useRobots(null);
        Files.deleteIfExists(GroupShotTunerOpMode.savedFile().toPath());
        Files.deleteIfExists(FlywheelTuneResults.file().toPath());
        com.crossbotics.ftchelper.core.SettingsFile.shared().remove("tunable.Aim/cameraOffset");
        for (Tunable t : Tunable.all()) if (t.name().equals("Aim/cameraOffset")) t.reset();
    }

    private Path robotFile() throws Exception {
        return robotFile(new Bot());
    }

    private Path robotFile(RobotDefinition bot) throws Exception {
        SimFromRobot.Result res = SimFromRobot.merge(null, bot.simLines(), bot.getClass().getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        StringBuilder text = new StringBuilder();
        for (String line : res.text.split("\n")) if (!line.startsWith("camera.")) text.append(line).append('\n');
        // A camera at the front, tilted up to see the CELL's tags from 30 to 40 in.
        text.append("camera.front = name=Webcam 1, forward=4.5, up=14, pitch=35, hfov=42.5, width=640, height=480\n");
        Path file = tmp.newFile("group.properties").toPath();
        Files.write(file, text.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String screen(SimRun s) {
        return String.join("\n", s.screen());
    }

    // Waits up to this long for it to become true; else fails with the screen as it is then.
    private static void within(SimRun s, java.util.function.BooleanSupplier check, double seconds) throws Exception {
        boolean ok = SimRun.waitFor(check, seconds);
        assertTrue(screen(s), ok);
    }

    // Tune and check, the arrow on the Group Shot Tuner, A, RED: its INIT screen.
    private SimRun open() throws Exception {
        return open(null);
    }

    // savedOffset: a cameraOffset the tuning menu saved, or null for none.
    private SimRun open(String savedOffset) throws Exception {
        return open(savedOffset, Bot.class, "Group Shot Tuner", true);
    }

    // A tool of Tune and check on this robot: its INIT screen (RED chosen when red is true).
    private SimRun open(String savedOffset, Class<? extends RobotDefinition> bot, String tool, boolean red) throws Exception {
        SimRun s = new SimRun(robotFile(bot.getDeclaredConstructor().newInstance()).toString());
        if (savedOffset != null) {
            // As the tuning menu saves it: in the settings file, and in the tunable if an earlier test made it.
            com.crossbotics.ftchelper.core.SettingsFile.shared().put("tunable.Aim/cameraOffset", savedOffset);
            for (Tunable t : Tunable.all()) if (t.name().equals("Aim/cameraOffset")) t.set(Double.parseDouble(savedOffset));
        }
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>singletonList(bot));
        s.init(TuneAndCheckOpMode.NAME);
        for (int i = 0; i < 25 && !s.screenHas("> " + tool); i++) s.press(v -> s.g1().dpad_down = v);
        assertTrue(screen(s), s.screenHas("> " + tool));
        s.press(v -> s.g1().a = v);
        within(s, () -> s.screenHas("STOP (■) on the Driver Hub"), 2);
        if (red) s.press(v -> s.g1().b = v);              // RED
        return s;
    }

    // START, then the robot facing RED's up CELL, about 30 in from it.
    private static void start(SimRun s) throws Exception {
        s.start();
        s.robot().setPose(57, -13, 90);
    }

    private static double feeder(SimRun s) {
        synchronized (s.robot()) {
            return s.robot().crServos.get("feeder_left")[0];
        }
    }

    private static boolean anyDriven(SimRun s) {
        for (SimMotor m : s.robot().motors.values()) if (m.driven()) return true;
        return feeder(s) != 0;
    }

    // The camera's distance on the screen ("Camera distance : 27.4 in: ..."), NaN when it shows none.
    private static double distance(SimRun s) {
        for (String l : s.screen()) {
            if (!l.startsWith("Camera distance : ") || l.contains("NOT seen")) continue;
            return Double.parseDouble(l.substring(18, l.indexOf(" in")));
        }
        return Double.NaN;
    }

    // Moves the robot by hand, straight forward or back, until the camera is at this distance.
    private static void placeAt(SimRun s, double inches) throws Exception {
        for (int i = 0; i < 3; i++) {
            within(s, () -> !Double.isNaN(distance(s)), 3);
            double off = distance(s) - inches;
            if (Math.abs(off) < 0.3) return;
            SimRobot r = s.robot();
            double h, x, y;
            synchronized (r) {
                h = Math.toRadians(r.headingDeg);
                x = r.x - off * Math.sin(h);
                y = r.y + off * Math.cos(h);
            }
            r.setPose(x, y, Math.toDegrees(h));
            SimRun.pause(1.2);                             // a fresh half second of pictures
        }
    }

    // A on the place screen, then A again: the countdown.
    private static void shootFromHere(SimRun s) throws Exception {
        s.press(v -> s.g1().a = v);
        within(s, () -> s.screenHas("{A} = SHOOT") || s.screenHas("A = SHOOT"), 2);
        s.press(v -> s.g1().a = v);
        within(s, () -> s.screenHas("Starting in"), 1);
    }

    @Test public void nothingMovesInInitAHeldButtonStartsNothingAndTheSticksDriveOnlyToPlaceIt() throws Exception {
        try (SimRun s = open()) {
            assertTrue(screen(s), s.screenHas("It SHOOTS real balls. Safety glasses."));
            assertTrue(screen(s), s.screenHas("Have 12 balls ready."));
            assertTrue(screen(s), s.screenHas("Flywheel Tuner: no best setting yet."));
            s.g1().a = true;                               // held through START
            SimRun.pause(1);
            assertFalse("INIT moves nothing", anyDriven(s));
            s.start();
            SimRun.pause(1);
            assertFalse("a held A starts nothing", anyDriven(s));
            s.g1().a = false;
            within(s, () -> s.screenHas("The STICKS drive (30% power)."), 2);
            SimRun.pause(0.3);                             // let go of everything once
            s.g1().left_stick_y = -1;                      // full stick: 30%
            assertTrue("a fresh push drives\n" + screen(s), SimRun.waitFor(() -> s.robot().motors.get("front_left").driven(), 1));
            SimRun.pause(0.3);
            for (String m : new String[] {"front_left", "front_right", "back_left", "back_right"}) {
                assertTrue(m, Math.abs(s.robot().motors.get(m).userPower()) <= 0.3 + 1e-9);
            }
            s.g1().left_stick_y = 0;
            SimRun.pause(0.3);
            assertFalse("let go: still", anyDriven(s));
            assertEquals("", s.error());
        }
    }

    @Test public void anyButtonOrHelpStopsAShotAndNothingStartsAgainByItself() throws Exception {
        try (SimRun s = open()) {
            start(s);
            placeAt(s, 30.7);
            shootFromHere(s);
            s.press(v -> s.g1().b = v);                    // in the countdown
            within(s, () -> s.screenHas("STOPPED"), 1);
            SimRun.pause(4);
            assertFalse("the countdown's end starts nothing", anyDriven(s));
            @SuppressWarnings("unchecked")
            Consumer<Boolean>[] buttons = new Consumer[] {
                (Consumer<Boolean>) v -> s.g1().y = v,
                (Consumer<Boolean>) v -> s.g2().right_bumper = v,
            };
            for (Consumer<Boolean> b : buttons) {
                s.press(v -> s.g1().a = v);                // go on
                s.press(v -> s.g1().a = v);                // shoot again
                within(s, () -> s.robot().motors.get("launcher").driven(), 6);
                b.accept(true);
                within(s, () -> s.screenHas("STOPPED") || s.screenHas("where did it go?"), 1);
                s.waitForLoops(2);
                assertFalse("power off\n" + screen(s), s.robot().motors.get("launcher").driven());
                b.accept(false);
                SimRun.pause(1.5);
                assertFalse("nothing restarts", anyDriven(s));
                if (s.screenHas("where did it go?")) s.press(v -> s.g1().x = v);   // flew already: not sure, repeat
            }
            s.press(v -> s.g1().a = v);
            s.press(v -> s.g1().a = v);
            within(s, () -> s.robot().motors.get("launcher").driven(), 6);
            s.press(v -> s.g1().guide = v);                // help
            s.waitForLoops(2);
            assertFalse("help: power off", s.robot().motors.get("launcher").driven());
            s.press(v -> s.g1().guide = v);
            SimRun.pause(1.5);
            assertFalse("help closed: nothing restarts", anyDriven(s));
            assertEquals("", s.error());
        }
    }

    // Four check shots, then a group: each check shot feeds once, the group feeds without a break until its
    // time limit, and the power is off while the person enters where the balls went.
    @Test public void checkShotsThenAGroupFeedAndThePowerIsOffForTheEntry() throws Exception {
        try (SimRun s = open()) {
            start(s);
            placeAt(s, 30.7);
            s.press(v -> s.g1().a = v);                    // here
            for (int i = 0; i < 4; i++) {
                s.press(v -> s.g1().a = v);
                within(s, () -> feeder(s) != 0, 15);
                within(s, () -> s.screenHas("where did it go?"), 5);
                s.waitForLoops(2);
                assertFalse("power off in the entry", anyDriven(s));
                s.press(v -> s.g1().a = v);                // scored
                within(s, () -> s.screenHas("Saved: check shot scored."), 1);
            }
            assertTrue(screen(s), s.screenHas("Group 1 of 3. Setting: today's (PID)."));
            assertTrue(screen(s), s.screenHas("Next: load exactly 4 balls. Then {A}.") || s.screenHas("Next: load exactly 4 balls. Then A."));
            s.press(v -> s.g1().a = v);
            within(s, () -> feeder(s) != 0, 15);
            double fedAt = System.nanoTime() / 1e9;
            within(s, () -> s.screenHas("where did the 4 go?"), 5);
            double fedFor = System.nanoTime() / 1e9 - fedAt;
            assertTrue("one feed without a break, to its 2 s limit: " + fedFor, fedFor > 1.7);
            s.waitForLoops(2);
            assertFalse("power off in the entry", anyDriven(s));
            for (int i = 0; i < 3; i++) s.press(v -> s.g1().dpad_right = v);   // 3 scored
            s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().dpad_right = v);           // 1 short
            assertTrue(screen(s), s.screenHas("Entered : 4 of 4"));
            s.press(v -> s.g1().a = v);
            within(s, () -> s.screenHas("Saved: 3 scored, 1 short."), 1);
            assertTrue(screen(s), s.screenHas("Group 2 of 3. Setting: today's (PID)."));
            assertTrue(screen(s), s.screenHas("spot 1 of 3 (30.0 in)"));
            assertEquals("", s.error());
        }
    }

    private static double stopAt(SimRun s) {
        synchronized (s.robot()) {
            return s.robot().servos.get("stop").device.getPosition();
        }
    }

    private static double stage(SimRun s) {
        synchronized (s.robot()) {
            return s.robot().crServos.get("stage")[0];
        }
    }

    // The robot is set up but not started (nothing moves in INIT). After A and the countdown the tuner starts the
    // feed itself: the feed stop goes to hold and opens for each shot, and the always-on feed wheel runs.
    @Test public void theFeedStopOpensAndTheAlwaysOnFeedWheelRuns() throws Exception {
        try (SimRun s = open(null, StopBot.class, "Group Shot Tuner", true)) {
            SimRun.pause(1);
            assertEquals("INIT: the feed wheel is off", 0, stage(s), 0);
            assertFalse("INIT: the stop did not move", Math.abs(stopAt(s) - 0.2) < 1e-6 || Math.abs(stopAt(s) - 0.7) < 1e-6);
            start(s);
            placeAt(s, 30.7);
            s.press(v -> s.g1().a = v);                    // here
            s.press(v -> s.g1().a = v);                    // shoot one check shot
            within(s, () -> stage(s) != 0, 15);            // spun up: the always-on feed wheel runs
            within(s, () -> feeder(s) != 0, 15);
            assertEquals("the feed stop is open while it feeds", 0.7, stopAt(s), 1e-6);
            within(s, () -> s.screenHas("where did it go?"), 5);
            s.waitForLoops(2);
            assertEquals("power off for the entry: the feed wheel too", 0, stage(s), 0);
            assertEquals("", s.error());
        }
    }

    // The Shot Tuner too: LB turns the flywheel on, and the feed starts with it.
    @Test public void theShotTunerStartsTheFeedStopAndTheFeedWheelWithLb() throws Exception {
        try (SimRun s = open(null, StopBot.class, "Shot Tuner", false)) {
            SimRun.pause(1);
            assertEquals(0, stage(s), 0);
            start(s);
            SimRun.pause(1);
            assertEquals("not before LB", 0, stage(s), 0);
            s.press(v -> s.g1().left_bumper = v);         // the flywheel on
            within(s, () -> stage(s) != 0, 10);
            within(s, () -> Math.abs(stopAt(s) - 0.2) < 1e-6, 2);   // the feed stop holds (it opens for a shot)
            s.press(v -> s.g1().left_bumper = v);         // off again
            within(s, () -> stage(s) == 0, 2);
            assertEquals("", s.error());
        }
    }

    // Bang-bang's bad-speed-reading fault: the tool stops with the fix on the screen (not "NOT at speed" at every
    // A), and the next shot after the person's A clears it.
    @Test public void aBadSpeedReadingStopsItWithTheFixAndTheNextShotClearsIt() throws Exception {
        try (SimRun s = open(null, BangBot.class, "Group Shot Tuner", true)) {
            start(s);
            placeAt(s, 30.7);
            s.press(v -> s.g1().a = v);                    // here
            s.robot().setEncoder("launcher", "none");      // the cable comes off
            s.press(v -> s.g1().a = v);                    // shoot
            within(s, () -> s.screenHas("read 0 or backward"), 15);
            s.waitForLoops(2);
            assertFalse("power off", s.robot().motors.get("launcher").driven());
            assertEquals("nothing fed", 0, feeder(s), 0);
            s.robot().setEncoder("launcher", "own");       // fixed
            s.press(v -> s.g1().a = v);                    // I checked it
            within(s, () -> s.screenHas("SHOOT 1 ball"), 2);
            s.press(v -> s.g1().a = v);                    // the next A shoots, and its spin-up clears the fault
            within(s, () -> feeder(s) != 0, 15);
            assertEquals("", s.error());
        }
    }

    @Test public void aSavedAimOffsetThatIsNotTheCodesStopsEveryShot() throws Exception {
        try (SimRun s = open("2.5")) {
            assertTrue(screen(s), s.screenHas("Aim offset NOT the same. In the code:"));
            assertTrue(screen(s), s.screenHas("0.0 deg. Saved in the tuning menu:"));
            assertTrue(screen(s), s.screenHas("2.5 deg to the RIGHT."));
            start(s);
            placeAt(s, 30.7);
            s.press(v -> s.g1().a = v);                    // here
            s.press(v -> s.g1().a = v);                    // shoot: refused
            within(s, () -> s.screenHas("NO shots count: the saved aim offset"), 1);
            SimRun.pause(4);
            assertFalse("nothing shoots", anyDriven(s));
            assertEquals("", s.error());
        }
    }
}
