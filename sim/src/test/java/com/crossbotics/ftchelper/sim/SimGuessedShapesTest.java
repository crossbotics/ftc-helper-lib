package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.robot.Arm;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Turret;
import com.crossbotics.ftchelper.robot.Wrist;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The shapes the robot file guesses for parts with no looks(...) (marked "TODO measure"): they start from
 * the robot's sizes and the other parts, keep the robot legal (the 18 in start cube; 18 x 24 x 29 in at full
 * reach; nothing below the tiles), do not overlap each other at the start, and the guessed intake takes in
 * a piece the robot drives onto.
 */
public class SimGuessedShapesTest {
    static {
        CookbookSamples.fill();   // the cookbook writes MEASURE: sample numbers in their place
    }

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final List<Class<? extends RobotDefinition>> EXAMPLES = Arrays.asList(
            com.crossbotics.ftchelper.examples.gobildabot.Robot.class,
            com.crossbotics.ftchelper.examples.revbot.Robot.class,
            com.crossbotics.ftchelper.examples.andymarkbot.Robot.class,
            com.crossbotics.ftchelper.examples.studicabot.Robot.class,
            com.crossbotics.ftchelper.examples.lessonbot.Robot.class,
            com.crossbotics.ftchelper.examples.flywheelbot.Robot.class,
            com.crossbotics.ftchelper.examples.turretbot.Robot.class,
            com.crossbotics.ftchelper.examples.v2bot.Robot.class,
            com.crossbotics.ftchelper.examples.odometrybot.Robot.class,
            com.crossbotics.ftchelper.examples.pedrobot.Robot.class,
            com.crossbotics.ftchelper.examples.quickstart.Robot.class,
            com.crossbotics.ftchelper.examples.cookbook.Robot.class);

    // ----------------------------- robots made of guesses only -----------------------------

