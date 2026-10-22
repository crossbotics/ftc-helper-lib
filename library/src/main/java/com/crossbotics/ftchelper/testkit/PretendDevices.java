package com.crossbotics.ftchelper.testkit;

import com.qualcomm.robotcore.hardware.AnalogInput;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.ServoControllerEx;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.crossbotics.ftchelper.robot.Devices;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A pretend robot configuration for JUnit tests on a laptop: give it to your {@code Robot} instead of
 * the hardwareMap, and every motor, servo and switch it asks for is made on the spot. Your test then
 * moves the pretend encoders, presses the pretend switches and checks the powers and positions sent.
 * No robot, no Control Hub: a test runs in a second, after every change.
 *
 * <pre>{@code
 * public class LiftTest {
 *     @Test public void bothLiftMotorsGetTheSamePower() {
 *         PretendDevices devices = new PretendDevices();
 *         Robot robot = new Robot();
 *         robot.setup(devices);            // INIT, with pretend devices (Tunables.java as written)
 *         robot.startForTests();           // START
 *         robot.lift.goTo("high");
 *         robot.all().update();            // one loop
 *         assertTrue(devices.motor("lift_left").power > 0);
 *         assertEquals(devices.motor("lift_left").power, devices.motor("lift_right").power, 1e-9);
 *     }
 * }
 * }</pre>
 *
 * <p>Why in the library (not only in its own tests): so a team's tests in TeamCode/src/test can use it
 * with no extra Gradle lines. It is a few small classes; nothing in it runs on the robot unless a test
 * makes it.
 *
 * Docs: docs/modules/testkit.md, docs/guides/testing.md
 */
public class PretendDevices implements Devices {
    private final Map<String, PretendMotor> motors = new HashMap<>();
    private final Map<String, PretendServo> servos = new HashMap<>();
    private final Map<String, PretendCRServo> crServos = new HashMap<>();
    private final Map<String, PretendPinpoint> pinpoints = new HashMap<>();
    private final Map<String, boolean[]> switches = new HashMap<>();
    private final Map<String, double[]> analog = new HashMap<>();
    private final Set<String> notConfigured = new HashSet<>();
    private double volts = Double.NaN;

    /**
     * A fresh pretend configuration. It also forgets what the library keeps between programs in one
     * robot app (tuned numbers in memory, a turret's last angle, autonomous's alliance and pose), and
     * points the settings file at an empty temporary file: so every test starts like a robot just
     * turned on, whatever ran before it. And motors read their pretend encoder every time it is asked
     * (on a robot a reading is reused for 3 ms), so a test can move it and update at once.
     */
    public PretendDevices() {
        com.crossbotics.ftchelper.tuning.Tunable.forgetAllForTests();
        com.crossbotics.ftchelper.robot.Turret.forgetAngles();
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        // A test moves a pretend encoder and updates at once: read it every time (no 3 ms reuse).
        com.crossbotics.ftchelper.safety.SafeMotor.freshReadingsForTests(true);
        try {
            java.io.File f = java.io.File.createTempFile("ftc-helper-test", ".properties");
            f.deleteOnExit();
            com.crossbotics.ftchelper.core.SettingsFile.useForTests(new com.crossbotics.ftchelper.core.SettingsFile(f));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("PretendDevices: could not make a temporary settings file: " + e.getMessage(), e);
        }
    }

    /**
     * The pretend motor with this name (made now if nothing asked for it yet).
     *
     * @param name the configuration name, e.g. "lift_left"
     */
    public PretendMotor motor(String name) {
        PretendMotor m = motors.get(name);
        if (m == null) motors.put(name, m = new PretendMotor());
        return m;
    }

    /** The pretend servo with this name (made now if needed). */
    public PretendServo servo(String name) {
        PretendServo s = servos.get(name);
        if (s == null) servos.put(name, s = new PretendServo());
        return s;
    }

    /** The pretend CR servo with this name (made now if needed). */
    public PretendCRServo crServo(String name) {
        PretendCRServo s = crServos.get(name);
        if (s == null) crServos.put(name, s = new PretendCRServo());
        return s;
    }

    /** The pretend goBILDA Pinpoint with this name (made now if needed). */
    public PretendPinpoint pinpoint(String name) {
        PretendPinpoint p = pinpoints.get(name);
        if (p == null) pinpoints.put(name, p = new PretendPinpoint());
        return p;
    }

