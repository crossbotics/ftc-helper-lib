package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.finders.TuneAndCheckOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Every tuner and check is safe for a student alone: what moves is said before START, a held stick or
 * button starts nothing, and every motion can be stopped. And no failed test leaves a line to copy.
 */
public class TunerSafetyTest {
    static {
        CookbookSamples.fill();
    }

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @After public void findRobotsAgain() {
        TuneAndCheckOpMode.useRobots(null);
    }

    private static String screen(SimRun s) {
        return String.join("\n", s.screen());
    }

    private Path generate(Class<?> robotClass) throws Exception {
        RobotDefinition r = (RobotDefinition) robotClass.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, SimFromRobot.holdsFromPieces(r.simLines(), r),
                robotClass.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile().toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static void openFromMenu(SimRun s, Class<? extends RobotDefinition> robot, String tool) throws Exception {
        TuneAndCheckOpMode.useRobots(robot == null ? Collections.<Class<? extends RobotDefinition>>emptyList()
                : Collections.<Class<? extends RobotDefinition>>singletonList(robot));
        s.init(TuneAndCheckOpMode.NAME);
        for (int i = 0; i < 20 && !s.screenHas("> " + tool); i++) s.press(v -> s.g1().dpad_down = v);
        assertTrue(screen(s), s.screenHas("> " + tool));
        s.press(v -> s.g1().a = v);
        assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("STOP (■) on the Driver Hub"), 2));
    }

    private static void nothingPowered(SimRun s, String why) {
        for (SimMotor m : s.robot().motors.values()) assertEquals(why + ": " + m.name, 0, m.userPower(), 1e-9);
    }

    // ---------------------------------------------------------------- safety

    // Bug 9: a held stick moved a device at START, the next device after a change, and after help.
    @Test public void hardwareCheckWaitsForTheStickAtStartOnANewDeviceAndAfterHelp() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Hardware Check");
            s.g1().right_stick_y = -1;
            s.start();
            SimRun.pause(0.4);
            nothingPowered(s, "held through START");
            assertTrue(screen(s), s.screenHas("Let go of all sticks and buttons"));
            s.g1().right_stick_y = 0;
            SimRun.pause(0.2);
            s.g1().right_stick_y = -1;
            assertTrue("a fresh push moves it", SimRun.waitFor(() -> anyPowered(s), 1));
            s.press(v -> s.g1().dpad_down = v);          // the next device, the stick still held
            SimRun.pause(0.3);
            nothingPowered(s, "held while the device changed");
            s.g1().right_stick_y = 0;
            SimRun.pause(0.2);
            s.g1().right_stick_y = -1;
            assertTrue("a fresh push moves the new one", SimRun.waitFor(() -> anyPowered(s), 1));
            s.press(v -> s.g1().guide = v);              // help
            nothingPowered(s, "help stops it");
            s.press(v -> s.g1().guide = v);              // help closes, still held
            SimRun.pause(0.3);
            nothingPowered(s, "held while help closed");
            assertEquals("", s.error());
        }
    }

    private static boolean anyPowered(SimRun s) {
        for (SimMotor m : s.robot().motors.values()) if (m.userPower() != 0) return true;
        return false;
    }

    @Test public void hardwareCheckIgnoresAHeldAOnAServo() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Hardware Check").start();
            s.g1().a = true;                             // held while going down the list to a servo
            SimRun.pause(0.2);
            String chosen = null;
            for (int i = 0; i < 40 && chosen == null; i++) {
                for (String l : s.screen()) if (l.startsWith("> ") && l.contains("(servo)")) chosen = l;
                if (chosen == null) s.press(v -> s.g1().dpad_down = v);
            }
            assertTrue(screen(s), chosen != null);
            SimRobot.SimServo servo = s.robot().servos.get(chosen.substring(2, chosen.indexOf("  (")));
            assertTrue("a held A sends nothing", Double.isNaN(servo.commanded()));
            s.g1().a = false;
            SimRun.pause(0.2);
            s.press(v -> s.g1().a = v);
            assertEquals("a fresh A moves it", 0.5, servo.commanded(), 1e-9);
            assertEquals("", s.error());
        }
    }

    // Bug 3: the Odometry Tuner drove from a stick held through START.
    @Test public void odometryTunerWaitsForTheSticksAtStartAfterHelpAndAfterATest() throws Exception {
        try (SimRun s = new SimRun(generate(com.crossbotics.ftchelper.examples.odometrybot.Robot.class).toString())) {
            s.init("Odometry Bot Odometry Tuner");
            assertTrue(screen(s), s.screenHas("STICKS drive"));
            s.g1().left_stick_y = -1;
            s.start();
            SimRun.pause(0.5);
            nothingPowered(s, "held through START");
            s.g1().left_stick_y = 0;
            SimRun.pause(0.2);
            s.press(v -> s.g1().a = v);                  // start test 1
            s.g1().left_stick_y = -1;
            assertTrue("a fresh push drives", SimRun.waitFor(() -> anyPowered(s), 1));
            s.press(v -> s.g1().b = v);                  // the test ends, the stick still held
            SimRun.pause(0.3);
            nothingPowered(s, "held at the end of a test");
            s.press(v -> s.g1().guide = v);
            s.press(v -> s.g1().guide = v);
            SimRun.pause(0.3);
            nothingPowered(s, "held while help closed");
            assertEquals("", s.error());
        }
    }

    @Test public void motorFinderWaitsWhenTheMotorChangesAndWhenTheSecondEndIsMarked() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Motor Finder").start();
            s.press(v -> s.g1().dpad_up = v);            // "lift"
            s.g1().right_stick_y = -1;                   // held while it is chosen
            s.press(v -> s.g1().a = v);
            SimRun.pause(0.3);
            SimMotor lift = s.robot().motors.get("lift");
            assertEquals("held while choosing", 0, lift.userPower(), 1e-9);
            s.g1().right_stick_y = 0;
            SimRun.pause(0.2);
            s.press(v -> s.g1().x = v);                  // LOW end
            s.g1().right_stick_y = -1;
            assertTrue(SimRun.waitFor(() -> lift.encoder() > 300, 3));
            s.g1().right_stick_y = 0;
            s.g1().right_trigger = 1;                    // RT held while the HIGH end is marked
            SimRun.pause(0.2);
            s.press(v -> s.g1().b = v);
            SimRun.pause(0.3);
            assertEquals("full power needs a fresh pull", 0, lift.userPower(), 1e-9);
            assertEquals("", s.error());
        }
    }

    // Nothing moves before START, and every tool says what moves and how to stop first.
    @Test public void everyToolWarnsBeforeStartAndFitsTheScreen() throws Exception {
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>singletonList(
                com.crossbotics.ftchelper.examples.turretbot.Robot.class));
        List<String> problems = new ArrayList<>();
        try (SimRun s = new SimRun(generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class).toString())) {
            s.init(TuneAndCheckOpMode.NAME);
            List<String> tools = new ArrayList<>();
            for (String l : s.screen()) {
                if (l.matches("[> ] [A-Z].*(Check|Finder|Tuner)")) tools.add(l.substring(2));
            }
            assertTrue(screen(s), tools.size() >= 8);
            assertTrue("a launcher's hood has its finder\n" + screen(s), tools.contains("Hood Finder"));
            for (String tool : tools) {
                for (int i = 0; i < 20 && !s.screenHas("> " + tool); i++) s.press(v -> s.g1().dpad_down = v);
                s.press(v -> s.g1().a = v);
                assertTrue(tool + "\n" + screen(s), SimRun.waitFor(() -> s.screenHas("STOP (■) on the Driver Hub"), 3));
                List<String> lines = s.screen();
                if (!lines.get(0).startsWith("!! ")) problems.add(tool + ": no warning first");
                if (!s.screenHas("Help: GUIDE, or hold START 1 s.")) problems.add(tool + ": no help line");
                if (lines.size() > 18) problems.add(tool + ": " + lines.size() + " lines");
                for (String l : lines) {
                    if (l.length() > 60) problems.add(tool + ": too wide: " + l);
                }
                nothingPowered(s, tool + " in INIT");
                s.press(v -> s.g1().back = v);
                assertTrue(SimRun.waitFor(() -> s.screenHas("> " + tool), 2));
            }
            assertEquals("", s.error());
        }
        assertEquals("[]", problems.toString());
    }

    @Test public void theMovingToolsSayWhatMovesBeforeStart() throws Exception {
        String[][] say = {{"Motor Finder", "FULL power"}, {"Flywheel Finder", "FULL power"},
                {"Servo Finder", "JUMP"}, {"Hardware Check", "stick and A move parts"},
                {"Drive Tuner", "DRIVE BY ITSELF"}, {"Characterizer", "DRIVES and TURNS by itself"}};
        try (SimRun s = new SimRun()) {
            for (String[] tool : say) {
                s.init(tool[0]);
                assertTrue(tool[0] + "\n" + screen(s), s.screenHas(tool[1]));
                nothingPowered(s, tool[0]);
                s.sim.ds.stop();
                SimRun.waitFor(() -> s.sim.ds.phase() != SimDriverStation.Phase.INIT, 2);
            }
        }
    }

    // Every motion can be stopped: START ended the long moves at once, but not the Creep.
    @Test public void aTapOfStartStopsTheMoveTunersCreepAtOnce() throws Exception {
        boolean was = com.crossbotics.ftchelper.examples.turretbot.Tunables.Drive.calibrated;
        com.crossbotics.ftchelper.examples.turretbot.Tunables.Drive.calibrated = true;
        try (SimRun s = new SimRun()) {
            openFromMenu(s, com.crossbotics.ftchelper.examples.turretbot.Robot.class, "Move Tuner");
            s.start();
            for (int i = 0; i < 6 && !s.screenHas("> 1 Creep"); i++) s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().a = v);
            assertTrue(screen(s), SimRun.waitFor(() -> anyPowered(s), 2));
            s.press(v -> s.g1().start = v);              // a tap, not a 1 s hold
            assertTrue(screen(s), SimRun.waitFor(() -> !anyPowered(s), 0.5));
            SimRun.pause(0.6);
            nothingPowered(s, "it does not start again");
            assertEquals("", s.error());
        } finally {
            com.crossbotics.ftchelper.examples.turretbot.Tunables.Drive.calibrated = was;
        }
    }

    // A button held while a move ends must not confirm the distance that nobody measured.
    @Test public void aHeldThroughADriveTestDoesNotConfirmTheMeasurement() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Drive Tuner").start();
            s.press(v -> s.g1().dpad_down = v);          // 2 Drive forward
            s.press(v -> s.g1().a = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("The robot is moving"), 2));
            s.g1().a = true;
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("Now measure it"), 15));
            SimRun.pause(0.5);
            assertTrue("still asking for the distance\n" + screen(s), s.screenHas("Now measure it"));
            assertFalse(screen(s), s.screenHas("calibrationConfirmed = true"));
            assertEquals("", s.error());
        }
    }

    @Test public void shotTunerNeedsAFreshRtAfterTheFlywheelIsOn() throws Exception {
        try (SimRun s = new SimRun(generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class).toString())) {
            s.init("TurretBot Shot Tuner").start();
            SimRun.pause(0.2);
            int before = s.robot().shots();
            s.g1().right_trigger = 1;                    // pulled while the flywheel is off
            SimRun.pause(0.2);
            s.press(v -> s.g1().left_bumper = v);        // flywheel on
            SimRun.pause(3);
            assertEquals("RT held from before LB fires nothing\n" + screen(s), before, s.robot().shots());
            assertEquals("", s.error());
        }
    }

    /** An Aim Tuner with a turret on the starter robot's "intake" motor (a free motor). */
    public static class TurretAim extends com.crossbotics.ftchelper.finders.AimTunerOpMode {
        @Override protected com.crossbotics.ftchelper.vision.TargetSource createCamera() {
            return com.crossbotics.ftchelper.vision.WebcamTags.create(hardwareMap, "Webcam 1");
        }
        @Override protected com.crossbotics.ftchelper.launcher.Turret createTurret() {
            return com.crossbotics.ftchelper.launcher.Turret.create(hardwareMap, "intake")
                    .countsPerDegree(10).limits(-90, 90).markHomed();
        }
    }

    @Test public void aimTunerLettingGoOfAStopsTheTurret() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Turret Aim", "TeleOp", TurretAim.class);
            s.init("Turret Aim");
            assertTrue(screen(s), s.screenHas("TURRET turns by itself"));
            s.start();
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("Goal is "), 3));
            SimMotor turret = s.robot().motors.get("intake");
            s.g1().a = true;
            assertTrue("it aims\n" + screen(s), SimRun.waitFor(() -> turret.userPower() != 0, 2));
            s.g1().a = false;
            assertTrue("let go = stop\n" + screen(s), SimRun.waitFor(() -> turret.userPower() == 0, 0.3));
            SimRun.pause(0.3);
            assertEquals(0, turret.userPower(), 1e-9);
            assertEquals("", s.error());
        }
    }

    // ---------------------------------------------------------------- false or stale results

    // Bugs 1 and 16: the table line has only this run's rows and says what it replaces; it does not aim a turret.
    @Test public void shotTunerSaysWhatItsTableReplacesAndThatYouPointTheTurret() throws Exception {
        try (SimRun s = new SimRun(generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class).toString())) {
            s.init("TurretBot Shot Tuner");
            assertTrue(screen(s), s.screenHas("It does NOT turn the turret") && s.screenHas("Point the turret at the goal yourself"));
            s.start();
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("(camera, averaged)"), 4));
            s.press(v -> s.g1().x = v);
            s.press(v -> s.g1().x = v);                  // saved without shots (asked twice)
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("replaces 4 rows in your code with this 1"), 2));
            assertTrue(screen(s), s.screenHas("Old rows are NOT kept") && s.screenHas("lose rows: 4 in code, 1 here"));
            assertTrue(screen(s), s.screenHas("NOT DONE"));
            int rows = 0;
            boolean inTable = false;
            for (String l : s.screen()) {
                if (l.contains("public static double[][] speedByDistance")) inTable = true;
                else if (inTable && l.trim().startsWith("{")) rows += l.split("\\{", -1).length - 1;
                else inTable = false;
            }
            assertEquals("never mixed with the old rows\n" + screen(s), 1, rows);
            assertEquals("", s.error());
        }
    }

    /** Numbers for the Aim Tuner's test robot. */
    public static class AimNumbers {
        public static class Aim {
            public static double cameraOffset = -10;
        }
    }

    public static class AimRobot extends RobotDefinition {
        @Override protected void define() {
            tunables(AimNumbers.class);
            drive(com.crossbotics.ftchelper.robot.Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
        }
    }

    public static class CodeOffsetAim extends com.crossbotics.ftchelper.finders.AimTunerOpMode {
        @Override protected RobotDefinition robot() { return new AimRobot(); }
        @Override protected com.crossbotics.ftchelper.vision.TargetSource createCamera() {
            return com.crossbotics.ftchelper.vision.WebcamTags.create(hardwareMap, "Webcam 1");
        }
    }

    public static class HelperOffsetAim extends com.crossbotics.ftchelper.finders.AimTunerOpMode {
        @Override protected com.crossbotics.ftchelper.core.RobotConfig robotConfig() {
            return new com.crossbotics.ftchelper.examples.starter.RobotSettings();
        }
        @Override protected com.crossbotics.ftchelper.vision.AimController createAimController() {
            return super.createAimController().offset(-4);
        }
        @Override protected com.crossbotics.ftchelper.vision.TargetSource createCamera() {
            return com.crossbotics.ftchelper.vision.WebcamTags.create(hardwareMap, "Webcam 1");
        }
    }

    // Bug 2: it started at 0 over the offset the team had found.
    @Test public void aimTunerStartsFromTheCodesOffset() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Code Offset Aim", "TeleOp", CodeOffsetAim.class);
            s.init("Code Offset Aim");
            assertTrue(screen(s), s.screenHas("Starts from : -10.0 deg (Tunables.java)"));
            s.start();
            assertTrue(screen(s), s.screenHas("Aim offset : 10.0 deg to the LEFT"));
            s.press(v -> s.g1().dpad_right = v);
            assertTrue(screen(s), s.screenHas("find class Aim.") && s.screenHas("public static double cameraOffset = -9.5;"));
            assertEquals("", s.error());
        }
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Helper Offset Aim", "TeleOp", HelperOffsetAim.class);
            s.init("Helper Offset Aim");
            assertTrue(screen(s), s.screenHas("Starts from : -4.0 deg (your code)"));
            assertEquals("", s.error());
        }
    }

    /** A motor hood on the starter robot's "intake" motor. */
    public static class MotorHood extends com.crossbotics.ftchelper.finders.HoodFinderOpMode {
        @Override protected String hoodName() { return "intake"; }
    }

    // Bug 10: the zero was taken in INIT, before the student was told where to put the hood.
    @Test public void aMotorHoodsZeroIsTakenOnAConfirm() throws Exception {
        try (SimRun s = new SimRun()) {
            SimMotor hood = s.robot().motors.get("intake");
            s.init("Motor Finder").start();              // first turn that motor a little
            for (int i = 0; i < 10 && !s.screenHas("> intake"); i++) s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().a = v);
            s.g1().right_stick_y = -1;
            assertTrue(SimRun.waitFor(() -> hood.encoder() > 300, 3));
            s.g1().right_stick_y = 0;
            SimRun.pause(0.3);
            s.sim.ds.stop();
            SimRun.waitFor(() -> s.sim.ds.phase() != SimDriverStation.Phase.RUNNING, 2);
            s.sim.ds.register("Motor Hood", "TeleOp", MotorHood.class);
            s.init("Motor Hood").start();
            assertTrue("INIT and START do not take the zero\n" + screen(s), Math.abs(hood.encoder()) > 200);
            assertTrue(screen(s), s.screenHas("set its 0"));
            s.press(v -> s.g1().x = v);
            s.press(v -> s.g1().b = v);
            assertFalse("nothing is saved before the zero", s.screenHas("posA ="));
            s.press(v -> s.g1().a = v);
            assertTrue("A takes the zero\n" + screen(s), SimRun.waitFor(() -> Math.abs(hood.encoder()) < 100, 1));
            assertTrue(screen(s), s.screenHas("end A"));
            assertEquals("", s.error());
        }
    }

    // Bug 12: a failed Loop left an earlier "checked = true".
    @Test public void aFailedLoopRemovesAnEarlierChecked() throws Exception {
        try (SimRun s = new SimRun(generate(com.crossbotics.ftchelper.examples.odometrybot.Robot.class).toString())) {
            s.init("Odometry Bot Odometry Tuner").start();
            for (int i = 0; i < 3; i++) s.press(v -> s.g1().dpad_down = v);     // 4 Loop and back
            s.press(v -> s.g1().a = v);
            s.robot().push(20, 0, 1.5);                  // 30 in away...
            SimRun.waitFor(() -> !s.robot().pushing(), 5);
            SimRun.pause(0.2);
            s.robot().push(-20, 0, 1.5);                 // ...and back onto the mark
            SimRun.waitFor(() -> !s.robot().pushing(), 5);
            SimRun.pause(0.4);
            s.press(v -> s.g1().b = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("public static boolean checked = true;"), 2));
            s.press(v -> s.g1().a = v);
            assertFalse("a Loop that runs is not checked yet", s.screenHas("checked = true"));
            s.press(v -> s.g1().b = v);                  // it never left the mark
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("It went only"), 2));
            assertFalse(screen(s), s.screenHas("checked = true"));
            assertEquals("", s.error());
        }
    }

    // Bug 13: the line showed after one shot though five are asked.
    @Test public void feedTimeShowsNoLineBeforeFiveShots() throws Exception {
        try (SimRun s = new SimRun("robots/starter-bot-gobilda.properties")) {
            openFromMenu(s, com.crossbotics.ftchelper.examples.gobildabot.Robot.class, "Feed Time Finder");
            s.start();
            s.press(v -> s.g1().left_bumper = v);
            for (int shot = 1; shot <= 5; shot++) {
                s.robot().pieces.setPreloaded(false);    // load again: the robot holds fewer than five
                s.robot().pieces.setPreloaded(true);
                SimRun.pause(1.5);                       // the last piece's dip is over
                assertTrue(shot + "\n" + screen(s), SimRun.waitFor(() -> s.screenHas("RPM, ready"), 6));
                s.press(v -> s.g1().right_bumper = v);
                final String counted = shot < 5 ? "(" + shot + " of 5)" : "public static double feedSec =";
                assertTrue(shot + "\n" + screen(s), SimRun.waitFor(() -> s.screenHas(counted), 6));
                if (shot < 5) assertFalse(shot + " shots: no line yet", s.screenHas("public static double feedSec"));
            }
            assertTrue(screen(s), s.screenHas("DONE"));
            assertEquals("", s.error());
        }
    }

    // A wheel size that changed makes the strafe that was measured with the old one wrong.
    @Test public void aNewWheelSizeRemovesCalibrated() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Drive Tuner").start();
            for (int test = 1; test <= 2; test++) {
                final String line = "> " + (test + 1) + " ";
                for (int i = 0; i < 6 && !s.screenHas(line); i++) s.press(v -> s.g1().dpad_down = v);
                s.robot().setPose(0, -40, 0);
                s.press(v -> s.g1().a = v);
                assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("Now measure it"), 20));
                SimRun.pause(0.3);
                s.press(v -> s.g1().a = v);              // the student measured 48
            }
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("calibrationConfirmed = true"), 2));
            for (int i = 0; i < 6 && !s.screenHas("> 1 "); i++) s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().a = v);                  // Push: a new wheel size
            s.robot().push(24, 0, 2.0);
            SimRun.waitFor(() -> !s.robot().pushing(), 10);
            SimRun.pause(0.2);
            s.press(v -> s.g1().b = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("Wheel size is now"), 2));
            assertFalse(screen(s), s.screenHas("calibrationConfirmed = true"));
            assertEquals("", s.error());
        }
    }

    // One version only: in Tune and check, with Robot.java, the lines are for Tunables.java.
    @Test public void tuneAndCheckGivesARobotInFiveFilesOnlyTunablesLines() throws Exception {
        try (SimRun s = new SimRun()) {
            openFromMenu(s, com.crossbotics.ftchelper.examples.turretbot.Robot.class, "Servo Finder");
            s.start();
            for (int i = 0; i < 8 && !s.screenHas("> claw"); i++) s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().a = v);
            s.press(v -> s.g1().a = v);                  // go to 0.500
            s.press(v -> s.g1().a = v);                  // save P1
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("Open Tunables.java, find class Claw.")
                    && s.screenHas("public static double P1 = 0.500;"), 1));
            assertFalse(screen(s), s.screenHas("PositionServo.create") || s.screenHas(".position(\"P1\""));
            s.sim.ds.stop();
            assertTrue("STOP shows them too\n" + screen(s), SimRun.waitFor(() -> s.screenHas("STOPPED")
                    && s.screenHas("public static double P1 = 0.500;"), 2));
            assertEquals("", s.error());
        }
    }

    /** Numbers for a robot on bang-bang. */
    public static class BangNumbers {
        public static class Launcher {
            public static String speedControl = "BANG_BANG";
            public static double speedRpm = 2600;
        }
    }

    public static class BangRobot extends RobotDefinition {
        @Override protected void define() {
            tunables(BangNumbers.class);
            drive(com.crossbotics.ftchelper.robot.Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            add(com.crossbotics.ftchelper.robot.Launcher.flywheel("launcher", "gobilda-5203-6000"));
        }
    }

    // E2: a tool that tunes PID only says so to a robot on bang-bang, and only to that robot.
    @Test public void theFlywheelFinderSaysItTunesPidOnlyToABangBangRobot() throws Exception {
        try (SimRun s = new SimRun(generate(BangRobot.class).toString())) {
            openFromMenu(s, BangRobot.class, "Flywheel Finder");
            assertTrue(screen(s), s.screenHas("This tool tunes PID only.")
                    && s.screenHas("Your robot uses bang-bang: use the Flywheel Tuner."));
            s.start();
            for (int i = 0; i < 10 && !s.screenHas("> launcher"); i++) s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().a = v);
            assertTrue(screen(s), s.screenHas("This tool tunes PID only."));
            assertEquals("", s.error());
        }
        try (SimRun s = new SimRun(generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class).toString())) {
            openFromMenu(s, com.crossbotics.ftchelper.examples.turretbot.Robot.class, "Flywheel Finder");
            assertFalse(screen(s), s.screenHas("tunes PID only"));
            assertEquals("", s.error());
        }
    }

    // Help must not close the piece sensors: after it, a measurement still gets readings.
    @Test public void pieceTunerStillReadsAfterHelp() throws Exception {
        try (SimRun s = new SimRun(generate(com.crossbotics.ftchelper.examples.v2bot.Robot.class).toString())) {
            openFromMenu(s, com.crossbotics.ftchelper.examples.v2bot.Robot.class, "Piece Tuner");
            assertTrue(screen(s), s.screenHas("Nothing moves in this tool"));
            s.start();
            s.press(v -> s.g1().guide = v);
            s.press(v -> s.g1().guide = v);
            s.press(v -> s.g1().dpad_left = v);          // EMPTY
            s.press(v -> s.g1().a = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("Measured EMPTY"), 6));
            assertEquals("", s.error());
        }
    }
}
