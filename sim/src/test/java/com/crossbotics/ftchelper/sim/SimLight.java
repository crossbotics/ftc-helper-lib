package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.robot.LightColor;
import com.qualcomm.hardware.rev.RevBlinkinLedDriver;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.ServoControllerEx;
import java.lang.reflect.Proxy;
import java.util.Locale;

/**
 * A pretend colored light (robot file: {@code light.NAME = gobilda} or {@code light.NAME = blinkin}):
 * it remembers the signal the OpMode sent and turns it into the color the real light would show,
 * for the page (a dot per light).
 *
 * <ul>
 *   <li>goBILDA RGB Indicator Light 3118-0808-0002: a "Servo". Its color comes from the pulse (servo
 *       position 0..1 = 600..2400 us): off below 1100 us, a rainbow from red (1100) to violet (1900),
 *       white above (seller page, Product Insight #4, checked 2026-10-21).</li>
 *   <li>REV Blinkin LED Driver REV-11-1105: the SDK's RevBlinkinLedDriver, which sends pattern n as servo
 *       position 0.2525 + 0.005 n. Before any command the real one shows its "no signal" pattern.</li>
 * </ul>
 */
final class SimLight {
    final String name;
    final boolean blinkin;
    final HardwareDevice device;
    // The last position sent (0..1); NaN = nothing sent yet.
    private volatile double position = Double.NaN;

    // goBILDA's color stops: pulse (us) and color, from the seller's chart.
    private static final double[] STOP_US = {1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900};
    private static final int[] STOP_RGB = {0xff0000, 0xff8000, 0xffff00, 0x9acd32, 0x008000, 0x008080, 0x0066ff, 0x3300ff, 0x8000ff};
    private static final String[] STOP_NAME = {"red", "orange", "yellow", "sage", "green", "azure", "blue", "indigo", "violet"};

    SimLight(String name, String kind) {
        this.name = name;
        String k = kind.trim().toLowerCase(Locale.US);
        if (!k.equals("gobilda") && !k.equals("blinkin")) {
            throw new IllegalArgumentException("Robot file: light." + name + " = gobilda (goBILDA RGB Indicator Light) or blinkin (REV Blinkin)");
        }
        blinkin = k.equals("blinkin");
        if (blinkin) {
            // The SDK's own Blinkin class, on a pretend servo controller.
            ServoControllerEx c = (ServoControllerEx) Proxy.newProxyInstance(ServoControllerEx.class.getClassLoader(),
                    new Class<?>[] {ServoControllerEx.class}, (proxy, m, a) -> {
                        if (SimRobot.isBlocked() && m.getName().startsWith("set")) return null;
                        if (m.getName().equals("setServoPosition")) position = ((Number) a[1]).doubleValue();
                        if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (m.getName().equals("equals")) return proxy == a[0];
                        return SimDefaults.of(m.getReturnType());
                    });
            device = new RevBlinkinLedDriver(c, 0);
        } else {
            device = (Servo) Proxy.newProxyInstance(Servo.class.getClassLoader(), new Class<?>[] {Servo.class}, (proxy, m, a) -> {
                if (SimRobot.isBlocked() && m.getName().startsWith("set")) return null;
                switch (m.getName()) {
                    case "setPosition": position = Math.max(0, Math.min(1, ((Number) a[0]).doubleValue())); return null;
                    case "getPosition": return Double.isNaN(position) ? 0.0 : position;
                    case "getDeviceName": return "Simulated goBILDA RGB Indicator Light";
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == a[0];
                    default: return SimDefaults.of(m.getReturnType());
                }
            });
        }
    }

    /** The last position sent (NaN = none). */
    double position() {
        return position;
    }

    /** The color it shows, as a web color ("#ff0000"); "" = dark. */
    String webColor() {
        double p = position;
        if (blinkin) {
            RevBlinkinLedDriver.BlinkinPattern b = pattern(p);
            if (b == null) return Double.isNaN(p) ? "#8a6cff" : "";   // no signal: its color waves (shown as one color)
            for (LightColor c : LightColor.values()) if (c.blinkinPattern() == b) return c == LightColor.OFF ? "" : c.webColor();
            return "#b0b0b0";   // a pattern (rainbow, chase...): a gray dot, the pattern's name in words()
        }
        if (Double.isNaN(p)) return "";
        // One of the library's colors: the same dot color as everywhere else.
        for (LightColor c : LightColor.values()) {
            if (Math.abs(c.gobildaPosition() - p) < 1e-6) return c == LightColor.OFF ? "" : c.webColor();
        }
        double us = 600 + 1800 * p;
        if (us < STOP_US[0]) return "";
        if (us > STOP_US[STOP_US.length - 1]) return "#ffffff";
        for (int i = 0; i < STOP_US.length - 1; i++) {
            if (us <= STOP_US[i + 1]) {
                double f = (us - STOP_US[i]) / (STOP_US[i + 1] - STOP_US[i]);
                return String.format(Locale.US, "#%06x", blend(STOP_RGB[i], STOP_RGB[i + 1], f));
            }
        }
        return "#ffffff";
    }

    /** What it shows, in words: "GREEN", "off", "no signal". */
    String words() {
        double p = position;
        if (blinkin) {
            if (Double.isNaN(p)) return "no signal (its own color waves)";
            RevBlinkinLedDriver.BlinkinPattern b = pattern(p);
            if (b == null) return String.format(Locale.US, "unknown signal %.4f", p);
            return b == RevBlinkinLedDriver.BlinkinPattern.BLACK ? "off" : b.name();
        }
        if (Double.isNaN(p)) return "off (no signal)";
        for (LightColor c : LightColor.values()) if (Math.abs(c.gobildaPosition() - p) < 1e-6) return c == LightColor.OFF ? "off" : c.name();
        double us = 600 + 1800 * p;
        if (us < STOP_US[0]) return "off";
        if (us > STOP_US[STOP_US.length - 1]) return "white";
        int best = 0;
        for (int i = 1; i < STOP_US.length; i++) if (Math.abs(STOP_US[i] - us) < Math.abs(STOP_US[best] - us)) best = i;
        return STOP_NAME[best];
    }

    // The Blinkin pattern for a position (null = none sent, or between patterns).
    private static RevBlinkinLedDriver.BlinkinPattern pattern(double p) {
        if (Double.isNaN(p)) return null;
        double n = (p - 0.2525) / 0.005;
        long i = Math.round(n);
        RevBlinkinLedDriver.BlinkinPattern[] all = RevBlinkinLedDriver.BlinkinPattern.values();
        if (Math.abs(n - i) > 0.1 || i < 0 || i >= all.length) return null;
        return all[(int) i];
    }

    private static int blend(int a, int b, double f) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int ca = (a >> shift) & 0xff, cb = (b >> shift) & 0xff;
            out |= ((int) Math.round(ca + (cb - ca) * f) & 0xff) << shift;
        }
        return out;
    }
}
