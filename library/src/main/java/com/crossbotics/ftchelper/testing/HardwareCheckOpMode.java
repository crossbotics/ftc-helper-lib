package com.crossbotics.ftchelper.testing;

import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.AnalogInput;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.ColorSensor;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.DistanceSensor;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

/**
 * A ready-made "check every part" OpMode. It lists every device in the active robot configuration
 * and lets you test them one at a time: spin a motor, move a servo, read a sensor.
 * Use it after wiring changes, before every event, and when something "does not work".
 *
 * <p>To use it, make this tiny file in TeamCode:
 * <pre>{@code
 * @TeleOp(name = "Hardware Check", group = "Test")
 * public class HardwareCheck extends HardwareCheckOpMode { }
 * }</pre>
 * ({@code abstract} on this class means it is only used through such a tiny class of your own;
 * the {@code @TeleOp} line is what puts it in the Driver Station's list.)
 *
 * <p>With your robot in five files, add {@code @Override protected RobotDefinition robot() { return new Robot(); }}:
 * INIT then also lists every device Robot.java names that is NOT in the configuration, and what that
 * means ("INIT will stop", or for a part marked {@code optional()}: "the part is off (fine)").
 *
 * <p>Controls (gamepad 1): D-pad up/down = choose a device.
 * <ul>
 *   <li>Motor / CR servo: right stick Y = power (max 50%; hold right bumper for 100%). A = zero the encoder.</li>
 *   <li>Servo: D-pad right/left = +/- 0.01, bumpers = +/- 0.1, X = 0.5. (A servo does not move until you press.)</li>
 *   <li>Sensors: just read the values.</li>
 * </ul>
 * Changing the chosen device stops the last one. Put the robot on a block first.
 * Help: GUIDE (or hold START 1 s) = a help page with every button, like the finders; while it is open,
 * every motor and CR servo is stopped and the buttons do nothing.
 * (A CR servo is a "continuous rotation" servo: it spins like a small motor instead of going to a position.)
 *
 * Steps: docs/guides/testing.md. Docs: docs/modules/testing.md
 */
public abstract class HardwareCheckOpMode extends LinearOpMode {

    // How many devices fit on one screen page of the list.
    private static final int PAGE = 6;

    // One device found in the configuration: its name, what kind it is, and the SDK object.
    private static final class Device {
        final String name;
        final String kind;
        final HardwareDevice device;
        // The servo position WE last sent (0..1). NaN = not moved yet in this OpMode.
        double servoPosition = Double.NaN;
        Device(String name, String kind, HardwareDevice device) {
            this.name = name;
            this.kind = kind;
            this.device = device;
        }
    }

    private final List<Device> devices = new ArrayList<>();
    // which device in the list is chosen now (its place, counting from 0)
    private int selected = 0;
    // Each button's state in the LAST loop (same order as "now" in handleInput), to notice new presses.
    private final boolean[] was = new boolean[10];

    /**
     * The whole check. The FTC SDK calls this when you press INIT.
     * {@code @Override} means it fills in the method LinearOpMode asks for.
     */
    @Override
    public void runOpMode() {
        // ---- INIT ----
        RobotTelemetry t = new RobotTelemetry(telemetry);
        findDevices();
        startImus();   // IMU start-up can take a moment: do it in INIT, never in the loop
        t.text("Found " + devices.size() + " devices. Robot on a block! Press START.");
        // Robot.java against this configuration (only if robot() gives the robot).
        robotLines = robotCheck();
        // INIT: the screen until START (GUIDE shows the help here too).
        while (opModeInInit()) {
            updateHelp();
            if (helpOpen) {
                showHelp(t);
                t.text("Press START on the Driver Station to begin.");
            } else {
                t.text("Found " + devices.size() + " devices. Robot on a block! Press START.");
                for (String line : robotLines) t.text(line);
                t.text("GUIDE (center button), or hold START 1 s = help.");
            }
            t.update();
        }
        // ---- Running ----
        // try/finally: the "finally" part runs however the loop ends (STOP pressed, or an error),
        // so every motor and CR servo is always stopped at the end.
        try {
            while (opModeIsActive()) {
                updateHelp();
                if (helpOpen) {
                    // Help open: nothing moves while the driver reads, and no button acts.
                    stopAll();
                    showHelp(t);
                    t.text("Help open: motors stopped. GUIDE (or hold START) = back.");
                    t.update();
                    // the buttons held now must not act when help closes
                    rememberButtons();
                    continue;
                }
                handleInput();
                show(t);
                t.text("GUIDE (or hold START 1 s) = help");
                t.update();
            }
        } finally {
            stopAll();
        }
    }

    // ---- Help (the same keys as the finders) ----
    private boolean helpOpen = false;
    private boolean guideWas = false;
    private double startHeldSince = Double.NaN;
    private boolean startHoldUsed = false;

