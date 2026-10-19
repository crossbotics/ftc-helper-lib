package io.github.jasoncross.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.jasoncross.ftchelper.examples.flywheelbot.Tunables;
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
        io.github.jasoncross.ftchelper.robot.RobotDefinition r =
                (io.github.jasoncross.ftchelper.robot.RobotDefinition) robotClass.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, r.simLines(), robotClass.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile(robotClass.getPackage().getName() + ".properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ----------------------------- merging -----------------------------

    @Test public void aNewFileLoadsAndMarksEveryGeneratedLine() throws Exception {
        Path file = generate(io.github.jasoncross.ftchelper.examples.flywheelbot.Robot.class);
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
        Path file = generate(io.github.jasoncross.ftchelper.examples.flywheelbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            s.init("FlywheelBot TeleOp");
            assertTrue("the help page shows in INIT", s.screenHas("G2 RT: in on/off [Intake]"));
            assertEquals("nothing moves in INIT", 0, s.robot().motors.get("intake").userPower(), 1e-9);
            s.start();
            SimRobot.SimServo deploy = s.robot().servos.get("intake_deploy");
            assertTrue("the deploy servo pushes at START", SimRun.waitFor(() -> deploy.actual > 0.5, 2));
            assertTrue("and comes back", SimRun.waitFor(() -> deploy.actual < 0.1, 3));
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
        Path file = generate(io.github.jasoncross.ftchelper.examples.flywheelbot.Robot.class);
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
        Path file = generate(io.github.jasoncross.ftchelper.examples.turretbot.Robot.class);
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
                new io.github.jasoncross.ftchelper.examples.gobildabot.Robot().simLines(), "Robot", "biobuzz-2026");
        assertTrue("Robot.java and the CAD-made robot file agree: " + r.problems, r.problems.isEmpty());
        assertTrue("the measured shapes are kept: " + r.kept, r.kept.contains("shape.intake") && r.kept.contains("motor.launcher"));
    }

    @Test public void goBildaBotRunsOnItsRobotFile() throws Exception {
        boolean was = io.github.jasoncross.ftchelper.examples.gobildabot.Tunables.Drive.calibrated;
        io.github.jasoncross.ftchelper.examples.gobildabot.Tunables.Drive.calibrated = true;
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
            io.github.jasoncross.ftchelper.examples.gobildabot.Tunables.Drive.calibrated = was;
        }
    }
}
