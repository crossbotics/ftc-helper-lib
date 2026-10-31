package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.vision.HuskyLensCamera;
import com.crossbotics.ftchelper.vision.TagGroup;
import com.crossbotics.ftchelper.vision.TargetObservation;
import com.qualcomm.hardware.dfrobot.HuskyLens;
import java.io.File;
import java.nio.file.Files;
import java.util.function.Supplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The pretend HuskyLens, through the library's HuskyLensCamera and the SDK's own class. */
public class SimHuskyLensTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private SimRobot robot(String line) throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File f = tmp.newFile();
        Files.write(f.toPath(), (base + "\n" + line + "\n").getBytes("UTF-8"));
        SimRobot r = SimRobot.load(f.toPath());
        // Facing -x, level with the red CELL group of tags 30-33 (as SimCameraTest).
        r.setPose(32, -12.74, 90);
        return r;
    }

    private static <T> T waitFor(HuskyLensCamera cam, Supplier<T> got) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            cam.update();
            T t = got.get();
            if (t != null) return t;
            Thread.sleep(10);
        }
        return null;
    }

    private static final String TAUGHT = "huskylens.h = name=huskylens, forward=8, up=10, pitch=30, hfov=60, taught=30|31|32|33";

    @Test public void itIsTheSdksClassAndReportsTaughtNumbersNotTagNumbers() throws Exception {
        SimRobot r = robot(TAUGHT);
        HuskyLens h = r.hardwareMap().get(HuskyLens.class, "huskylens");
        assertTrue(h.knock());
        h.selectAlgorithm(HuskyLens.Algorithm.TAG_RECOGNITION);
        HuskyLens.Block[] seen = h.blocks();
        assertTrue("the 4 taught tags are in view: " + seen.length, seen.length >= 4);
        boolean taughtOne = false;
        for (HuskyLens.Block b : seen) {
            assertTrue("in the picture: " + b, b.x >= 0 && b.x <= 320 && b.y >= 0 && b.y <= 240);
            assertEquals("the SDK reads the box we sent", b.width, b.height);
            assertTrue("never a real tag number: " + b.id, b.id <= 4);
            if (b.id == 1) taughtOne = true;
        }
        assertTrue(taughtOne);
        assertEquals("only ID1", 1, h.blocks(1).length);
    }

    @Test public void anUntaughtTagIsZero() throws Exception {
        SimRobot r = robot("huskylens.h = name=huskylens, forward=8, up=10, pitch=30, hfov=60");
        HuskyLens h = r.hardwareMap().get(HuskyLens.class, "huskylens");
        for (HuskyLens.Block b : h.blocks()) assertEquals(0, b.id);
    }

    @Test public void theCameraClassFindsTheGroupWithABearingAndARoughDistance() throws Exception {
        SimRobot r = robot(TAUGHT);
        HuskyLensCamera cam = HuskyLensCamera.create(r.hardwareMap(), "huskylens")
                .tag(1, 30).tag(2, 31).tag(3, 32).tag(4, 33).sizeIn(3.25);
        cam.start();
        TargetObservation group = waitFor(cam, () -> cam.target("RED SCORING"));
        assertNotNull("the 4 tags as one target: " + cam.targets(), group);
        assertTrue(cam.status(), cam.isWorking());
        assertEquals("straight ahead", 0, group.bearingDeg, 1.5);
        assertEquals("the true distance to the row's middle is about 44 in", 44, group.distanceIn, 12);
        assertNotNull("by its real number", cam.target(31));
        assertEquals("Tag 31", cam.target(31).name);
        // At the start this is the HIVE's down CELL: its tags read upside down, so it is not the goal.
        assertFalse("upside down: " + group.rollDeg, group.isUpright());
        assertNull(TagGroup.goalTarget(cam, "RED SCORING"));
        cam.close();
    }

    @Test public void theGoalIsTheUpCellWhenItsTagsWereTaught() throws Exception {
        SimRobot r = robot("huskylens.h = name=huskylens, forward=8, up=10, pitch=45, hfov=60, taught=34|35|36|37");
        r.setPose(50, -13, 90);
        HuskyLensCamera cam = HuskyLensCamera.create(r.hardwareMap(), "huskylens")
                .tag(1, 34).tag(2, 35).tag(3, 36).tag(4, 37);
        cam.start();
        TargetObservation goal = waitFor(cam, () -> TagGroup.goalTarget(cam, "RED SCORING"));
        assertNotNull("seen: " + cam.targets(), goal);
        assertEquals("RED AUDIENCE", goal.name);
        cam.close();
    }

    @Test public void onlySixBoxesLikeTheDriver() throws Exception {
        SimRobot r = robot("huskylens.h = name=huskylens, forward=8, up=10, pitch=30, hfov=120");
        r.setPose(45, -13, 90);
        assertTrue(r.huskyLenses.get("huskylens").blocks().length <= 6);
    }

    @Test public void turningLeftPutsTheGoalToTheRightPositiveLeftBearing() throws Exception {
        SimRobot r = robot(TAUGHT);
        HuskyLensCamera cam = HuskyLensCamera.create(r.hardwareMap(), "huskylens").hfovDeg(60)
                .tag(1, 30).tag(2, 31).tag(3, 32).tag(4, 33);
        cam.start();
        assertNotNull(waitFor(cam, () -> cam.target("RED SCORING")));
        synchronized (r) { r.headingDeg = 100; }
        TargetObservation t = waitFor(cam, () -> {
            TargetObservation g = cam.target("RED SCORING");
            return g != null && g.bearingDeg < -5 ? g : null;
        });
        assertNotNull("the robot turned 10 degrees left: " + cam.targets(), t);
        assertEquals(-10, t.bearingDeg, 2);
        assertFalse("no sizeIn: no distance", t.hasDistance());
        cam.close();
    }

    @Test public void theRightHfovGivesTheRightBearingOnAWideCamera() throws Exception {
        SimRobot r = robot("huskylens.h = name=huskylens, forward=8, up=10, pitch=30, hfov=75, taught=30|31|32|33");
        HuskyLensCamera cam = HuskyLensCamera.create(r.hardwareMap(), "huskylens").hfovDeg(75)
                .tag(1, 30).tag(2, 31).tag(3, 32).tag(4, 33);
        r.setPose(32, -12.74, 105);
        cam.start();
        TargetObservation t = waitFor(cam, () -> cam.target("RED SCORING"));
        assertNotNull(t);
        assertEquals(-15, t.bearingDeg, 2);
        cam.close();
    }

    // The robot 20 in from a piece on the field, facing it, and that piece's type. A pose that hits
    // something is refused, so it tries the pieces until one fits.
    private static String faceAPiece(SimRobot r) {
        for (Object[] p : r.pieces.drawn()) {
            try {
                r.setPose((Double) p[1] + 20, (Double) p[2], 90);
                return ((SimField.PieceType) p[0]).name;
            } catch (IllegalArgumentException wall) {
                // try the next piece
            }
        }
        throw new AssertionError("no piece with room beside it");
    }

    private static final String LOOKING_DOWN = "forward=8, up=8, pitch=-15, hfov=60";

    @Test public void colorsFindTaughtPiecesAndNotOthers() throws Exception {
        String piece = faceAPiece(robot("huskylens.a = name=a, " + LOOKING_DOWN));
        SimRobot r = robot("huskylens.h = name=huskylens, " + LOOKING_DOWN + ", taught=" + piece);
        faceAPiece(r);
        HuskyLensCamera cam = HuskyLensCamera.create(r.hardwareMap(), "huskylens")
                .mode(HuskyLensCamera.Mode.COLORS).label(1, piece).sizeIn(5);
        cam.start();
        TargetObservation t = waitFor(cam, () -> cam.target(piece));
        assertNotNull(cam.status() + " " + cam.targets(), t);
        assertEquals(HuskyLens.Algorithm.COLOR_RECOGNITION, r.huskyLenses.get("huskylens").algorithm());
        // target(name) gives the biggest box, maybe a nearer piece beside it: the one we face is straight ahead.
        boolean ahead = false;
        for (TargetObservation o : cam.targets()) ahead |= Math.abs(o.bearingDeg) < 2;
        assertTrue("one straight ahead: " + cam.targets(), ahead);
        assertTrue(t.hasDistance());
        cam.close();

        SimRobot untaught = robot("huskylens.h = name=huskylens, " + LOOKING_DOWN);
        faceAPiece(untaught);
        untaught.huskyLenses.get("huskylens").selectAlgorithm(HuskyLens.Algorithm.COLOR_RECOGNITION);
        assertEquals("nothing taught: nothing found", 0, untaught.huskyLenses.get("huskylens").blocks().length);
    }

    @Test public void objectsFollowOnlyOne() throws Exception {
        String piece = faceAPiece(robot("huskylens.a = name=a, " + LOOKING_DOWN));
        SimRobot r = robot("huskylens.h = name=huskylens, forward=8, up=8, pitch=-15, hfov=120, taught=" + piece);
        faceAPiece(r);
        SimHuskyLens h = r.huskyLenses.get("huskylens");
        h.selectAlgorithm(HuskyLens.Algorithm.OBJECT_TRACKING);
        assertEquals(1, h.blocks().length);
    }

    @Test public void askingForOneNumberFindsItEvenBeyondTheSixBoxes() throws Exception {
        // Tags 30-37 are all in view from here, and more than 6. Tag 37 may be left out of blocks().
        SimRobot r = robot("huskylens.h = name=huskylens, forward=8, up=10, pitch=40, hfov=120, width=1280, height=960, taught=30|31|32|33|34|35|36|37");
        r.setPose(50, -13, 90);
        SimHuskyLens h = r.huskyLenses.get("huskylens");
        assertEquals(6, h.blocks().length);
        for (int id = 1; id <= 8; id++) assertEquals("taught number " + id, 1, h.blocks(id).length);
    }

    @Test public void itAnswersTheAreYouThereRequest() throws Exception {
        assertTrue(HuskyLensCamera.answers(robot(TAUGHT).huskyLenses.get("huskylens")));
    }

    @Test public void theHardwareCheckShowsWhatItSeesWithoutWaitingInItsLoop() throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File f = tmp.newFile();
        Files.write(f.toPath(), (base + "\n" + TAUGHT + "\n").getBytes("UTF-8"));
        try (SimRun s = new SimRun(f.getPath())) {
            s.init("Hardware Check").start();
            s.robot().setPose(32, -12.74, 90);
            s.press(v -> s.g1().dpad_up = v);            // the list wraps to its last device: the HuskyLens
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("huskylens") && s.screenHas("Answers"), 3));
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("yes") && s.screenHas("taught number 1"), 3));
            assertEquals("", s.error());
        }
    }

    @Test public void aRobotConfigurationSaysItIsAHuskyLens() {
        assertEquals("huskylens", SimConfigImport.kindOf("HuskyLens"));
    }

    @Test public void theCameraClosesWhenTheOpModeStops() throws Exception {
        SimRobot r = robot(TAUGHT);
        SimCamera c = r.cameras.get("huskylens");
        r.huskyLenses.get("huskylens").blocks();
        assertEquals(1, c.openPortals());
        c.closeAll();
        assertEquals(0, c.openPortals());
        r.huskyLenses.get("huskylens").blocks();
        assertEquals("opens again for the next OpMode", 1, c.openPortals());
    }
}
