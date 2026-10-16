package io.github.jasoncross.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.github.jasoncross.ftchelper.vision.TargetObservation;
import io.github.jasoncross.ftchelper.vision.WebcamTags;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.apriltag.AprilTagClusterDetection;
import org.firstinspires.ftc.vision.apriltag.AprilTagDetection;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The pretend webcam, through the library's WebcamTags and the SDK's own builders. */
public class SimCameraTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private SimRobot robot() throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File f = tmp.newFile("camera.properties");
        Files.write(f.toPath(), (base + "\ncamera.front = name=Webcam 1, forward=8, up=10, pitch=30\n").getBytes("UTF-8"));
        SimRobot r = SimRobot.load(f.toPath());
        // Facing -x, level with the red CELL group of tags 30-33 (they face +x and down, 35.5 in up).
        r.setPose(32, -12.74, 90);
        return r;
    }

    private static TargetObservation waitForTarget(WebcamTags cam, String name) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            cam.update();
            for (TargetObservation t : cam.targets()) if (t.name.equals(name)) return t;
            Thread.sleep(20);
        }
        return null;
    }

    @Test public void webcamTagsSeesTheCellGroupAsTheSdkWouldAndBearingIsPositiveLeft() throws Exception {
        SimRobot r = robot();
        WebcamTags cam = WebcamTags.create(r.hardwareMap(), "Webcam 1");
        cam.start();
        assertFalse("a webcam takes about 1 s to start", cam.isWorking());
        TargetObservation t = waitForTarget(cam, "RED SCORING");
        assertNotNull("BIOBUZZ tags 30-33 are one cluster in the SDK's tag library: " + cam.targets(), t);
        assertTrue(cam.status(), cam.isWorking());
        assertEquals(TargetObservation.CLUSTER, t.id);
        assertEquals("straight ahead", 0, t.bearingDeg, 0.5);
        assertEquals("flat distance to the cluster's aiming point", 52.1, t.distanceIn, 1.5);
        // Turn the robot 10 degrees LEFT: the target is now 10 degrees to the right (negative bearing).
        synchronized (r) { r.headingDeg = 100; }
        Thread.sleep(80);
        t = waitForTarget(cam, "RED SCORING");
        assertNotNull(t);
        assertEquals(-10, t.bearingDeg, 1.0);
        // Turned away: nothing.
        synchronized (r) { r.headingDeg = -90; }
        Thread.sleep(150);
        cam.update();
        assertTrue("facing away sees nothing: " + cam.targets(), cam.targets().isEmpty());
        cam.close();
        assertEquals(0, r.cameras.get("Webcam 1").openPortals());
    }

    @Test public void sdkBuildersMakeThePretendClassesAndStopClosesThem() throws Exception {
        SimRobot r = robot();
        AprilTagProcessor tags = AprilTagProcessor.easyCreateWithDefaults();
        VisionPortal portal = VisionPortal.easyCreateWithDefaults(
                r.hardwareMap().get(org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName.class, "Webcam 1"), tags);
        List<AprilTagDetection> d = null;
        for (int i = 0; i < 100 && (d == null || d.isEmpty()); i++) {
            Thread.sleep(20);
            d = tags.getDetections();
        }
        assertEquals(VisionPortal.CameraState.STREAMING, portal.getCameraState());
        assertEquals(1, d.size());
        AprilTagClusterDetection c = (AprilTagClusterDetection) d.get(0);
        assertEquals(100, c.percentClusterFound);
        assertNotNull(c.ftcPose);
        assertEquals("ftcPose.range is flat: hypot(x, y)", Math.hypot(c.ftcPose.x, c.ftcPose.y), c.ftcPose.range, 1e-9);
        assertEquals("seen by the page", "[30, 31, 32, 33]", r.cameras.get("Webcam 1").seenIds().toString());
        r.closeCameras();                       // what the simulator does when an OpMode stops
        assertEquals(VisionPortal.CameraState.CAMERA_DEVICE_CLOSED, portal.getCameraState());
    }
}
