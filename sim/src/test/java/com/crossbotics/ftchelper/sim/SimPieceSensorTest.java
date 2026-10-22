package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.PieceSensor;
import com.crossbotics.ftchelper.robot.Pieces;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.hardware.DistanceSensor;
import com.qualcomm.robotcore.hardware.NormalizedColorSensor;
import com.qualcomm.robotcore.hardware.NormalizedRGBA;
import java.io.File;
import java.nio.file.Files;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The pretend piece sensors (color., distance.) and the library's Pieces counting on them. */
public class SimPieceSensorTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String SENSORS = "collect.intake = shape=intake, motor=intake, holds=8\n"
            + "color.ready_color = at=ready\n"
            + "color.intake_color = at=intake\n"
            + "distance.size = at=ready, acrossMm=150\n";

    private SimRobot robot() throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File f = tmp.newFile();
        Files.write(f.toPath(), (base + "\n" + SENSORS).getBytes("UTF-8"));
        return SimRobot.load(f.toPath());
    }

    private static void run(SimRobot r, double sec) {
        for (double t = 0; t < sec; t += 0.01) r.step(0.01);
    }

    @Test public void theSensorsSeeThePieceAtTheirSpot() throws Exception {
        SimRobot r = robot();
        NormalizedColorSensor ready = r.hardwareMap().get(NormalizedColorSensor.class, "ready_color");
        NormalizedColorSensor in = r.hardwareMap().get(NormalizedColorSensor.class, "intake_color");
        DistanceSensor size = r.hardwareMap().get(DistanceSensor.class, "size");
        NormalizedRGBA c = ready.getNormalizedColors();
        assertTrue("the preload POLLEN is yellow: red and green above blue", c.red > c.blue && c.green > c.blue);
        assertEquals(15, ((DistanceSensor) ready).getDistance(DistanceUnit.MM), 1e-9);
        assertEquals("150 mm channel minus a 2.8 in POLLEN", 150 - 2.8 * 25.4, size.getDistance(DistanceUnit.MM), 1e-6);
        assertEquals("nothing coming in", 120, ((DistanceSensor) in).getDistance(DistanceUnit.MM), 1e-9);
    }

    /**
     * Tunables with the simulator's colours (the field file's screen colours, as hues). steadyMs = 0: this
     * test runs faster than real time, and the sensors wait on the real clock.
     */
    public static class Numbers {
        public static class IntakeColor {
            public static double POLLEN = 50, RED_NECTAR = 3, BLUE_NECTAR = 219, steadyMs = 0;
        }
        public static class ReadyColor {
            public static double POLLEN = 50, RED_NECTAR = 3, BLUE_NECTAR = 219, steadyMs = 0;
        }
    }

    public static class Bot extends RobotDefinition {
        public Pieces pieces;
        public Indexer feeder;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            feeder = add(Indexer.motor("feeder"));
            pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
                    .comingIn(PieceSensor.color("intake_color"))
                    .readyToFire(PieceSensor.color("ready_color"))
                    .max(8).startsWith(4, "POLLEN"));
        }
    }

    // The simulator and the robot code take turns, 10 ms each.
    private static void loop(SimRobot r, Bot b, double sec) {
        for (double t = 0; t < sec; t += 0.01) {
            r.step(0.01);
            b.all().update();
        }
    }

    @Test public void piecesCountsWhatTheIntakeTakesInAndWhatIsFired() throws Exception {
        SimRobot r = robot();
        Bot b = new Bot();
        b.setup(r.hardwareMap());
        loop(r, b, 0.1);
        assertEquals(4, b.pieces.count());
        assertEquals("POLLEN", b.pieces.next());
        // Drive into a red NECTAR with the intake running in.
        r.setPose(30, -30, 0);
        r.pieces.place("red NECTAR", 30, -16, 0);
        r.motors.get("intake").device.setPower(1);
        r.push(10, 0, 1.0);
        loop(r, b, 1.5);
        assertEquals("taken in (simulator)", 5, r.pieces.storedCount());
        assertEquals("counted (robot code)", 5, b.pieces.count());
        assertEquals("[POLLEN, POLLEN, POLLEN, POLLEN, RED_NECTAR]", b.pieces.held().toString());
        // Fire the four POLLEN: the flywheel spins, the feeder sends one each feed.
        r.motors.get("intake").device.setPower(0);
        r.motors.get("flywheel").device.setPower(1);
        loop(r, b, 2.0);
        for (int i = 0; i < 4; i++) {
            b.feeder.feedOne();
            // The feeder's timer runs on the real clock, and this test runs faster than real time: end
            // each feed after 0.1 s of simulator time (one piece; the simulator sends one every 0.3 s).
            loop(r, b, 0.1);
            b.feeder.stop();
            loop(r, b, 0.3);
        }
        assertEquals("simulator: one left", 1, r.pieces.storedCount());
        assertEquals("robot code: one left", 1, b.pieces.count());
        assertEquals("RED_NECTAR", b.pieces.next());
        assertEquals("the ready sensor sees it too", "RED_NECTAR", b.pieces.atReady());
        b.all().stop();
    }
}
