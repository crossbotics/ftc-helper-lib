package io.github.jasoncross.ftchelper.sim;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
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
    final Map<String, double[]> crServos = new LinkedHashMap<>();   // name -> {power, reversed (1/0)}
    final Map<String, SimTouch> touches = new LinkedHashMap<>();
    // Webcams (camera.KEY = name=Webcam 1, forward=..., see SimCamera), by their configuration name.
    final Map<String, SimCamera> cameras = new LinkedHashMap<>();
    // Limelights (limelight.KEY = name=limelight, ...): their pretend camera is in cameras too.
    final Map<String, SimLimelight> limelights = new LinkedHashMap<>();
    private final Map<String, HardwareDevice> devices = new LinkedHashMap<>();
    final String imuName;
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
    double startX = 0, startY = -48, startHeading = 0;
    // The season's field, and the element the robot bumped into last ("" = none).
    SimField field = SimField.empty();
    // A 3D model of the field for the page (field.model in the robot file), or null: the page then
    // builds the field from the field file's shapes.
    Path fieldModel = null;
    // The robot file's folder (relative paths in it start here); null for a robot not read from a file.
    final Path dir;
    // The robot as simple shapes (shape.NAME lines): for the 3D view and the size rules.
    final SimShapes shapes;
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
    private double imuZero = 0;
    private volatile double yawRateDeg = 0;   // degrees per second, counterclockwise
    // A hand push: inches per second forward/right, and how long it still lasts.
    private double pushForward = 0, pushRight = 0, pushLeft = 0;
    // Per flywheel: seconds since the last piece went through (a running feeder sends one every 0.3 s).
    private final Map<String, Double> sinceShot = new LinkedHashMap<>();
    static final double SECONDS_PER_PIECE = 0.3;
    private int shots = 0;

    // The parts catalog this robot uses: the library's, plus the team's own file if it names one (parts =).
    private final SimParts parts;

    private SimRobot(Properties p, Path dir) {
        this.dir = dir;
        String teamParts = p.getProperty("parts", "").trim();
        parts = teamParts.isEmpty() ? SimParts.catalog() : SimParts.withTeamFile(dir.resolve(teamParts).normalize());
        batteryVolts = num(p, "battery", 12.8);
        servoSpeed = num(p, "servo.speed", 1.0);
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
            double maxTicks = num(p, "drive.maxTicksPerSec", dp != null ? dp.maxTicksPerSec() : 2780);
            wheelDiameterIn = num(p, "drive.wheelDiameterIn", 3.78);
            ticksPerRev = num(p, "drive.ticksPerRev", dp != null ? dp.countsPerRev : 537.7);
            gearRatio = num(p, "drive.gearRatio", 1);
            trackWidthIn = num(p, "drive.trackWidthIn", 15);
            wheelBaseIn = num(p, "drive.wheelBaseIn", 13);
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
            motors.put(name, m);
        }
        for (String key : sortedKeys(p, "servo.")) {
            if (key.equals("servo.speed")) continue;
            String name = key.substring("servo.".length());
            // servo.NAME = start position [, part=catalog-id]
            Map<String, String> s = settings(p.getProperty(key));
            SimServo servo = new SimServo(name, dbl(s, "", 0.5));
            if (s.containsKey("part")) {
                SimParts.Servo part = parts.servo(s.get("part"));
                servo.partName = part.name;
                servo.partId = part.id;
                servo.rangeDeg = part.rangeDeg;
                servo.speed = part.rawPerSecond();
            }
            // A servo not in the catalog: its range (degrees) and seconds per 60 degrees (at about 5 V).
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
        for (String key : sortedKeys(p, "crservo.")) crServos.put(key.substring("crservo.".length()), new double[] {0, 0});
        // A flywheel's feeder is a motor or a CR servo (a windmill, a feed roller).
        for (SimMotor m : motors.values()) {
            if (m.feeder != null && !motors.containsKey(m.feeder) && !crServos.containsKey(m.feeder)) {
                throw new IllegalArgumentException("Robot file: motor." + m.name + ": feeder=" + m.feeder
                        + " is not a motor or crservo in the robot file");
            }
        }
        for (String key : sortedKeys(p, "touch.")) {
            Map<String, String> s = settings(p.getProperty(key));
            touches.put(key.substring("touch.".length()), new SimTouch(s.get(""), dbl(s, "below", 20)));
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
        imuName = p.getProperty("imu", "").trim();
        lengthIn = num(p, "robot.lengthIn", 18);
        widthIn = num(p, "robot.widthIn", 18);
        heightIn = num(p, "robot.heightIn", 18);
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
        massKg = num(p, "robot.massKg", 15);
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
        buildDevices();
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
        }
        return robot;
    }

    // ---------------------------------------------------------------- devices

    private void buildDevices() {
        for (SimMotor m : motors.values()) devices.put(m.name, m.device);
        for (SimServo s : servos.values()) devices.put(s.name, s.device);
        for (Map.Entry<String, double[]> e : crServos.entrySet()) devices.put(e.getKey(), crServo(e.getValue()));
        for (Map.Entry<String, SimTouch> e : touches.entrySet()) {
            devices.put(e.getKey(), e.getValue().touchSensor());
        }
        if (!imuName.isEmpty()) devices.put(imuName, imu());
        for (SimCamera c : cameras.values()) if (c.device != null) devices.put(c.name, c.device);
        for (SimLimelight l : limelights.values()) devices.put(l.cam.name, l);
        devices.put("Control Hub battery", voltageSensor());
    }

    /** A new hardware map with every device, for one OpMode run (like the FTC app makes at INIT). */
    synchronized HardwareMap hardwareMap() {
        return new SimHardwareMap(devices);
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
                            synchronized (this) { state[0] = Math.max(-1, Math.min(1, ((Number) a[0]).doubleValue())); }
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

    /** Puts the robot back at the start spot, facing away from the drivers. */
    synchronized void resetPose() {
        x = startX;
        y = startY;
        headingDeg = startHeading;
        bumpedInto = "";
        if (world != null) world.place(x, y, headingDeg);
        if (pieces != null) pieces.reset();   // back to the start of a match: the pieces too
    }

    /** What a robot at this pose would overlap ("field wall" or an element's name), or null. */
    private String blockedAt(double cx, double cy, double h) {
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
        headingDeg = nh;
        bumpedInto = "";
        if (world != null) world.place(x, y, headingDeg);
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
        for (SimMotor m : motors.values()) m.step(dt, battery);
        if (!wheels.isEmpty()) moveChassis(dt);
        pieces.step();
        for (SimServo s : servos.values()) s.step(dt, Double.isNaN(s.speed) ? servoSpeed : s.speed);
        // A flywheel loses speed when its feeder starts pushing a piece in.
        for (SimMotor m : motors.values()) {
            if (m.kind != SimMotor.Kind.FLYWHEEL || m.feeder == null) continue;
            SimMotor feeder = motors.get(m.feeder);
            double[] cr = crServos.get(m.feeder);
            boolean on = feeder != null ? Math.abs(feeder.appliedPower) > 0.3 : cr != null && Math.abs(cr[0]) > 0.3;
            double since = sinceShot.getOrDefault(m.name, SECONDS_PER_PIECE) + dt;
            // While the feeder runs, a piece goes through every SECONDS_PER_PIECE (the first at once).
            if (on && since >= SECONDS_PER_PIECE && Math.abs(m.shaftSpeed()) > 100) {
                // With game pieces, a shot needs a piece in the robot (it flies off the shooter shape).
                if (!pieces.shootsPieces() || pieces.shoot(m)) {
                    m.shot();
                    shots++;
                }
                since = 0;
            }
            if (!on) since = SECONDS_PER_PIECE;
            sinceShot.put(m.name, since);
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
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: \"" + key + " = " + v + "\" needs a number");
        }
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
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: \"" + key + "=" + v + "\" needs a number");
        }
    }

    // ---------------------------------------------------------------- servos and switches

    /** A pretend servo: it moves toward where it was sent, at the servo speed. */
    static final class SimServo {
        final String name;
        String partName = "";       // the catalog part, if the robot file names one
        String partId = "";         // its catalog id
        double rangeDeg = Double.NaN;   // how far positions 0..1 turn it (part, or rangeDeg= in the robot file)
        double speed = Double.NaN;  // raw positions per second from the part (NaN = the file's servo.speed)
        double actual;              // where it really is (raw 0..1)
        private double target;      // where it is going (raw)
        private double commanded = Double.NaN;   // the last position the OpMode set (its own scale)
        private double scaleMin = 0, scaleMax = 1;
        private Servo.Direction direction = Servo.Direction.FORWARD;
        final Servo device;

        SimServo(String name, double start) {
            this.name = name;
            this.actual = start;
            this.target = start;
            this.device = (Servo) Proxy.newProxyInstance(Servo.class.getClassLoader(), new Class<?>[] {Servo.class},
                    (proxy, m, a) -> handle(proxy, m.getName(), a, m.getReturnType()));
        }

        /** Like the FTC app at INIT: direction and range back to normal (it stays where it is). */
        synchronized void resetConfig() {
            direction = Servo.Direction.FORWARD;
            scaleMin = 0;
            scaleMax = 1;
        }

        private synchronized Object handle(Object proxy, String method, Object[] a, Class<?> returns) {
            if (isBlocked() && method.startsWith("set")) return null;
            switch (method) {
                case "setPosition": {
                    double pos = Math.max(0, Math.min(1, ((Number) a[0]).doubleValue()));
                    commanded = pos;
                    // Like the SDK: reverse first, then scale into the range.
                    double p = direction == Servo.Direction.REVERSE ? 1 - pos : pos;
                    target = scaleMin + p * (scaleMax - scaleMin);
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
    }

    /** A pretend switch: pressed when its motor is near its bottom, or when clicked in the panel. */
    final class SimTouch {
        final String motor;
        final double below;
        boolean manual = false;

        SimTouch(String motor, double below) {
            this.motor = motor;
            this.below = below;
        }

        boolean pressed() {
            synchronized (SimRobot.this) {
                if (manual) return true;
                SimMotor m = motor == null ? null : motors.get(motor);
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
