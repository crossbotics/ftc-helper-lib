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
    static {
        CookbookSamples.fill();   // the cookbook writes MEASURE: sample numbers in their place
    }

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private Path generate(Class<?> robotClass) throws Exception {
        com.crossbotics.ftchelper.robot.RobotDefinition r =
                (com.crossbotics.ftchelper.robot.RobotDefinition) robotClass.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, SimFromRobot.holdsFromPieces(r.simLines(), r), robotClass.getName(), "biobuzz-2026");
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

    @Test public void guessesAreReplacedWhenTheCodeGivesTheSize() {
        String first = SimFromRobot.merge(null, Arrays.asList("drive.type = tank"), "Robot", "x").text;
        assertTrue(first, first.contains("robot.lengthIn = 18\n"));
        // the team adds chassisMm(...) and measures the width by hand
        String edited = first.replace("robot.widthIn = 18", "robot.widthIn = 17.5");
        SimFromRobot.Result r = SimFromRobot.merge(edited, Arrays.asList("drive.type = tank",
                "robot.lengthIn = 16.2", "robot.widthIn = 15"), "Robot", "x");
        assertTrue(r.text, r.text.contains("robot.lengthIn = 16.2   " + SimFromRobot.MARK));
        assertFalse(r.text, r.text.contains("robot.lengthIn = 18"));
        assertTrue("the measured one is kept: " + r.text, r.text.contains("robot.widthIn = 17.5\n"));
        assertEquals(Collections.singletonList("robot.widthIn"), r.kept);
    }

    @Test public void aNumberTheCodeOnlyGuessesKeepsTheTeamsEdit() {
        java.util.List<String> code = Arrays.asList("drive.type = tank", "drive.trackWidthIn = 0   # TODO measure",
                "tunable.Claw.open = 0.5   # stand-in", "drive.wheelDiameterIn = 4");
        String first = SimFromRobot.merge(null, code, "Robot", "x").text;
        String edited = first.replace("drive.trackWidthIn = 0 ", "drive.trackWidthIn = 15.2 ")
                .replace("tunable.Claw.open = 0.5 ", "tunable.Claw.open = 0.31 ")
                .replace("drive.wheelDiameterIn = 4 ", "drive.wheelDiameterIn = 3.8 ");
        SimFromRobot.Result r = SimFromRobot.merge(edited, code, "Robot", "x");
        assertTrue("a TODO number the team measured stays: " + r.text, r.text.contains("drive.trackWidthIn = 15.2 "));
        assertTrue("a stand-in the team changed stays: " + r.text, r.text.contains("tunable.Claw.open = 0.31 "));
        assertTrue("a number the code gives wins: " + r.text, r.text.contains("drive.wheelDiameterIn = 4 "));
        assertEquals(Collections.singletonList("drive.wheelDiameterIn: 3.8 -> 4"), r.changed);
        assertEquals(Arrays.asList("drive.trackWidthIn = 15.2 (the code guesses 0)", "tunable.Claw.open = 0.31 (the code guesses 0.5)"), r.kept);
    }

    @Test public void runningItAgainNeverAddsAnotherBlock() {
        String first = SimFromRobot.merge(null, Arrays.asList("drive.type = tank"), "Robot", "x").text;
        String second = SimFromRobot.merge(first, Arrays.asList("drive.type = tank", "motor.lift = lift"), "Robot", "x").text;
        String third = SimFromRobot.merge(second, Arrays.asList("drive.type = tank", "motor.lift = lift", "servo.claw = 0.2"), "Robot", "x").text;
        assertFalse(third, third.contains("added from Robot.java"));
        assertEquals(third, 1, third.split("---- from Robot.java ----", -1).length - 1);
        assertTrue(third, third.contains("drive.type = tank   " + SimFromRobot.MARK + "\nmotor.lift = lift   "
                + SimFromRobot.MARK + "\nservo.claw = 0.2   " + SimFromRobot.MARK + "\n"));
        assertEquals("the same again: no change", third, SimFromRobot.merge(third,
                Arrays.asList("drive.type = tank", "motor.lift = lift", "servo.claw = 0.2"), "Robot", "x").text);
    }

    @Test public void theIntakeHoldsWhatPiecesMaxAllows() {
        com.crossbotics.ftchelper.robot.RobotDefinition r = new com.crossbotics.ftchelper.examples.v2bot.Robot();
        java.util.List<String> lines = SimFromRobot.holdsFromPieces(r.simLines(), r);
        String collect = null;
        for (String l : lines) if (l.startsWith("collect.")) collect = l;
        assertTrue(lines.toString(), collect != null);
        assertTrue(collect, collect.contains("holds=4"));   // v2bot: Pieces.max(4)
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
            assertFalse(String.join("\n", s.screen()), s.screenHas("not written"));
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
            // It fires only when LOCKED on the goal: face BLUE's up CELL (tags 42-45), then fire one.
            s.sim.robot.setPose(-57, 12.75, 270);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Goal : LOCKED"), 6));
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
            assertTrue(s.screenHas("Back wall, 2nd tile right of the centre line"));
            double y0 = s.robot().y;
            s.start();
            assertTrue("it finishes", SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 25));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Fire all: done"));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Leave: done"));
            assertTrue("it moved", Math.abs(s.robot().y - y0) > 12);
            assertTrue("it shot\n" + String.join("\n", s.screen()), s.robot().shots() >= 1);
            assertEquals("", s.error());
        } finally {
            Tunables.Drive.calibrated = was;
        }
    }

    // Its speed and shot angle are tuned in the simulator: the shots go cleanly into our HIVE's up CELL.
    @Test public void flywheelBotAutoScoresOnBothAlliances() throws Exception {
        boolean was = Tunables.Drive.calibrated;
        Tunables.Drive.calibrated = true;
        try {
            Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
            runShootingAuto(file, "FlywheelBot Shoot and Leave", false, true);
            runShootingAuto(file, "FlywheelBot Shoot and Leave", true, true);
        } finally {
            Tunables.Drive.calibrated = was;
        }
    }

    // The flywheel robot fires only when LOCKED on the goal: facing away (the goal not in view), a press of
    // fire feeds nothing.
    @Test public void flywheelBotNeverFiresBlind() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp");
            s.press(v -> s.g1().x = v);                          // BLUE
            s.start();
            synchronized (s.robot()) { s.sim.robot.setPose(-48, 40, 0); }   // facing the blue wall: no goal
            s.press(v -> s.g2().left_bumper = v);                // spin up
            SimRun.pause(2.0);
            s.press(v -> s.g2().right_bumper = v);               // fire one
            SimRun.pause(3.0);
            assertEquals("no blind shot\n" + String.join("\n", s.screen()), 0, s.robot().shots());
            assertTrue(String.join("\n", s.screen()), s.screenHas("aimed"));
            assertEquals("", s.error());
        }
    }

    // The turret robots' autos: ready, the turret aims, fire all, leave.
    @Test public void turretBotAutoShootsAndLeaves() throws Exception {
        boolean was = com.crossbotics.ftchelper.examples.turretbot.Tunables.Drive.calibrated;
        com.crossbotics.ftchelper.examples.turretbot.Tunables.Drive.calibrated = true;
        try {
            Path file = generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class);
            runShootingAuto(file, "TurretBot Shoot from Start", false, true);
            runShootingAuto(file, "TurretBot Shoot from Start", true, true);
        } finally {
            com.crossbotics.ftchelper.examples.turretbot.Tunables.Drive.calibrated = was;
        }
    }

    @Test public void v2BotAutoShootsAndLeaves() throws Exception {
        boolean was = com.crossbotics.ftchelper.examples.v2bot.Tunables.Drive.calibrated;
        com.crossbotics.ftchelper.examples.v2bot.Tunables.Drive.calibrated = true;
        try {
            runShootingAuto(generate(com.crossbotics.ftchelper.examples.v2bot.Robot.class), "V2 Bot Shoot and Stow", false, false);
        } finally {
            com.crossbotics.ftchelper.examples.v2bot.Tunables.Drive.calibrated = was;
        }
    }

    // red: the INIT menu's B (it starts on BLUE). clean: the shots must go cleanly into our HIVE's up CELL
    // (turretbot's and flywheelbot's speeds are tuned in the simulator; v2bot's are not yet).
    private void runShootingAuto(Path file, String auto, boolean red, boolean clean) throws Exception {
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init(auto);
            if (red) s.press(v -> s.g1().b = v);
            s.start();
            assertTrue("it finishes\n" + String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Autonomous finished"), 28));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Fire all: done"));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Leave: done"));
            assertTrue((red ? "RED" : "BLUE") + ": it shot\n" + String.join("\n", s.screen()), s.robot().shots() >= 1);
            SimRun.pause(2.0);                                  // the last shot lands
            String hive = red ? "Red HIVE" : "Blue HIVE";
            int cleanShots = 0;
            StringBuilder all = new StringBuilder();
            for (SimPieces.ShotVerdict v : s.robot().pieces.verdicts()) {
                all.append(v.verdict).append(' ').append(v.hive).append("; ");
                if (v.verdict.equals("clean") && v.hive.equals(hive)) cleanShots++;
            }
            assertTrue(auto + (red ? " RED" : " BLUE") + ": clean shots into our HIVE: " + all, !clean || cleanShots >= 1);
            assertEquals("", s.error());
        } finally {
            com.crossbotics.ftchelper.match.MatchMemory.clear();
        }
    }

    // ----------------------------- example robot 2 in the simulator -----------------------------

    @Test public void turretBotRunsAndTheClawActionWorks() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("TurretBot TeleOp");
            assertTrue(s.screenHas("G2 X: grab"));
            // Every number the modules read is in its Tunables.java: no "not written" notes at INIT.
            assertFalse(String.join("\n", s.screen()), s.screenHas("not written"));
            // autoPipelines: the lighting test runs in INIT and its line is on the Driver Station.
            assertTrue(SimRun.waitFor(() -> s.screenHas("limelight lighting"), 2));
            assertTrue(s.screenHas("G2 BACK: follow the goal on/off [Turret]"));
            s.start();
            SimLight light = s.robot().lights.get("light");          // the light: purple while following the goal
            assertTrue(light.words(), SimRun.waitFor(() -> light.words().equals("PURPLE"), 3));
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
            // The hood went home at START (0 deg: Tunables.Hood posA 0.20) and shows on the Driver Station.
            SimRobot.SimServo hood = s.robot().servos.get("hood");
            assertTrue("hood home: " + hood.actual, SimRun.waitFor(() -> Math.abs(hood.actual - 0.20) < 0.01, 2));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Hood : 0.0 deg"));
            assertEquals("", s.error());
        }
    }

    // The Limelight rides the turret (Robot.java: follows(camera)): the robot file says on=turret, so the
    // simulator's camera turns with the turret. RED, beside the HIVE and turned 20 degrees away from the
    // goal (the Limelight sees 27 degrees each way): the turret turns about -20 degrees, centres the goal, says AIMED, and the shots go in.
    @Test public void turretBotsCameraTurnsWithTheTurretAndTheShotsScore() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class);
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(text, text.contains("limelight.limelight = name=limelight, forward=6, left=0, up=12, yaw=0, pitch=30, on=turret"));
        assertTrue("the flywheel rides the turret too: " + text, text.contains("shape.launcher = flywheel, on=turret"));
        // The example's shot table is tuned in the simulator, so its own numbers score from here.
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("TurretBot TeleOp");
            s.press(v -> s.g1().b = v);                              // RED: it aims at the red HIVE's up CELL
            assertTrue(SimRun.waitFor(() -> s.screenHas("RED (chosen here)"), 2));
            s.start();
            s.sim.robot.setPose(57, -13, 110);                       // the up CELL (+x, RED AUDIENCE) 20 degrees right
            SimShapes.Shape turret = s.robot().shapes.shapes.get("turret");
            boolean aimed = SimRun.waitFor(() -> s.screenHas("AIMED"), 6);
            assertTrue("aimed: " + turret.value + "\n" + String.join("\n", s.screen()), aimed);
            assertEquals("turned toward the goal", -20, turret.value, 5);
            SimRun.pause(1.0);
            assertTrue("still aimed: it does not hunt", s.screenHas("AIMED"));
            int before = s.robot().pieces.launched();
            int inBefore = s.robot().pieces.inCells().get("Red HIVE");
            int tipsBefore = s.robot().pieces.tips().get("Red HIVE");
            // 2 s per shot: the example has no flywheel gains yet, so it runs on the parts catalog's
            // starting gains (a gentle P): spin-up from idle and recovery take longer than when tuned.
            for (int i = 0; i < 3; i++) {
                s.press(v -> s.g2().right_bumper = v);
                SimRun.pause(2.0);
            }
            SimRun.pause(2.0);
            assertTrue("it fired\n" + String.join("\n", s.screen()), s.robot().pieces.launched() - before >= 2);
            // In the red up CELL: there now, or they tipped the HIVE (3 NECTAR start in it; a few more tip it).
            boolean scored = s.robot().pieces.tips().get("Red HIVE") > tipsBefore || s.robot().pieces.inCells().get("Red HIVE") >= inBefore + 2;
            assertTrue("the shots went into the red up CELL: " + s.robot().pieces.inCells() + ", tips " + s.robot().pieces.tips(), scored);
            assertEquals("", s.error());
        }
    }

    // BLUE's goal (BLUE SCORING, tags 42-45) starts high, under BLUE's raised CELL on the far side of the
    // HIVE, facing away from the audience: seen from the far side (lesson 9's spot), the turret centres it.
    @Test public void turretBotOnBlueSeesItsGoalFromTheFarSide() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("TurretBot TeleOp");
            s.press(v -> s.g1().x = v);                              // BLUE
            assertTrue(SimRun.waitFor(() -> s.screenHas("BLUE (chosen here)"), 2));
            s.start();
            s.sim.robot.setPose(-50, 14, -80);                       // facing the HIVE (+x), turned 10 degrees left
            SimShapes.Shape turret = s.robot().shapes.shapes.get("turret");
            assertTrue("aimed: " + turret.value + "\n" + String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("AIMED"), 6));
            assertTrue("it sees BLUE SCORING: " + s.robot().cameras.get("limelight").seenIds(),
                    s.robot().cameras.get("limelight").seenIds().contains(43));
            assertEquals("turned toward the goal", -10, turret.value, 5);
            assertEquals("", s.error());
        }
    }

    // TeleOp zeroes the IMU at INIT; choosing the alliance then puts the robot on its spot (a hand move).
    // The IMU must not count that move as a turn: heading 0 on the spot reads 0, not minus the old heading.
    @Test public void theImuDoesNotSeeTheMoveToTheAlliancesSpot() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("TurretBot TeleOp").start();
            s.sim.robot.setPose(-40, -40, 73.6);                     // driven somewhere, turned
            SimRun.pause(0.3);
            s.sim.ds.stop();
            com.crossbotics.ftchelper.match.MatchMemory.clear();
            s.init("TurretBot TeleOp");                              // INIT zeroes the IMU here (73.6)
            s.press(v -> s.g1().b = v);                              // RED: placed on RED's spot, heading 0
            assertTrue(SimRun.waitFor(() -> Math.abs(s.robot().headingDeg) < 0.01 && Math.abs(s.robot().y + 61.55) < 0.01, 2));
            s.start();
            SimRun.pause(0.3);
            assertEquals("the IMU reads the turn since INIT: none", 0, s.robot().imuYaw(), 0.5);
            assertEquals("", s.error());
        }
    }

    // ----------------------------- the V2 robot: turret + lift with wrist and claw + interlock -----------------------------

    @Test public void theV2BotFileHasTheLifterOnTheSlideAndTheHomeSwitch() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.v2bot.Robot.class);
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(text, text.contains("shape.wrist = arm, on=lift"));
        assertTrue(text, text.contains("shape.claw = claw, on=wrist"));
        assertTrue(text, text.contains("grab.claw = shape=claw, servo=claw"));
        assertTrue(text, text.contains("touch.turret_home = turret, at="));
        SimRobot.load(file);
    }

    @Test public void theV2BotScoresInAFlowerAndTheInterlockHoldsTheTurret() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.v2bot.Robot.class);
        com.crossbotics.ftchelper.robot.Turret.forgetAngles();
        try (SimRun s = new SimRun(file.toString())) {
            v2Interlock(s);
        } finally {
            // The turret ends pointing back: another robot's turret must not start from that angle.
            com.crossbotics.ftchelper.robot.Turret.forgetAngles();
        }
    }

    private void v2Interlock(SimRun s) throws Exception {
        // Waits on conditions with generous time limits (the machine may be busy running other tests in
        // parallel: slow loops), and checks the zone at every look, not only at the end.
        s.init("V2 Bot TeleOp");
        assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Interlock TurretClaw: Turret between 147 and 217 deg / Wrist between 0.00 and 0.27"), 5));
        assertTrue(s.screenHas("G2 Y: score in a FLOWER"));
        SimMotor lift = s.robot().motors.get("lift"), turret = s.robot().motors.get("turret");
        s.start();
        // The turret homes on its switch at START (Tunables.Turret.homeAngle 0): then it is known.
        assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> !s.screenHas("homing"), 15));
        // The interlock: the wrist starts folded (in its zone, 0 to 47 degrees), so the turret must
        // stay out of the back. Following off (BACK), the turret by hand toward the back (stick right
        // = clockwise, toward -190: the zone 147 to 217 is the same as -213 to -143). It slows down as it
        // comes near (the rule measures its speed) and waits two margins (2 x 3) before it, at -137:
        // never into the zone, however slow the loops are (the machine may be busy with other tests).
        s.press(v -> s.g2().back = v);
        double perDeg = 537.7 * 2.75 / 360;
        double[] lowest = {0};
        s.g2().right_stick_x = 1;
        assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> {
            lowest[0] = Math.min(lowest[0], turret.shaft() / perDeg);
            return s.screenHas("TurretClaw : Turret held at");
        }, 15));
        // Pushed on for a second more: it never goes into the zone (checked at every look).
        SimRun.waitFor(() -> {
            lowest[0] = Math.min(lowest[0], turret.shaft() / perDeg);
            return false;
        }, 1.0);
        assertTrue("the turret never went into its zone (-143): lowest " + lowest[0], lowest[0] > -143);
        assertTrue("the turret waits near the edge of its zone: " + turret.shaft() / perDeg + "\n" + String.join("\n", s.screen()),
                turret.shaft() / perDeg < -105);
        s.g2().right_stick_x = 0;
        // Score (the turret still points back-ish, outside the zone): the wrist swings out, the lift
        // goes up to the FLOWER and back.
        s.press(v -> s.g2().y = v);
        assertTrue("the lift goes up: " + lift.shaft(), SimRun.waitFor(() -> lift.shaft() > 1250, 15));
        SimRobot.SimServo claw = s.robot().servos.get("claw"), wrist = s.robot().servos.get("wrist");
        // With the wrist out (above 47 degrees) the turret may point anywhere: straight back now.
        s.g2().right_stick_x = 1;
        assertTrue("the turret goes past -143 with the wrist out: " + turret.shaft() / perDeg,
                SimRun.waitFor(() -> turret.shaft() / perDeg < -160, 10));
        s.g2().right_stick_x = 0;
        assertTrue("it lets go over the FLOWER", SimRun.waitFor(() -> claw.actual > 0.53, 10));
        assertTrue("and comes back down: " + lift.shaft(), SimRun.waitFor(() -> lift.shaft() < 100, 15));
        // Folding would put the claw into the turret: the wrist waits above its zone.
        assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("TurretClaw : Wrist held at"), 10));
        SimRun.pause(0.5);
        assertTrue("the wrist is still out: " + wrist.actual, wrist.actual > 0.274);
        assertTrue(String.join("\n", s.screen()), s.screenHas("TurretClaw : Wrist held at"));
        assertEquals("", s.error());
    }

    // The Hood Finder with robot(): a start point, then the two stops; it shows the Tunables line.
    @Test public void theTurretBotHoodFinderWritesTheStops() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("TurretBot Hood Finder");
            assertTrue(String.join("\n", s.screen()), s.screenHas("Tunables.Hood"));
            s.start();
            s.press(v -> s.g1().dpad_down = v);                       // start point 0.45
            s.press(v -> s.g1().a = v);                               // move there
            s.press(v -> s.g1().dpad_down = v);                       // 0.40
            s.press(v -> s.g1().x = v);                               // stop A
            for (int i = 0; i < 3; i++) s.press(v -> s.g1().dpad_up = v);   // 0.55
            s.press(v -> s.g1().b = v);                               // stop B
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("posA = 0.400, posB = 0.550"), 1));
            assertTrue("the servo was moved", Math.abs(s.robot().servos.get("hood").target() - 0.55) < 1e-9);
            assertEquals("", s.error());
        }
    }

    // Hood Finder: an angle is used once (stop B never gets stop A's), a table needs both stops' angles and
    // angles that keep going one way, and the screen shows posA/posB OR the table, never both.
    @Test public void theHoodFinderShowsTheStopsOrATableThatInitTakes() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.turretbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("TurretBot Hood Finder").start();
            s.press(v -> s.g1().a = v);                               // move to the start point 0.50
            s.press(v -> s.g1().dpad_down = v);                       // 0.45
            s.press(v -> s.g1().x = v);                               // stop A, no angle
            s.press(v -> s.g1().dpad_up = v);
            s.press(v -> s.g1().dpad_up = v);                         // 0.55
            hoodAngle(s, 20);
            s.press(v -> s.g1().b = v);                               // stop B at 20
            String all = String.join("\n", s.screen());
            assertTrue(all, SimRun.waitFor(() -> s.screenHas("posA = 0.450, posB = 0.550") && s.screenHas("Angle at stop A missing"), 1));
            assertFalse("no half pair of angles\n" + all, s.screenHas("angleB ="));
            s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().dpad_down = v);                       // 0.45
            hoodAngle(s, -20);                                        // 0 here
            s.press(v -> s.g1().x = v);                               // stop A at 0
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("angleA = 0, angleB = 20"), 1));
            s.press(v -> s.g1().dpad_up = v);                         // 0.50
            s.press(v -> s.g1().a = v);                               // no angle measured here yet
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Measure the hood's angle here first"), 1));
            hoodAngle(s, 25);                                         // 25: more than at stop B, the wrong way
            s.press(v -> s.g1().a = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("keep going one way"), 1));
            assertFalse(s.screenHas("calibration ="));
            s.press(v -> s.g1().y = v);                               // undo
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Point 1 removed: 0 left."), 1));
            assertFalse("no stale point", s.screenHas("Point 1: 0.500"));
            hoodAngle(s, -15);                                        // 10
            s.press(v -> s.g1().a = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("calibration = {{0.450, 0}, {0.500, 10}, {0.550, 20}}"), 1));
            assertFalse("the table OR the stops, never both\n" + String.join("\n", s.screen()), s.screenHas("posA ="));
            assertEquals("", s.error());
        }
    }

    // Hold LB and press the D-pad up (+1) or down (-1) this many times: the hood's angle typed in.
    private static void hoodAngle(SimRun s, int steps) throws InterruptedException {
        s.g1().left_bumper = true;
        for (int i = 0; i < Math.abs(steps); i++) {
            if (steps > 0) s.press(v -> s.g1().dpad_up = v);
            else s.press(v -> s.g1().dpad_down = v);
        }
        s.g1().left_bumper = false;
    }

    // Whole-robot aim (flywheelbot, no turret): G1 RB on, and the robot turns itself to face the goal
    // its webcam sees; the turn stick still wins.
    @Test public void flywheelBotTurnsItselfToFaceTheGoal() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp");
            s.press(v -> s.g1().b = v);                              // RED: it aims at the red HIVE's up CELL
            assertTrue(SimRun.waitFor(() -> s.screenHas("RED (chosen here)"), 2));
            s.start();
            // Level with the up CELL's tags (34-37, RED AUDIENCE), facing them, then turned 15 degrees left.
            s.sim.robot.setPose(57, -12.75, 105);
            SimRun.pause(0.5);
            assertEquals("nothing turns it before aim is on", 105, s.robot().headingDeg, 1.0);
            // The aim button turns aim on only with the goal in view: wait for the camera to see it.
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Goal : in range")
                    || s.screenHas("Goal : seen") || s.screenHas("Goal : LOCKED"), 6));
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
            // Its start spot is where the robot file puts it (back against the wall); then it moves off the wall.
            assertTrue(String.join("\n", s.screen()), s.screenHas("Away from the wall: done"));
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

    // The same robot in a plain LinearOpMode (docs/guides/your-own-code.md): init, the INIT loop, start,
    // the loop and stop by hand, with the library's parts, actions, driving and screen.
    @Test public void theLessonBotRunsInAPlainOpMode() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.lessonbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Lesson Bot: own OpMode");
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Press START : A scores high"), 3));
            s.start();
            s.press(v -> s.g1().a = v);                               // score high (an action: never waits)
            s.g1().left_stick_y = -1;                                 // and drive at the same time
            assertTrue("the lift rises\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.robot().motors.get("lift").shaft() > 1500, 5));
            assertTrue("it drives while the lift moves", Math.abs(s.robot().motors.get("front_left").userPower()) > 0.3);
            assertTrue(String.join("\n", s.screen()), s.screenHas("Scored : 1"));
            s.g1().left_stick_y = 0;
            assertEquals("", s.error());
            s.sim.ds.stop();
            assertTrue("STOP stops the wheels", SimRun.waitFor(() -> s.robot().motors.get("front_left").userPower() == 0, 2));
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

    // A pod wired backward that Robot.java does not reverse: the Forward test says so (as on a real robot);
    // too few turns and a Loop that never left the mark are refused.
    @Test public void theOdometryTunerCatchesABackwardPodFewTurnsAndNoLoop() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.odometrybot.Robot.class);
        Files.write(file, "odometry.reversed = x   # measured: this pod counts backward\n".getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Odometry Bot Odometry Tuner").start();
            s.press(v -> s.g1().a = v);                              // 1 Forward 48 in
            s.robot().push(24, 0, 2);
            SimRun.pause(2.4);
            s.press(v -> s.g1().b = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Forward counted BACKWARD"), 2));
            s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().dpad_down = v);                     // 4 Loop and back
            s.press(v -> s.g1().a = v);
            SimRun.pause(0.3);
            s.press(v -> s.g1().b = v);                              // never moved
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("It went only"), 2));
            assertFalse(s.screenHas("checked = true"));
            s.press(v -> s.g1().dpad_up = v);                       // 3 Spin 3+ turns
            s.press(v -> s.g1().a = v);
            s.g1().right_stick_x = 1;
            assertTrue(SimRun.waitFor(() -> Math.abs(s.robot().odometry.turnTotalDeg()) > 1.3 * 360, 20));
            s.g1().right_stick_x = 0;
            SimRun.pause(0.3);
            s.press(v -> s.g1().b = v);                              // only about 1.5 turns
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("spin at least 3"), 2));
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
            // The distance comes from the robot's camera (its Limelight, tilted 30 degrees up, sees the
            // goal from the start position; without a camera it would be a distance set by hand).
            // The table line: FindersTest.
            assertTrue(String.join("\n", s.screen()), s.screenHas("(camera, "));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Saved "));
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
    // here"); then they find the alliance was wrong and hold START, then BACK, 1.5 s to switch it (the
    // odometry robot's Controls.java turns the switch on). Its field is turned (mirrorForRed = false),
    // so the pose cannot be mirrored: the odometry aim is off until the pose is set again.
    @Test public void teleOpSetsThePoseHereAndSwitchesTheAlliance() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.odometrybot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        // Every step waits on what the screen shows (never on a fixed number of loops), with room for a
        // busy computer: the button pairs depend on which button the OpMode SAW first.
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Odometry Bot TeleOp");
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("hold G1 START 1 s at x -56, y -61 in, heading 0 (BLUE), now or in the match"), 10));
            assertTrue(String.join("\n", s.screen()), s.screenHas("the same numbers from the red wall (Tunables.Odometry.mirrorForRed = false)"));
            assertTrue(String.join("\n", s.screen()), s.screenHas("in TeleOp: hold START, then BACK, 1.5 s to switch"));
            s.start();
            assertTrue(SimRun.waitFor(() -> s.screenHas("Time"), 10));
            // A short press does nothing.
            s.press(v -> s.g1().start = v);
            assertFalse(s.screenHas("Pose set"));
            s.g1().start = true;
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Pose set: x -56, y -61 in, heading 0 (BLUE)"), 10));
            s.g1().start = false;
            s.waitForLoops(2);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("x -56, y -61 in"), 5));
            // BACK first, then START: only a pause (BACK first is never the pair). The pause comes at
            // BACK's press: wait until the screen says so before START joins. A tap goes on again.
            s.g1().back = true;
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("PAUSED"), 5));
            s.g1().start = true;
            s.waitForLoops(3);
            s.g1().start = false;
            s.g1().back = false;
            s.waitForLoops(3);
            assertTrue("still paused: START joining later took nothing back", s.screenHas("PAUSED"));
            s.press(v -> s.g1().back = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> !s.screenHas("PAUSED"), 5));
            assertFalse("BACK first: no alliance switch", s.screenHas("Alliance now"));
            // START first (seen by the OpMode), then BACK, both held 1.5 s: the alliance switches (a
            // quick press would be the debug lines). No pause.
            s.g1().start = true;
            s.waitForLoops(3);
            s.g1().back = true;
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Alliance now RED: odometry aim OFF until the pose is set"), 10));
            s.g1().back = false;
            s.g1().start = false;
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("RED (switched in TeleOp)"), 5));
            s.waitForLoops(3);
            assertEquals(com.crossbotics.ftchelper.match.Alliance.RED, com.crossbotics.ftchelper.match.MatchMemory.chosen());
            assertFalse("not paused: the pair took BACK's tap", s.screenHas("PAUSED"));
            assertEquals("", s.error());
        }
    }

    // "Set pose here" before the match: hold G1 START 1 s in INIT. Not while driving in the match.
    @Test public void thePoseCanBeSetInInitAndNotWhileDriving() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.odometrybot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("Odometry Bot TeleOp");
            s.press(v -> s.g1().b = v);                          // RED
            s.g1().start = true;
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Pose set: x -56, y -61 in, heading 0 (RED, same numbers)"), 3));
            s.g1().start = false;
            s.start();
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("x -56, y -61 in"), 2));
            // Driving: "set pose" is refused.
            s.g1().left_stick_y = -0.8f;
            s.g1().start = true;
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Pose NOT set: stop first"), 3));
            s.g1().start = false;
            s.g1().left_stick_y = 0;
            assertEquals("", s.error());
        } finally {
            com.crossbotics.ftchelper.match.MatchMemory.clear();   // RED must not carry over to the next test
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
            assertTrue("blue: the spot from the blue wall", SimRun.waitFor(() -> Math.abs(s.robot().x + 48) < 0.01
                    && Math.abs(s.robot().y - 61.55) < 0.01 && Math.abs(Math.abs(s.robot().headingDeg) - 180) < 0.01, 2));
            s.press(v -> s.g1().b = v);                          // RED: the same spot from the red wall
            assertTrue("red: turned half a turn", SimRun.waitFor(() -> Math.abs(s.robot().x - 48) < 0.01
                    && Math.abs(s.robot().y + 61.55) < 0.01 && Math.abs(s.robot().headingDeg) < 0.01, 2));
            assertEquals("", s.error());
        }
    }

    @Test public void aStartRightAfterTheAllianceChangeStartsFromTheNewSpot() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot Shoot and Leave");
            s.press(v -> s.g1().b = v);                          // RED, then START at once
            s.sim.ds.start();
            double x, y;
            synchronized (s.robot()) { x = s.robot().x; y = s.robot().y; }
            assertTrue("on the red spot at START: " + x + ", " + y, Math.hypot(x - 48, y + 61.55) < 1);
        }
    }

    @Test public void teleOpWithoutAutoStartsOnItsAlliancesSide() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp");                          // no autonomous: nothing chosen yet
            // "Alliance : BLUE until you choose": the robot stands on BLUE's spot, and the page looks from there
            assertTrue("on BLUE's spot until the alliance is chosen", SimRun.waitFor(() -> Math.abs(s.robot().y - 61.55) < 0.01
                    && Math.abs(s.robot().x - 56) < 0.01, 2));
            assertEquals(com.crossbotics.ftchelper.match.Alliance.BLUE, SimEngine.allianceUntilChosen(s.sim.ds));   // what the page shows
            s.press(v -> s.g1().x = v);                          // BLUE chosen: it stays
            assertTrue(SimRun.waitFor(() -> Math.abs(s.robot().y - 61.55) < 0.01 && Math.abs(s.robot().x - 56) < 0.01, 2));
            s.press(v -> s.g1().b = v);                          // RED: the robot file's start
            assertTrue(SimRun.waitFor(() -> Math.abs(s.robot().y + 61.55) < 0.01, 2));
            s.sim.robot.setPose(-56, -45, 0);                     // dragged on the map
            SimRun.pause(0.3);
            assertEquals("a dragged robot stays", -45, s.robot().y, 0.01);
            assertEquals("", s.error());
        }
    }

    // Like the real Driver Station with its TeleOp timer on: TeleOp stops at the end of the period (2:00;
    // shortened here), and nothing keeps driving at "Time : 0:00". With the timer off it runs on.
    @Test public void teleOpStopsWhenItsTimerRunsOut() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.sim.ds.teleOpSec = 1.5;
            s.init("FlywheelBot TeleOp").start();
            assertTrue("it stops by itself", SimRun.waitFor(() -> s.sim.ds.phase() == SimDriverStation.Phase.STOPPED, 4));
            s.sim.ds.teleTimer = false;
            s.init("FlywheelBot TeleOp").start();
            SimRun.pause(2.5);
            assertEquals("timer off: it runs on", SimDriverStation.Phase.RUNNING, s.sim.ds.phase());
            assertEquals("", s.error());
        }
    }

    // "Set pose here" (hold G1 START 1 s) on a robot without odometry: a note says why nothing happens.
    @Test public void setPoseHereWithoutOdometrySaysItNeedsOdometry() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp").start();
            s.g1().start = true;
            SimRun.pause(1.3);
            s.g1().start = false;
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Set pose here needs odometry"), 1));
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
            // Held: paused at once (before BACK is let go); 1 s on, forward is reset too and it stays paused.
            s.g1().back = true;
            assertTrue("paused at the press", SimRun.waitFor(() -> s.screenHas("PAUSED"), 0.5));
            assertEquals(0, intake.userPower(), 1e-9);
            SimRun.pause(1.5);
            s.g1().back = false;
            SimRun.pause(0.3);
            assertTrue("stays paused after the hold", s.screenHas("PAUSED"));
            s.press(v -> s.g1().back = v);                       // a tap: go on
            assertTrue(SimRun.waitFor(() -> intake.userPower() > 0.5, 1));
            assertFalse(s.screenHas("PAUSED"));
            assertEquals("", s.error());
        }
    }
}