    // GUIDE pressed, or START held 1 s: open or close the help page.
    private void updateHelp() {
        if (gamepad1.guide && !guideWas) helpOpen = !helpOpen;
        guideWas = gamepad1.guide;
        if (!gamepad1.start) {
            startHeldSince = Double.NaN;
            startHoldUsed = false;
            return;
        }
        double now = System.nanoTime() / 1e9;
        if (Double.isNaN(startHeldSince)) startHeldSince = now;
        if (!startHoldUsed && now - startHeldSince >= 1.0) {
            helpOpen = !helpOpen;
            startHoldUsed = true;
        }
    }

    private void showHelp(RobotTelemetry t) {
        t.section("Hardware Check help  (GUIDE = back)");
        for (String line : helpLines()) t.text(line);
    }

    /** The help page's lines (GUIDE, or hold START 1 s). */
    protected String[] helpLines() {
        return new String[] {
            "Tests each device in the configuration.",
            "Robot on a block first!",
            "D-pad up/down = choose a device (stops the last).",
            "Motor / CR servo: right stick = power (50%;",
            "  hold RB = full). A = zero the encoder.",
            "  Check: + power turns the way you expect and",
            "  the encoder counts UP.",
            "Servo: D-pad right/left = +/- 0.01, bumpers =",
            "  +/- 0.1, X = 0.5 (moves only when you press).",
            "IMU: turn the robot left, yaw must go UP.",
            "Sensors: just read the values.",
            "Steps: docs/guides/testing.md",
        };
    }

    // The buttons as they are now count as "held": closing help does not press them.
    private void rememberButtons() {
        boolean[] now = {gamepad1.dpad_up, gamepad1.dpad_down, gamepad1.dpad_right, gamepad1.dpad_left,
                gamepad1.right_bumper, gamepad1.left_bumper, gamepad1.a, gamepad1.x, false, false};
        System.arraycopy(now, 0, was, 0, was.length);
    }

    // The lines about Robot.java's devices (empty without robot()), shown in INIT and above the list.
    private List<String> robotLines = new ArrayList<>();

    /**
     * Your robot in five files ({@code return new Robot();}), to check that every device it names is in
     * this configuration. Default null: no check, just the list of devices.
     */
    protected RobotDefinition robot() {
        return null;
    }

    // "Robot.java: every device is in the configuration", or one line per device that is not.
    private List<String> robotCheck() {
        List<String> out = new ArrayList<>();
        RobotDefinition r = robot();
        if (r == null) return out;
        List<String> missing = r.checkConfiguration(hardwareMap);
        if (missing.isEmpty()) {
            out.add("Robot.java: every device is in the configuration");
            return out;
        }
        out.add("Robot.java devices not in this configuration:");
        out.addAll(missing);
        return out;
    }

    // Makes the list of every device in the active robot configuration, one kind after another.
    private void findDevices() {
        // Some devices are two kinds at once (a REV color sensor is also a distance sensor). "seen"
        // remembers the devices already listed, so each one appears only once, under the first kind.
        // An IdentityHashMap compares the actual objects ("is it the very same device?").
        Map<HardwareDevice, Boolean> seen = new IdentityHashMap<>();
        add(DcMotorEx.class, "motor", seen);
        add(CRServo.class, "CR servo", seen);
        add(Servo.class, "servo", seen);
        add(IMU.class, "IMU", seen);
        add(TouchSensor.class, "touch", seen);
        add(DigitalChannel.class, "digital", seen);
        add(ColorSensor.class, "color", seen);
        add(DistanceSensor.class, "distance", seen);
        add(AnalogInput.class, "analog", seen);
        add(WebcamName.class, "webcam", seen);
        add(Limelight3A.class, "limelight", seen);
    }

    // Adds every device of one kind. "<T extends HardwareDevice>" means this one method works for
    // any kind of device (T stands for "motor", "servo"... whatever type we pass in).
    private <T extends HardwareDevice> void add(Class<T> type, String kind, Map<HardwareDevice, Boolean> seen) {
        for (T d : hardwareMap.getAll(type)) {
            // already listed under an earlier kind: skip it
            if (seen.containsKey(d)) continue;
            seen.put(d, true);
            // the name from the robot configuration ("?" if it somehow has none)
            Set<String> names = hardwareMap.getNamesOf(d);
            String name = names.isEmpty() ? "?" : names.iterator().next();
            devices.add(new Device(name, kind, d));
        }
    }

