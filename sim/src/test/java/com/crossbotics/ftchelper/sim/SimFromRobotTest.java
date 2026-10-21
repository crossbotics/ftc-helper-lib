package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.examples.flywheelbot.Tunables;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The simulator robot file made from Robot.java (./gradlew :sim:fromRobot), and the two example robots
 * (examples/flywheelbot, examples/turretbot) driven through it: their TeleOp and autonomous.
 */
public class SimFromRobotTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private Path generate(Class<?> robotClass) throws Exception {
        com.crossbotics.ftchelper.robot.RobotDefinition r =
                (com.crossbotics.ftchelper.robot.RobotDefinition) robotClass.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, r.simLines(), robotClass.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile(robotClass.getPackage().getName() + ".properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ----------------------------- merging -----------------------------

    @Test public void aNewFileLoadsAndMarksEveryGeneratedLine() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(text, text.contains("drive.type = tank   # from Robot.java"));
        assertTrue(text, text.contains("drive.motors = left_drive, right_drive   # from Robot.java"));
        assertTrue(text, text.contains("drive.mirrored = right   # from Robot.java"));
        assertTrue(text, text.contains("motor.launcher = flywheel, part=gobilda-5203-6000, feeder=indexer"));
        assertTrue(text, text.contains("servo.indexer = 0.15"));
        assertTrue(text, text.contains("servo.intake_deploy = 0.00"));
        SimRobot.load(file);   // the simulator reads it
    }

    @Test public void theTeamsLinesAreKeptAndOursRefreshed() {
        String existing = "battery = 12.8\n"
                + "shape.intake = roller, at=7,0,2, size=2x13x2, motor=intake\n"   // measured: no mark
                + "motor.intake = free, part=gobilda-5203-435   " + SimFromRobot.MARK + "\n"
                + "servo.old_gate = 0.5   " + SimFromRobot.MARK + "\n";              // gone from the code
        SimFromRobot.Result r = SimFromRobot.merge(existing, Arrays.asList(
                "motor.intake = free, part=gobilda-5203-1150   # TODO the real motor",
                "shape.intake = roller, at=8,0,2, size=2x14x2, motor=intake   # TODO measure",
                "servo.claw = 0.20, part=gobilda-2000-torque"), "Robot", "biobuzz-2026");
        assertTrue(r.problems.isEmpty());
        assertTrue(r.text, r.text.contains("shape.intake = roller, at=7,0,2, size=2x13x2, motor=intake\n"));
        assertFalse(r.text, r.text.contains("at=8,0,2"));
        assertTrue(r.text, r.text.contains("motor.intake = free, part=gobilda-5203-1150   # from Robot.java. TODO the real motor"));
        assertFalse(r.text, r.text.contains("old_gate"));
        assertTrue(r.text, r.text.contains("servo.claw = 0.20, part=gobilda-2000-torque   # from Robot.java"));
        assertEquals(Collections.singletonList("shape.intake"), r.kept);
    }

    @Test public void teamLinesWrittenWithAColonOrASpaceAreTheTeams() {
        String existing = "drive.wheelDiameterIn: 4.0\ndrive.gearRatio 2\n";
        SimFromRobot.Result r = SimFromRobot.merge(existing, Arrays.asList(
                "drive.wheelDiameterIn = 3.780", "drive.gearRatio = 1"), "Robot", "x");
        assertEquals("drive.wheelDiameterIn: 4.0\ndrive.gearRatio 2\n", r.text);
        assertEquals(Arrays.asList("drive.wheelDiameterIn", "drive.gearRatio"), r.kept);
    }

    @Test public void aDeviceOfAnotherKindOrOtherDriveMotorsStop() {
        String existing = "motor.claw = free\ndrive.motors = fl, fr, bl, br\n";
        SimFromRobot.Result r = SimFromRobot.merge(existing, Arrays.asList(
                "servo.claw = 0.20", "drive.motors = front_left, front_right, back_left, back_right"), "Robot", "x");
        assertEquals(r.problems.toString(), 2, r.problems.size());
        assertTrue(r.problems.get(0), r.problems.get(0).contains("uses \"claw\" as a servo, the robot file has motor.claw"));
        assertTrue(r.problems.get(1), r.problems.get(1).startsWith("drive.motors: Robot.java has front_left"));
    }

    // ----------------------------- example robot 1 in the simulator -----------------------------

