package com.crossbotics.ftchelper.telemetry;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import java.util.Locale;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * Shortcuts for clear, same-every-time Driver Station telemetry.
 *
 * <pre>{@code
 * TelemetryHelper.section(telemetry, "Lift");
 * TelemetryHelper.motor(telemetry, "Lift", liftMotor);
 * TelemetryHelper.value(telemetry, "Target", liftTarget, "ticks");
 * TelemetryHelper.flag(telemetry, "At bottom", lift.isAtLower());
 * telemetry.update();
 * }</pre>
 *
 * <p>Shows on the Driver Station like this:
 * <pre>
 * --- Lift ---
 * Lift : pwr 0.45 | pos 1203 | vel 812/s
 * Target : 1500 ticks
 * At bottom : no
 * </pre>
 *
 * <p>For driver and debug levels and warnings on top, see {@link RobotTelemetry}.
 *
 * Docs: docs/modules/telemetry.md
 */
public final class TelemetryHelper {
    // "final" class + private constructor: a box of "static" helpers you call on the class name
    // (TelemetryHelper.value(...)), never an object you make with "new".
    private TelemetryHelper() { }

    /** Adds a heading line, to split the screen into groups. */
    public static void section(Telemetry telemetry, String title) {
        telemetry.addLine("--- " + title + " ---");
    }

    /**
     * Adds "label : value unit", with 2 decimals.
     *
     * @param unit the unit to show after the number, e.g. "ticks" or "in"
     */
    public static void value(Telemetry telemetry, String label, double value, String unit) {
        telemetry.addData(label, "%s %s", num(value, 2), unit);
    }

    /**
     * Adds "label : value", with the number of decimals you choose.
     *
     * @param decimals how many digits after the decimal point (0 or more)
     */
    public static void value(Telemetry telemetry, String label, double value, int decimals) {
        telemetry.addData(label, num(value, decimals));
    }

    /** Adds "label : yes" or "label : no". */
    public static void flag(Telemetry telemetry, String label, boolean value) {
        // "a ? b : c" is a short if/else: "yes" when true, "no" when false.
        telemetry.addData(label, value ? "yes" : "no");
    }

    /**
     * Adds a motor's power, encoder position and speed (counts per second) on one line.
     * Power is -1 to 1; position and speed are in encoder ticks (counts).
     */
    public static void motor(Telemetry telemetry, String label, DcMotorEx motor) {
        telemetry.addData(label, "pwr %s | pos %d | vel %.0f/s",
                num(motor.getPower(), 2), motor.getCurrentPosition(), motor.getVelocity());
    }

    /** Adds a servo's last commanded position (servos cannot report where they really are). */
    public static void servo(Telemetry telemetry, String label, Servo servo) {
        telemetry.addData(label, "pos %s", num(servo.getPosition(), 3));
    }

    /**
     * Formats a number with a fixed number of decimals, e.g. {@code num(0.4567, 2)} gives "0.46".
     * NaN shows as "--" (for example "no target").
     *
     * @param value    the number
     * @param decimals digits after the decimal point; below 0 counts as 0
     * @return the number as text
     */
    public static String num(double value, int decimals) {
        if (Double.isNaN(value)) return "--";
        // Builds a format like "%.2f" from the number of decimals. Locale.US: always "." for decimals.
        return String.format(Locale.US, "%." + Math.max(0, decimals) + "f", value);
    }
}
