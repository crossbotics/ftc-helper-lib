package com.crossbotics.ftchelper.sim;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.PwmControl;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

/**
 * The pretend robot: every device from the robot file, and simple physics that moves them
 * (a drivetrain that drives on a 12 ft field, lifts with hard stops, flywheels that slow down on a
 * shot, servos that take time to move). One per simulator.
 */
final class SimRobot {
    final Map<String, SimMotor> motors = new LinkedHashMap<>();
    final Map<String, SimServo> servos = new LinkedHashMap<>();
    final Map<String, double[]> crServos = new LinkedHashMap<>();   // name -> {power, reversed (1/0), times turned on}
    final Map<String, SimTouch> touches = new LinkedHashMap<>();
    // motor name -> the CR servo that turns it (drivenBy=).
    private final Map<String, String> drivenBy = new LinkedHashMap<>();
    // motor name -> the motor whose slide or arm it also turns (with=: a two-motor lift), and mirrored=.
    private final Map<String, String> tiedTo = new LinkedHashMap<>();
    private final Map<String, Boolean> tiedMirrored = new LinkedHashMap<>();
    // Absolute encoders (analog.NAME): {unused, offset degrees}, and the motor whose shaft they read.
    final Map<String, double[]> analogs = new LinkedHashMap<>();
    private final Map<String, String> analogMotor = new LinkedHashMap<>();
    // Pretend piece sensors (color.NAME = at=ready, distance.NAME = at=intake, acrossMm=150): see SimPieceSensor.
    final Map<String, SimPieceSensor> pieceSensors = new LinkedHashMap<>();
    // Colored lights (light.NAME = gobilda or blinkin): the page shows their color.
    final Map<String, SimLight> lights = new LinkedHashMap<>();
    // Webcams (camera.KEY = name=Webcam 1, forward=..., see SimCamera), by their configuration name.
    final Map<String, SimCamera> cameras = new LinkedHashMap<>();
    // Limelights (limelight.KEY = name=limelight, ...): their pretend camera is in cameras too.
    final Map<String, SimLimelight> limelights = new LinkedHashMap<>();
    // HuskyLens cameras (huskylens.KEY = name=huskylens, ...): their pretend camera is in cameras too.
    final Map<String, SimHuskyLens> huskyLenses = new LinkedHashMap<>();
    private final Map<String, HardwareDevice> devices = new LinkedHashMap<>();
    // Devices described in the robot file but NOT in the configuration ("missing = a, b", or a
    // scenario's "set device NAME missing"): a part not built yet. Used at the next INIT.
    private final java.util.Set<String> missingDevices = new java.util.LinkedHashSet<>();
    final String imuName;
    // Pretend odometry (a Pinpoint, an OTOS or dead wheels), or null.
    final SimOdometry odometry;
    volatile double batteryVolts = 12.8;
    // OpMode threads that ignored STOP: their hardware commands are ignored from then on.
    private static final java.util.Set<Thread> BLOCKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void block(Thread t) {
        BLOCKED.add(t);
    }

    static boolean isBlocked() {
        return BLOCKED.contains(Thread.currentThread());
    }
    double servoSpeed = 1.0;   // raw positions per second

    // The drivetrain.
    final String driveType;          // mecanum, tank, none
    final boolean rightMirrored;     // drive.mirrored = right: the right wheels' motors face backward (default: the left)
    final List<SimMotor> wheels = new ArrayList<>();   // FL, FR, BL, BR (tank: FL, FR, [BL, BR])
    double wheelDiameterIn = 3.78, ticksPerRev = 537.7, gearRatio = 1, trackWidthIn = 15, wheelBaseIn = 13;
    double strafeEfficiency = 0.85;
    // Degrees the robot turns per wheel count of turning ((left - right) / 2), measured by the
    // Characterizer; NaN = worked out from the wheel size and track (wheels that never slip).
    double turnDegPerCount = Double.NaN;
    // The robot's size (for bumping into field elements) and where it starts.
    double lengthIn = 18, widthIn = 18, heightIn = 18;
    // robot.tallestIn: a part the shapes do not draw reaches this high; the size checks never see the
    // robot shorter (NaN = not given).
    double tallestIn = Double.NaN;
    double startX = 0, startY = -48, startHeading = 0;
    // The season's field, and the element the robot bumped into last ("" = none).
    SimField field = SimField.empty();
    // A 3D model of the field for the page (field.model in the robot file), or null: the page then
    // builds the field from the field file's shapes.
    Path fieldModel = null;
    // The robot file's folder (relative paths in it start here); null for a robot not read from a file.
    final Path dir;
    // The robot as simple shapes (shape.NAME lines): for the 3D view and the size rules. The page's Edit
    // robot mode can swap them for changed ones ("Try it", replaceShapes), so read it once per use.
    volatile SimShapes shapes;
    // The robot file as read, and its shape lines (shape name -> settings), for Edit robot's changes.
    final Properties props;
    final Map<String, Map<String, String>> shapeLines;
    // The physics engine (physics = engine, the default): the robot bumps and slides, its parts hit
    // things. null = the simple rule (physics = simple): a bump stops the whole move.
    SimWorld world = null;
    double massKg = 15, grip = 1.0;
    // Game pieces and what the robot does with them (collect., shoot., grab. lines).
    SimPieces pieces;
    volatile String bumpedInto = "";
    // The robot on the field: inches from the field center (x right, y away from the drivers),
    // heading in degrees, counterclockwise = positive, 0 = facing away from the drivers.
    double x = 0, y = -48, headingDeg = 0;
    // The running path's plan and the robot's trail, for the field map and the 3D view.
    final SimPaths paths = new SimPaths();
    private double imuZero = 0;
    private volatile double yawRateDeg = 0;   // degrees per second, counterclockwise
    // A hand push: inches per second forward/right, and how long it still lasts.
    private double pushForward = 0, pushRight = 0, pushLeft = 0;
    // Per flywheel with a motor or CR servo feeder (each feed sends one piece at once, then one more every 0.3 s
    // from 0.45 s on): how far the feeder has run towards its next piece (seconds; a piece at 0.3), the feeder's
    // starts seen so far, whether it ran last step, and a feed whose piece waits for the wheel to spin.
    private final Map<String, Double> sinceShot = new LinkedHashMap<>();
    private final Map<String, Integer> feedStartsSeen = new LinkedHashMap<>();
    private final java.util.Set<String> feederOn = new java.util.HashSet<>();
    private final java.util.Set<String> feedWaiting = new java.util.HashSet<>();
    // Per flywheel with a servo pusher: the pusher is out now (it already sent this push's piece).
    private final java.util.Set<String> pusherOut = new java.util.HashSet<>();
    static final double SECONDS_PER_PIECE = 0.3;
    // A feed stop lets pieces through while it is within this much (raw position) of its open position.
    static final double STOP_OPEN_WITHIN = 0.05;
    // A servo feeder (an indexer) is feeding while it is this far (raw position) from where it starts.
    static final double SERVO_FEED_MOVE = 0.15;
    private int shots = 0;

    // The parts catalog this robot uses: the library's, plus the team's own file if it names one (parts =).
    private final SimParts parts;
    /**
     * The stand-ins for Tunables numbers written MEASURE ("tunable.IntakeDeploy.deployed = 0.5"): "Section.field"
     * -> value. The simulator uses one at INIT only while the code still says MEASURE (SimTunables.StandIns).
     */
    final Map<String, String> standIns = new LinkedHashMap<>();

