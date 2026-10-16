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
    final Map<String, double[]> crServos = new LinkedHashMap<>();   // name -> {power}
    final Map<String, SimTouch> touches = new LinkedHashMap<>();
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
    final List<SimMotor> wheels = new ArrayList<>();   // FL, FR, BL, BR (tank: FL, FR, [BL, BR])
    double wheelDiameterIn = 3.78, ticksPerRev = 537.7, gearRatio = 1, trackWidthIn = 15, wheelBaseIn = 13;
    double strafeEfficiency = 0.85;
    // The robot on the field: inches from the field center (x right, y away from the drivers),
    // heading in degrees, counterclockwise = positive, 0 = facing away from the drivers.
    double x = 0, y = -48, headingDeg = 0;
    private double imuZero = 0;
    // A hand push: inches per second forward/right, and how long it still lasts.
    private double pushForward = 0, pushRight = 0, pushLeft = 0;
    private final Map<String, Boolean> feederWasOn = new LinkedHashMap<>();
    private int shots = 0;

    private SimRobot(Properties p) {
        batteryVolts = num(p, "battery", 12.8);
        servoSpeed = num(p, "servo.speed", 1.0);
        int port = 0;
        // Drivetrain motors first, so they get the first ports like on a real hub.
        driveType = p.getProperty("drive.type", "none").trim().toLowerCase(Locale.US);
        if (!driveType.equals("none")) {
            double maxTicks = num(p, "drive.maxTicksPerSec", 2780);
            wheelDiameterIn = num(p, "drive.wheelDiameterIn", 3.78);
            ticksPerRev = num(p, "drive.ticksPerRev", 537.7);
            gearRatio = num(p, "drive.gearRatio", 1);
            trackWidthIn = num(p, "drive.trackWidthIn", 15);
            wheelBaseIn = num(p, "drive.wheelBaseIn", 13);
            strafeEfficiency = num(p, "drive.strafeEfficiency", 0.85);
            for (String name : list(p.getProperty("drive.motors", ""))) {
                SimMotor m = new SimMotor(name, port++, SimMotor.Kind.WHEEL, maxTicks, 0.15);
                m.ticksPerRev = ticksPerRev;
                wheels.add(m);
                motors.put(name, m);
            }
        }
        for (String key : sortedKeys(p, "motor.")) {
            String name = key.substring("motor.".length());
            Map<String, String> s = settings(p.getProperty(key));
            SimMotor.Kind kind = SimMotor.Kind.valueOf(s.getOrDefault("", "free").toUpperCase(Locale.US));
            double max = dbl(s, "maxTicksPerSec", 2780);
            SimMotor m = new SimMotor(name, port++, kind, max, kind == SimMotor.Kind.FLYWHEEL ? 0.6 : 0.12);
            m.ticksPerRev = dbl(s, "ticksPerRev", kind == SimMotor.Kind.FLYWHEEL ? 28 : 537.7);
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
            servos.put(name, new SimServo(name, Double.parseDouble(p.getProperty(key).trim())));
        }
        for (String key : sortedKeys(p, "crservo.")) crServos.put(key.substring("crservo.".length()), new double[] {0});
        for (String key : sortedKeys(p, "touch.")) {
            Map<String, String> s = settings(p.getProperty(key));
            touches.put(key.substring("touch.".length()), new SimTouch(s.get(""), dbl(s, "below", 20)));
        }
        imuName = p.getProperty("imu", "").trim();
        buildDevices();
    }

    /** Reads a robot file (see sim/robots/starter.properties). */
    static SimRobot load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        // Properties keeps "# comments" out, but not comments after a value: cut them off.
        for (String k : p.stringPropertyNames()) {
            String v = p.getProperty(k);
            int hash = v.indexOf('#');
            if (hash >= 0) p.setProperty(k, v.substring(0, hash).trim());
        }
        return new SimRobot(p);
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
        for (double[] cr : crServos.values()) cr[0] = 0;
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
        x = 0;
        y = -48;
        headingDeg = 0;
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
        for (SimServo s : servos.values()) s.step(dt, servoSpeed);
        // A flywheel loses speed when its feeder starts pushing a piece in.
        for (SimMotor m : motors.values()) {
            if (m.kind != SimMotor.Kind.FLYWHEEL || m.feeder == null) continue;
            SimMotor feeder = motors.get(m.feeder);
            boolean on = feeder != null && Math.abs(feeder.appliedPower) > 0.3;
            if (on && !feederWasOn.getOrDefault(m.name, false) && Math.abs(m.shaftSpeed()) > 100) {
                m.shot();
                shots++;
            }
            feederWasOn.put(m.name, on);
        }
    }

    private double inchesPerTick() {
        return Math.PI * wheelDiameterIn / (ticksPerRev * gearRatio);
    }

    // Left wheels are mounted mirrored: their shaft turns backward when the wheel rolls forward.
    private double mount(int index) {
        return index % 2 == 0 ? -1 : 1;
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
        double h = Math.toRadians(headingDeg);
        // Forward = (-sin h, cos h) on the field; right = (cos h, sin h).
        x += (-Math.sin(h) * forward + Math.cos(h) * right) * dt;
        y += (Math.cos(h) * forward + Math.sin(h) * right) * dt;
        headingDeg = wrap(headingDeg - Math.toDegrees(cw * dt));
        // The field walls: 72 in from the center, minus half the robot.
        double edge = 72 - 9;
        x = Math.max(-edge, Math.min(edge, x));
        y = Math.max(-edge, Math.min(edge, y));
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
        return v == null || v.trim().isEmpty() ? fallback : Double.parseDouble(v.trim());
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
        for (String part : list(text)) {
            int eq = part.indexOf('=');
            if (eq < 0) out.put("", part);
            else out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return out;
    }

    private static double dbl(Map<String, String> s, String key, double fallback) {
        String v = s.get(key);
        return v == null ? fallback : Double.parseDouble(v);
    }

    // ---------------------------------------------------------------- servos and switches

    /** A pretend servo: it moves toward where it was sent, at the servo speed. */
    static final class SimServo {
        final String name;
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
                // Like the SDK: the last position sent (0 if none yet: the SDK cannot know either).
                case "getPosition": return Double.isNaN(commanded) ? 0.0 : commanded;
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