    // Reads the buttons once per loop and drives the chosen device. Never waits.
    private void handleInput() {
        // The buttons we use, in a fixed order: 0 up, 1 down, 2 right, 3 left, 4 right bumper,
        // 5 left bumper, 6 A, 7 X (8 and 9 are spare).
        boolean[] now = {gamepad1.dpad_up, gamepad1.dpad_down, gamepad1.dpad_right, gamepad1.dpad_left,
                gamepad1.right_bumper, gamepad1.left_bumper, gamepad1.a, gamepad1.x, false, false};
        if (devices.isEmpty()) return;
        int before = selected;
        // D-pad up/down moves through the list, wrapping around at the ends ("%" = the remainder,
        // which brings us back to the start after the last device).
        if (pressed(now, 0)) selected = (selected + devices.size() - 1) % devices.size();
        if (pressed(now, 1)) selected = (selected + 1) % devices.size();
        // SAFETY: a new device was chosen, so stop the motors and CR servos. Otherwise the last
        // motor would keep spinning while you look at something else.
        if (selected != before) stopAll();

        Device d = devices.get(selected);
        // The stick: pushing UP gives a negative number in the SDK, so we flip it (up = positive power).
        // Tiny values are ignored: a stick that does not quite center itself must not creep a motor.
        double stick = Math.abs(gamepad1.right_stick_y) < 0.05 ? 0 : -gamepad1.right_stick_y;   // ignore stick drift
        // SAFETY: half power at most, unless the right bumper is held.
        double stickPower = stick * (gamepad1.right_bumper ? 1.0 : 0.5);
        // "instanceof" asks "is this device a motor?"; "(DcMotorEx) d.device" then lets us use it as one.
        if (d.device instanceof DcMotorEx) {
            DcMotorEx m = (DcMotorEx) d.device;
            m.setPower(stickPower);
            // A: set the encoder (the motor's turn counter) back to 0, then go back to plain power control.
            if (pressed(now, 6)) {
                m.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
                m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            }
        } else if (d.device instanceof CRServo) {
            ((CRServo) d.device).setPower(stickPower);
        } else if (d.device instanceof Servo) {
            Servo s = (Servo) d.device;
            // Start from the position we last sent; if we have not sent one yet, from the position the
            // SDK last sent to this servo (the servo's real position is never known: a servo cannot report it).
            double p = Double.isNaN(d.servoPosition) ? s.getPosition() : d.servoPosition;
            // No position sent yet since power-on (the SDK may report NaN or a number out of range):
            // start from the middle, 0.5, so the first nudge is a small known step.
            if (Double.isNaN(p) || p < 0 || p > 1) p = 0.5;
            boolean change = false;
            if (pressed(now, 2)) { p += 0.01; change = true; }
            if (pressed(now, 3)) { p -= 0.01; change = true; }
            if (pressed(now, 4)) { p += 0.1; change = true; }
            if (pressed(now, 5)) { p -= 0.1; change = true; }
            if (pressed(now, 7)) { p = 0.5; change = true; }
            // SAFETY: only send a position when a button was pressed, so just choosing a servo in the
            // list never makes it jump. Keep it in 0..1, the only range a servo accepts.
            if (change) {
                d.servoPosition = Math.max(0, Math.min(1, p));
                s.setPosition(d.servoPosition);
            }
        }
        // Remember this loop's buttons, so next loop we can tell a NEW press from a held button.
        System.arraycopy(now, 0, was, 0, was.length);
    }

    // Shows the battery, the device list (one page) and the chosen device's readings.
    private void show(RobotTelemetry t) {
        // Battery: only the first voltage sensor is shown (each hub has one), then "break" leaves the loop.
        for (VoltageSensor v : hardwareMap.getAll(VoltageSensor.class)) {
            double volts = v.getVoltage();
            // Under 12 V the motors are weaker than usual, so a test may look like a fault. (0 = no reading.)
            if (volts > 0 && volts < 12.0) t.warn(String.format(Locale.US, "Battery low: %.2f V", volts));
            t.line("Battery", "%.2f V", volts);
            break;
        }
        if (devices.isEmpty()) {
            t.text("No devices in the active configuration.");
            return;
        }
        t.section("Devices (D-pad up/down)");
        // Only the page that holds the chosen device: the first device on that page, then up to PAGE devices.
        int first = (selected / PAGE) * PAGE;
        for (int i = first; i < Math.min(devices.size(), first + PAGE); i++) {
            Device d = devices.get(i);
            // "> " marks the chosen device
            t.text((i == selected ? "> " : "  ") + d.name + "  (" + d.kind + ")");
        }
        t.text(String.format(Locale.US, "%d of %d", selected + 1, devices.size()));
        Device d = devices.get(selected);
        t.section(d.name);
        // A broken or unplugged device can throw an error when read. Catch it and show a warning
        // instead, so the check keeps running and you can test the other devices.
        try {
            describe(d, t);
        } catch (RuntimeException e) {
            t.warn("Could not read " + d.name + ": " + e.getMessage());
        }
    }

