package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** ./gradlew :sim:newRobot: the five files from a robot configuration compile, and make a robot file. */
public class SimNewRobotTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static Map<String, String> fromExampleConfig() throws Exception {
        String xml = new String(Files.readAllBytes(new File("configs/example-config.xml").toPath()), StandardCharsets.UTF_8);
        return SimNewRobot.files(SimConfigImport.read(xml), "org.firstinspires.ftc.teamcode");
    }

    @Test public void eachDeviceGetsItsModuleAndTheUnsureOnesATodo() throws Exception {
        Map<String, String> f = fromExampleConfig();
        assertEquals(5, f.size());
        String robot = f.get("Robot.java");
        assertTrue(robot, robot.contains("drive(Drive.mecanum(\"leftFront\", \"rightFront\", \"leftBack\", \"rightBack\")"));
        assertTrue(robot, robot.contains(".imu(\"imu\", LogoFacingDirection.UP, UsbFacingDirection.FORWARD)"));
        assertTrue(robot, robot.contains("lift = add(Lift.slide(\"lift\").bottomSwitch(\"lift_bottom\"));"));
        assertTrue(robot, robot.contains("arm = add(Arm.motor(\"arm\", \"rev-core-hex\"));"));
        assertTrue(robot, robot.contains("Launcher.flywheel(\"flywheel\", \"gobilda-5203-6000\")"));
        assertTrue(robot, robot.contains(".feeder(Indexer.motor(\"feeder\"))"));
        assertTrue(robot, robot.contains("claw = add(Claw.servo(\"claw\"));"));
        assertTrue(robot, robot.contains("intake = add(Intake.crServos(\"intake\"));"));
        assertTrue(robot, robot.contains("Camera.limelight(\"limelight\").goal(\"BLUE SCORING\", \"RED SCORING\")"));
        assertTrue(robot, robot.contains("// TODO RevColorSensorV3 \"color\": no module for it yet"));
        String tunables = f.get("Tunables.java");
        assertTrue(tunables, tunables.contains("public static class Feeder {"));
        assertTrue(tunables, tunables.contains("public static class Webcam1 {") || !tunables.contains("Webcam1"));
        assertTrue(f.get("Actions.java"), f.get("Actions.java").contains("public static Action scoreHigh(Robot r)"));
    }

    @Test public void theFilesCompileAndMakeASimulatorRobot() throws Exception {
        compileAndLoad(fromExampleConfig());
    }

    @Test public void oddDeviceNamesStillCompile() throws Exception {
        String xml = "<Robot><LynxUsbDevice name=\"p\"><LynxModule name=\"Control Hub\" port=\"173\">"
                + "<goBILDA5202SeriesMotor name=\"leftDrive\" port=\"0\"/><goBILDA5202SeriesMotor name=\"rightDrive\" port=\"1\"/>"
                + "<goBILDA5202SeriesMotor name=\"1_lift\" port=\"2\"/>"
                + "<Servo name=\"left-claw\" port=\"0\"/><Servo name=\"left_claw\" port=\"1\"/><Servo name=\"new\" port=\"2\"/>"
                + "</LynxModule></LynxUsbDevice></Robot>";
        Map<String, String> f = SimNewRobot.files(SimConfigImport.read(xml), "org.firstinspires.ftc.teamcode");
        String robot = f.get("Robot.java");
        assertTrue(robot, robot.contains("Lift.slide(\"1_lift\").tunables(\"Part1Lift\")"));
        assertTrue(robot, robot.contains("Claw.servo(\"left_claw\").tunables(\"LeftClaw2\")"));
        compileAndLoad(f);
    }

    @Test public void intakeServosRunWithAnIntakeMotor() throws Exception {
        String xml = "<Robot><LynxUsbDevice name=\"p\"><LynxModule name=\"Control Hub\" port=\"173\">"
                + "<goBILDA5202SeriesMotor name=\"left_drive\" port=\"0\"/><goBILDA5202SeriesMotor name=\"right_drive\" port=\"1\"/>"
                + "<goBILDA5202SeriesMotor name=\"intake\" port=\"2\"/>"
                + "<ContinuousRotationServo name=\"left_intake_servo\" port=\"0\"/><ContinuousRotationServo name=\"right_intake_servo\" port=\"1\"/>"
                + "</LynxModule></LynxUsbDevice></Robot>";
        Map<String, String> f = SimNewRobot.files(SimConfigImport.read(xml), "org.firstinspires.ftc.teamcode");
        String robot = f.get("Robot.java");
        assertTrue(robot, robot.contains("intake = add(Intake.motor(\"intake\")\n                .withCrServos(\"left_intake_servo\", \"right_intake_servo\"));"));
        assertFalse(robot, robot.contains("Intake.crServos"));
        compileAndLoad(f);
    }

    private void compileAndLoad(Map<String, String> f) throws Exception {
        File src = tmp.newFolder("src");
        File out = tmp.newFolder("classes");
        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(out.getPath());
        args.add("-cp");
        args.add(System.getProperty("java.class.path"));
        args.add("-proc:none");
        for (Map.Entry<String, String> e : f.entrySet()) {
            Path p = src.toPath().resolve("org/firstinspires/ftc/teamcode").resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue().getBytes(StandardCharsets.UTF_8));
            args.add(p.toString());
        }
        // The JDK's own compiler, found while the test runs (the Android build cannot see javax.tools).
        Object javac = Class.forName("javax.tools.ToolProvider").getMethod("getSystemJavaCompiler").invoke(null);
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int code = (Integer) Class.forName("javax.tools.Tool").getMethod("run", java.io.InputStream.class, java.io.OutputStream.class,
                java.io.OutputStream.class, String[].class).invoke(javac, null, null, err, args.toArray(new String[0]));
        assertEquals(err.toString(), 0, code);

        try (URLClassLoader loader = new URLClassLoader(new URL[] {out.toURI().toURL()}, getClass().getClassLoader())) {
            RobotDefinition robot = (RobotDefinition) loader.loadClass("org.firstinspires.ftc.teamcode.Robot")
                    .getDeclaredConstructor().newInstance();
            assertFalse(robot.notes().toString(), robot.notes().toString().contains("missing"));   // every section is in Tunables.java
            SimFromRobot.Result r = SimFromRobot.merge(null, robot.simLines(), "Robot", "biobuzz-2026");
            Path file = tmp.newFile("robot.properties").toPath();
            Files.write(file, r.text.getBytes(StandardCharsets.UTF_8));
            SimRobot.load(file);
        }
    }
}