    @Test public void flywheelBotTeleOpDeploysRunsTheIntakeAndFires() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp");
            assertTrue("the help page shows in INIT", s.screenHas("G2 RT: in on/off [Intake]"));
            assertEquals("nothing moves in INIT", 0, s.robot().motors.get("intake").userPower(), 1e-9);
            // The shapes from Robot.java: folded, it fits the start cube; at full reach, 18 x 24 x 29 in.
            assertTrue("fits the start cube: " + s.robot().shapes.now(), s.robot().shapes.now().fits(18, 18, 18));
            assertTrue("fits at full reach: " + s.robot().shapes.mostPose, s.robot().shapes.mostOk);
            SimShapes.Shape arm = s.robot().shapes.shapes.get("intake_arm");
            assertEquals("the intake starts folded up", 90, arm.value, 1e-9);
            s.start();
            SimRobot.SimServo deploy = s.robot().servos.get("intake_deploy");
            assertTrue("the deploy servo pushes at START", SimRun.waitFor(() -> deploy.actual > 0.5, 2));
            assertTrue("the intake falls to its stop", SimRun.waitFor(() -> arm.value == -15, 2));
            assertTrue("the servo comes back", SimRun.waitFor(() -> deploy.actual < 0.1, 3));
            assertEquals("and the intake stays down", -15, arm.value, 1e-9);
            assertTrue("the intake runs from START", s.robot().motors.get("intake").userPower() > 0.5);
            s.press(v -> s.g2().right_trigger = v ? 1 : 0);           // intake off
            assertTrue(SimRun.waitFor(() -> s.robot().motors.get("intake").userPower() == 0, 1));
            s.press(v -> s.g2().left_bumper = v);                     // spin up
            SimMotor fly = s.robot().motors.get("launcher");
            boolean spun = SimRun.waitFor(() -> fly.velocity() > 1000, 4);
            assertTrue("the flywheel spins up: " + fly.velocity() + " " + fly.userPower() + "\n" + String.join("\n", s.screen()), spun);
            int before = s.robot().shots();
            s.press(v -> s.g2().right_bumper = v);                    // fire one
            assertTrue("a shot goes", SimRun.waitFor(() -> s.robot().shots() > before, 4));
            assertEquals("", s.error());
        }
    }

    @Test public void flywheelBotAutoShootsAndLeaves() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        boolean was = Tunables.Drive.calibrated;
        Tunables.Drive.calibrated = true;   // as if the Drive Tuner was done
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot Shoot and Leave");
            assertTrue(s.screenHas("Back wall, touching the goal side"));
            double y0 = s.robot().y;
            s.start();
            assertTrue("it finishes", SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 25));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Fire three: done"));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Leave: done"));
            assertTrue("it moved", Math.abs(s.robot().y - y0) > 12);
            assertTrue("it shot\n" + String.join("\n", s.screen()), s.robot().shots() >= 1);
            assertEquals("", s.error());
        } finally {
            Tunables.Drive.calibrated = was;
        }
    }

    // ----------------------------- example robot 2 in the simulator -----------------------------

    @Test public void turretBotRunsAndTheClawActionWorks() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("TurretBot TeleOp");
            assertTrue(s.screenHas("G2 X: grab"));
            assertTrue(s.screenHas("G2 BACK: follow the goal on/off [Turret]"));
            s.start();
            s.press(v -> s.g2().x = v);                               // grab
            SimRobot.SimServo fold = s.robot().servos.get("claw_fold");
            assertTrue("folds out to grab", SimRun.waitFor(() -> Math.abs(fold.actual - 0.62) < 0.02, 2));
            assertTrue("and back in", SimRun.waitFor(() -> Math.abs(fold.actual - 0.05) < 0.02, 3));
            assertTrue("the claw closed on the way", s.robot().servos.get("claw").actual > 0.7);
            s.g2().right_stick_x = 1;                                 // turret by hand
            assertTrue(SimRun.waitFor(() -> Math.abs(s.robot().motors.get("turret").userPower()) > 0.1, 1));
            s.g2().right_stick_x = 0;
            assertEquals("", s.error());
        }
    }

    // ----------------------------- the goBILDA starter bot, rewritten -----------------------------

    @Test public void goBildaBotMatchesTheMeasuredRobotFile() throws Exception {
        String existing = new String(Files.readAllBytes(java.nio.file.Paths.get("robots/starter-bot-gobilda.properties")),
                StandardCharsets.UTF_8);
        SimFromRobot.Result r = SimFromRobot.merge(existing,
                new com.crossbotics.ftchelper.examples.gobildabot.Robot().simLines(), "Robot", "biobuzz-2026");
        assertTrue("Robot.java and the CAD-made robot file agree: " + r.problems, r.problems.isEmpty());
        assertTrue("the measured shapes are kept: " + r.kept, r.kept.contains("shape.intake") && r.kept.contains("motor.launcher"));
    }

    @Test public void goBildaBotRunsOnItsRobotFile() throws Exception {
        boolean was = com.crossbotics.ftchelper.examples.gobildabot.Tunables.Drive.calibrated;
        com.crossbotics.ftchelper.examples.gobildabot.Tunables.Drive.calibrated = true;
        try (SimRun s = new SimRun("robots/starter-bot-gobilda.properties")) {
            s.init("goBILDA Bot TeleOp");
            assertTrue(String.join("\n", s.screen()), s.screenHas("G1 RT: in (hold) [Intake]"));
            s.start();
            s.press(v -> s.g1().left_bumper = v);                     // spin up
            int before = s.robot().shots();
            s.press(v -> s.g1().right_bumper = v);                    // fire one
            assertTrue("a shot goes\n" + String.join("\n", s.screen()), SimRun.waitFor(() -> s.robot().shots() > before, 5));
            assertEquals("", s.error());
            s.sim.ds.stop();

            s.init("goBILDA Bot Launch and Back Up");
            int shotsBefore = s.robot().shots();
            s.start();
            assertTrue("it finishes", SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 25));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Fire all: done"));
            assertTrue("it shot", s.robot().shots() > shotsBefore);
            // The robot file starts it against the wall, so backing up pushes into the wall: the
            // move is tried (and reported), it cannot go far.
            assertTrue(String.join("\n", s.screen()), s.screenHas("Back up:"));
            assertEquals("", s.error());
        } finally {
            com.crossbotics.ftchelper.examples.gobildabot.Tunables.Drive.calibrated = was;
        }
    }

    // The guide's example (docs/guides/autonomous-builder.md): every builder feature runs, and the menu
    // choice picks the branch.
    @Test public void theAutoCookbookRunsBothBranches() throws Exception {
        try (SimRun s = new SimRun("robots/starter-bot-gobilda.properties")) {
            s.init("goBILDA Bot Auto Cookbook");
            assertTrue(String.join("\n", s.screen()), s.screenHas("Then"));
            s.start();
            assertTrue("it finishes", SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 30));
            String report = String.join("\n", s.screen());
            assertTrue(report, s.screenHas("Spin up the flywheel:"));
            assertTrue(report, s.screenHas("Fire all:"));
            assertTrue(report, s.screenHas("Clear the intake: done"));
            assertTrue(report, s.screenHas("Leave the launch zone:"));
            assertFalse("the other branch did not run\n" + report, s.screenHas("To the pieces"));
            assertEquals("", s.error());
            s.sim.ds.stop();

            // "Then" is the third line of the menu (after alliance and start delay): down twice, right once.
            s.init("goBILDA Bot Auto Cookbook");
            s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().dpad_right = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Collect more"), 1));
            s.start();
            assertTrue("it finishes", SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 30));
            report = String.join("\n", s.screen());
            assertTrue(report, s.screenHas("To the pieces:"));
            assertFalse(report, s.screenHas("Leave the launch zone"));
            assertEquals("", s.error());
        }
    }

    // The main-track lessons' robot (docs/tutorials/start/): its actions and its autonomous run.
    @Test public void theLessonBotScoresAndParks() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.lessonbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Lesson Bot TeleOp");
            assertTrue(String.join("\n", s.screen()), s.screenHas("G2 RB: score high"));
            assertTrue(String.join("\n", s.screen()), s.screenHas("G2 LB: tap: grab and lift; held 1 s: lift all the way down"));
            assertTrue(String.join("\n", s.screen()), s.screenHas("G2 B: claw (buzz: 2 on, 1 off)"));
            s.start();
            s.press(v -> s.g2().right_bumper = v);                    // score high
            assertTrue("the lift rises\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.robot().motors.get("lift").shaft() > 1500, 5));
            assertEquals("", s.error());
            // Feedback: B toggles the claw and buzzes gamepad 2 (the page shakes its picture).
            s.press(v -> s.g2().b = v);
            assertTrue("gamepad 2 buzzes", SimRun.waitFor(() -> s.sim.ds.pad2.rumbleLeft() > 0, 1));
            // The page gets the pattern, so two buzzes play as two (not one long buzz).
            String buzz = s.sim.ds.pad2.rumbleJson();
            assertTrue(buzz, buzz.contains("[120,1],[80,0],[120,1]") || buzz.equals("{\"seq\":1,\"steps\":[[120,1]]}"));
            s.sim.ds.stop();

            s.init("Lesson Bot Score and Park");
            s.start();
            assertTrue("it finishes", SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 30));
            String report = String.join("\n", s.screen());
            assertTrue(report, s.screenHas("Score high:"));
            assertTrue(report, s.screenHas("Park:"));
            assertEquals("", s.error());
        }
    }

    // The finders' hookup for the robot in five files (lesson 6): robot() { return new Robot(); }, and
    // lines for Robot.java and Tunables.java.
    @Test public void theLessonBotDriveTunerShowsFiveFilesLines() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.lessonbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Lesson Bot Drive Tuner").start();
            s.press(v -> s.g1().a = v);                               // the Push test
            assertTrue(s.screenHas("BY HAND"));
            s.robot().push(24, 0, 2.0);                               // roll it 48 in forward, by hand
            assertTrue(SimRun.waitFor(() -> !s.robot().pushing(), 10));
            SimRun.pause(0.2);
            s.press(v -> s.g1().b = v);                               // done
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Robot.java: .wheelMm("), 1));
            assertTrue(s.screenHas("c.wheelDiameterIn = "));          // and the RobotSettings line
            assertEquals("", s.error());
        }
    }

    // The Shot Tuner with robot(): the robot's launcher and camera, and the "turret aimed" check is off
    // (the turret stays still), so a shot goes.
    @Test public void theTurretBotShotTunerShootsWithTheRobotsLauncher() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("TurretBot Shot Tuner");
            assertTrue(String.join("\n", s.screen()), s.screenHas("Camera"));
            s.start();
            s.press(v -> s.g1().left_bumper = v);                     // flywheel on
            int before = s.robot().shots();
            SimRun.pause(1.5);
            s.press(v -> s.g1().right_bumper = v);                    // shoot one
            assertTrue("a shot goes\n" + String.join("\n", s.screen()), SimRun.waitFor(() -> s.robot().shots() > before, 5));
            s.press(v -> s.g1().a = v);                               // try to save a point
            // The distance comes from the robot's camera (no goal in view from the start position;
            // without a camera it would be a distance set by hand). The table line: FindersTest.
            assertTrue(String.join("\n", s.screen()), s.screenHas("No distance: the camera does not see the target."));
            assertEquals("", s.error());
        }
    }

    // The quick start's template (docs/quick-start.md): it drives, its action runs, its autonomous ends.
    @Test public void theQuickStartTemplateDrivesAndRuns() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.quickstart.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Quick Start TeleOp");
            assertTrue(String.join("\n", s.screen()), s.screenHas("G2 A: spit out"));
            s.start();
            double y0 = s.robot().y;
            s.g1().left_stick_y = -1;                                 // stick up: forward
            SimRun.pause(1.0);
            s.g1().left_stick_y = 0;
            assertTrue("it drove forward: " + (s.robot().y - y0), s.robot().y - y0 > 3);
            s.press(v -> s.g2().a = v);                               // spit out
            assertTrue("the intake pushes out", SimRun.waitFor(() -> s.robot().motors.get("intake").appliedPower < -0.1, 1));
            assertEquals("", s.error());
            s.sim.ds.stop();

            s.init("Quick Start Auto");
            s.start();
            assertTrue("it finishes", SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 30));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Leave the start:"));
            assertEquals("", s.error());
        }
    }

    @Test public void guideShowsTheButtonMapDuringTheMatch() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp").start();
            assertFalse(s.screenHas("Buttons (GUIDE: close)"));
            s.press(v -> s.g1().guide = v);                      // a tap
            assertTrue(SimRun.waitFor(() -> s.screenHas("Buttons (GUIDE: close)"), 1));
            assertTrue(s.screenHas("G2 RB: fire one; hold: keep firing [Launcher]"));
            s.press(v -> s.g1().guide = v);                      // tap again: closed
            assertTrue(SimRun.waitFor(() -> !s.screenHas("Buttons (GUIDE: close)"), 1));
            assertEquals("", s.error());
        }
    }

    @Test public void anUntunedAutoRunsInTheSimulatorButNotOnARobot() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot Shoot and Leave");
            assertTrue(String.join("\n", s.screen()), s.screenHas("Drive not tuned: runs in the simulator only"));
            s.sim.ds.stop();
            // As on a real robot (no simulator mark): a clear message, no error, nothing moves.
            System.clearProperty(com.crossbotics.ftchelper.auto.EasyAuto.SIMULATOR_PROPERTY);
            try {
                s.init("FlywheelBot Shoot and Leave");
                assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("AUTONOMOUS IS OFF"), 2));
                double y0 = s.robot().y;
                s.start();
                SimRun.pause(1.0);
                assertEquals("it does not move", y0, s.robot().y, 0.01);
                assertEquals("", s.error());
            } finally {
                System.setProperty(com.crossbotics.ftchelper.auto.EasyAuto.SIMULATOR_PROPERTY, "true");
            }
        }
    }

    @Test public void autoEndsWhenDoneAndTeleOpKnowsTheAlliance() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot Shoot and Leave");
            s.press(v -> s.g1().b = v);                          // the INIT menu: B = RED
            s.start();
            assertTrue("it ends by itself, with time left",
                    SimRun.waitFor(() -> s.sim.ds.phase() == SimDriverStation.Phase.STOPPED, 25));
            assertTrue(s.sim.ds.runSeconds() < 29);
            assertEquals("the Driver Station moves to its TeleOp", "FlywheelBot TeleOp", s.sim.ds.next);
            s.init("FlywheelBot TeleOp");
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("(from autonomous"), 2));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Leave: done"));
            assertEquals("", s.error());
        }
    }

    @Test public void withoutAutoTheAllianceIsChosenInInit() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp");
            assertTrue(SimRun.waitFor(() -> s.screenHas("Choose the alliance: gamepad 1 X = BLUE, B = RED"), 2));
            s.press(v -> s.g1().b = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("RED (chosen here)"), 2));
            assertEquals("", s.error());
        }
    }

    @Test public void theAutosStartSpotPlacesTheRobotForEachAlliance() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot Shoot and Leave");                 // the INIT menu starts on BLUE
            assertTrue("blue: the spot from the blue wall", SimRun.waitFor(() -> Math.abs(s.robot().x - 56) < 0.01
                    && Math.abs(s.robot().y - 61.55) < 0.01 && Math.abs(Math.abs(s.robot().headingDeg) - 180) < 0.01, 2));
            s.press(v -> s.g1().b = v);                          // RED: the same spot from the red wall
            assertTrue("red: turned half a turn", SimRun.waitFor(() -> Math.abs(s.robot().x + 56) < 0.01
                    && Math.abs(s.robot().y + 61.55) < 0.01 && Math.abs(s.robot().headingDeg) < 0.01, 2));
            assertEquals("", s.error());
        }
    }

    @Test public void teleOpWithoutAutoStartsOnItsAlliancesSide() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp");                          // no autonomous: nothing chosen yet
            SimRun.pause(0.5);
            assertEquals("it stays at the robot file's start until the alliance is chosen", -61.55, s.robot().y, 0.01);
            s.press(v -> s.g1().x = v);                          // BLUE: the same spot from the blue wall
            assertTrue(SimRun.waitFor(() -> Math.abs(s.robot().y - 61.55) < 0.01 && Math.abs(s.robot().x - 56) < 0.01, 2));
            s.press(v -> s.g1().b = v);                          // RED: the robot file's start
            assertTrue(SimRun.waitFor(() -> Math.abs(s.robot().y + 61.55) < 0.01, 2));
            s.sim.robot.setPose(-56, -45, 0);                     // dragged on the map
            SimRun.pause(0.3);
            assertEquals("a dragged robot stays", -45, s.robot().y, 0.01);
            assertEquals("", s.error());
        }
    }

    @Test public void backPausesAndGoesOn() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp").start();
            SimMotor intake = s.robot().motors.get("intake");
            assertTrue("the intake runs from START", SimRun.waitFor(() -> intake.userPower() > 0.5, 3));
            s.press(v -> s.g1().back = v);                       // pause
            assertTrue(SimRun.waitFor(() -> s.screenHas("PAUSED"), 1));
            assertEquals(0, intake.userPower(), 1e-9);
            s.press(v -> s.g1().back = v);                       // go on
            assertTrue(SimRun.waitFor(() -> intake.userPower() > 0.5, 1));
            assertFalse(s.screenHas("PAUSED"));
            s.g1().back = true;                                  // held 1.5 s: resets forward, no pause
            SimRun.pause(1.5);
            s.g1().back = false;
            SimRun.pause(0.3);
            assertFalse(s.screenHas("PAUSED"));
            assertEquals("", s.error());
        }
    }
}
