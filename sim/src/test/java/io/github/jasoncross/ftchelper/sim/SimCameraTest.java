package io.github.jasoncross.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.github.jasoncross.ftchelper.vision.TargetObservation;
import io.github.jasoncross.ftchelper.vision.LimelightTags;
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
        Files.write(f.toPath(), (base + "\ncamera.front = name=Webcam 1, forward=8, up=10, pitch=30\n"
                + "limelight.ll = name=limelight, forward=8, up=10, pitch=30\n").getBytes("UTF-8"));
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

    @Test public void limelightTagsSeesEachTagAndTheSdkReadsThePretendResult() throws Exception {
        SimRobot r = robot();
        LimelightTags ll = LimelightTags.create(r.hardwareMap(), "limelight").pipeline(3);
        ll.start();
        List<TargetObservation> seen = null;
        for (int i = 0; i < 100 && (seen == null || seen.size() < 5); i++) {
            Thread.sleep(20);
            ll.update();
            seen = ll.targets();
        }
        assertEquals("each tag, plus the group as one target: " + seen, 5, seen.size());
        assertNotNull(ll.target("RED SCORING"));
        assertTrue(ll.status(), ll.isWorking());
        TargetObservation t31 = null, t32 = null;
        for (TargetObservation t : seen) { if (t.id == 31) t31 = t; if (t.id == 32) t32 = t; }
        assertNotNull(t31);
        assertNotNull(t32);
        // Facing -x, the camera's right is +y: tag 31 (y -9.99) is right of the robot's line (y -12.74),
        // so its bearing is negative (the library's bearing is + = left); tag 32 (y -15.49) is left.
        assertTrue("tag 31 right: " + t31, t31.bearingDeg < -1);
        assertTrue("tag 32 left: " + t32, t32.bearingDeg > 1);
        assertTrue("distance along the floor: " + t31, t31.distanceIn > 35 && t31.distanceIn < 60);
        // The SDK's own objects, read from the pretend JSON.
        com.qualcomm.hardware.limelightvision.Limelight3A sdk = ll.getLimelight();
        Thread.sleep(100);                        // the pipeline switch runs in the background
        assertTrue(sdk.getLatestResult().isValid());
        assertEquals(3, sdk.getLatestResult().getPipelineIndex());
        assertEquals(4, sdk.getLatestResult().getFiducialResults().size());
        assertEquals(3, sdk.getStatus().getPipelineIndex());
        r.closeCameras();                         // what the simulator does when an OpMode stops
        assertFalse(sdk.isRunning());
    }

    @Test public void libraryTagGroupsMatchTheSdkClusters() throws Exception {
        org.firstinspires.ftc.vision.apriltag.AprilTagLibrary lib =
                org.firstinspires.ftc.vision.apriltag.AprilTagGameDatabase.getCurrentGameTagLibrary();
        for (io.github.jasoncross.ftchelper.vision.TagGroup g : io.github.jasoncross.ftchelper.vision.TagGroup.currentSeason()) {
            for (int id : g.ids()) {
                org.firstinspires.ftc.vision.apriltag.AprilTagClusterMetadata c = lib.lookupCluster(id);
                assertNotNull("tag " + id + " is in an SDK cluster", c);
                assertEquals(g.name, c.name);
                java.lang.reflect.Method member = c.getClass().getDeclaredMethod("getMemberMetadata", int.class);
                member.setAccessible(true);
                org.firstinspires.ftc.vision.apriltag.AprilTagClusterMemberMetadata m =
                        (org.firstinspires.ftc.vision.apriltag.AprilTagClusterMemberMetadata) member.invoke(c, id);
                assertEquals("tag " + id + " place along the row", m.positionInClusterPlane.get(0), g.alongRowIn(id), 1e-3);
            }
        }
    }

    @Test public void limelightGroupTargetAimsLikeTheWebcamCluster() throws Exception {
        SimRobot r = robot();
        synchronized (r) { r.headingDeg = 100; }       // 10 degrees left of the group: it is to the right
        LimelightTags ll = LimelightTags.create(r.hardwareMap(), "limelight");
        WebcamTags cam = WebcamTags.create(r.hardwareMap(), "Webcam 1");
        ll.start();
        cam.start();
        TargetObservation fromLl = null, fromCam = waitForTarget(cam, "RED SCORING");
        for (int i = 0; i < 50 && fromLl == null; i++) {
            ll.update();
            fromLl = ll.target("RED SCORING");
            Thread.sleep(20);
        }
        assertNotNull(fromLl);
        assertNotNull(fromCam);
        assertEquals(TargetObservation.CLUSTER, fromLl.id);
        // Both cameras sit 8 in forward on the robot's center line here, so the bearings nearly agree.
        assertEquals(fromCam.bearingDeg, fromLl.bearingDeg, 1.5);
        assertTrue("right of the robot: " + fromLl, fromLl.bearingDeg < -5);
        ll.close();
        cam.close();
    }

    @Test public void clusterGeometryFollowsTheFieldFileAndTheSdk() throws Exception {
        SimRobot r = robot();
        Object cam = SimVision.camera(r.cameras.get("Webcam 1").device);
        org.firstinspires.ftc.vision.apriltag.AprilTagLibrary lib =
                org.firstinspires.ftc.vision.apriltag.AprilTagGameDatabase.getCurrentGameTagLibrary();
        for (int first : new int[] {30, 34, 38, 42}) {
            // The field file's printed up and the SDK's member order must agree: ids rise toward printed right.
            double[] t0 = SimVision.tag(cam, first), t3 = SimVision.tag(cam, first + 3);
            double[][] axes = org.firstinspires.ftc.vision.apriltag.AprilTagProcessorImpl.singleTagAxes(t0);
            double along = (t3[0] - t0[0]) * axes[0][0] + (t3[1] - t0[1]) * axes[0][1] + (t3[2] - t0[2]) * axes[0][2];
            assertEquals("tag " + first + " to " + (first + 3) + ": 13 in toward printed right", 13, along, 0.05);
        }
        // RED SCORING's aiming point: 7.19 in toward the printed top and 5.62 in behind the row (SDK numbers);
        // for these tags (face +x, 60 degrees down; printed top away from the field centre) that is
        // 9.0 in toward -x and 1.3 in up from the middle of the row (-11.61, -12.74, 35.53).
        double[] o = org.firstinspires.ftc.vision.apriltag.AprilTagProcessorImpl.clusterOrigin(cam, lib.lookupCluster(30));
        assertEquals(-11.61 - 9.04, o[0], 0.1);
        assertEquals(-12.74, o[1], 0.1);
        assertEquals(35.53 + 1.27, o[2], 0.1);
    }

    @Test public void limelightResultIsNeverNullAndFollowsThePollRate() throws Exception {
        SimRobot r = robot();
        com.qualcomm.hardware.limelightvision.Limelight3A ll = r.limelights.get("limelight");
        ll.setPollRateHz(5);                              // a new result every 200 ms
        ll.start();
        assertNotNull("like the SDK: an empty result before the first picture", ll.getLatestResult());
        assertFalse(ll.getLatestResult().isValid());
        Thread.sleep(600);
        double first = ll.getLatestResult().getTimestamp();
        assertTrue(ll.getLatestResult().isValid());
        Thread.sleep(60);
        assertEquals("not a new result before 200 ms", first, ll.getLatestResult().getTimestamp(), 1e-9);
        Thread.sleep(200);
        assertTrue("a new result after 200 ms", ll.getLatestResult().getTimestamp() > first);
        ll.stop();
        assertNotNull(ll.getLatestResult());
        assertFalse(ll.getLatestResult().isValid());
    }
}