    /**
     * Presses (true) or lets go of (false) a pretend switch: a touch sensor, or a magnetic switch on a
     * digital port (pressed = it reads LOW, like the REV one).
     *
     * @param name the switch's configuration name, e.g. "lift_bottom"
     */
    public PretendDevices press(String name, boolean pressed) {
        sw(name)[0] = pressed;
        return this;
    }

    /** Sets a pretend analog input (an absolute encoder): volts now, out of {@code maxVolts} (3.3 on a REV hub). */
    public PretendDevices analog(String name, double volts, double maxVolts) {
        double[] v = analog.get(name);
        if (v == null) analog.put(name, v = new double[2]);
        v[0] = volts;
        v[1] = maxVolts;
        return this;
    }

    /**
     * These names are NOT in the configuration: asking for one stops (like the real hardwareMap), and an
     * {@code optional()} part that uses one is off. Use it to test a part not on the robot yet.
     */
    public PretendDevices notConfigured(String... names) {
        for (String n : names) notConfigured.add(n);
        return this;
    }

    /** The battery the hubs read, volts (default: none, NaN). */
    public PretendDevices battery(double volts) {
        this.volts = volts;
        return this;
    }

    @Override
    public double batteryVolts() {
        return volts;
    }

    @Override
    public boolean has(String name) {
        return !notConfigured.contains(name);
    }

    /**
     * Like {@link #get}, but null for a name that is not configured, and for a kind of device the kit has
     * no pretend one for (a camera, a color sensor): parts that can do without it (they warn at INIT)
     * then go on as on a robot where it is unplugged.
     */
    @Override
    public <T> T tryGet(Class<? extends T> type, String name) {
        if (notConfigured.contains(name)) return null;
        try {
            return get(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<? extends T> type, String name) {
        if (notConfigured.contains(name)) {
            throw new IllegalArgumentException("No " + type.getSimpleName() + " named \"" + name + "\" in the active robot configuration.");
        }
        if (type == DcMotorEx.class || type == com.qualcomm.robotcore.hardware.DcMotor.class) return (T) motor(name).motor;
        if (type == Servo.class) return (T) servo(name).servo;
        if (type == CRServo.class) return (T) crServo(name).servo;
        if (type == com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.class) return (T) pinpoint(name);
        if (type == TouchSensor.class) {
            final boolean[] pressed = sw(name);
            return (T) Proxy.newProxyInstance(TouchSensor.class.getClassLoader(), new Class<?>[]{TouchSensor.class},
                    (proxy, m, a) -> m.getName().equals("isPressed") ? (Object) pressed[0]
                            : m.getName().equals("getValue") ? (Object) (pressed[0] ? 1.0 : 0.0) : nothing(m.getReturnType()));
        }
        if (type == DigitalChannel.class) {
            final boolean[] pressed = sw(name);
            return (T) Proxy.newProxyInstance(DigitalChannel.class.getClassLoader(), new Class<?>[]{DigitalChannel.class},
                    (proxy, m, a) -> m.getName().equals("getState") ? (Object) !pressed[0] : nothing(m.getReturnType()));
        }
        if (type == AnalogInput.class) {
            final double[] v = analog.containsKey(name) ? analog.get(name) : new double[] {0, 3.3};
            analog.put(name, v);
            // AnalogInput is a class: a subclass that answers the test's numbers.
            return (T) new AnalogInput(null, 0) {
                @Override public double getVoltage() { return v[0]; }
                @Override public double getMaxVoltage() { return v[1]; }
            };
        }
        if (type == com.qualcomm.hardware.rev.RevBlinkinLedDriver.class) {
            // The SDK's own class, on a pretend servo controller.
            ServoControllerEx c = (ServoControllerEx) Proxy.newProxyInstance(ServoControllerEx.class.getClassLoader(),
                    new Class<?>[]{ServoControllerEx.class}, (proxy, m, a) -> nothing(m.getReturnType()));
            return (T) new com.qualcomm.hardware.rev.RevBlinkinLedDriver(c, 0);
        }
        throw new IllegalArgumentException("PretendDevices has no pretend " + type.getSimpleName() + " (\"" + name
                + "\"): test that part on the robot or in the simulator");
    }

    private boolean[] sw(String name) {
        boolean[] s = switches.get(name);
        if (s == null) switches.put(name, s = new boolean[1]);
        return s;
    }

    // What a pretend device answers to a question it does not know: 0, false or nothing.
    static Object nothing(Class<?> r) {
        if (r == boolean.class) return false;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == double.class) return 0.0;
        if (r == float.class) return 0f;
        return null;
    }
}