    private SimRobot(Properties p, Path dir) {
        this.dir = dir;
        this.props = p;
        String teamParts = p.getProperty("parts", "").trim();
        parts = teamParts.isEmpty() ? SimParts.catalog() : SimParts.withTeamFile(dir.resolve(teamParts).normalize());
        batteryVolts = num(p, "battery", 12.8);
        for (String key : sortedKeys(p, "tunable.")) {
            String name = key.substring("tunable.".length());
            String v = p.getProperty(key).trim();
            if (name.indexOf('.') <= 0 || name.endsWith(".")) {
                throw new IllegalArgumentException("Robot file: \"" + key + "\": a stand-in is written tunable.Section.field = number "
                        + "(e.g. tunable.IntakeDeploy.deployed = 0.5)");
            }
            if (!v.equals("true") && !v.equals("false")) {
                try {
                    Double.parseDouble(v);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Robot file: \"" + key + "\": the stand-in must be a number (or true/false), not \"" + v + "\"");
                }
            }
            standIns.put(name, v);
        }
        servoSpeed = above0(p, "servo.speed", 1.0);
        int port = 0;
        // Drivetrain motors first, so they get the first ports like on a real hub.
        driveType = p.getProperty("drive.type", "none").trim().toLowerCase(Locale.US);
        String mirrored = p.getProperty("drive.mirrored", "left").trim().toLowerCase(Locale.US);
        if (!mirrored.equals("left") && !mirrored.equals("right")) {
            throw new IllegalArgumentException("Robot file: drive.mirrored is the side whose wheel motors face the other way "
                    + "(the side your code reverses to drive straight): left or right");
        }
        rightMirrored = mirrored.equals("right");
        if (!driveType.equals("none")) {
            // A catalog part (drive.part = gobilda-5203-312) gives the speed and counts; numbers written
            // in the file win over it.
            String drivePart = p.getProperty("drive.part", "").trim();
            SimParts.Motor dp = drivePart.isEmpty() ? null : parts.motor(drivePart);
            double maxTicks = above0(p, "drive.maxTicksPerSec", dp != null ? dp.maxTicksPerSec() : 2780);
            wheelDiameterIn = above0(p, "drive.wheelDiameterIn", 3.78);
            ticksPerRev = above0(p, "drive.ticksPerRev", dp != null ? dp.countsPerRev : 537.7);
            gearRatio = above0(p, "drive.gearRatio", 1);
            trackWidthIn = above0(p, "drive.trackWidthIn", 15);
            wheelBaseIn = above0(p, "drive.wheelBaseIn", 13);
            strafeEfficiency = num(p, "drive.strafeEfficiency", 0.85);
            double driveKs = num(p, "drive.kS", 0), driveAccel = num(p, "drive.accelSec", 0.15);
            turnDegPerCount = num(p, "drive.turnDegPerCount", Double.NaN);
            for (String name : list(p.getProperty("drive.motors", ""))) {
                SimMotor m = new SimMotor(name, port++, SimMotor.Kind.WHEEL, maxTicks, driveAccel);
                m.kS = driveKs;
                m.ticksPerRev = ticksPerRev;
                if (dp != null) m.setPart(dp);
                wheels.add(m);
                motors.put(name, m);
            }
        }
        for (String key : sortedKeys(p, "motor.")) {
            String name = key.substring("motor.".length());
            Map<String, String> s = settings(p.getProperty(key));
            SimMotor.Kind kind;
            try {
                kind = SimMotor.Kind.valueOf(s.getOrDefault("", "free").toUpperCase(Locale.US));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Robot file: \"" + key + "\": the kind must be free, lift or flywheel");
            }
            SimParts.Motor part = s.containsKey("part") ? parts.motor(s.get("part")) : null;
            // A motor not in the catalog: rpm= (free speed) and ticksPerRev= give its top speed, or
            // maxTicksPerSec= directly. Numbers written here win over the part's.
            double tpr = dbl(s, "ticksPerRev", part != null ? part.countsPerRev : (kind == SimMotor.Kind.FLYWHEEL ? 28 : 537.7));
            double fromRpm = s.containsKey("rpm") ? dbl(s, "rpm", 0) * tpr / 60.0 : Double.NaN;
            double max = dbl(s, "maxTicksPerSec", !Double.isNaN(fromRpm) ? fromRpm : part != null ? part.maxTicksPerSec() : 2780);
            if (!(tpr > 0) || !(max > 0) || !finite(tpr) || !finite(max)) {
                throw new IllegalArgumentException("Robot file: \"" + key + "\": its counts per turn and top speed must be more than 0");
            }
            if (motors.containsKey(name)) {
                throw new IllegalArgumentException("Robot file: \"" + key + "\": \"" + name + "\" is already a drive motor (drive.motors): "
                        + "list it in one place only");
            }
            SimMotor m = new SimMotor(name, port++, kind, max, kind == SimMotor.Kind.FLYWHEEL ? 0.6 : 0.12);
            m.ticksPerRev = tpr;
            if (part != null) m.setPart(part);
            // Measured numbers (the Characterizer's): friction, how quickly it speeds up, how it coasts.
            m.kS = dbl(s, "kS", 0);
            if (s.containsKey("accelSec")) m.lagSec = positive(s, "accelSec", key);
            if (s.containsKey("coastSec")) m.coastSec = positive(s, "coastSec", key);
            if (s.containsKey("min")) m.minTicks = dbl(s, "min", 0);
            if (s.containsKey("max")) m.maxTicks = dbl(s, "max", 0);
            m.gravity = dbl(s, "gravity", 0);
            m.feeder = s.get("feeder");
            m.shotDrop = dbl(s, "shotDrop", 0.15);
            m.stop = s.get("stop");
            m.stopOpen = dbl(s, "stopOpen", 0.5);
            if (s.containsKey("drivenBy")) drivenBy.put(name, s.get("drivenBy"));
            if (s.containsKey("with")) {
                tiedTo.put(name, s.get("with"));
                tiedMirrored.put(name, Boolean.parseBoolean(s.getOrDefault("mirrored", "false")));
            }
            motors.put(name, m);
        }
        for (String key : sortedKeys(p, "servo.")) {
            if (key.equals("servo.speed")) continue;
            String name = key.substring("servo.".length());
            // servo.NAME = start position [, part=catalog-id] [, pwm=500-2500]
            Map<String, String> s = settings(p.getProperty(key));
            // pwm=: the pulse range the robot's code gives this servo (.fullRange() in Robot.java). The
            // start position and every position the shapes use are in that range. Default: the SDK's 600-2400.
            double lo = SimParts.SDK_LOW_US, hi = SimParts.SDK_HIGH_US;
            if (s.containsKey("pwm")) {
                String[] lh = s.get("pwm").split("-");
                try {
                    lo = Double.parseDouble(lh[0].trim());
                    hi = Double.parseDouble(lh[1].trim());
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("Robot file: \"" + key + "\": pwm=" + s.get("pwm") + ": write it like pwm=500-2500 (microseconds)");
                }
                if (!(lo >= 100 && hi <= 3000 && lo < hi)) {
                    throw new IllegalArgumentException("Robot file: \"" + key + "\": pwm=" + s.get("pwm") + ": 100 to 3000 microseconds, low first");
                }
            }
            SimServo servo = new SimServo(name, dbl(s, "", 0.5), lo, hi);
            if (s.containsKey("part")) {
                SimParts.Servo part = parts.servo(s.get("part"));
                servo.partName = part.name;
                servo.partId = part.id;
                // What positions 0..1 turn it with the file's pulse range (a goBILDA 2000: 270 deg at 600-2400).
                servo.rangeDeg = part.degreesOver(lo, hi);
                servo.speed = part.rawPerSecond(lo, hi);
            }
            // A servo not in the catalog: its range (degrees over 0..1 with the file's pulse range) and
            // seconds per 60 degrees (at about 5 V).
            if (s.containsKey("rangeDeg") || s.containsKey("secPer60")) {
                if (!s.containsKey("rangeDeg") || !s.containsKey("secPer60")) {
                    throw new IllegalArgumentException("Robot file: \"" + key + "\": give both rangeDeg= and secPer60=");
                }
                double range = dbl(s, "rangeDeg", 0), sec = dbl(s, "secPer60", 0);
                if (range <= 0 || sec <= 0) throw new IllegalArgumentException("Robot file: \"" + key + "\": rangeDeg and secPer60 must be more than 0");
                servo.speed = (60.0 / sec) / range;
                servo.rangeDeg = range;
            }
            servos.put(name, servo);
        }
        for (String key : sortedKeys(p, "crservo.")) crServos.put(key.substring("crservo.".length()), new double[] {0, 0, 0});
        // An encoder turned by a CR servo (motor.X = ..., drivenBy=SERVO): its power is the CR servo's.
        for (Map.Entry<String, String> e : drivenBy.entrySet()) {
            final double[] cr = crServos.get(e.getValue());
            if (cr == null) {
                throw new IllegalArgumentException("Robot file: motor." + e.getKey() + ": drivenBy=" + e.getValue() + " is not a crservo in the robot file");
            }
            // A reversed CR servo turns the other way for the same power.
            motors.get(e.getKey()).drivenBy = () -> cr[0] * (cr[1] == 1 ? -1 : 1);
        }
        // A second motor on one slide or arm (motor.X = lift, with=FIRST [, mirrored=true]): its shaft is
        // geared to the first one's, so both turn together and both push (see SimMotor.step).
        for (Map.Entry<String, String> e : tiedTo.entrySet()) {
            SimMotor second = motors.get(e.getKey()), first = motors.get(e.getValue());
            if (first == null || first == second) {
                throw new IllegalArgumentException("Robot file: motor." + e.getKey() + ": with=" + e.getValue()
                        + " is not another motor in the robot file");
            }
            if (first.leader != null || tiedTo.containsKey(e.getValue()) || wheels.contains(first)) {
                throw new IllegalArgumentException("Robot file: motor." + e.getKey() + ": with=" + e.getValue()
                        + " must be the slide's own motor (not a drive motor, not one that is itself with= another)");
            }
            second.leader = first;
            second.mirrored = tiedMirrored.get(e.getKey());
            first.partners.add(second);
        }
        for (String key : sortedKeys(p, "light.")) {
            String name = key.substring("light.".length());
            lights.put(name, new SimLight(name, p.getProperty(key)));
        }
        // A flywheel's feeder is a motor or a CR servo (a windmill, a feed roller), or a servo that
        // pushes each piece in (an indexer: it feeds while it is away from where it starts).
        for (SimMotor m : motors.values()) {
            if (m.feeder != null && !motors.containsKey(m.feeder) && !crServos.containsKey(m.feeder)
                    && !servos.containsKey(m.feeder)) {
                throw new IllegalArgumentException("Robot file: motor." + m.name + ": feeder=" + m.feeder
                        + " is not a motor, crservo or servo in the robot file");
            }
            if (m.stop != null && !servos.containsKey(m.stop)) {
                throw new IllegalArgumentException("Robot file: motor." + m.name + ": stop=" + m.stop + " is not a servo in the robot file");
            }
        }
        for (String key : sortedKeys(p, "touch.")) {
            Map<String, String> s = settings(p.getProperty(key));
            SimTouch t = new SimTouch(s.get(""), dbl(s, "below", 20));
            // at= (and width=): a switch part way along, e.g. a turret's home switch: pressed within width
            // counts of that shaft count (from where the motor starts).
            if (s.containsKey("at")) {
                t.at = dbl(s, "at", 0);
                t.width = Math.abs(dbl(s, "width", 10));
            }
            if (t.motor != null && !t.motor.isEmpty() && !motors.containsKey(t.motor)) {
                throw new IllegalArgumentException("Robot file: \"" + key + "\": no motor." + t.motor + " in the robot file");
            }
            touches.put(key.substring("touch.".length()), t);
        }
        // analog.NAME = MOTOR, offsetDeg=D: an absolute encoder on that motor's shaft (one turn = 0 V to
        // 3.3 V), reading D degrees at the start.
        for (String key : sortedKeys(p, "analog.")) {
            Map<String, String> s = settings(p.getProperty(key));
            String m = s.get("");
            if (m == null || !motors.containsKey(m)) throw new IllegalArgumentException("Robot file: \"" + key + "\": the first value must be a motor in the robot file");
            analogs.put(key.substring("analog.".length()), new double[] {0, dbl(s, "offsetDeg", 0)});
            analogMotor.put(key.substring("analog.".length()), m);
        }
        for (String prefix : new String[] {"color.", "distance."}) {
            for (String key : sortedKeys(p, prefix)) {
                String n = key.substring(prefix.length());
                if (pieceSensors.containsKey(n)) throw new IllegalArgumentException("Robot file: two piece sensors are called \"" + n + "\"");
                pieceSensors.put(n, new SimPieceSensor(this, key, prefix.equals("color."), settings(p.getProperty(key))));
            }
        }
        for (String key : sortedKeys(p, "camera.")) {
            Map<String, String> s = settings(p.getProperty(key));
            // The name in the robot configuration: name=Webcam 1 (a name with a space can't be a key),
            // or the part after "camera." when there is no name=.
            String camName = s.containsKey("name") ? s.get("name") : key.substring("camera.".length());
            if (camName.isEmpty()) throw new IllegalArgumentException("Robot file: \"" + key + "\" needs a name");
            cameras.put(camName, new SimCamera(camName, s, this));
        }
        for (String key : sortedKeys(p, "limelight.")) {
            Map<String, String> s = settings(p.getProperty(key));
            String llName = s.containsKey("name") ? s.get("name") : key.substring("limelight.".length());
            if (llName.isEmpty()) throw new IllegalArgumentException("Robot file: \"" + key + "\" needs a name");
            if (cameras.containsKey(llName)) throw new IllegalArgumentException("Robot file: two cameras are called \"" + llName + "\"");
            SimCamera c = new SimCamera(llName, "limelight", s, this);
            cameras.put(llName, c);
            limelights.put(llName, new SimLimelight(c));
        }
        for (String key : sortedKeys(p, "huskylens.")) {
            Map<String, String> s = settings(p.getProperty(key));
            String hName = s.containsKey("name") ? s.get("name") : key.substring("huskylens.".length());
            if (hName.isEmpty()) throw new IllegalArgumentException("Robot file: \"" + key + "\" needs a name");
            if (cameras.containsKey(hName)) throw new IllegalArgumentException("Robot file: two cameras are called \"" + hName + "\"");
            SimCamera c = new SimCamera(hName, "huskylens", s, this);
            cameras.put(hName, c);
            huskyLenses.put(hName, new SimHuskyLens(c, s));
        }
        imuName = p.getProperty("imu", "").trim();
        // missing = intake_deploy, lift: in the robot file (drawn, simulated) but not in the configuration.
        missingDevices.addAll(list(p.getProperty("missing", "")));
        odometry = p.getProperty("odometry", "").trim().isEmpty() ? null : new SimOdometry(p);
        lengthIn = above0(p, "robot.lengthIn", 18);
        widthIn = above0(p, "robot.widthIn", 18);
        heightIn = above0(p, "robot.heightIn", 18);
        tallestIn = p.containsKey("robot.tallestIn") ? above0(p, "robot.tallestIn", 18) : Double.NaN;
        startX = num(p, "start.x", 0);
        startY = num(p, "start.y", -48);
        startHeading = num(p, "start.heading", 0);
        String fieldName = p.getProperty("field", "").trim();
        if (!fieldName.isEmpty()) {
            try {
                field = SimField.load(fieldName);
            } catch (IOException e) {
                throw new IllegalArgumentException("Robot file: could not read field \"" + fieldName + "\": " + e.getMessage());
            }
        }
        x = startX;
        y = startY;
        headingDeg = startHeading;
        // A start that overlaps a wall or a solid element would leave the robot stuck: say so now.
        String blocked = blockedAt(startX, startY, startHeading);
        if (blocked != null) {
            throw new IllegalArgumentException("Robot file: the start position (start.x/start.y/start.heading) overlaps "
                    + blocked + ". Move it (BIOBUZZ G304: touching the wall, not in the LOADING ZONE).");
        }
        Map<String, Map<String, String>> shapeLines = new LinkedHashMap<>();
        for (String key : sortedKeys(p, "shape.")) shapeLines.put(key.substring("shape.".length()), settings(p.getProperty(key)));
        shapes = new SimShapes(this, p, shapeLines, dir);
        this.shapeLines = java.util.Collections.unmodifiableMap(shapeLines);
        // A camera on a turret (on=turret) needs that shape: the turret's angle turns the camera.
        for (SimCamera c : cameras.values()) {
            if (c.on == null) continue;
            SimShapes.Shape s = shapes.shapes.get(c.on);
            if (s == null || !(s.type.equals("turret") || s.type.equals("arm") || s.type.equals("slide"))) {
                throw new IllegalArgumentException("Robot file: camera \"" + c.name + "\": on=" + c.on
                        + " needs a turret (or arm or slide) shape called " + c.on + " (shape." + c.on + " = turret, motor=...)");
            }
        }
        massKg = above0(p, "robot.massKg", 15);
        grip = num(p, "drive.grip", 1.0);
        String physics = p.getProperty("physics", "engine").trim();
        if (!physics.equals("engine") && !physics.equals("simple")) {
            throw new IllegalArgumentException("Robot file: physics = engine (bumping and sliding, the default) or simple");
        }
        if (physics.equals("engine") && !driveType.equals("none")) {
            world = new SimWorld(field, shapes.chassisHeightIn, massKg, grip);
            world.place(x, y, headingDeg);
            world.setRobotParts(shapes.pieces());
        }
        pieces = new SimPieces(this, world, p, lines(p, "collect."), lines(p, "shoot."), lines(p, "grab."));
        if (world != null) world.surfaceSpeed = pieces::surfaceSpeed;
        buildDevices();
        for (String n : missingDevices) {
            if (!devices.containsKey(n)) {
                throw new IllegalArgumentException("Robot file: missing = ... names \"" + n + "\", which is not a device in this file "
                        + "(it lists devices that ARE described here but not in the configuration yet)");
            }
        }
    }

    /**
     * Swaps the robot's shapes for these shape lines (the page's Edit robot "Try it"; nothing is written to
     * a file). Each shape keeps where it is now (a slide's height, a dropped intake). Throws, and keeps the
     * old shapes, when the lines are not right.
     */
    synchronized void replaceShapes(Map<String, Map<String, String>> lines) {
        SimShapes next = new SimShapes(this, props, lines, dir);
        for (SimShapes.Shape s : next.shapes.values()) {
            SimShapes.Shape old = shapes.shapes.get(s.name);
            if (old == null || !old.type.equals(s.type)) continue;
            s.dropped = old.dropped;
            s.value = s.moves() ? Math.max(s.min, Math.min(s.max, old.value)) : old.value;
        }
        shapes = next;
        if (world != null) world.setRobotParts(shapes.pieces());
    }

    /** "type, k=v, k=v": a shape line's settings as robot file text (the first word is the type). */
    static String lineText(Map<String, String> settings) {
        StringBuilder b = new StringBuilder(settings.getOrDefault("", "box"));
        for (Map.Entry<String, String> e : settings.entrySet()) {
            if (!e.getKey().isEmpty()) b.append(", ").append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
    }

    /** Reads a robot file (see sim/robots/starter.properties). */
    static SimRobot load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        // Properties keeps "# comments" out, but not comments after a value: cut them off. A note starts
        // with a # after a space (or at the start of the value), so color=#ff0000 is not a note.
        for (String k : p.stringPropertyNames()) {
            String v = p.getProperty(k);
            int hash = v.startsWith("#") ? 0 : -1;
            for (int i = 1; i < v.length() && hash < 0; i++) if (v.charAt(i) == '#' && Character.isWhitespace(v.charAt(i - 1))) hash = i;
            if (hash >= 0) p.setProperty(k, v.substring(0, hash).trim());
        }
        SimRobot robot = new SimRobot(p, file.toAbsolutePath().getParent());
        String model = p.getProperty("field.model", "").trim();
        if (!model.isEmpty()) {
            Path m = file.toAbsolutePath().getParent().resolve(model).normalize();
            if (!Files.isRegularFile(m)) throw new IllegalArgumentException("Robot file: field.model \"" + model + "\": no such file (" + m + ")");
            if (!m.getFileName().toString().toLowerCase(Locale.US).endsWith(".glb")) {
                throw new IllegalArgumentException("Robot file: field.model must be a .glb file");
            }
            robot.fieldModel = m;
        } else {
            // No field.model: the library's own 3D model of this season's field, if it has one
            // (sim/fields/<field>.glb next to the field file), so every team sees the real field parts.
            robot.fieldModel = libraryFieldModel(p.getProperty("field", "").trim());
        }
        return robot;
    }

    // The library's 3D model for a field named in the robot file: sim/fields/<name>.glb on the classpath
    // (a file in the library checkout), or <file>.glb next to a field given by its path. Null if none.
    static Path libraryFieldModel(String field) {
        if (field.isEmpty()) return null;
        try {
            Path asFile = java.nio.file.Paths.get(field);
            if (Files.isRegularFile(asFile)) {
                Path glb = asFile.resolveSibling(asFile.getFileName().toString().replaceFirst("\\.json$", "") + ".glb");
                return Files.isRegularFile(glb) ? glb : null;
            }
            java.net.URL u = SimRobot.class.getResource("/" + field + ".glb");
            if (u == null || !"file".equals(u.getProtocol())) return null;
            Path glb = java.nio.file.Paths.get(u.toURI());
            return Files.isRegularFile(glb) ? glb : null;
        } catch (RuntimeException | java.net.URISyntaxException e) {
            return null;   // a name that is not a path, or an odd class path: no model (the simple field)
        }
    }

    // ---------------------------------------------------------------- devices

    private void buildDevices() {
        for (SimMotor m : motors.values()) devices.put(m.name, m.device);
        for (SimServo s : servos.values()) devices.put(s.name, s.device);
        for (SimLight l : lights.values()) devices.put(l.name, l.device);
        for (Map.Entry<String, double[]> e : crServos.entrySet()) devices.put(e.getKey(), crServo(e.getValue()));
        for (Map.Entry<String, SimTouch> e : touches.entrySet()) {
            devices.put(e.getKey(), e.getValue().touchSensor());
        }
        for (Map.Entry<String, double[]> e : analogs.entrySet()) {
            final SimMotor m = motors.get(analogMotor.get(e.getKey()));
            final double offset = e.getValue()[1];
            // AnalogInput is a class: one whose voltage is the shaft's angle (0 to 3.3 V per turn).
            devices.put(e.getKey(), new com.qualcomm.robotcore.hardware.AnalogInput(null, 0) {
                @Override public double getVoltage() {
                    double deg = (m.shaft() / m.ticksPerRev * 360 + offset) % 360;
                    return 3.3 * (deg < 0 ? deg + 360 : deg) / 360;
                }
                @Override public double getMaxVoltage() { return 3.3; }
                @Override public String getDeviceName() { return "Simulated absolute encoder"; }
            });
        }
        for (Map.Entry<String, SimPieceSensor> e : pieceSensors.entrySet()) devices.put(e.getKey(), e.getValue().device());
        if (!imuName.isEmpty()) devices.put(imuName, imu());
        if (odometry != null) odometry.addDevices(devices, motors);
        for (SimCamera c : cameras.values()) if (c.device != null) devices.put(c.name, c.device);
        for (SimLimelight l : limelights.values()) devices.put(l.cam.name, l);
        for (SimHuskyLens h : huskyLenses.values()) devices.put(h.cam.name, h);
        devices.put("Control Hub battery", voltageSensor());
    }

    /**
     * A new hardware map with every device, for one OpMode run (like the FTC app makes at INIT). The
     * missing devices ({@link #setMissing}) are left out, like a part not in the configuration yet.
     */
    synchronized HardwareMap hardwareMap() {
        Map<String, HardwareDevice> configured = new LinkedHashMap<>(devices);
        configured.keySet().removeAll(missingDevices);
        return new SimHardwareMap(configured);
    }

    /**
     * Takes a device out of the configuration (missing = true) or puts it back, from the next INIT on.
     * The robot file still describes it: it is drawn and moves as before, but the code cannot find it.
     */
    synchronized void setMissing(String name, boolean missing) {
        if (!devices.containsKey(name)) {
            throw new IllegalArgumentException("set device: no device \"" + name + "\" in the robot file (devices: " + devices.keySet() + ")");
        }
        if (missing) missingDevices.add(name);
        else missingDevices.remove(name);
    }

    /** True if this device is left out of the configuration (see {@link #setMissing}). */
    synchronized boolean isMissing(String name) {
        return missingDevices.contains(name);
    }

    /** Like the FTC app at INIT: every device's settings back to their defaults (not its position). */
    synchronized void resetConfig() {
        for (SimMotor m : motors.values()) m.resetConfig();
        for (SimServo s : servos.values()) s.resetConfig();
        for (double[] cr : crServos.values()) { cr[0] = 0; cr[1] = 0; }
    }

    /**
     * Closes every open camera portal (the FTC app does it when an OpMode stops). Call it WITHOUT holding
     * the robot's lock: a camera's picture thread takes the camera's lock, then the robot's.
     */
    void closeCameras() {
        for (SimCamera c : cameras.values()) c.closeAll();
    }

    /** Battery voltage from the panel: kept to 9..14 V (a real battery's range). */
    void setBattery(double volts) {
        if (Double.isNaN(volts) || Double.isInfinite(volts)) return;
        batteryVolts = Math.max(9, Math.min(14, volts));
    }

    /** All outputs off (the FTC app does this when an OpMode stops). */
    /** The motors and continuous servos with power on now, by name. */
    synchronized List<String> motorsOn() {
        List<String> on = new ArrayList<>();
        for (SimMotor m : motors.values()) if (Math.abs(m.userPower()) > 1e-9) on.add(m.name);
        for (Map.Entry<String, double[]> cr : crServos.entrySet()) if (Math.abs(cr.getValue()[0]) > 1e-9) on.add(cr.getKey());
        return on;
    }

    synchronized void allOff() {
        for (SimMotor m : motors.values()) m.device.setPower(0);
        for (double[] cr : crServos.values()) cr[0] = 0;
    }

    private CRServo crServo(double[] state) {
        return (CRServo) Proxy.newProxyInstance(CRServo.class.getClassLoader(), new Class<?>[] {CRServo.class},
                (proxy, m, a) -> {
                    if (isBlocked() && m.getName().startsWith("set")) return null;
                    switch (m.getName()) {
                        case "setPower":
                            synchronized (this) {
                                double was = state[0];
                                state[0] = Math.max(-1, Math.min(1, ((Number) a[0]).doubleValue()));
                                // as a flywheel's feeder: one more feed (see SimMotor.starts)
                                if (Math.abs(was) <= SimMotor.FEED_ON && Math.abs(state[0]) > SimMotor.FEED_ON) state[2]++;
                            }
                            return null;
                        case "getPower": synchronized (this) { return state[0]; }
                        // state[0] is the power asked for; the panel shows the turning direction (reversed flips it).
                        case "setDirection": synchronized (this) { state[1] = a[0] == com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE ? 1 : 0; } return null;
                        case "getDirection": synchronized (this) { return state[1] == 1 ? com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE : com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD; }
                        case "getDeviceName": return "Simulated CR servo";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        default: return SimDefaults.of(m.getReturnType());
                    }
                });
    }

    private IMU imu() {
        return (IMU) Proxy.newProxyInstance(IMU.class.getClassLoader(), new Class<?>[] {IMU.class},
                (proxy, m, a) -> {
                    switch (m.getName()) {
                        case "initialize": return true;
                        case "resetYaw": synchronized (this) { imuZero = headingDeg; } return null;
                        case "getRobotYawPitchRollAngles":
                            return new YawPitchRollAngles(AngleUnit.DEGREES, imuYaw(), 0, 0, System.nanoTime());
                        case "getRobotOrientation": {
                            // (reference, order, unit): yaw is the turn about Z; pitch and roll are 0 on a flat field
                            org.firstinspires.ftc.robotcore.external.navigation.AngleUnit unit =
                                    (org.firstinspires.ftc.robotcore.external.navigation.AngleUnit) a[2];
                            float yaw = (float) (unit == AngleUnit.RADIANS ? Math.toRadians(imuYaw()) : imuYaw());
                            return new org.firstinspires.ftc.robotcore.external.navigation.Orientation(
                                    (org.firstinspires.ftc.robotcore.external.navigation.AxesReference) a[0],
                                    (org.firstinspires.ftc.robotcore.external.navigation.AxesOrder) a[1], unit, yaw, 0f, 0f, System.nanoTime());
                        }
                        case "getRobotAngularVelocity": {
                            org.firstinspires.ftc.robotcore.external.navigation.AngleUnit unit =
                                    (org.firstinspires.ftc.robotcore.external.navigation.AngleUnit) a[0];
                            float z = (float) (unit == AngleUnit.RADIANS ? Math.toRadians(yawRateDeg) : yawRateDeg);
                            return new org.firstinspires.ftc.robotcore.external.navigation.AngularVelocity(unit, 0f, 0f, z, System.nanoTime());
                        }
                        case "getDeviceName": return "Simulated IMU";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        default: return SimDefaults.of(m.getReturnType());
                    }
                });
    }

    synchronized double imuYaw() {
        return wrap(headingDeg - imuZero);
    }

    private VoltageSensor voltageSensor() {
        return (VoltageSensor) Proxy.newProxyInstance(VoltageSensor.class.getClassLoader(), new Class<?>[] {VoltageSensor.class},
                (proxy, m, a) -> {
                    switch (m.getName()) {
                        case "getVoltage": return batteryVolts;
                        case "getDeviceName": return "Simulated battery";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        default: return SimDefaults.of(m.getReturnType());
                    }
                });
    }

    // ---------------------------------------------------------------- the panel's controls

    /** Rolls the robot by hand: inches per second forward and right, for this many seconds. */
    synchronized void push(double forwardInPerSec, double rightInPerSec, double seconds) {
        if (!finite(forwardInPerSec) || !finite(rightInPerSec) || !finite(seconds)) return;
        pushForward = Math.max(-60, Math.min(60, forwardInPerSec));
        pushRight = Math.max(-60, Math.min(60, rightInPerSec));
        pushLeft = Math.max(0, Math.min(10, seconds));
    }

    /** True while a hand push is still going. */
    synchronized boolean pushing() {
        return pushLeft > 0;
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /** Frees the physics engine's world (the simulator is shutting down). */
    synchronized void closeWorld() {
        if (world != null) world.close();
        world = null;
    }

    /** Puts the robot back at the start spot, facing away from the drivers. */
    synchronized void resetPose() {
        pushLeft = 0;                          // a hand push in progress ends
        shots = 0;
        sinceShot.clear();
        pusherOut.clear();
        feederOn.clear();
        feedWaiting.clear();
        x = startX;
        y = startY;
        imuZero += wrap(startHeading - headingDeg);   // a hand move: the IMU does not see it (like the odometry)
        headingDeg = startHeading;
        bumpedInto = "";
        if (world != null) world.place(x, y, headingDeg);
        if (odometry != null) odometry.teleported(x, y, headingDeg);
        paths.jumped();
        if (pieces != null) pieces.reset();   // back to the start of a match: the pieces too
        if (shapes != null) shapes.resetPassive();   // and a dropped intake goes back up
    }

    /** What a robot at this pose would overlap ("field wall" or an element's name), or null. */
    String blockedAt(double cx, double cy, double h) {
        for (double[] c : footprint(cx, cy, h)) {
            if (Math.abs(c[0]) > field.wallHalf + 1e-6 || Math.abs(c[1]) > field.wallHalf + 1e-6) return "the field wall";
        }
        SimField.Element e = field.bumps(footprint(cx, cy, h), heightIn);
        return e == null ? null : e.name;
    }

    /** Puts the robot at a pose (scenario "set pose"); refused if it would overlap something. */
    synchronized void setPose(double nx, double ny, double nh) {
        String blocked = blockedAt(nx, ny, nh);
        if (blocked != null) throw new IllegalArgumentException("that pose overlaps " + blocked);
        x = nx;
        y = ny;
        // A hand move (the INIT start spot, a drag on the map, a scenario's "set pose"): the IMU does not
        // see it, like the odometry pods. So its reading stays what it was: a TeleOp that zeroed it at
        // INIT and is then put on its alliance's spot still reads 0, not the turn of the move.
        imuZero += wrap(nh - headingDeg);
        headingDeg = nh;
        bumpedInto = "";
        if (world != null) world.place(x, y, headingDeg);
        if (odometry != null) odometry.teleported(x, y, headingDeg);
        paths.jumped();
    }

    /** Holds a switch pressed (on) or lets it go (off), like clicking it in the panel. */
    synchronized void setTouch(String name, boolean on) {
        SimTouch t = touches.get(name);
        if (t == null) throw new IllegalArgumentException("no switch \"" + name + "\" in the robot file");
        t.manual = on;
    }

    /** The robot's four corners on the field (inches), for bumping. */
    synchronized List<double[]> footprint(double cx, double cy, double headingDegrees) {
        double h = Math.toRadians(headingDegrees);
        double fx = -Math.sin(h), fy = Math.cos(h), rx = Math.cos(h), ry = Math.sin(h);
        double l = lengthIn / 2, w = widthIn / 2;
        List<double[]> c = new ArrayList<>();
        c.add(new double[] {cx + fx * l + rx * w, cy + fy * l + ry * w});
        c.add(new double[] {cx + fx * l - rx * w, cy + fy * l - ry * w});
        c.add(new double[] {cx - fx * l - rx * w, cy - fy * l - ry * w});
        c.add(new double[] {cx - fx * l + rx * w, cy - fy * l + ry * w});
        return c;
    }

    synchronized void toggleTouch(String name) {
        SimTouch t = touches.get(name);
        if (t != null) t.manual = !t.manual;
    }

    synchronized int shots() {
        return shots;
    }

    // ---------------------------------------------------------------- physics

    /** Moves everything forward by dt seconds. */
    synchronized void step(double dt) {
        shapes.update(dt);
        double battery = batteryVolts / 12.8;
        // Wheels: a hand push decides how they turn; otherwise their motors do.
        boolean pushing = pushLeft > 0;
        if (pushing) {
            setWheelsFromChassis(pushForward, pushRight, 0);
            pushLeft -= dt;
        } else {
            // The hand stops: the robot (and so its wheels) stops with it, it does not coast on.
            for (SimMotor w : wheels) w.stopForcedSpin();
        }
        // A second motor on a slide (with=) first says how hard it pushes, then the slide's own motor moves
        // the slide with both pushes, then the second motor's shaft follows it.
        for (SimMotor m : motors.values()) if (m.leader != null) m.pushOnly(dt);
        for (SimMotor m : motors.values()) if (m.leader == null) m.step(dt, battery);
        for (SimMotor m : motors.values()) if (m.leader != null) m.follow();
        if (!wheels.isEmpty()) moveChassis(dt);
        paths.record(x, y, dt);
        if (odometry != null) odometry.step(x, y, headingDeg, dt);
        pieces.step();
        for (SimServo s : servos.values()) s.step(dt, Double.isNaN(s.speed) ? servoSpeed : s.speed);
        // A flywheel loses speed when its feeder starts pushing a piece in.
        for (SimMotor m : motors.values()) {
            if (m.kind != SimMotor.Kind.FLYWHEEL || m.feeder == null) continue;
            SimMotor feeder = motors.get(m.feeder);
            double[] cr = crServos.get(m.feeder);
            SimServo pusher = servos.get(m.feeder);
            boolean on = feeder != null ? Math.abs(feeder.appliedPower) > 0.3
                    : cr != null ? Math.abs(cr[0]) > 0.3
                    : pusher != null && Math.abs(pusher.actual - pusher.start) > SERVO_FEED_MOVE;
            // A feed stop: pieces get through only while it is (nearly) at its open position.
            SimServo gate = m.stop == null ? null : servos.get(m.stop);
            boolean open = gate == null || Math.abs(gate.actual - m.stopOpen) < STOP_OPEN_WITHIN;
            if (pusher != null) {
                // A servo pusher (an indexer) sends ONE piece per push, at once, however long it stays
                // out: a push and a slow return can last longer than SECONDS_PER_PIECE, and that fired a
                // second piece.
                // A push against a closed feed stop waits: its piece goes when the stop opens (while the
                // pusher is still out), and counts as this push's one piece.
                boolean again = !pusherOut.contains(m.name);
                if (!on) pusherOut.remove(m.name);
                else if (again && open) {
                    // (a still wheel: the push is spent and its piece does not go, as before)
                    if (Math.abs(m.shaftSpeed()) > 100) feedShot(m);
                    pusherOut.add(m.name);
                }
                continue;
            }
            // A motor or CR servo feeder (a windmill, a belt): each time it is turned on, one piece goes in at
            // once; if it keeps running, one more every SECONDS_PER_PIECE, the first of them 1.5 x that after
            // it started. So a feed of up to 0.45 s sends exactly one piece, however long the OpMode's loop
            // takes to turn it off. WHY: before, one piece went at once and one more every 0.3 s while it
            // ran, and feeds that follow each other in the same loop look like one long run. A feeder tuned
            // to one piece per feed runs about 0.3 s, so three feeds in a row ran 0.9 s, exactly when a
            // fourth piece was due: a few ms of lag (a busy computer) fired it, and a recorded autonomous
            // test failed now and then ("launched >= 4", but the OpMode had fired 3).
            int starts = feeder != null ? feeder.starts : (int) cr[2];
            Integer seen = feedStartsSeen.put(m.name, starts);
            // Turned on by the OpMode's setPower (the device counts it), or, for a power set directly (tests),
            // seen running after it was off.
            boolean started = starts > 0 ? starts != (seen == null ? 0 : seen) : on && !feederOn.contains(m.name);
            if (on) feederOn.add(m.name);
            else feederOn.remove(m.name);
            boolean spinning = Math.abs(m.shaftSpeed()) > 100;
            double since = sinceShot.getOrDefault(m.name, 0.0);
            if (started) feedWaiting.add(m.name);
            if (feedWaiting.contains(m.name)) {
                // the feed's piece: as soon as the wheel spins and the feed stop is open (turned off before
                // that: it never went in)
                if (spinning && open) {
                    feedShot(m);
                    feedWaiting.remove(m.name);
                    since = -SECONDS_PER_PIECE / 2;
                } else if (!on) {
                    feedWaiting.remove(m.name);
                }
            } else if (on && open) {
                since += dt;
                if (since >= SECONDS_PER_PIECE && spinning) {
                    feedShot(m);
                    // keep the remainder (no drift from the 10 ms steps), at most a piece-time behind
                    since = Math.min(since - SECONDS_PER_PIECE, SECONDS_PER_PIECE / 2);
                }
            }
            sinceShot.put(m.name, since);
        }
    }

    // One piece from the feeder into the flywheel: with game pieces, a shot needs a piece in the robot
    // (it flies off the shooter shape); without them, every feed is a shot.
    private void feedShot(SimMotor m) {
        if (!pieces.shootsPieces() || pieces.shoot(m)) {
            m.shot();
            shots++;
        }
    }

    private double inchesPerTick() {
        return Math.PI * wheelDiameterIn / (ticksPerRev * gearRatio);
    }

    // One side's wheels are mounted mirrored (the left, unless drive.mirrored = right): their shaft turns
    // backward when the wheel rolls forward. Even indexes are the left wheels.
    private double mount(int index) {
        boolean left = index % 2 == 0;
        return left != rightMirrored ? -1 : 1;
    }

    // The robot's motion from its wheels (forward, right in inches per second; turn clockwise rad/s).
    private void moveChassis(double dt) {
        double ipt = inchesPerTick();
        double[] v = new double[4];
        for (int i = 0; i < 4; i++) {
            // A 2-motor tank drive has no back motors: the back "wheels" are the front ones.
            int index = wheels.size() == 4 ? i : i % 2;
            v[i] = mount(i) * wheels.get(index).shaftSpeed() * ipt;
        }
        double forward, right, cw;
        double k = (trackWidthIn + wheelBaseIn) / 2;
        if (driveType.equals("mecanum")) {
            forward = (v[0] + v[1] + v[2] + v[3]) / 4;
            right = (v[0] - v[1] - v[2] + v[3]) / 4 * strafeEfficiency;
            cw = (v[0] - v[1] + v[2] - v[3]) / 4 / k;
        } else {
            double left = (v[0] + v[2]) / 2, rightSide = (v[1] + v[3]) / 2;
            forward = (left + rightSide) / 2;
            right = 0;
            cw = (left - rightSide) / 2 / (trackWidthIn / 2);
        }
        if (!Double.isNaN(turnDegPerCount)) {
            // the measured turning: (left - right) / 2 in wheel counts per second, times degrees per count
            double turnCounts = ((v[0] + v[2]) / 2 - (v[1] + v[3]) / 2) / 2 / ipt;
            cw = Math.toRadians(turnDegPerCount * turnCounts);
        }
        if (world != null) {
            // The physics engine: the drive pushes the body toward the wheels' speeds; walls and field
            // parts stop it or make it slide; its parts (shapes) can hit things too.
            world.setRobotParts(shapes.pieces());
            world.step(dt, forward, right, Math.toDegrees(cw));
            x = world.xIn();
            y = world.yIn();
            headingDeg = wrap(world.headingDeg());
            yawRateDeg = world.yawRateDeg();
            List<String> touched = world.touching();
            if (!touched.isEmpty()) bumpedInto = touched.get(0);
            else if (Math.abs(forward) + Math.abs(right) + Math.abs(cw) > 1e-6) bumpedInto = "";
            return;
        }
        double h = Math.toRadians(headingDeg);
        // Forward = (-sin h, cos h) on the field; right = (cos h, sin h).
        double nx = x + (-Math.sin(h) * forward + Math.cos(h) * right) * dt;
        double ny = y + (Math.cos(h) * forward + Math.sin(h) * right) * dt;
        double nh = wrap(headingDeg - Math.toDegrees(cw * dt));
        // The field walls: no corner of the robot past the wall.
        double half = field.wallHalf + 1e-6;
        boolean wall = false;
        for (double[] c : footprint(nx, ny, nh)) if (Math.abs(c[0]) > half || Math.abs(c[1]) > half) wall = true;
        SimField.Element hit = wall ? null : field.bumps(footprint(nx, ny, nh), heightIn);
        if (wall || hit != null) {
            // Bumped: the robot stays where it is (its wheels may still turn, like a real robot pushing a wall).
            bumpedInto = wall ? "field wall" : hit.name;
            yawRateDeg = 0;
            return;
        }
        x = nx;
        y = ny;
        headingDeg = nh;
        yawRateDeg = -Math.toDegrees(cw);
        // Moving freely again: no longer bumping (a robot standing still keeps its last bump shown).
        if (Math.abs(forward) + Math.abs(right) + Math.abs(cw) > 1e-6) bumpedInto = "";
    }

    // The wheels turn to match a hand push (they are not driven).
    private void setWheelsFromChassis(double forward, double right, double cw) {
        double ipt = inchesPerTick();
        double k = (trackWidthIn + wheelBaseIn) / 2;
        double strafe = driveType.equals("mecanum") ? right / strafeEfficiency : 0;
        double[] v = {forward + strafe + cw * k, forward - strafe - cw * k, forward - strafe + cw * k, forward + strafe - cw * k};
        for (int i = 0; i < wheels.size(); i++) wheels.get(i).forceSpin(mount(i) * v[i] / ipt);
    }

    static double wrap(double deg) {
        double d = deg % 360;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return d;
    }

    // ---------------------------------------------------------------- reading the robot file

    private static double num(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) return fallback;
        double d;
        try {
            d = Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: \"" + key + " = " + v + "\" needs a number");
        }
        if (!finite(d)) throw new IllegalArgumentException("Robot file: \"" + key + " = " + v + "\" needs an ordinary number");
        return d;
    }

    // A number that must be more than 0 (a size, a speed, counts per turn): 0 would divide by zero in the physics.
    private static double above0(Properties p, String key, double fallback) {
        double d = num(p, key, fallback);
        if (!(d > 0)) throw new IllegalArgumentException("Robot file: \"" + key + "\" must be more than 0");
        return d;
    }

    private static List<String> list(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split(",")) if (!part.trim().isEmpty()) out.add(part.trim());
        return out;
    }

    private static List<String> sortedKeys(Properties p, String prefix) {
        List<String> out = new ArrayList<>();
        for (String k : p.stringPropertyNames()) if (k.startsWith(prefix)) out.add(k);
        java.util.Collections.sort(out);
        return out;
    }

    // "lift, max=2700, gravity=0.08" -> {"": "lift", "max": "2700", "gravity": "0.08"}
    private static Map<String, String> settings(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        String last = null;
        for (String part : list(text)) {
            int eq = part.indexOf('=');
            if (eq >= 0) {
                last = part.substring(0, eq).trim();
                out.put(last, part.substring(eq + 1).trim());
            } else if (last == null) {
                out.put("", part);                                   // the first word: the kind
            } else if (isNumber(part) && isNumber(out.get(last).substring(out.get(last).lastIndexOf(',') + 1))) {
                out.put(last, out.get(last) + "," + part);           // "at=-7,0,4": a list of numbers goes on
            } else {
                throw new IllegalArgumentException("Robot file: \"" + text.trim() + "\": \"" + part
                        + "\" has no =. After the first word, settings are name=value (did you mean " + part + "=...?)");
            }
        }
        return out;
    }

    private static double positive(Map<String, String> s, String key, String line) {
        double v = dbl(s, key, 0);
        if (!(v > 0)) throw new IllegalArgumentException("Robot file: \"" + line + "\": " + key + " must be more than 0");
        return v;
    }

    private static Map<String, Map<String, String>> lines(Properties p, String prefix) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (String key : sortedKeys(p, prefix)) out.put(key.substring(prefix.length()), settings(p.getProperty(key)));
        return out;
    }

    private static boolean isNumber(String t) {
        try {
            Double.parseDouble(t.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double dbl(Map<String, String> s, String key, double fallback) {
        String v = s.get(key);
        if (v == null) return fallback;
        double d;
        try {
            d = Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: \"" + key + "=" + v + "\" needs a number");
        }
        if (!finite(d)) throw new IllegalArgumentException("Robot file: \"" + key + "=" + v + "\" needs an ordinary number");
        return d;
    }

    // ---------------------------------------------------------------- servos and switches

    /**
     * A pretend servo: it moves toward where it was sent, at the servo speed. Like a hub servo it also
     * takes a pulse range (PwmControl.setPwmRange, what .fullRange() calls): the same position then
     * means another pulse, so another angle. Inside, positions are kept in the robot file's pulse range
     * (pwm=, default 600-2400 us): that is what the shapes and the robot file's numbers use.
     */
    static final class SimServo {
        final String name;
        String partName = "";       // the catalog part, if the robot file names one
        String partId = "";         // its catalog id
        double rangeDeg = Double.NaN;   // how far positions 0..1 (in the file's pulse range) turn it
        double speed = Double.NaN;  // raw positions per second from the part (NaN = the file's servo.speed)
        double actual;              // where it really is (raw 0..1 in the file's pulse range)
        private double target;      // where it is going (raw, the file's pulse range)
        private double commanded = Double.NaN;   // the last position the OpMode set (its own scale)
        private double scaleMin = 0, scaleMax = 1;
        private Servo.Direction direction = Servo.Direction.FORWARD;
        final Servo device;
        // The robot file's pulse range (pwm=) and the one the code set now (the SDK's 600-2400 after INIT).
        final double fileLowUs, fileHighUs;
        private double lowUs, highUs;

        final double start;         // where it starts (the robot file's number)

        SimServo(String name, double start) {
            this(name, start, SimParts.SDK_LOW_US, SimParts.SDK_HIGH_US);
        }

        SimServo(String name, double start, double fileLowUs, double fileHighUs) {
            this.name = name;
            this.start = start;
            this.actual = start;
            this.target = start;
            this.fileLowUs = fileLowUs;
            this.fileHighUs = fileHighUs;
            this.lowUs = fileLowUs;
            this.highUs = fileHighUs;
            // A Servo that is also a PwmControl, like the hub's ServoImplEx.
            this.device = (Servo) Proxy.newProxyInstance(Servo.class.getClassLoader(), new Class<?>[] {Servo.class, PwmControl.class},
                    (proxy, m, a) -> handle(proxy, m.getName(), a, m.getReturnType()));
        }

        /** Like the FTC app at INIT: direction, range and pulse range back to normal (it stays where it is). */
        synchronized void resetConfig() {
            direction = Servo.Direction.FORWARD;
            scaleMin = 0;
            scaleMax = 1;
            lowUs = SimParts.SDK_LOW_US;
            highUs = SimParts.SDK_HIGH_US;
        }

        /** The pulse range the code set (microseconds): {low, high}. */
        synchronized double[] pulseRange() {
            return new double[] {lowUs, highUs};
        }

        // A position in the code's pulse range -> the same pulse as a position in the file's range.
        private double toFile(double raw) {
            double us = lowUs + raw * (highUs - lowUs);
            return (us - fileLowUs) / (fileHighUs - fileLowUs);
        }

        private synchronized Object handle(Object proxy, String method, Object[] a, Class<?> returns) {
            if (isBlocked() && method.startsWith("set")) return null;
            switch (method) {
                case "setPosition": {
                    double pos = Math.max(0, Math.min(1, ((Number) a[0]).doubleValue()));
                    commanded = pos;
                    // Like the SDK: reverse first, then scale into the range, then the pulse.
                    double p = direction == Servo.Direction.REVERSE ? 1 - pos : pos;
                    target = toFile(scaleMin + p * (scaleMax - scaleMin));
                    return null;
                }
                // Like the SDK: the hub's last raw position (0 before any command) turned back through
                // the range and the direction. After a command that is just the position sent.
                case "getPosition": {
                    if (!Double.isNaN(commanded)) return commanded;
                    double p = Math.max(0, Math.min(1, (0 - scaleMin) / (scaleMax - scaleMin)));
                    return direction == Servo.Direction.REVERSE ? 1 - p : p;
                }
                case "scaleRange": {
                    double lo = ((Number) a[0]).doubleValue(), hi = ((Number) a[1]).doubleValue();
                    // Like the SDK: 0 <= min < max <= 1, or an error.
                    if (!(lo >= 0 && hi <= 1 && lo < hi)) throw new IllegalArgumentException("scaleRange(" + lo + ", " + hi + "): need 0 <= min < max <= 1");
                    scaleMin = lo;
                    scaleMax = hi;
                    return null;
                }
                // PwmControl: the pulse range. It does not move the servo by itself (the hub sends the new
                // pulse with the next position).
                case "setPwmRange": {
                    PwmControl.PwmRange r = (PwmControl.PwmRange) a[0];
                    lowUs = r.usPulseLower;
                    highUs = r.usPulseUpper;
                    return null;
                }
                case "getPwmRange": return new PwmControl.PwmRange(lowUs, highUs);
                case "isPwmEnabled": return true;
                case "getPulseWidth": return fileLowUs + target * (fileHighUs - fileLowUs);
                case "setPulseWidth": {
                    target = (((Number) a[0]).doubleValue() - fileLowUs) / (fileHighUs - fileLowUs);
                    return null;
                }
                case "setDirection": direction = (Servo.Direction) a[0]; return null;
                case "getDirection": return direction;
                case "getPortNumber": return 0;
                case "getDeviceName": return "Simulated servo";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == a[0];
                default: return SimDefaults.of(returns);
            }
        }

        synchronized void step(double dt, double speed) {
            double stepMax = speed * dt;
            actual += Math.max(-stepMax, Math.min(stepMax, target - actual));
        }

        synchronized double target() {
            return target;
        }

        /** The last position the OpMode sent (its own scale); NaN = none yet. */
        synchronized double commanded() {
            return commanded;
        }
    }

    /** A pretend switch: pressed when its motor is near its bottom, or when clicked in the panel. */
    final class SimTouch {
        final String motor;
        final double below;
        // A switch part way along (at=, width=): NaN = at the bottom (below=).
        double at = Double.NaN, width = 10;
        boolean manual = false;

        SimTouch(String motor, double below) {
            this.motor = motor;
            this.below = below;
        }

        boolean pressed() {
            synchronized (SimRobot.this) {
                if (manual) return true;
                SimMotor m = motor == null ? null : motors.get(motor);
                if (m != null && !Double.isNaN(at)) return Math.abs(m.shaft() - at) <= width;
                return m != null && m.nearBottom(below);
            }
        }

        HardwareDevice touchSensor() {
            return (HardwareDevice) Proxy.newProxyInstance(TouchSensor.class.getClassLoader(),
                    new Class<?>[] {TouchSensor.class, DigitalChannel.class}, (proxy, m, a) -> {
                        switch (m.getName()) {
                            case "isPressed": return pressed();
                            case "getValue": return pressed() ? 1.0 : 0.0;
                            // A REV touch sensor on a digital port reads FALSE when pressed.
                            case "getState": return !pressed();
                            case "getDeviceName": return "Simulated touch sensor";
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals": return proxy == a[0];
                            default: return SimDefaults.of(m.getReturnType());
                        }
                    });
        }
    }
}
