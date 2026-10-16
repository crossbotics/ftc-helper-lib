package io.github.jasoncross.ftchelper.sim;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Makes a first robot file from the robot configuration the Driver Station saved (an XML file on the
 * Control Hub, /sdcard/FIRST/<name>.xml): every motor, servo, CR servo, IMU, switch and camera with its
 * exact name, the motors the configuration's motor type pins down matched to the parts catalog, and a
 * TODO for everything a person (or the robot-file skill) still has to answer. The draft always loads in
 * the simulator, so it can be checked at once.
 *
 * ./gradlew :sim:fromConfig -Pconfig=path/to/config.xml [-Pout=simulator/robot.properties] [-Pfield=biobuzz-2026]
 */
public final class SimConfigImport {
    private SimConfigImport() { }

    /** One configured device. */
    static final class Device {
        final String name, tag, module;
        final int port;

        Device(String name, String tag, String module, int port) {
            this.name = name;
            this.tag = tag;
            this.module = module;
            this.port = port;
        }
    }

    // Motor types that tell the gearbox: configuration tag -> parts catalog id.
    static final Map<String, String> MOTOR_PARTS = new LinkedHashMap<>();
    static {
        MOTOR_PARTS.put("RevRobotics20HDHexMotor", "rev-hd-hex-20");
        MOTOR_PARTS.put("RevRobotics40HDHexMotor", "rev-hd-hex-40");
        MOTOR_PARTS.put("RevRoboticsCoreHexMotor", "rev-core-hex");
        MOTOR_PARTS.put("NeveRest20Gearmotor", "am-neverest-orbital-20");
        MOTOR_PARTS.put("NeveRest3.7v1Gearmotor", "am-neverest-orbital-3-7");
        MOTOR_PARTS.put("NeveRest40Gearmotor", "am-neverest-classic-40");
        MOTOR_PARTS.put("NeveRest60Gearmotor", "am-neverest-classic-60");
    }

    // Motor types that need a question: the family is known, the gear ratio is not.
    static final Map<String, String> MOTOR_FAMILIES = new LinkedHashMap<>();
    static {
        MOTOR_FAMILIES.put("goBILDA5202SeriesMotor", "a goBILDA Yellow Jacket (5202/5203/5204): which RPM? part=gobilda-5203-<rpm>");
        MOTOR_FAMILIES.put("goBILDA5201SeriesMotor", "an old goBILDA 5201: closest Yellow Jacket part, or rpm= and ticksPerRev=");
        MOTOR_FAMILIES.put("RevRoboticsUltraplanetaryHDHexMotor", "a REV HD Hex with UltraPlanetary cartridges: which ratio? part=rev-hd-hex-up<ratio>");
        MOTOR_FAMILIES.put("Motor", "an \"unspecified\" motor: which motor and ratio? (part= from the catalog, or rpm= and ticksPerRev=)");
        MOTOR_FAMILIES.put("StudicaMaverick", "a Studica Maverick: not in the catalog yet: rpm= and ticksPerRev= from Studica's page");
        MOTOR_FAMILIES.put("Matrix12vMotor", "a Matrix 12 V motor: rpm= and ticksPerRev=");
        MOTOR_FAMILIES.put("TetrixMotor", "a TETRIX motor: rpm= and ticksPerRev=");
    }