    /** Everything on a lift: a wrist and a claw on it, a turret with the flywheel, an intake. */
    public static class LiftBot extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            add(Intake.motor("intake").part("gobilda-5203-1150"));
            add(Lift.slide("lift").part("gobilda-5203-435"));
            add(Wrist.servo("wrist").part("gobilda-2000-speed"));
            add(Claw.servo("claw").part("gobilda-2000-torque"));
            add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
        }
    }

    /** A small chassis and a low robot (chassisMm, heightMm): an arm with a wrist and a claw, a launcher, two intakes. */
    public static class ArmBot extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.tank("l", "r"));
            chassisMm(360, 340, 90);
            heightMm(380);
            add(Intake.motor("intake").part("gobilda-5203-1150"));
            add(Intake.crServos("back_roller"));
            add(Arm.motor("arm", "gobilda-5203-117").gearRatio(2));
            add(Wrist.servo("wrist"));
            add(Claw.servo("claw"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
        }
    }

    /** A servo turret, a wrist with no arm or lift, a claw on it. */
    public static class ServoTurretBot extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.tank("l", "r"));
            chassisMm(420, 400, 100);
            add(Turret.servo("turret").part("gobilda-2000-torque"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            add(Wrist.servo("wrist"));
            add(Claw.servo("claw"));
            add(Intake.motor("intake"));
        }
    }

    /** A claw with nothing to ride, a lift alone (no riders), a launcher with no turret. */
    public static class PlainBot extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.tank("l", "r"));
            add(Lift.slide("lift"));
            add(Claw.servo("claw").part("gobilda-2000-torque"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
        }
    }

    /** Two of each: the second goes to the side. */
    public static class TwoOfEachBot extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.tank("l", "r"));
            add(Lift.slide("lift"));
            add(Lift.slide("lift2"));
            add(Claw.servo("claw"));
            add(Claw.servo("claw2"));
            add(Wrist.servo("wrist"));
            add(Wrist.servo("wrist2"));
            add(Turret.motor("turret", "gobilda-5203-117"));
            add(Turret.motor("turret2", "gobilda-5203-117"));
            add(Arm.motor("arm2", "gobilda-5203-117"));
        }
    }

    private static final List<Class<? extends RobotDefinition>> GUESSED = Arrays.asList(
            LiftBot.class, ArmBot.class, ServoTurretBot.class, PlainBot.class, TwoOfEachBot.class);

    private Path generate(Class<? extends RobotDefinition> c) throws Exception {
        RobotDefinition r = c.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, SimFromRobot.holdsFromPieces(r.simLines(), r), c.getName(), "biobuzz-2026");
        assertTrue(c.getName() + ": " + res.problems, res.problems.isEmpty());
        Path file = tmp.newFile(c.getName() + ".properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ----------------------------- the checks -----------------------------

    @Test public void theExampleRobotsKeepTheSizeRules() throws Exception {
        checkAll(EXAMPLES);
    }

    @Test public void robotsMadeOfGuessesKeepTheSizeRules() throws Exception {
        checkAll(GUESSED);
    }

    private void checkAll(List<Class<? extends RobotDefinition>> robots) throws Exception {
        StringBuilder failed = new StringBuilder();
        for (Class<? extends RobotDefinition> c : robots) {
            Path file = generate(c);
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            SimRobot r = SimRobot.load(file);
            SimShapes.Extent now = r.shapes.now();
            String name = c.getSimpleName() + " (" + c.getPackage().getName() + ")";
            if (!now.fits(18, 18, 18) || !now.aboveFloor()) failed.append("\n").append(name).append(": the start: ").append(now);
            if (!r.shapes.mostOk) failed.append("\n").append(name).append(": full reach: ").append(r.shapes.mostPose);
            String overlap = overlap(r, guessedNames(text));
            if (!overlap.isEmpty()) failed.append("\n").append(name).append(": ").append(overlap);
        }
        if (failed.length() > 0) fail("Guessed shapes that break a size rule or overlap:" + failed);
    }

    // The shapes the code guessed (their lines say TODO measure).
    private static Set<String> guessedNames(String text) {
        Set<String> out = new HashSet<>();
        for (String line : text.split("\n")) {
            if (line.startsWith("shape.") && line.contains("TODO measure")) out.add(line.substring(6, line.indexOf('=')).trim());
        }
        return out;
    }

    // Two guessed shapes (not one riding the other) that overlap at the start, in words; "" if none.
    private static String overlap(SimRobot r, Set<String> guessed) {
        List<SimShapes.Piece> pieces = new ArrayList<>();
        for (SimShapes.Piece p : r.shapes.pieces()) if (guessed.contains(p.shape)) pieces.add(p);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pieces.size(); i++) {
            for (int j = i + 1; j < pieces.size(); j++) {
                SimShapes.Piece a = pieces.get(i), b = pieces.get(j);
                if (a.shape.equals(b.shape) || rides(r, a.shape, b.shape) || rides(r, b.shape, a.shape)) continue;
                if (boxesOverlap(a, b)) out.append(a.shape).append(" overlaps ").append(b.shape).append("; ");
            }
        }
        return out.toString();
    }

    // True if shape a rides on b (directly or through others).
    private static boolean rides(SimRobot r, String a, String b) {
        String up = r.shapes.shapes.get(a).parent;
        while (!up.equals("chassis")) {
            if (up.equals(b)) return true;
            up = r.shapes.shapes.get(up).parent;
        }
        return false;
    }

    // Two turned boxes overlap (by more than a hair) unless some axis separates them (the separating-axis test).
    private static boolean boxesOverlap(SimShapes.Piece a, SimShapes.Piece b) {
        double[][] ea = {a.ef, a.el, a.eu}, eb = {b.ef, b.el, b.eu};
        double[] ha = {a.sf / 2, a.sl / 2, a.su / 2}, hb = {b.sf / 2, b.sl / 2, b.su / 2};
        List<double[]> axes = new ArrayList<>();
        for (int i = 0; i < 3; i++) { axes.add(ea[i]); axes.add(eb[i]); }
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) axes.add(cross(ea[i], eb[j]));
        double[] d = {b.center[0] - a.center[0], b.center[1] - a.center[1], b.center[2] - a.center[2]};
        for (double[] ax : axes) {
            double len = Math.sqrt(dot(ax, ax));
            if (len < 1e-9) continue;
            double ra = 0, rb = 0;
            for (int i = 0; i < 3; i++) {
                ra += ha[i] * Math.abs(dot(ea[i], ax));
                rb += hb[i] * Math.abs(dot(eb[i], ax));
            }
            if (Math.abs(dot(d, ax)) / len >= (ra + rb) / len - 0.05) return false;
        }
        return true;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    // ----------------------------- what they say, and the intake at work -----------------------------

    @Test public void theGuessesSayWhatIsGuessedAndWhereTheSizeCameFrom() throws Exception {
        String text = new String(Files.readAllBytes(generate(LiftBot.class)), StandardCharsets.UTF_8);
        assertTrue(text, text.contains("shape.wrist = arm, on=lift,"));
        assertTrue(text, text.contains("shape.claw = claw, on=wrist, at=2.5,0,0,"));
        assertTrue(text, text.contains("shape.launcher = flywheel, on=turret,"));
        // the flywheel's size from the parts catalog: a 96 mm wheel, 24 mm wide
        assertTrue(text, text.contains("size=3.78x0.94x3.78, motor=launcher   # from Robot.java. size from the catalog (gobilda-hogback-96"));
        for (String line : text.split("\n")) {
            if (line.startsWith("shape.")) assertTrue(line, line.contains("TODO measure"));
        }
    }

    @Test public void theGuessedIntakeTakesInAPieceTheRobotDrivesOnto() throws Exception {
        for (Class<? extends RobotDefinition> c : Arrays.asList(
                com.crossbotics.ftchelper.examples.lessonbot.Robot.class, com.crossbotics.ftchelper.examples.quickstart.Robot.class)) {
            Path file = generate(c);
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            assertTrue(text, text.contains("shape.intake = roller, at=8,0,2, size=2x14x2, motor=intake"));
            // room for one more than the preload
            text = text.replaceAll("holds=\\d+", "holds=8");
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
            SimRobot r = SimRobot.load(file);
            int before = r.pieces.storedCount();
            r.setPose(30, -30, 0);                               // facing +y: the front is at y -21
            r.pieces.place("POLLEN", 30, -16, 0);               // 3.6 in ahead of the front: out of reach
            r.motors.get("intake").device.setPower(1);
            run(r, 0.3);
            assertEquals(c.getName() + ": not taken from afar", before, r.pieces.storedCount());
            r.push(10, 0, 1.0);                                  // roll forward onto it
            run(r, 1.5);
            assertEquals(c.getName() + ": taken in", before + 1, r.pieces.storedCount());
        }
    }

    private static void run(SimRobot r, double sec) {
        for (double t = 0; t < sec; t += 0.01) r.step(0.01);
    }
}
