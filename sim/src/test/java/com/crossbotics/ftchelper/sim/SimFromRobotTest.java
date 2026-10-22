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
            // autoPipelines: the lighting test runs in INIT and its line is on the Driver Station.
            assertTrue(SimRun.waitFor(() -> s.screenHas("limelight lighting"), 2));
            assertTrue(s.screenHas("G2 BACK: follow the goal on/off [Turret]"));
            s.start();
            SimLight light = s.robot().lights.get("light");          // the light: blue while following the goal
            assertTrue(light.words(), SimRun.waitFor(() -> light.words().equals("BLUE"), 2));
            s.press(v -> s.g2().x = v);                               // grab
            SimRobot.SimServo fold = s.robot().servos.get("claw_fold");
            assertTrue("folds out to grab", SimRun.waitFor(() -> Math.abs(fold.actual - 0.62) < 0.02, 2));
            assertTrue("and back in", SimRun.waitFor(() -> Math.abs(fold.actual - 0.05) < 0.02, 3));
            assertTrue("the claw closed on the way", s.robot().servos.get("claw").actual > 0.7);
            s.g2().right_stick_x = 1;                                 // turret by hand
            assertTrue(SimRun.waitFor(() -> Math.abs(s.robot().motors.get("turret").userPower()) > 0.1, 1));
            s.g2().right_stick_x = 0;
            // Two shot tables: it starts on POLLEN; D-pad right picks NECTAR.
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Shot table : POLLEN"), 1));
            s.press(v -> s.g2().dpad_right = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Shot table : NECTAR"), 1));
            assertEquals("", s.error());
        }
    }

    // Whole-robot aim (flywheelbot, no turret): G1 RB on, and the robot turns itself to face the goal
    // its webcam sees; the turn stick still wins.
    @Test public void flywheelBotTurnsItselfToFaceTheGoal() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp");
            s.press(v -> s.g1().b = v);                              // RED: it aims at RED SCORING
            assertTrue(SimRun.waitFor(() -> s.screenHas("RED (chosen here)"), 2));
            s.start();
            // Level with the RED SCORING tags, facing them, then turned 15 degrees left.
            s.sim.robot.setPose(32, -12.74, 105);
            SimRun.pause(0.5);
            assertEquals("nothing turns it before aim is on", 105, s.robot().headingDeg, 1.0);
            s.press(v -> s.g1().right_bumper = v);                   // aim on
            assertTrue("it turns to face the goal: " + s.robot().headingDeg + "\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> Math.abs(s.robot().headingDeg - 90) < 3, 6));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Aim : camera"));
            s.g1().right_stick_x = 1;                                // the driver turns: the driver wins
            assertTrue("the stick turns it away", SimRun.waitFor(() -> Math.abs(s.robot().headingDeg - 90) > 10, 3));
            s.g1().right_stick_x = 0;
            assertTrue("and the aim brings it back", SimRun.waitFor(() -> Math.abs(s.robot().headingDeg - 90) < 3, 6));
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

    // ----------------------------- the REV, AndyMark and Studica starter bots -----------------------------

    // Each kit bot's TeleOp and autonomous start and stop cleanly on its own robot file
    // (sim/robots/starter-bot-*.properties), and nothing is left running after STOP.
    private void kitBotRuns(String robotFile, java.lang.reflect.Field calibrated, String teleOp, String auto)
            throws Exception {
        boolean was = calibrated.getBoolean(null);
        calibrated.setBoolean(null, true);
        try (SimRun s = new SimRun(robotFile)) {
            s.init(teleOp);
            s.start();
            s.press(v -> s.g1().right_bumper = v);                    // fire / throw one
            SimRun.pause(1.0);
            assertEquals(teleOp, "", s.error());
            s.sim.ds.stop();
            for (SimMotor m : s.robot().motors.values()) {
                assertEquals(teleOp + ": motor " + m.name + " is off after STOP", 0, m.userPower(), 1e-9);
            }

            s.init(auto);
            s.start();
            assertTrue(auto + " finishes\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 30));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Leave:"));
            assertEquals(auto, "", s.error());
            s.sim.ds.stop();
            for (SimMotor m : s.robot().motors.values()) {
                assertEquals(auto + ": motor " + m.name + " is off after STOP", 0, m.userPower(), 1e-9);
            }
        } finally {
            calibrated.setBoolean(null, was);
        }
    }

    @Test public void revBotRunsOnItsRobotFile() throws Exception {
        kitBotRuns("robots/starter-bot-rev.properties",
                com.crossbotics.ftchelper.examples.revbot.Tunables.Drive.class.getField("calibrated"),
                "REV Bot TeleOp", "REV Bot Shoot and Leave");
    }

    @Test public void andyMarkBotRunsOnItsRobotFile() throws Exception {
        kitBotRuns("robots/starter-bot-andymark.properties",
                com.crossbotics.ftchelper.examples.andymarkbot.Tunables.Drive.class.getField("calibrated"),
                "AndyMark Bot TeleOp", "AndyMark Bot Throw and Leave");
    }

    @Test public void studicaBotRunsOnItsRobotFile() throws Exception {
        kitBotRuns("robots/starter-bot-studica.properties",
                com.crossbotics.ftchelper.examples.studicabot.Tunables.Drive.class.getField("calibrated"),
                "Studica Bot TeleOp", "Studica Bot Shoot and Leave");
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

    // Lesson 7: odometry. The simulator's pretend Pinpoint follows the robot; driveTo ends at the field
    // points (the start spot is the drivers' frame: x right, y away, for BLUE).
    @Test public void theOdometryBotDrivesToFieldPoints() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.odometrybot.Robot.class);
        String text = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text, text.contains("odometry = pinpoint pinpoint"));
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Odometry Bot To the Goal");
            // Where the robot really starts (the bottom wall: the numbers as written; the top: turned half a turn).
            double startX = s.robot().x, startY = s.robot().y;
            boolean topWall = startY > 0;
            s.start();
            assertTrue("it finishes\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 30));
            String report = String.join("\n", s.screen());
            assertTrue(report, s.screenHas("To the goal: done"));
            assertTrue(report, s.screenHas("Park: done"));
            assertEquals("", s.error());
            // The robot really is at the park point: (-56, -36) seen from its drivers' wall, the start
            // spot (-56, -61); so 25 in further away from that wall, the same distance from the side.
            double sign = topWall ? -1 : 1;
            assertEquals("start " + startX + ", " + startY, startX, s.robot().x, 2.0);
            assertEquals("start " + startX + ", " + startY, startY + sign * 25, s.robot().y, 2.5);
        }
    }

    // The pedro module: Pedro Pathing follows a line and a curve on the simulated robot, in the same
    // field numbers as driveTo. Pedro's Quickstart sample numbers (PedroConstants), not tuned for it.
    @Test public void thePedroBotFollowsPaths() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.pedrobot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Pedro Bot Curve to the Goal");
            double startX = s.robot().x, startY = s.robot().y;
            boolean topWall = startY > 0;
            assertEquals("the intake waits", 0, s.robot().motors.get("intake").userPower(), 1e-9);
            s.start();
            // PathAt.fraction(0.5, "Intake on", ...): the intake starts halfway along the path, while it drives.
            assertTrue("the intake starts on the way\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.robot().motors.get("intake").userPower() > 0, 15));
            com.crossbotics.ftchelper.pedro.PedroFollower f = com.crossbotics.ftchelper.pedro.PedroFollower.lastStarted();
            double left = f.remainingIn(), total = f.totalIn();
            assertTrue("still on the path", f.isBusy());
            // Half of the path was left when it fired; the test looks a loop or two later.
            assertTrue("left " + left + " of " + total, left <= 0.5 * total + 0.5 && left >= 0.3 * total);
            double fromStart = Math.hypot(s.robot().x - startX, s.robot().y - startY);
            assertTrue("moved " + fromStart + " in", fromStart > 0.3 * total);
            assertTrue("it finishes\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 30));
            String report = String.join("\n", s.screen());
            assertTrue(report, s.screenHas("To the goal: done"));
            assertTrue(report, s.screenHas("Park: done"));
            assertEquals("", s.error());
            // At the park point (-56, -36): 25 in further from the wall than the start (-56, -61).
            // Within Pedro's own "done" distance (2.5 in) plus a little.
            double sign = topWall ? -1 : 1;
            assertEquals("start " + startX + ", " + startY, startX, s.robot().x, 3.0);
            assertEquals("start " + startX + ", " + startY, startY + sign * 25, s.robot().y, 3.0);
        }
    }

    // Lesson 7: the Odometry Tuner's spin test finds the Pinpoint's offsets (the robot file has -84, -168).
    @Test public void theOdometryTunerFindsThePinpointOffsets() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.odometrybot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Odometry Bot Odometry Tuner").start();
            s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().dpad_down = v);                     // "Spin 3+ turns"
            s.press(v -> s.g1().a = v);
            s.g1().right_stick_x = 1;                                // turn in place (30%)
            assertTrue("it turns 3 times\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> Math.abs(s.robot().odometry.turnTotalDeg()) > 3.2 * 360, 40));
            s.g1().right_stick_x = 0;
            SimRun.pause(0.3);
            s.press(v -> s.g1().b = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Offsets measured"), 2));
            String line = "";
            for (String l : s.screen()) if (l.contains("Offsets measured")) line = l;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("X pod (-?[0-9.]+) mm left, Y pod (-?[0-9.]+) mm forward").matcher(line);
            assertTrue(line, m.find());
            // Within 6 mm: the robot drifts a little while it spins, as a real one does.
            assertEquals(-84, Double.parseDouble(m.group(1)), 6);
            assertEquals(-168, Double.parseDouble(m.group(2)), 6);
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
            s.press(v -> s.g1().a = v);                               // A: that shot was a HIT
            assertTrue(String.join("\n", s.screen()), s.screenHas("in at this speed."));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Points"));
            s.press(v -> s.g1().b = v);                               // B with no shot left to mark
            assertTrue(String.join("\n", s.screen()), s.screenHas("No shot to mark"));
            s.press(v -> s.g1().x = v);                               // X: try to save a point
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
            assertTrue(String.join("\n", s.screen()), s.screenHas("G2 D-pad left: spit out"));
            s.start();
            double y0 = s.robot().y;
            s.g1().left_stick_y = -1;                                 // stick up: forward
            SimRun.pause(1.0);
            s.g1().left_stick_y = 0;
            assertTrue("it drove forward: " + (s.robot().y - y0), s.robot().y - y0 > 3);
            s.press(v -> s.g2().dpad_left = v);                       // spit out
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

    // TeleOp without autonomous: the drivers park on the reset spot and hold G1 START 1 s ("set pose
    // here"); then they find the alliance was wrong and hold BACK + START 1.5 s to switch it.
    @Test public void teleOpSetsThePoseHereAndSwitchesTheAlliance() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.odometrybot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Odometry Bot TeleOp");
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("hold G1 START 1 s at x -56, y -61 in, heading 0 (BLUE)"), 2));
            s.start();
            // A short press does nothing.
            s.press(v -> s.g1().start = v);
            assertFalse(s.screenHas("Pose set"));
            s.g1().start = true;
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Pose set: x -56, y -61 in, heading 0 (BLUE)"), 3));
            s.g1().start = false;
            s.waitForLoops(2);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("x -56, y -61 in"), 2));
            // Both held 1.5 s: the alliance switches (a quick press of both would be the debug lines).
            s.g1().back = true;
            s.g1().start = true;
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Alliance now RED"), 3));
            s.g1().back = false;
            s.g1().start = false;
            s.waitForLoops(2);
            assertEquals(com.crossbotics.ftchelper.match.Alliance.RED, com.crossbotics.ftchelper.match.MatchMemory.chosen());
            assertTrue(String.join("\n", s.screen()), s.screenHas("RED (switched in TeleOp)"));
            assertFalse("not paused: the pair took BACK's tap", s.screenHas("PAUSED"));
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