    /** Reads the devices from a configuration XML file. */
    static List<Device> read(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setExpandEntityReferences(false);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);   // no tricks in the file
        Document doc = f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        List<Device> out = new ArrayList<>();
        walk(doc.getDocumentElement(), "", out);
        return out;
    }

    private static void walk(Element e, String module, List<Device> out) {
        String tag = e.getTagName();
        String name = e.getAttribute("name");
        String here = module;
        if (tag.equals("LynxModule") || tag.equals("ServoHub")) here = name.isEmpty() ? tag : name;
        boolean container = tag.equals("Robot") || tag.equals("LynxUsbDevice") || tag.equals("LynxModule") || tag.equals("ServoHub");
        if (!container && !name.isEmpty()) {
            int port = -1;
            try { port = Integer.parseInt(e.getAttribute("port")); } catch (NumberFormatException ignored) { }
            out.add(new Device(name, tag, here, port));
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) walk((Element) n, here, out);
        }
    }

    static boolean isMotor(String tag) {
        return MOTOR_PARTS.containsKey(tag) || MOTOR_FAMILIES.containsKey(tag) || tag.endsWith("Motor") || tag.endsWith("Gearmotor");
    }

    static String kindOf(String tag) {
        if (isMotor(tag)) return "motor";
        if (tag.equals("Servo") || tag.equals("ServoFullRange")) return "servo";
        if (tag.equals("ContinuousRotationServo")) return "crservo";
        if (tag.equals("ControlHubImuBHI260AP") || tag.equals("LynxEmbeddedIMU") || tag.equals("RevExternalImu")) return "imu";
        if (tag.equals("RevTouchSensor") || tag.equals("DigitalDevice") || tag.equals("ModernRoboticsAnalogTouchSensor")) return "touch";
        if (tag.equals("Webcam")) return "webcam";
        if (tag.equals("EthernetDevice")) return "limelight";
        return "other";
    }

    // The drivetrain's four motors from their names (front/back, left/right; fl, frontLeft, leftFront ...).
    static String[] driveMotors(List<Device> devices) {
        String[] order = new String[4];   // FL, FR, BL, BR
        for (Device d : devices) {
            if (!kindOf(d.tag).equals("motor")) continue;
            String raw = d.name.toLowerCase(Locale.US), n = raw.replaceAll("[^a-z]", "");
            // short names (fl fr bl br, lf rf lb rb, rl rr) only as a whole word: "flywheel" is not front-left
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(fl|fr|bl|br|lf|rf|lb|rb|rl|rr)(?![a-z])").matcher(raw);
            String two = m.find() ? m.group(1) : "";
            boolean front = n.contains("front") || two.equals("fl") || two.equals("fr") || two.equals("lf") || two.equals("rf");
            boolean back = n.contains("back") || n.contains("rear") || two.equals("bl") || two.equals("br") || two.equals("lb")
                    || two.equals("rb") || two.equals("rl") || two.equals("rr");
            boolean left = n.contains("left") || two.equals("fl") || two.equals("bl") || two.equals("lf") || two.equals("lb") || two.equals("rl");
            boolean right = n.contains("right") || two.equals("fr") || two.equals("br") || two.equals("rf") || two.equals("rb") || two.equals("rr");
            if (front == back || left == right) continue;
            int i = (back ? 2 : 0) + (right ? 1 : 0);
            if (order[i] == null) order[i] = d.name;
        }
        for (String s : order) if (s == null) return null;
        return order;
    }

    /** The draft robot file, and the questions still open (TODO lines). */
    static String draft(String configName, List<Device> devices, String field, List<String> questions) {
        StringBuilder b = new StringBuilder();
        b.append("# Robot file made by SimConfigImport from the robot configuration \"").append(configName).append("\".\n");
        b.append("# Every device has its configuration name. Lines marked TODO still need an answer (the robot-file\n");
        b.append("# skill asks them). Guide: docs/guides/simulator.md and docs/guides/robot-file-skill.md.\n\n");
        b.append("battery = 12.8\n");
        b.append("field = ").append(field).append("\n\n");
        b.append("# ---- Size and start (TODO: measure the robot) ----\n");
        b.append("robot.lengthIn = 18   # TODO: front to back, inches\nrobot.widthIn = 18    # TODO: side to side\nrobot.heightIn = 18   # TODO: tallest at the start\n");
        b.append("start.x = -56\nstart.y = -61.55\nstart.heading = 0     # TODO: where it starts (inches from the field centre; 0 = facing away from the drivers)\n\n");
        questions.add("Robot size: length, width, height at the start (inches), and where it starts on the field.");
        String[] drive = driveMotors(devices);
        b.append("# ---- Drivetrain ----\n");
        List<String> used = new ArrayList<>();
        if (drive != null) {
            b.append("drive.type = mecanum   # TODO: mecanum or tank?\n");
            b.append("drive.motors = ").append(String.join(", ", drive)).append("\n");
            Device d0 = find(devices, drive[0]);
            String part = MOTOR_PARTS.get(d0.tag);
            if (part != null) b.append("drive.part = ").append(part).append("\n");
            else b.append("# drive.part = TODO: ").append(MOTOR_FAMILIES.getOrDefault(d0.tag, "which motor?")).append("\n");
            b.append("drive.wheelDiameterIn = 4.09   # TODO: the wheel's real diameter (goBILDA 104 mm = 4.09; REV 75 mm = 2.95)\n");
            questions.add("Drivetrain: mecanum or tank? Which motors (catalog part) and wheels (diameter)? Track width and wheelbase (wheel centre to wheel centre).");
            for (String s : drive) used.add(s);
        } else {
            b.append("drive.type = none   # TODO: no motors named like front_left/back_right were found: name the four drive motors\n");
            questions.add("Which four motors drive the robot (front left, front right, back left, back right)?");
        }
        b.append("\n# ---- Motors ----\n# motor.NAME = kind (free, lift or flywheel), part=, min=, max= (hard stops in counts), gravity=\n");
        for (Device d : devices) {
            if (!kindOf(d.tag).equals("motor") || used.contains(d.name)) continue;
            String part = MOTOR_PARTS.get(d.tag);
            b.append("motor.").append(d.name).append(" = free").append(part != null ? ", part=" + part : "")
                    .append("   # TODO: what is it (lift/arm = lift with min= max=; launcher = flywheel; intake = free)?")
                    .append(part == null ? " Motor: " + MOTOR_FAMILIES.getOrDefault(d.tag, d.tag) : "").append("\n");
            questions.add("Motor \"" + d.name + "\" (" + d.module + " port " + d.port + "): what does it move, which part, and its limits?");
        }
        b.append("\n# ---- Servos ----\n# servo.NAME = start position, part=  (servo.speed = positions per second for servos with no part)\nservo.speed = 1.0\n");
        for (Device d : devices) {
            if (!kindOf(d.tag).equals("servo")) continue;
            b.append("servo.").append(d.name).append(" = 0.5   # TODO: start position and part= (which servo?)\n");
            questions.add("Servo \"" + d.name + "\": what does it move, which servo (part), where does it start?");
        }
        for (Device d : devices) {
            if (!kindOf(d.tag).equals("crservo")) continue;
            b.append("crservo.").append(d.name).append(" =\n");
        }
        b.append("\n# ---- Sensors and cameras ----\n");
        for (Device d : devices) {
            String k = kindOf(d.tag);
            if (k.equals("imu")) b.append("imu = ").append(d.name).append("\n");
            if (k.equals("touch")) {
                b.append("touch.").append(d.name).append(" =    # TODO: the motor it watches and how near its bottom: touch.").append(d.name).append(" = lift, below=20\n");
                questions.add("Switch \"" + d.name + "\": what presses it (a lift at its bottom? which motor)?");
            }
            if (k.equals("webcam")) {
                b.append("camera.").append(safe(d.name)).append(" = name=").append(d.name)
                        .append(", forward=0, left=0, up=10, yaw=0, pitch=0   # TODO: where it sits and how it is tilted\n");
                questions.add("Webcam \"" + d.name + "\": where is it (forward/left/up from the robot's centre, inches) and how is it tilted?");
            }
            if (k.equals("limelight")) {
                b.append("limelight.").append(safe(d.name)).append(" = name=").append(d.name)
                        .append(", forward=0, left=0, up=10, yaw=0, pitch=0   # TODO: a Limelight 3A? where it sits and its tilt\n");
                questions.add("Ethernet device \"" + d.name + "\": is it a Limelight 3A? Where does it sit, how is it tilted?");
            }
        }
        boolean other = false;
        for (Device d : devices) {
            if (!kindOf(d.tag).equals("other")) continue;
            if (!other) b.append("# Not simulated yet (an OpMode that uses them stops with \"Unable to find a hardware device\"):\n");
            other = true;
            b.append("#   ").append(d.name).append(" (").append(d.tag).append(")\n");
        }
        b.append("\n# ---- Shapes and game pieces (TODO: the robot-file skill builds these from photos and measurements) ----\n");
        b.append("robot.chassisHeightIn = 4   # TODO: the chassis frame's height\n");
        questions.add("Mechanisms: for each one (intake, lift, arm, claw, launcher), what it looks like and where it sits (photos + a few measurements).");
        return b.toString();
    }

    private static Device find(List<Device> devices, String name) {
        for (Device d : devices) if (d.name.equals(name)) return d;
        return null;
    }

    // A robot-file key from a configuration name ("Webcam 1" -> "webcam_1").
    private static String safe(String name) {
        return name.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_");
    }

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            // A mistake in a robot file, field file or command: its message names it; no Java trace.
            System.err.println();
            System.err.println("PROBLEM: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void run(String[] args) throws Exception {
        String config = null, out = null, field = "biobuzz-2026";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--config")) config = args[++i];
            else if (args[i].equals("--out")) out = args[++i];
            else if (args[i].equals("--field")) field = args[++i];
        }
        if (config == null) throw new IllegalArgumentException("-Pconfig=path/to/the/configuration.xml (from the Control Hub's /sdcard/FIRST/)");
        Path in = Paths.get(config);
        List<Device> devices = read(new String(Files.readAllBytes(in), StandardCharsets.UTF_8));
        List<String> questions = new ArrayList<>();
        String text = draft(in.getFileName().toString().replace(".xml", ""), devices, field, questions);
        System.out.println("Devices: " + devices.size());
        for (Device d : devices) System.out.println(String.format(Locale.US, "  %-22s %-36s %s port %d -> %s", d.name, d.tag, d.module, d.port, kindOf(d.tag)));
        if (out != null) {
            File f = new File(out);
            if (f.exists()) {
                out = out + ".draft";
                System.out.println("\n" + f + " exists: the draft goes to " + out + " (merge it by hand, or move it over the old one).");
            }
            Files.write(Paths.get(out), text.getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + Paths.get(out).toAbsolutePath());
            // the draft must load: check it now
            SimRobot.load(Paths.get(out));
            System.out.println("It loads in the simulator.");
        } else {
            System.out.println("\n" + text);
        }
        System.out.println("\nStill to answer:");
        for (String q : questions) System.out.println("  - " + q);
    }
}
