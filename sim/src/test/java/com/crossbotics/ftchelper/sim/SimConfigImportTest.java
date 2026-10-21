package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A robot configuration (XML) becomes a draft robot file that loads. */
public class SimConfigImportTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void theExampleConfigurationGivesALoadableDraft() throws Exception {
        String xml = new String(Files.readAllBytes(new File("configs/example-config.xml").toPath()), "UTF-8");
        List<SimConfigImport.Device> devices = SimConfigImport.read(xml);
        assertEquals(17, devices.size());
        assertEquals("the four drive motors by their names (and not the flywheel)",
                Arrays.asList("leftFront", "rightFront", "leftBack", "rightBack"), Arrays.asList(SimConfigImport.driveMotors(devices)));
        List<String> questions = new ArrayList<>();
        String text = SimConfigImport.draft("example", devices, "biobuzz-2026", questions);
        assertTrue(text.contains("motor.arm = free, part=rev-core-hex"));
        assertTrue(text.contains("camera.webcam_1 = name=Webcam 1"));
        assertTrue(text.contains("limelight.limelight = name=limelight"));
        assertTrue(text.contains("#   odo (goBILDAPinpoint)"));
        assertTrue(questions.size() >= 8);
        File f = tmp.newFile("draft.properties");
        Files.write(f.toPath(), text.getBytes("UTF-8"));
        SimRobot r = SimRobot.load(f.toPath());
        assertTrue("every configured motor is in the robot", r.motors.keySet().containsAll(Arrays.asList("lift", "arm", "flywheel", "feeder")));
        assertEquals(4, r.wheels.size());
        assertTrue(r.cameras.containsKey("Webcam 1") && r.cameras.containsKey("limelight"));
    }

    @Test public void shortDriveNamesAndUnknownLayouts() throws Exception {
        String xml = "<Robot><LynxUsbDevice name=\"p\"><LynxModule name=\"Control Hub\" port=\"173\">"
                + "<goBILDA5202SeriesMotor name=\"fl\" port=\"0\"/><goBILDA5202SeriesMotor name=\"fr\" port=\"1\"/>"
                + "<goBILDA5202SeriesMotor name=\"bl\" port=\"2\"/><goBILDA5202SeriesMotor name=\"br\" port=\"3\"/>"
                + "</LynxModule></LynxUsbDevice></Robot>";
        assertEquals(Arrays.asList("fl", "fr", "bl", "br"), Arrays.asList(SimConfigImport.driveMotors(SimConfigImport.read(xml))));
        String odd = "<Robot><LynxUsbDevice name=\"p\"><LynxModule name=\"Control Hub\" port=\"173\">"
                + "<Motor name=\"a\" port=\"0\"/><Motor name=\"b\" port=\"1\"/></LynxModule></LynxUsbDevice></Robot>";
        org.junit.Assert.assertNull(SimConfigImport.driveMotors(SimConfigImport.read(odd)));
        try {
            SimConfigImport.read("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><Robot>&e;</Robot>");
            org.junit.Assert.fail("a file with a DOCTYPE is refused");
        } catch (Exception expected) {
            // refused, as it should be
        }
    }

    @Test public void reviewersCases() throws Exception {
        String xml = "<Robot><LynxUsbDevice name=\"p\"><LynxModule name=\"Control Hub\" port=\"173\">"
                + "<goBILDA5202SeriesMotor name=\"left front\" port=\"0\"/><goBILDA5202SeriesMotor name=\"FRMotor\" port=\"1\"/>"
                + "<goBILDA5202SeriesMotor name=\"drive_bl\" port=\"2\"/><goBILDA5202SeriesMotor name=\"rightBack\" port=\"3\"/>"
                + "<Servo name=\"speed\" port=\"0\"/><Servo name=\"claw grip\" port=\"1\"/>"
                + "<ControlHubImuBHI260AP name=\"imu\" port=\"0\" bus=\"0\"/></LynxModule>"
                + "<LynxModule name=\"Expansion Hub 2\" port=\"2\"><goBILDA5202SeriesMotor name=\"leftFrontIntake\" port=\"0\"/>"
                + "<LynxEmbeddedIMU name=\"imu2\" port=\"0\" bus=\"0\"/></LynxModule></LynxUsbDevice>"
                + "<Webcam name=\"Webcam 1\" serialNumber=\"a\"/><Webcam name=\"webcam_1\" serialNumber=\"b\"/></Robot>";
        List<SimConfigImport.Device> devices = SimConfigImport.read(xml);
        assertEquals("names in any style; an intake is not a wheel",
                Arrays.asList("left front", "FRMotor", "drive_bl", "rightBack"), Arrays.asList(SimConfigImport.driveMotors(devices)));
        List<String> q = new ArrayList<>();
        String text = SimConfigImport.draft("x", devices, "biobuzz-2026", q);
        assertTrue("one IMU line", text.contains("imu = imu\n") && !text.contains("imu = imu2\n"));
        assertTrue("two webcams, two keys", text.contains("camera.webcam_1 = name=Webcam 1") && text.contains("camera.webcam_1_2 = name=webcam_1"));
        File f = tmp.newFile("names.properties");
        Files.write(f.toPath(), text.getBytes("UTF-8"));
        SimRobot r = SimRobot.load(f.toPath());
        assertTrue("a name with a space works", r.servos.containsKey("claw grip") && r.motors.containsKey("left front"));
        assertTrue("no servo called speed", !r.servos.containsKey("speed"));
        String tank = "<Robot><LynxUsbDevice name=\"p\"><LynxModule name=\"Control Hub\" port=\"173\">"
                + "<goBILDA5202SeriesMotor name=\"leftDrive\" port=\"0\"/><goBILDA5202SeriesMotor name=\"rightDrive\" port=\"1\"/>"
                + "</LynxModule></LynxUsbDevice></Robot>";
        String[] two = SimConfigImport.driveMotors(SimConfigImport.read(tank));
        assertEquals(Arrays.asList("leftDrive", "rightDrive"), Arrays.asList(two));
        String tankText = SimConfigImport.draft("t", SimConfigImport.read(tank), "biobuzz-2026", new ArrayList<>());
        assertTrue(tankText.contains("drive.type = tank"));
        File g = tmp.newFile("tank.properties");
        Files.write(g.toPath(), tankText.getBytes("UTF-8"));
        assertEquals(2, SimRobot.load(g.toPath()).wheels.size());
    }
}
