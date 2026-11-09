package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.vision.TagGroup;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes a team's five starting files (Robot.java, Tunables.java, Controls.java, Actions.java and one
 * autonomous) from the robot configuration the Driver Station saved, so the drive works on day one and
 * each mechanism already has its module, its numbers and its usual buttons, written out in Controls.java.
 *
 * <pre>
 * ./gradlew :sim:newRobot -Pconfig=path/to/config.xml -PteamCode=../TeamCode [-Ppackage=org.firstinspires.ftc.teamcode]
 * </pre>
 *
 * <p>Which module a device gets is a guess from its name and type ("lift" is a Lift, a servo called
 * "claw" a Claw): every guess the generator is not sure of is a {@code TODO} in Robot.java. It never
 * overwrites a file: it stops if any of the five is already there.
 */
public final class SimNewRobot {
    private SimNewRobot() { }

    /** One module line for Robot.java. */
    static final class Part {
        final String type, field, line, section;
        final Map<String, String> numbers = new LinkedHashMap<>();   // Tunables: name -> value text
        String note = "";
        final List<Part> inner = new ArrayList<>();   // a launcher's indexer and hood (each its own Tunables section)

        Part(String type, String field, String line, String section) {
            this.type = type;
            this.field = field;
            this.line = line;
            this.section = section;
        }

        Part num(String name, String value) {
            numbers.put(name, value);
            return this;
        }
    }

    // Joins "X_left" + "X_right" (or "left_X" + "right_X") servo parts of the same kind into one part on two
    // servos: the first line gets .alsoServoReversed("X_right") (they face each other, the usual way).
    static void pairServos(List<Part> list) {
        for (int i = 0; i < list.size(); i++) {
            Part p = list.get(i);
            String name = servoName(p);
            if (name == null || !name.contains("left")) continue;
            String other = name.replace("left", "right");
            for (int j = 0; j < list.size(); j++) {
                Part o = list.get(j);
                if (j == i || !o.type.equals(p.type) || !other.equals(servoName(o))) continue;
                int at = p.line.indexOf(')') + 1;   // after Claw.servo("claw_left")
                Part joined = new Part(p.type, p.field, p.line.substring(0, at) + ".alsoServoReversed(" + q(other) + ")"
                        + p.line.substring(at), p.section);
                joined.numbers.putAll(p.numbers);
                joined.note = (p.note.isEmpty() ? "" : p.note + "; ") + "one part on two servos: " + other
                        + " mirrors it (same way? use .alsoServo)";
                list.set(i, joined);
                list.remove(j);
                if (j < i) i--;
                break;
            }
        }
    }

    // The servo name of a Claw/Wrist/Positioner/Indexer.servo("...") line, else null.
    private static String servoName(Part p) {
        for (String k : new String[] {"Claw.servo(\"", "Wrist.servo(\"", "Positioner.servo(\"", "Indexer.servo(\""}) {
            if (p.line.startsWith(k)) {
                int end = p.line.indexOf('"', k.length());
                return end < 0 ? null : p.line.substring(k.length(), end);
            }
        }
        return null;
    }

    // A light: its rules go in Controls.java (which color, when).
    private static Part lightPart(Part p) {
        p.note = "TODO its rules in Controls.java, e.g. robot." + p.field + ".show(LightColor.GREEN).when(...) (docs/modules/robot.md#lights)";
        return p;
    }

