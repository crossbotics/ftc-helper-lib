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
        assertTrue(robot, robot.contains("add(PieceSensor.color(\"color\").kinds(\"PIECE\"));"));
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

    // A Pinpoint in the configuration becomes the odometry line (offsets to measure), a Tunables.Odometry
    // section, and the simulator robot file's odometry lines.
    @Test public void aPinpointBecomesTheOdometry() throws Exception {
        String xml = "<Robot><LynxUsbDevice name=\"p\"><LynxModule name=\"Control Hub\" port=\"173\">"
                + "<goBILDA5202SeriesMotor name=\"front_left\" port=\"0\"/><goBILDA5202SeriesMotor name=\"front_right\" port=\"1\"/>"
                + "<goBILDA5202SeriesMotor name=\"back_left\" port=\"2\"/><goBILDA5202SeriesMotor name=\"back_right\" port=\"3\"/>"
                + "<goBILDAPinpoint name=\"pinpoint\" port=\"1\" bus=\"1\"/>"
                + "</LynxModule></LynxUsbDevice></Robot>";
        Map<String, String> f = SimNewRobot.files(SimConfigImport.read(xml), "org.firstinspires.ftc.teamcode");
        assertTrue(f.get("Robot.java"), f.get("Robot.java").contains("odometry(Odometry.pinpoint(\"pinpoint\").offsetsMm(0, 0));"));
        assertTrue(f.get("Tunables.java"), f.get("Tunables.java").contains("public static class Odometry"));
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

    // A Blinkin and a servo port named like a light become Lights lines; the robot file gets light. lines.
    @Test public void lightsBecomeLightsModules() throws Exception {
        String xml = "<Robot><LynxUsbDevice name=\"p\"><LynxModule name=\"Control Hub\" port=\"173\">"
                + "<goBILDA5202SeriesMotor name=\"left_drive\" port=\"0\"/><goBILDA5202SeriesMotor name=\"right_drive\" port=\"1\"/>"
                + "<Servo name=\"status_led\" port=\"0\"/><RevBlinkinLedDriver name=\"blinkin\" port=\"1\"/>"
                + "</LynxModule></LynxUsbDevice></Robot>";
        List<SimConfigImport.Device> devices = SimConfigImport.read(xml);
        Map<String, String> f = SimNewRobot.files(devices, "org.firstinspires.ftc.teamcode");
        String robot = f.get("Robot.java");
        assertTrue(robot, robot.contains("Lights.gobilda(\"status_led\")"));
        assertTrue(robot, robot.contains("Lights.blinkin(\"blinkin\")"));
        assertTrue(robot, robot.contains("import com.crossbotics.ftchelper.robot.Lights;"));
        String draft = SimConfigImport.draft("lights", devices, "biobuzz-2026", new ArrayList<>());
        assertTrue(draft, draft.contains("light.status_led = gobilda") && draft.contains("light.blinkin = blinkin"));
        compileAndLoad(f);
    }

    @Test public void aHoodServoGoesOnTheLauncher() throws Exception {
        String xml = "<Robot><LynxUsbDevice name=\"p\"><LynxModule name=\"Control Hub\" port=\"173\">"
                + "<goBILDA5202SeriesMotor name=\"left_drive\" port=\"0\"/><goBILDA5202SeriesMotor name=\"right_drive\" port=\"1\"/>"
                + "<goBILDA5202SeriesMotor name=\"flywheel\" port=\"2\"/>"
                + "<Servo name=\"indexer\" port=\"0\"/><Servo name=\"hood\" port=\"1\"/>"
                + "</LynxModule></LynxUsbDevice></Robot>";
        Map<String, String> f = SimNewRobot.files(SimConfigImport.read(xml), "org.firstinspires.ftc.teamcode");
        String robot = f.get("Robot.java");
        assertTrue(robot, robot.contains(".feeder(Indexer.servo(\"indexer\"))"));
        assertTrue(robot, robot.contains(".hood(Hood.servo(\"hood\"))"));
        assertTrue(robot, robot.contains("Hood Finder"));
        assertTrue(f.get("Tunables.java"), f.get("Tunables.java").contains("public static class Hood {"));
        assertTrue(f.get("Tunables.java"), f.get("Tunables.java").contains("posA = MEASURE, posB = MEASURE"));
        compileAndLoad(f);
    }

    // The written files, and (when Tunables.java has MEASURE lines) a second time with numbers in their place:
    // the files as written INIT until the first MEASURE and stop there, naming it; with the numbers written
    // in (as a team does after the finders), INIT works.
    private void compileAndLoad(Map<String, String> f) throws Exception {
        String tunables = f.get("Tunables.java");
        if (tunables == null || !tunables.contains("= MEASURE")) {
            compileAndLoad(f, null);
            return;
        }
        compileAndLoad(f, "= MEASURE:");
        Map<String, String> measured = new java.util.LinkedHashMap<>(f);
        measured.put("Tunables.java", measured(tunables));
        compileAndLoad(measured, null);
    }

    // Numbers in place of MEASURE, as the finders would give: a range's ends (max above min) and positions.
    private static String measured(String tunables) {
        return tunables.replaceAll("\\bmin = MEASURE", "min = 0").replaceAll("\\bmax = MEASURE", "max = 1000")
                .replaceAll("\\bposA = MEASURE", "posA = 0.2").replaceAll("\\bposB = MEASURE", "posB = 0.7")
                .replace("= MEASURE", "= 0.5");
    }

    private void compileAndLoad(Map<String, String> f, String initStops) throws Exception {
        File src = tmp.newFolder();
        File out = tmp.newFolder();
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
            assertFalse(robot.notes().toString(), robot.notes().toString().contains("not written"));   // every section is in Tunables.java
            SimFromRobot.Result r = SimFromRobot.merge(null, robot.simLines(), "Robot", "biobuzz-2026");
            Path file = tmp.newFile().toPath();
            Files.write(file, r.text.getBytes(StandardCharsets.UTF_8));
            SimRobot sim = SimRobot.load(file);
            // INIT on the simulated hardware: no error (INIT stops on a Tunables field no part reads).
            RobotDefinition fresh = (RobotDefinition) loader.loadClass("org.firstinspires.ftc.teamcode.Robot")
                    .getDeclaredConstructor().newInstance();
            try {
                fresh.setup(sim.hardwareMap());
            } catch (IllegalStateException e) {
                if (initStops == null || !e.getMessage().contains(initStops)) throw e;
                // Stopped on a MEASURE, as it should. The robot file has a stand-in for each one: with them in
                // place of MEASURE (as the simulator does at INIT), INIT works.
                assertTrue(r.text, r.text.contains("tunable."));
                for (java.util.Map.Entry<String, String> st : sim.standIns.entrySet()) {
                    String section = st.getKey().substring(0, st.getKey().indexOf('.'));
                    java.lang.reflect.Field fld = loader.loadClass("org.firstinspires.ftc.teamcode.Tunables$" + section)
                            .getDeclaredField(st.getKey().substring(section.length() + 1));
                    assertTrue(st.getKey(), com.crossbotics.ftchelper.robot.Starting.isMeasure(fld.getDouble(null)));
                    fld.setDouble(null, Double.parseDouble(st.getValue()));
                }
                RobotDefinition standIns = (RobotDefinition) loader.loadClass("org.firstinspires.ftc.teamcode.Robot")
                        .getDeclaredConstructor().newInstance();
                standIns.setup(SimRobot.load(file).hardwareMap());
                standIns.all().stop();
                return;
            }
            if (initStops != null) org.junit.Assert.fail("INIT should stop on a MEASURE line");
            fresh.all().stop();
        }
    }

    // Android Studio's run buttons (sim/run-buttons): each file is a Gradle run configuration of a task that
    // tasks.gradle makes; newRobot, :sim:installButtons and each Gradle sync copy them into the robot project's
    // .idea/runConfigurations once (Android Studio does not read a .run folder).
    @Test public void theRunButtonsAreAddedOnceAndRunTheSimulatorTasks() throws Exception {
        Path buttons = new File("run-buttons").toPath();
        String tasks = new String(Files.readAllBytes(buttons.resolve("tasks.gradle")), StandardCharsets.UTF_8);
        List<String> names = new ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.list(buttons)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".run.xml")).sorted()::iterator) {
                org.w3c.dom.Element conf = (org.w3c.dom.Element) javax.xml.parsers.DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder().parse(f.toFile()).getElementsByTagName("configuration").item(0);
                assertEquals(f.toString(), "GradleRunConfiguration", conf.getAttribute("type"));
                names.add(conf.getAttribute("name"));
                String xml = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                assertTrue(xml, xml.contains("<option name=\"externalProjectPath\" value=\"$PROJECT_DIR$\" />"));
                java.util.regex.Matcher task = java.util.regex.Pattern.compile("<option value=\"(\\w+)\" />").matcher(xml);
                assertTrue(xml, task.find());
                assertTrue("tasks.gradle makes the task " + task.group(1), tasks.contains("name: '" + task.group(1) + "'"));
            }
        }
        assertEquals(java.util.Arrays.asList("Simulator", "Simulator tests"), names);

        Path project = tmp.newFolder("robot-project").toPath();
        assertEquals(java.util.Arrays.asList("Simulator", "Simulator_tests"), SimNewRobot.addRunButtons(buttons, project));
        assertTrue(Files.isRegularFile(project.resolve(".idea/runConfigurations/Simulator.xml")));
        Files.write(project.resolve(".idea/runConfigurations/Simulator.xml"), "the team's own".getBytes(StandardCharsets.UTF_8));
        assertTrue("only the missing ones", SimNewRobot.addRunButtons(buttons, project).isEmpty());
        assertEquals("the team's own", new String(Files.readAllBytes(project.resolve(".idea/runConfigurations/Simulator.xml")), StandardCharsets.UTF_8));
    }
}