    // The readings and hints for one device, depending on its kind.
    private void describe(Device d, RobotTelemetry t) {
        HardwareDevice h = d.device;
        if (h instanceof DcMotorEx) {
            DcMotorEx m = (DcMotorEx) h;
            t.text("Right stick = power (RB = full), A = zero encoder");
            t.line("Power", "%.2f", m.getPower());
            t.line("Encoder", m.getCurrentPosition());
            t.line("Speed", "%.0f ticks/s", m.getVelocity());
            t.line("Current", "%.2f A", m.getCurrent(CurrentUnit.AMPS));
            t.text("Check: positive power turns the way you expect, and the encoder counts UP.");
        } else if (h instanceof CRServo) {
            t.text("Right stick = power (RB = full)");
            t.line("Power", "%.2f", ((CRServo) h).getPower());
        } else if (h instanceof Servo) {
            t.text("D-pad L/R = 0.01, bumpers = 0.1, X = 0.5");
            t.line("Position", Double.isNaN(d.servoPosition) ? "not moved yet" : String.format(Locale.US, "%.2f", d.servoPosition));
            t.text("Write down the positions you need (open, closed...).");
        } else if (h instanceof IMU) {
            IMU imu = (IMU) h;
            // yaw = turning left/right, pitch = nose up/down, roll = tipping sideways
            YawPitchRollAngles a = imu.getRobotYawPitchRollAngles();
            t.line("Yaw", "%.1f deg", a.getYaw(AngleUnit.DEGREES));
            t.line("Pitch / Roll", "%.1f / %.1f deg", a.getPitch(AngleUnit.DEGREES), a.getRoll(AngleUnit.DEGREES));
            t.text("Uses logo UP, USB FORWARD here. Turn left: yaw must go UP (if your hub is mounted that way).");
        } else if (h instanceof TouchSensor) {
            t.line("Pressed", ((TouchSensor) h).isPressed());
        } else if (h instanceof DigitalChannel) {
            t.line("State", ((DigitalChannel) h).getState());
            t.text("REV magnetic switch: false = magnet near.");
        } else if (h instanceof ColorSensor) {
            ColorSensor c = (ColorSensor) h;
            t.line("R G B", "%d %d %d", c.red(), c.green(), c.blue());
            // many color sensors (like the REV one) can also measure distance: show it too
            if (h instanceof DistanceSensor) t.line("Distance", "%.1f in", ((DistanceSensor) h).getDistance(DistanceUnit.INCH));
        } else if (h instanceof DistanceSensor) {
            t.line("Distance", "%.1f in", ((DistanceSensor) h).getDistance(DistanceUnit.INCH));
        } else if (h instanceof AnalogInput) {
            AnalogInput a = (AnalogInput) h;
            t.line("Voltage", "%.3f V (max %.1f)", a.getVoltage(), a.getMaxVoltage());
        } else if (h instanceof Limelight3A) {
            Limelight3A ll = (Limelight3A) h;
            t.line("Connected", ll.isConnected());
            t.text("Open its web page (limelight.local:5801) to see the picture.");
        } else {
            // anything else (e.g. a webcam): at least show what the SDK calls it
            t.line("Type", h.getDeviceName());
        }
        // where it is plugged in (hub and port), shown when debug lines are on
        t.debug("Connection", h.getConnectionInfo());
    }

    // Starts every IMU, in INIT, with a known mounting so its angles mean something.
    // (The IMU is the sensor in the hub that knows which way the robot faces.)
    private void startImus() {
        for (Device d : devices) {
            if (d.device instanceof IMU) {
                IMU imu = (IMU) d.device;
                // We cannot know how YOUR hub is mounted, so we assume the most common way: REV logo
                // facing up, USB ports facing forward. Your own code uses your real mounting.
                imu.initialize(new IMU.Parameters(new RevHubOrientationOnRobot(
                        RevHubOrientationOnRobot.LogoFacingDirection.UP, RevHubOrientationOnRobot.UsbFacingDirection.FORWARD)));
                // the heading the robot faces now counts as 0
                imu.resetYaw();
            }
        }
    }

    // SAFETY: power 0 to every motor and CR servo. Servos are left where they are (they hold still).
    private void stopAll() {
        for (Device d : devices) {
            if (d.device instanceof DcMotorEx) ((DcMotorEx) d.device).setPower(0);
            else if (d.device instanceof CRServo) ((CRServo) d.device).setPower(0);
        }
    }

    // True only in the loop where the button goes from up to down: a new press, not a held button.
    // Without this, holding a button would repeat its action about 50 times per second.
    private boolean pressed(boolean[] now, int i) {
        return now[i] && !was[i];
    }
}