    public static void main(String[] args) throws Exception {
        String config = "", teamCode = "", pkg = "org.firstinspires.ftc.teamcode", buttons = "";
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (args[i].equals("--config")) config = args[i + 1];
            if (args[i].equals("--teamCode")) teamCode = args[i + 1];
            if (args[i].equals("--package") && !args[i + 1].isEmpty()) pkg = args[i + 1];
            if (args[i].equals("--buttons")) buttons = args[i + 1];
        }
        if (config.isEmpty() || teamCode.isEmpty()) {
            System.err.println("newRobot: -Pconfig=path/to/config.xml and -PteamCode=path/to/TeamCode are needed");
            System.exit(1);
        }
        String xml = new String(Files.readAllBytes(Paths.get(config)), StandardCharsets.UTF_8);
        Map<String, String> files = files(SimConfigImport.read(xml), pkg);
        Path root = Paths.get(teamCode, "src", "main", "java").resolve(pkg.replace('.', '/'));
        for (String f : files.keySet()) {
            if (Files.exists(root.resolve(f))) {
                System.err.println("newRobot: " + root.resolve(f) + " is already there: nothing written (move it away first)");
                System.exit(1);
            }
        }
        for (Map.Entry<String, String> f : files.entrySet()) {
            Path p = root.resolve(f.getKey());
            Files.createDirectories(p.getParent());
            Files.write(p, f.getValue().getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + p);
        }
        Path project = Paths.get(teamCode).toAbsolutePath().normalize().getParent();
        if (!buttons.isEmpty() && project != null) {
            for (String b : addRunButtons(Paths.get(buttons), project)) System.out.println("Added the run button " + b);
        }
        System.out.println("Next: read the TODO lines in Robot.java, then try it in the simulator: in Android Studio, pick "
                + "Simulator in the list next to the green Run button and click Run (or ./gradlew :sim:run). "
                + "Tutorial: docs/tutorials/start/01-robot-in-five-files.md");
    }

    /**
     * Android Studio's run buttons (Simulator, Simulator tests): the *.run.xml files of the buttons folder
     * (sim/run-buttons) copied into the robot project's .idea/runConfigurations folder as NAME.xml (Android
     * Studio reads them there, not from a .run folder), only those not there yet.
     *
     * @return the buttons added, by name
     */
    static List<String> addRunButtons(Path buttons, Path project) throws java.io.IOException {
        List<String> added = new ArrayList<>();
        if (!Files.isDirectory(buttons)) return added;
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> s = Files.list(buttons)) {
            s.filter(f -> f.getFileName().toString().endsWith(".run.xml")).sorted().forEach(files::add);
        }
        Path to = project.resolve(".idea").resolve("runConfigurations");
        for (Path f : files) {
            String name = f.getFileName().toString().replace(".run.xml", "");
            Path dest = to.resolve(name + ".xml");
            if (Files.exists(dest)) continue;
            Files.createDirectories(to);
            Files.copy(f, dest);
            added.add(name);
        }
        return added;
    }

    // ----------------------------- the guesses -----------------------------

    private static boolean has(List<String> words, String... any) {
        for (String a : any) if (words.contains(a)) return true;
        return false;
    }

    /**
     * A device's Java names: {field, Tunables section, rename}. "intake_deploy" -> intakeDeploy,
     * IntakeDeploy, "". A name Java cannot use ("1_lift") or one already taken ("left-claw" next to
     * "left_claw") gets another section, and rename is then the {@code .tunables("...")} that tells
     * the module where its numbers are.
     */
    static String[] ids(String device, Set<String> used) {
        String natural = RobotDefinition.sectionName(device);
        String section = Character.isDigit(natural.charAt(0)) ? "Part" + natural : natural;
        String base = section;
        for (int i = 2; used.contains(section) || used.contains(lowerFirst(section)) || section.equals("Drive")
                || section.equals("Auto") || JAVA_WORDS.contains(lowerFirst(section)); i++) section = base + i;
        used.add(section);
        String field = lowerFirst(section);
        used.add(field);
        String ren = section.equals(natural) ? "" : ".tunables(" + q(section) + ")";
        return new String[] {field, section, ren};
    }

    // Words Java keeps for itself: a field cannot be called one of these.
    private static final Set<String> JAVA_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"));

    private static String lowerFirst(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** The five files, by path under the package folder. */
    static Map<String, String> files(List<SimConfigImport.Device> devices, String pkg) {
        String[] drive = SimConfigImport.driveMotors(devices);
        Set<String> used = new LinkedHashSet<>();
        Set<String> usedNames = new LinkedHashSet<>();   // field and section names given out so far
        if (drive != null) for (String d : drive) used.add(d);
        String imu = null;
        String odometry = null;   // the Robot.java odometry line, or null
        List<String> todo = new ArrayList<>();
        List<Part> parts = new ArrayList<>();
        List<String> intakeServos = new ArrayList<>();
        String[] intakeId = null;   // the first intake CR servo's names (the module's)
        Part launcher = null;
        List<Part> indexers = new ArrayList<>();
        List<Part> hoods = new ArrayList<>();
        List<SimConfigImport.Device> switches = new ArrayList<>();

        for (SimConfigImport.Device d : devices) {
            if (used.contains(d.name)) continue;
            String kind = SimConfigImport.kindOf(d.tag);
            List<String> w = SimConfigImport.words(d.name);
            String[] id = ids(d.name, usedNames);
            String field = id[0], section = id[1], ren = id[2];
            String part = SimConfigImport.MOTOR_PARTS.get(d.tag);
            switch (kind) {
                case "imu":
                    if (imu == null) imu = d.name;
                    break;
                case "pinpoint":
                    if (odometry == null) odometry = "Odometry.pinpoint(" + q(d.name) + ").offsetsMm(0, 0)";
                    break;
                case "otos":
                    if (odometry == null) odometry = "Odometry.otos(" + q(d.name) + ").mountIn(0, 0, 0)";
                    break;
                case "motor":
                    if (has(w, "intake", "roller", "sweeper", "collector")) {
                        parts.add(new Part("Intake", field, "Intake.motor(" + q(d.name) + ")" + ren, section)
                                .num("inPower", "DEFAULT").num("outPower", "DEFAULT"));
                    } else if (has(w, "lift", "slide", "slides", "elevator", "viper", "linear", "extension")) {
                        parts.add(new Part("Lift", field, "Lift.slide(" + q(d.name) + ")" + ren, section)
                                .num("min", "MEASURE").num("max", "MEASURE").num("kP", "DEFAULT").num("kI", "DEFAULT").num("kD", "DEFAULT")
                                .num("kF", "DEFAULT").num("down", "0").num("high", "MEASURE"));
                    } else if (has(w, "arm", "pivot", "shoulder", "elbow")) {
                        Part p = new Part("Arm", field, "Arm.motor(" + q(d.name) + ", " + q(part != null ? part : "gobilda-5203-117") + ")" + ren, section)
                                .num("min", "MEASURE").num("max", "MEASURE").num("kP", "DEFAULT").num("kI", "DEFAULT").num("kD", "DEFAULT")
                                .num("kF", "DEFAULT").num("stowed", "0").num("score", "MEASURE");
                        if (part == null) p.note = "TODO the motor's catalog id (docs/guides/parts-catalog.md) and gearRatio(...)";
                        parts.add(p);
                    } else if (has(w, "turret")) {
                        Part p = new Part("Turret", field, "Turret.motor(" + q(d.name) + ", " + q(part != null ? part : "gobilda-5203-117") + ")" + ren, section)
                                .num("min", "MEASURE").num("max", "MEASURE").num("kP", "DEFAULT").num("kI", "DEFAULT").num("kD", "DEFAULT")
                                .num("maxPower", "DEFAULT").num("tolerance", "DEFAULT");
                        p.note = "TODO the motor's catalog id and gearRatio(...); follows(camera) to follow the goal";
                        parts.add(p);
                    } else if (has(w, "launcher", "shooter", "flywheel", "fly", "outtake")) {
                        launcher = new Part("Launcher", field, "Launcher.flywheel(" + q(d.name) + ", " + q(part != null ? part : "gobilda-5203-6000") + ")" + ren, section)
                                .num("speedRpm", "DEFAULT").num("kP", "DEFAULT").num("kI", "DEFAULT").num("kD", "DEFAULT").num("kF", "DEFAULT")
                                .num("stepRpm", "DEFAULT").num("readyBandRpm", "DEFAULT").num("idleRpm", "DEFAULT");
                        if (part == null) launcher.note = "TODO the motor's catalog id";
                        parts.add(launcher);
                    } else if (has(w, "feeder", "indexer", "kicker", "transfer", "windmill", "feed", "index")) {
                        indexers.add(new Part("Indexer", field, "Indexer.motor(" + q(d.name) + ")" + ren, section)
                                .num("power", "DEFAULT").num("feedSec", "DEFAULT"));
                    } else {
                        todo.add("motor " + q(d.name) + ": which module? (Intake, Lift, Arm, Turret, Launcher)");
                    }
                    break;
                case "blinkin":
                    parts.add(lightPart(new Part("Lights", field, "Lights.blinkin(" + q(d.name) + ")" + ren, section)));
                    break;
                case "servo":
                    if (SimConfigImport.isLightName(d.name)) {
                        parts.add(lightPart(new Part("Lights", field, "Lights.gobilda(" + q(d.name) + ")" + ren, section)));
                    } else if (has(w, "claw", "grab", "grabber", "gripper", "grip", "pincer")) {
                        parts.add(new Part("Claw", field, "Claw.servo(" + q(d.name) + ")" + ren, section)
                                .num("open", "MEASURE").num("closed", "MEASURE").num("travelSec", "DEFAULT"));
                    } else if (has(w, "hood")) {
                        // The stops are a guess: the Hood Finder finds them (and the angles, if you measure them).
                        hoods.add(new Part("Hood", field, "Hood.servo(" + q(d.name) + ")" + ren, section)
                                .num("posA", "MEASURE").num("posB", "MEASURE").num("travelSec", "DEFAULT"));
                    } else if (has(w, "wrist")) {
                        parts.add(new Part("Wrist", field, "Wrist.servo(" + q(d.name) + ")" + ren, section)
                                .num("down", "MEASURE").num("level", "MEASURE").num("up", "MEASURE").num("travelSec", "DEFAULT"));
                    } else if (has(w, "indexer", "kicker", "pusher", "feeder", "index", "feed")) {
                        indexers.add(new Part("Indexer", field, "Indexer.servo(" + q(d.name) + ")" + ren, section)
                                .num("rest", "MEASURE").num("push", "MEASURE").num("pushSec", "DEFAULT").num("returnSec", "DEFAULT"));
                    } else {
                        Part p = new Part("Positioner", field, "Positioner.servo(" + q(d.name) + ")" + ren, section)
                                .num("stowed", "MEASURE").num("deployed", "MEASURE").num("travelSec", "DEFAULT");
                        p.note = "TODO name its positions in Tunables." + section;
                        parts.add(p);
                    }
                    break;
                case "crservo":
                    if (has(w, "intake", "roller", "sweeper", "collector")) {
                        if (intakeServos.isEmpty()) intakeId = id;
                        intakeServos.add(d.name);
                    }
                    else if (has(w, "feeder", "indexer", "kicker", "transfer", "windmill", "feed", "index")) {
                        indexers.add(new Part("Indexer", field, "Indexer.crServo(" + q(d.name) + ")" + ren, section)
                                .num("power", "DEFAULT").num("feedSec", "DEFAULT"));
                    } else {
                        todo.add("CR servo " + q(d.name) + ": which module? (Intake.crServos, Indexer.crServo)");
                    }
                    break;
                case "touch":
                    switches.add(d);
                    break;
                case "color":
                case "distance": {
                    // A piece sensor alone; with your kinds, it can go into a Pieces line (docs/modules/robot.md).
                    Part p = new Part("PieceSensor", field, "PieceSensor." + kind + "(" + q(d.name) + ").kinds(\"PIECE\")" + ren, section)
                            .num("nearMm", "DEFAULT");
                    p.note = "TODO your kinds of piece (e.g. \"POLLEN\", \"NECTAR\"), then the Piece Tuner";
                    parts.add(p);
                    break;
                }
                case "webcam":
                    parts.add(0, new Part("Camera", field, "Camera.webcam(" + q(d.name) + ")" + goal() + ren, section));
                    break;
                case "huskylens": {
                    Part p = new Part("Camera", field, "Camera.huskyLens(" + q(d.name) + ")" + ren, section);
                    p.note = "TODO what you taught it: .tag(1, 30) = ID1 is AprilTag 30; or .colors().label(1, \"POLLEN\")";
                    parts.add(0, p);
                    break;
                }
                case "limelight":
                    parts.add(0, new Part("Camera", field, "Camera.limelight(" + q(d.name) + ")" + goal() + ren, section)
                            .num("pipeline", "0"));
                    break;
                default:
                    todo.add(d.tag + " " + q(d.name) + ": no module for it yet (use the library's parts directly)");
            }
        }
        if (!intakeServos.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (String n : intakeServos) names.append(names.length() == 0 ? "" : ", ").append(q(n));
            Part motorIntake = null;
            for (Part p : parts) if (p.type.equals("Intake") && motorIntake == null) motorIntake = p;
            if (motorIntake != null) {
                // An intake motor already: the CR servos run with it (one module, one pair of buttons).
                // TODO in the line: which servo turns the other way (.reversed("...")).
                Part with = new Part("Intake", motorIntake.field, motorIntake.line
                        + "\n                .withCrServos(" + names + ")", motorIntake.section);
                with.numbers.putAll(motorIntake.numbers);
                with.note = "TODO check which intake servo must run .reversed(\"...\")";
                parts.set(parts.indexOf(motorIntake), with);
                usedNames.remove(intakeId[0]);
                usedNames.remove(intakeId[1]);
            } else {
                parts.add(new Part("Intake", intakeId[0], "Intake.crServos(" + names + ")" + intakeId[2], intakeId[1])
                        .num("inPower", "DEFAULT").num("outPower", "DEFAULT"));
            }
        }
        // A launcher takes the indexer and the hood; any other indexer or hood stands alone.
        // A left and a right servo of the same kind ("claw_left", "claw_right"): one part on two servos,
        // the right one mirrored (alsoServoReversed), not two parts.
        pairServos(parts);
        pairServos(indexers);
        if (launcher != null && (!indexers.isEmpty() || !hoods.isEmpty())) {
            Part ix = indexers.isEmpty() ? null : indexers.remove(0);
            Part hd = hoods.isEmpty() ? null : hoods.remove(0);
            String line = launcher.line + (ix != null ? "\n                .feeder(" + ix.line + ")" : "")
                    + (hd != null ? "\n                .hood(" + hd.line + ")" : "");
            Part merged = new Part("Launcher", launcher.field, line, launcher.section);
            merged.numbers.putAll(launcher.numbers);
            merged.note = launcher.note;
            if (hd != null) merged.note = (merged.note.isEmpty() ? "TODO" : merged.note + ";")
                    + " the hood's stops (posA, posB) with the Hood Finder";
            if (ix != null) merged.inner.add(ix);
            if (hd != null) merged.inner.add(hd);
            parts.set(parts.indexOf(launcher), merged);
        }
        parts.addAll(indexers);
        for (Part h : hoods) {
            h.note = "TODO the hood's stops (posA, posB) with the Hood Finder";
            parts.add(h);
        }
        // A switch goes on the first lift (or arm): its bottom stop.
        for (SimConfigImport.Device sw : switches) {
            boolean placed = false;
            for (int i = 0; i < parts.size() && !placed; i++) {
                Part p = parts.get(i);
                if ((p.type.equals("Lift") || p.type.equals("Arm")) && !p.line.contains("bottomSwitch")) {
                    Part withSwitch = new Part(p.type, p.field, p.line + ".bottomSwitch(" + q(sw.name) + ")", p.section);
                    withSwitch.numbers.putAll(p.numbers);
                    withSwitch.note = p.note;
                    withSwitch.inner.addAll(p.inner);
                    parts.set(i, withSwitch);
                    placed = true;
                }
            }
            if (!placed) todo.add("touch sensor " + q(sw.name) + ": which part's stop? (Lift/Arm .bottomSwitch(...))");
        }

        Map<String, String> files = new LinkedHashMap<>();
        files.put("Robot.java", robotJava(pkg, drive, imu, odometry, parts, todo));
        files.put("Tunables.java", tunablesJava(pkg, parts, odometry != null));
        files.put("Controls.java", controlsJava(pkg, parts, drive == null || drive.length == 4, odometry != null));
        files.put("Actions.java", actionsJava(pkg, parts));
        files.put("autos/LeaveStart.java", autoJava(pkg));
        return files;
    }

    // The goal's tag groups for this season (e.g. BLUE SCORING / RED SCORING), from the library.
    private static String goal() {
        String blue = null, red = null;
        for (TagGroup g : TagGroup.currentSeason()) {
            if (g.name.startsWith("BLUE") && g.name.contains("SCORING")) blue = g.name;
            if (g.name.startsWith("RED") && g.name.contains("SCORING")) red = g.name;
        }
        return blue != null && red != null ? ".goal(" + q(blue) + ", " + q(red) + ")" : "";
    }

    // ----------------------------- the files -----------------------------

    private static String robotJava(String pkg, String[] drive, String imu, String odometry, List<Part> parts, List<String> todo) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        Set<String> types = new java.util.TreeSet<>();
        types.add("Drive");
        types.add("RobotDefinition");
        if (odometry != null) types.add("Odometry");
        for (Part p : parts) {
            types.add(p.type);
            if (p.line.contains("Indexer.")) types.add("Indexer");
            if (p.line.contains("Hood.")) types.add("Hood");
        }
        if (imu != null) {
            sb.append("import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;\n");
            sb.append("import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;\n");
        }
        for (String t : types) sb.append("import com.crossbotics.ftchelper.robot.").append(t).append(";\n");
        sb.append("\n/**\n");
        sb.append(" * WHAT is on the robot, with each part's name in the robot configuration. Made by\n");
        sb.append(" * ./gradlew :sim:newRobot from the configuration: check every TODO.\n");
        sb.append(" *\n");
        sb.append(" * <p>The numbers are in Tunables.java, the buttons in Controls.java, steps that use several parts\n");
        sb.append(" * in Actions.java, the autonomous programs in autos/. Guide: ftc-helper-lib docs/modules/robot.md\n");
        sb.append(" */\n");
        sb.append("public class Robot extends RobotDefinition {\n");
        for (Part p : parts) sb.append("    public ").append(p.type).append(' ').append(p.field).append(";\n");
        if (!parts.isEmpty()) sb.append('\n');
        sb.append("    @Override\n    protected void define() {\n");
        if (drive == null) {
            sb.append("        // TODO the drive motors (the names were not clear): mecanum or tank, and which is which.\n");
            sb.append("        drive(Drive.mecanum(\"front_left\", \"front_right\", \"back_left\", \"back_right\")\n");
        } else if (drive.length == 4) {
            sb.append("        // TODO check: mecanum (it can strafe) or Drive.tank(...) with these four names?\n");
            sb.append("        drive(Drive.mecanum(").append(q(drive[0])).append(", ").append(q(drive[1])).append(", ")
                    .append(q(drive[2])).append(", ").append(q(drive[3])).append(")\n");
        } else {
            sb.append("        drive(Drive.tank(").append(q(drive[0])).append(", ").append(q(drive[1])).append(")\n");
        }
        sb.append("                .motor(\"gobilda-5203-312\").wheelMm(96)   // TODO your drive motor and wheel size\n");
        if (imu != null) {
            sb.append("                // TODO how the Control Hub is mounted: the way its logo and its USB port face\n");
            sb.append("                .imu(").append(q(imu)).append(", LogoFacingDirection.UP, UsbFacingDirection.FORWARD)");
        } else {
            sb.append("                .noImu()");
        }
        sb.append(");\n");
        if (odometry != null) {
            sb.append("        // TODO the Odometry Tuner (lesson 7) measures where the pods / sensor sit, then set\n");
            sb.append("        //      Tunables.Odometry.checked = true\n");
            sb.append("        odometry(").append(odometry).append(");\n");
        }
        for (Part p : parts) {
            sb.append('\n');
            if (!p.note.isEmpty()) sb.append("        // ").append(p.note).append('\n');
            sb.append("        ").append(p.field).append(" = add(").append(p.line).append(");\n");
        }
        if (!todo.isEmpty()) {
            sb.append('\n');
            for (String t : todo) sb.append("        // TODO ").append(t).append('\n');
        }
        sb.append("    }\n}\n");
        return sb.toString();
    }

    private static String tunablesJava(String pkg, List<Part> parts, boolean odometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE\n\n");
        sb.append("/**\n");
        sb.append(" * Every NUMBER, one section per part (the section's name is the part's configuration name in\n");
        sb.append(" * CamelCase). DEFAULT = the library's starting value (from the parts catalog where\n");
        sb.append(" * Robot.java names the motor or servo; INIT shows each one). MEASURE = a number you find on the\n");
        sb.append(" * robot: INIT stops on it and names the tool (Motor Finder, Servo Finder, Hood Finder). Write\n");
        sb.append(" * each number in place of its word once you have it (docs/guides/tunables.md).\n");
        sb.append(" *\n");
        sb.append(" * <p>Tune on the robot with the tuning menu (GUIDE on gamepad 1, PRACTICE profile): INIT then lists\n");
        sb.append(" * every value that differs from this file, ready to copy back. This file stays the record.\n");
        sb.append(" */\n");
        sb.append("public class Tunables {\n");
        sb.append("    /** calibrated: set true after the Drive Tuner (autonomous refuses to run before). */\n");
        sb.append("    public static class Drive {\n");
        sb.append("        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;\n");
        sb.append("        public static double strafeCorrection = DEFAULT, turnKp = DEFAULT;\n");
        sb.append("        public static double turnKd = DEFAULT, moveMinPower = DEFAULT;\n");
        sb.append("        public static boolean calibrated = false;\n");
        sb.append("    }\n");
        if (odometry) {
            sb.append("\n    /** checked: set true after the Odometry Tuner (driveTo steps are skipped before). */\n");
            sb.append("    public static class Odometry {\n");
            sb.append("        public static boolean checked = false;\n");
            sb.append("    }\n");
        }
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        for (Part p : parts) {
            if (!p.numbers.isEmpty()) sections.put(p.section, p.numbers);
            for (Part in : p.inner) sections.put(in.section, in.numbers);
        }
        for (Map.Entry<String, Map<String, String>> s : sections.entrySet()) {
            sb.append("\n    public static class ").append(s.getKey()).append(" {\n");
            sb.append("        public static double ");
            int i = 0;
            for (Map.Entry<String, String> n : s.getValue().entrySet()) {
                sb.append(i++ == 0 ? "" : ", ").append(n.getKey()).append(" = ").append(n.getValue());
            }
            sb.append(";\n    }\n");
        }
        sb.append("\n    /** Distances (inches) and speeds (0 to 1) for the autonomous programs. */\n");
        sb.append("    public static class Auto {\n");
        sb.append("        public static double leaveInches = 24, leavePower = 0.5;\n");
        sb.append("    }\n}\n");
        return sb.toString();
    }

    // Each part's own buttons, written out as the library would add them (RobotDefinition.suggestButtons: a part's
    // inner parts first, then the part; the first part to take a button keeps it). AllButtonsTest checks the
    // examples the same way; SimNewRobotTest INITs the written TeleOp and compares its button map.
    private static void partButtons(Part p, String ref, boolean inLauncher, java.util.Set<String> used, List<String> out) {
        List<String[]> b = new ArrayList<>();   // {button, line}
        switch (p.type) {
            case "Intake":
                b.add(new String[] {"rt", ref + ".inOn(g2.rt);"});
                b.add(new String[] {"lt", ref + ".outOn(g2.lt);"});
                break;
            case "Lift": {
                List<String> pos = new ArrayList<>();
                for (String k : p.numbers.keySet()) {
                    if (!java.util.Arrays.asList("min", "max", "kP", "kI", "kD", "kF", "maxPower", "slowZone", "slowZonePower",
                            "tolerance").contains(k)) pos.add(k);
                }
                if (pos.size() >= 1) b.add(new String[] {"a", "g2.a.press(() -> " + ref + ".goTo(\"" + pos.get(0) + "\")).says(\"" + pos.get(0) + "\");"});
                if (pos.size() >= 3) b.add(new String[] {"x", "g2.x.press(() -> " + ref + ".goTo(\"" + pos.get(1) + "\")).says(\"" + pos.get(1) + "\");"});
                if (pos.size() >= 2) {
                    String top = pos.get(pos.size() - 1);
                    b.add(new String[] {"y", "g2.y.press(() -> " + ref + ".goTo(\"" + top + "\")).says(\"" + top + "\");"});
                }
                b.add(new String[] {"dpadUp", "g2.dpadUp.press(" + ref + "::stepUp).says(\"next height up\");"});
                b.add(new String[] {"dpadDown", "g2.dpadDown.press(" + ref + "::stepDown).says(\"next height down\");"});
                b.add(new String[] {"leftStickY", "g2.leftStickY.use(" + ref + "::manual).says(\"by hand\");"});
                break;
            }
            case "Arm":
                b.add(new String[] {"rightStickY", "g2.rightStickY.use(" + ref + "::manual).says(\"by hand\");"});
                break;
            case "Claw":
                b.add(new String[] {"b", "g2.b.toggle(" + ref + ").says(\"open / close\");"});
                break;
            case "Wrist":
                b.add(new String[] {"dpadLeft", "g2.dpadLeft.press(" + ref + "::previous).says(\"previous position\");"});
                b.add(new String[] {"dpadRight", "g2.dpadRight.press(" + ref + "::next).says(\"next position\");"});
                break;
            case "Turret":
                b.add(new String[] {"rightStickX", "g2.rightStickX.use(v -> " + ref + ".manual(-v)).says(\"by hand (right = clockwise)\");"});
                break;
            case "Indexer":
                if (!inLauncher) b.add(new String[] {"rb", "g2.rb.press(" + ref + "::feedOne).says(\"feed one\");"});
                break;
            case "Hood":
                b.add(new String[] {"leftStickY", "g2.leftStickY.use(" + ref + "::byHand).says(\"hood angle (up = bigger angle)\");"});
                break;
            case "Launcher":
                b.add(new String[] {"lb", ref + ".spinOn(g2.lb);"});
                b.add(new String[] {"rb", ref + ".fireOn(g2.rb);"});
                // speedOn takes both D-pad buttons; one another part already has stays null, as the part did.
                String up = used.contains("dpadUp") ? "null" : "g2.dpadUp", down = used.contains("dpadDown") ? "null" : "g2.dpadDown";
                if (!(up.equals("null") && down.equals("null"))) {
                    b.add(new String[] {"speed", ref + ".speedOn(" + up + ", " + down + ");"});
                    if (!up.equals("null")) used.add("dpadUp");
                    if (!down.equals("null")) used.add("dpadDown");
                }
                break;
            default:
                break;
        }
        List<String> mine = new ArrayList<>();
        for (String[] l : b) {
            String[] keys = l[0].equals("speed") ? new String[0] : l[0].split("\\+");
            boolean free = true;
            for (String k : keys) free &= !used.contains(k);
            if (free) {
                used.addAll(java.util.Arrays.asList(keys));
                mine.add("        " + l[1]);
            }
            else mine.add("        // " + l[0] + " is already another part's: " + p.type + " " + p.field + " has no button for it here");
        }
        if (mine.isEmpty()) return;
        out.add("        // The " + p.type.toLowerCase(java.util.Locale.US) + "'s own buttons (" + p.field + ").");
        out.addAll(mine);
        out.add("");
    }

    private static String controlsJava(String pkg, List<Part> parts, boolean mecanum, boolean odometry) {
        List<String> lines = new ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Part p : parts) {
            for (Part c : p.inner) {
                String ref = p.type.equals("Launcher") && c.type.equals("Hood") ? "robot." + p.field + ".hood()" : "robot." + c.field;
                partButtons(c, ref, p.type.equals("Launcher"), used, lines);
            }
            partButtons(p, "robot." + p.field, false, used, lines);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n")
                .append("import com.qualcomm.robotcore.eventloop.opmode.TeleOp;\n")
                .append("import com.crossbotics.ftchelper.robot.ControlMap;\n")
                .append("import com.crossbotics.ftchelper.robot.Pad;\n\n")
                .append("/**\n")
                .append(" * The buttons, and the TeleOp itself. Every part's own buttons are written out below, so you can\n")
                .append(" * read, change, move or delete each one. INIT shows the whole map, and which buttons are free:\n")
                .append(" * <pre>\n")
                .append(" * g2.y.press(Actions.scoreHigh(robot));              // an action from Actions.java (press again: cancel)\n")
                .append(" * g2.b.toggle(robot.claw);                           // on / off\n")
                .append(" * g2.rt.hold(robot.intake::in, robot.intake::stop);  // while held, then on release\n")
                .append(" * g2.leftStickY.use(robot.lift::manual);             // a stick, every loop\n")
                .append(" * </pre>\n")
                .append(" */\n")
                .append("@TeleOp(name = \"TeleOp\")\n")
                .append("public class Controls extends ControlMap<Robot> {\n")
                .append("    // true: one driver does everything on gamepad 1 (the operator's buttons move there).\n")
                .append("    public static boolean oneDriver = false;\n\n")
                .append("    // Always pre-assigned on gamepad 1 (the library's): sticks: drive; LT: slow")
                .append(mecanum ? "; Y: field-centric (double tap)" : "").append(";\n")
                .append("    // BACK: pause; GUIDE: button map (held: tuning menu)").append(odometry ? "; START held 1 s: set pose" : "")
                .append("; START + BACK: debug lines;\n")
                .append("    // X / B in INIT: alliance. (One driver: left stick click slow, right stick click field-centric.)\n")
                .append("    // Every other button is a line in buttons(...): a part has no buttons until its lines are there.\n\n")
                .append("    @Override\n")
                .append("    protected void buttons(Robot robot, Pad g1, Pad g2) {\n");
        for (String l : lines) sb.append(l).append('\n');
        sb.append("        // Your buttons here. To use a part's button for something else, change or delete its line above.\n")
                .append("    }\n")
                .append("}\n");
        return sb.toString();
    }

    private static String actionsJava(String pkg, List<Part> parts) {
        String lift = null, claw = null;
        for (Part p : parts) {
            if (p.type.equals("Lift") && lift == null) lift = p.field;
            if (p.type.equals("Claw") && claw == null) claw = p.field;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import com.crossbotics.ftchelper.robot.Action;\n\n");
        sb.append("/**\n");
        sb.append(" * Steps that use several parts together, written once and used by a button (Controls.java), an\n");
        sb.append(" * autonomous (s.action(...)), or at START (Robot.java: atStart(() -> Actions.x(this))).\n");
        sb.append(" * An action never waits: the robot keeps driving while it runs, and moving one of its parts by\n");
        sb.append(" * hand stops it (the driver always wins).\n");
        sb.append(" */\n");
        sb.append("public class Actions {\n");
        if (lift != null && claw != null) {
            sb.append("\n    /** An example: lift to the top, open the claw, come back down. */\n");
            sb.append("    public static Action scoreHigh(Robot r) {\n");
            sb.append("        return Action.named(\"Score high\")\n");
            sb.append("                .then(() -> r.").append(lift).append(".goTo(\"high\"))\n");
            sb.append("                .waitUntil(\"lift at the top\", r.").append(lift).append("::isAtTarget, 3.0)\n");
            sb.append("                .then(r.").append(claw).append("::open)\n");
            sb.append("                .waitSeconds(0.3)\n");
            sb.append("                .then(() -> r.").append(lift).append(".goTo(\"down\"));\n");
            sb.append("    }\n");
        } else {
            sb.append("\n    // An example (with a lift and a claw):\n");
            sb.append("    // public static Action scoreHigh(Robot r) {\n");
            sb.append("    //     return Action.named(\"Score high\")\n");
            sb.append("    //             .then(() -> r.lift.goTo(\"high\"))\n");
            sb.append("    //             .waitUntil(\"lift at the top\", r.lift::isAtTarget, 3.0)\n");
            sb.append("    //             .then(r.claw::open);\n");
            sb.append("    // }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String autoJava(String pkg) {
        return "package " + pkg + ".autos;\n\n"
                + "import com.qualcomm.robotcore.eventloop.opmode.Autonomous;\n"
                + "import " + pkg + ".Robot;\n"
                + "import " + pkg + ".Tunables;\n"
                + "import com.crossbotics.ftchelper.robot.Auto;\n"
                + "import com.crossbotics.ftchelper.robot.Steps;\n\n"
                + "/**\n"
                + " * A first autonomous: wait for the start delay (INIT menu), then drive out of the start area.\n"
                + " * A new autonomous: copy this file, change its name and its steps. Moves and turns are written for\n"
                + " * BLUE and mirrored for RED. It runs once Tunables.Drive.calibrated is true (after the Drive Tuner).\n"
                + " */\n"
                + "@Autonomous(name = \"Leave Start\", preselectTeleOp = \"TeleOp\")\n"
                + "public class LeaveStart extends Auto<Robot> {\n\n"
                + "    @Override\n"
                + "    protected void steps(Steps s, Robot robot) {\n"
                + "        s.start(\"TODO where to place the robot\", 0)\n"
                + "         .move(\"Leave\", Tunables.Auto.leaveInches, 0, Tunables.Auto.leavePower);\n"
                + "    }\n"
                + "}\n";
    }
}
