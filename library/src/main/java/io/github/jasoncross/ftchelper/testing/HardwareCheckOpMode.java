package io.github.jasoncross.ftchelper.testing;

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
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
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
 *
 * <p>Controls (gamepad 1): D-pad up/down = choose a device.
 * <ul>
 *   <li>Motor / CR servo: right stick Y = power (max 50%; hold right bumper for 100%). A = zero the encoder.</li>
 *   <li>Servo: D-pad right/left = +/- 0.01, bumpers = +/- 0.1, X = 0.5. (A servo does not move until you press.)</li>
 *   <li>Sensors: just read the values.</li>
 * </ul>
 * Changing the chosen device stops the last one. Put the robot on a block first.
 *
 * Steps: docs/guides/testing.md
 */
public abstract class HardwareCheckOpMode extends LinearOpMode {

    private static final int PAGE = 6;

    private static final class Device {
        final String name;
        final String kind;
        final HardwareDevice device;
        double servoPosition = Double.NaN;
        Device(String name, String kind, HardwareDevice device) {
            this.name = name;
            this.kind = kind;
            this.device = device;
        }
    }

    private final List<Device> devices = new ArrayList<>();
    private int selected = 0;
    private final boolean[] was = new boolean[10];
    private boolean imuStarted = false;

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);
        findDevices();
        t.text("Found " + devices.size() + " devices. Robot on a block! Press START.");
        t.update();
        waitForStart();
        try {
            while (opModeIsActive()) {
                handleInput();
                show(t);
                t.update();
            }
        } finally {
            stopAll();
        }
    }

    private void findDevices() {
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

    private <T extends HardwareDevice> void add(Class<T> type, String kind, Map<HardwareDevice, Boolean> seen) {
        for (T d : hardwareMap.getAll(type)) {
            if (seen.containsKey(d)) continue;
            seen.put(d, true);
            Set<String> names = hardwareMap.getNamesOf(d);
            String name = names.isEmpty() ? "?" : names.iterator().next();
            devices.add(new Device(name, kind, d));
        }
    }

    private void handleInput() {
        boolean[] now = {gamepad1.dpad_up, gamepad1.dpad_down, gamepad1.dpad_right, gamepad1.dpad_left,
                gamepad1.right_bumper, gamepad1.left_bumper, gamepad1.a, gamepad1.x, false, false};
        if (devices.isEmpty()) return;
        int before = selected;
        if (pressed(now, 0)) selected = (selected + devices.size() - 1) % devices.size();
        if (pressed(now, 1)) selected = (selected + 1) % devices.size();
        if (selected != before) stopAll();

        Device d = devices.get(selected);
        double stick = Math.abs(gamepad1.right_stick_y) < 0.05 ? 0 : -gamepad1.right_stick_y;   // ignore stick drift
        double stickPower = stick * (gamepad1.right_bumper ? 1.0 : 0.5);
        if (d.device instanceof DcMotorEx) {
            DcMotorEx m = (DcMotorEx) d.device;
            m.setPower(stickPower);
            if (pressed(now, 6)) {
                m.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
                m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            }
        } else if (d.device instanceof CRServo) {
            ((CRServo) d.device).setPower(stickPower);
        } else if (d.device instanceof Servo) {
            Servo s = (Servo) d.device;
            double p = Double.isNaN(d.servoPosition) ? s.getPosition() : d.servoPosition;
            boolean change = false;
            if (pressed(now, 2)) { p += 0.01; change = true; }
            if (pressed(now, 3)) { p -= 0.01; change = true; }
            if (pressed(now, 4)) { p += 0.1; change = true; }
            if (pressed(now, 5)) { p -= 0.1; change = true; }
            if (pressed(now, 7)) { p = 0.5; change = true; }
            if (change) {
                d.servoPosition = Math.max(0, Math.min(1, p));
                s.setPosition(d.servoPosition);
            }
        }
        System.arraycopy(now, 0, was, 0, was.length);
    }

    private void show(RobotTelemetry t) {
        for (VoltageSensor v : hardwareMap.getAll(VoltageSensor.class)) {
            double volts = v.getVoltage();
            if (volts > 0 && volts < 12.0) t.warn(String.format(Locale.US, "Battery low: %.2f V", volts));
            t.line("Battery", "%.2f V", volts);
            break;
        }
        if (devices.isEmpty()) {
            t.text("No devices in the active configuration.");
            return;
        }
        t.section("Devices (D-pad up/down)");
        int first = (selected / PAGE) * PAGE;
        for (int i = first; i < Math.min(devices.size(), first + PAGE); i++) {
            Device d = devices.get(i);
            t.text((i == selected ? "> " : "  ") + d.name + "  (" + d.kind + ")");
        }
        t.text(String.format(Locale.US, "%d of %d", selected + 1, devices.size()));
        Device d = devices.get(selected);
        t.section(d.name);
        try {
            describe(d, t);
        } catch (RuntimeException e) {
            t.warn("Could not read " + d.name + ": " + e.getMessage());
        }
    }

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
            if (!imuStarted) {
                imu.initialize(new IMU.Parameters(new RevHubOrientationOnRobot(
                        RevHubOrientationOnRobot.LogoFacingDirection.UP, RevHubOrientationOnRobot.UsbFacingDirection.FORWARD)));
                imu.resetYaw();
                imuStarted = true;
            }
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
            t.line("Type", h.getDeviceName());
        }
        t.debug("Connection", h.getConnectionInfo());
    }

    private void stopAll() {
        for (Device d : devices) {
            if (d.device instanceof DcMotorEx) ((DcMotorEx) d.device).setPower(0);
            else if (d.device instanceof CRServo) ((CRServo) d.device).setPower(0);
        }
    }

    private boolean pressed(boolean[] now, int i) {
        return now[i] && !was[i];
    }
}
