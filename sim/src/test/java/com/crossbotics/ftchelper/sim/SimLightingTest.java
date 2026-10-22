package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.vision.LightingSweep;
import com.crossbotics.ftchelper.vision.LimelightTags;
import com.crossbotics.ftchelper.vision.TargetSource;
import com.crossbotics.ftchelper.vision.WebcamTags;
import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The venue-lighting test on pretend cameras whose "lighting" lets them find tags only with some
 * settings (goodPipelines / goodExposureMs in the robot file): it must find that setting and keep it.
 */
public class SimLightingTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private SimRobot robot(String cameraLines) throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File f = tmp.newFile("lighting.properties");
        Files.write(f.toPath(), (base + "\n" + cameraLines + "\n").getBytes("UTF-8"));
        SimRobot r = SimRobot.load(f.toPath());
        // Facing the red CELL group of tags 30-33 (as in SimCameraTest).
        r.setPose(32, -12.74, 90);
        return r;
    }

    // The loop, as the robot runs it (here with a short pause per pass: this is a test, not robot code).
    private static void runUntilDone(TargetSource cam, LightingSweep sweep) throws InterruptedException {
        for (int i = 0; i < 600 && !sweep.isDone(); i++) {
            cam.update();
            Thread.sleep(20);
        }
    }

    @Test public void limelightKeepsThePipelineThatSeesTagsHere() throws Exception {
        SimRobot r = robot("limelight.ll = name=limelight, forward=8, up=10, pitch=30, goodPipelines=2");
        LimelightTags cam = LimelightTags.create(r.hardwareMap(), "limelight").autoPipelines(0, 1, 2);
        cam.start();
        runUntilDone(cam, cam.lighting());
        assertTrue(cam.lighting().status(), cam.lighting().isDone());
        assertEquals(cam.lighting().scores(), "pipeline 2", cam.lighting().chosenLabel());
        assertEquals(0, cam.lighting().hitsOf(0));
        Thread.sleep(100);
        cam.update();
        assertNotNull("tags seen on the kept pipeline: " + cam.targets(), cam.target(31));
        assertTrue(cam.status(), cam.status().startsWith("pipeline 2"));
        cam.close();
    }

    @Test public void webcamKeepsTheExposureThatSeesTagsHere() throws Exception {
        SimRobot r = robot("camera.front = name=Webcam 1, forward=8, up=10, pitch=30, goodExposureMs=5-8");
        WebcamTags cam = WebcamTags.create(r.hardwareMap(), "Webcam 1").autoExposure();
        cam.start();
        runUntilDone(cam, cam.lighting());
        assertTrue(cam.lighting().status(), cam.lighting().isDone());
        assertEquals(cam.lighting().scores(), "6 ms exposure", cam.lighting().chosenLabel());
        assertEquals(6, r.cameras.get("Webcam 1").exposureMs);
        cam.close();
    }

    @Test public void noTagsAnywhereKeepsTheFirstSettingAtStart() throws Exception {
        SimRobot r = robot("limelight.ll = name=limelight, forward=8, up=10, pitch=30");
        synchronized (r) { r.headingDeg = -90; }          // facing away from every tag
        LimelightTags cam = LimelightTags.create(r.hardwareMap(), "limelight").autoPipelines(1, 0);
        cam.start();
        for (int i = 0; i < 100; i++) {
            cam.update();
            Thread.sleep(20);
        }
        assertFalse("still trying in INIT", cam.lighting().isDone());
        cam.matchStarted();
        assertTrue("stops at START", cam.lighting().isDone());
        assertTrue(cam.lighting().gaveUp());
        assertEquals("pipeline 1", cam.lighting().chosenLabel());
        Thread.sleep(100);
        assertEquals("the Limelight is back on the first pipeline", 1, r.cameras.get("limelight").pipeline);
        cam.close();
    }

    @Test public void trialPicturesAreNeverTargetsInTheMatch() throws Exception {
        // Tags only on pipeline 2, and time after START: until the test finds pipeline 2, no targets.
        SimRobot r = robot("limelight.ll = name=limelight, forward=8, up=10, pitch=30, goodPipelines=2");
        LimelightTags cam = LimelightTags.create(r.hardwareMap(), "limelight").autoPipelines(0, 1, 2);
        cam.lighting().giveUpAfterStartMs(10_000);
        cam.start();
        cam.matchStarted();
        boolean sawTargetWhileTrying = false;
        for (int i = 0; i < 600 && !cam.lighting().isDone(); i++) {
            cam.update();
            if (!cam.lighting().isDone() && !cam.targets().isEmpty()) sawTargetWhileTrying = true;
            Thread.sleep(20);
        }
        assertTrue(cam.lighting().isDone());
        assertFalse("no target while a pipeline was being tried", sawTargetWhileTrying);
        assertEquals("pipeline 2", cam.lighting().chosenLabel());
        Thread.sleep(100);
        cam.update();
        assertNotNull("targets again once done", cam.target(31));
        cam.close();
    }
}
