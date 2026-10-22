package com.crossbotics.ftchelper.sim;

import com.qualcomm.robotcore.hardware.DistanceSensor;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.NormalizedColorSensor;
import com.qualcomm.robotcore.hardware.NormalizedRGBA;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

/**
 * A pretend sensor that sees the game piece at one spot of the robot (needs game pieces: physics = engine):
 *
 *   color.NAME = at=ready                  a colour sensor with distance (REV Color Sensor V3): the piece's
 *                                          colour from the field file and 15 mm when one is there; dark grey
 *                                          and 120 mm when not
 *   distance.NAME = at=intake, acrossMm=150   a distance sensor looking across a channel: acrossMm minus the
 *                                          piece's diameter when one is there, acrossMm when not
 *
 * at=ready: the next piece to fire (the oldest held); after each shot it reads empty for 0.08 s while the
 * next piece rolls into the slot. at=intake: each piece the intake takes in, for 0.25 s.
 * The colours are the field file's screen colours, not a real sensor's: tune the real robot with the Piece
 * Tuner (the simulator's numbers will differ).
 */
final class SimPieceSensor {
    private final SimRobot robot;
    private final String spot;
    private final boolean color;
    private final double acrossMm;

    SimPieceSensor(SimRobot robot, String key, boolean color, Map<String, String> s) {
        this.robot = robot;
        this.color = color;
        String at = s.containsKey("at") ? s.get("at") : s.get("");
        if (!"ready".equals(at) && !"intake".equals(at)) {
            throw new IllegalArgumentException("Robot file: \"" + key + "\" needs at=ready or at=intake");
        }
        spot = at;
        double a = 150;
        if (s.containsKey("acrossMm")) {
            try {
                a = Double.parseDouble(s.get("acrossMm"));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Robot file: \"" + key + "\": acrossMm needs a number");
            }
            if (!(a > 0)) throw new IllegalArgumentException("Robot file: \"" + key + "\": acrossMm must be more than 0");
        }
        acrossMm = a;
    }

    /** After a shot, the ready slot is empty this long (simulator seconds) while the next piece rolls in. */
    static final double READY_GAP_SEC = 0.08;
    private int launchedSeen = -1;
    private double gapUntil = -1;

    // The piece at the spot now, or null.
    private SimField.PieceType piece() {
        if (robot.pieces == null) return null;
        if (spot.equals("ready") && robot.world != null) {
            int n = robot.pieces.launched();
            double t = robot.world.time();
            if (launchedSeen >= 0 && n != launchedSeen) gapUntil = t + READY_GAP_SEC;
            launchedSeen = n;
            if (t < gapUntil) return null;
        }
        return robot.pieces.atSpot(spot);
    }

    /** The distance it reads now, in mm. */
    double distanceMm() {
        SimField.PieceType p = piece();
        if (color) return p == null ? 120 : 15;
        return p == null ? acrossMm : Math.max(0, acrossMm - p.diameterIn * 25.4);
    }

    /** The colour it reads now (each 0-1). */
    float[] rgb() {
        SimField.PieceType p = piece();
        if (p == null) return new float[] {0.02f, 0.02f, 0.02f};
        int c = Integer.parseInt(p.color.replace("#", ""), 16);
        // Scaled down: a real sensor's normalized numbers are well below 1.
        return new float[] {((c >> 16) & 255) / 255f * 0.4f, ((c >> 8) & 255) / 255f * 0.4f, (c & 255) / 255f * 0.4f};
    }

    HardwareDevice device() {
        Class<?>[] kinds = color ? new Class<?>[] {NormalizedColorSensor.class, DistanceSensor.class}
                : new Class<?>[] {DistanceSensor.class};
        return (HardwareDevice) Proxy.newProxyInstance(DistanceSensor.class.getClassLoader(), kinds, (proxy, m, a) -> {
            switch (m.getName()) {
                case "getDistance": return ((DistanceUnit) a[0]).fromMm(distanceMm());
                case "getNormalizedColors": {
                    float[] c = rgb();
                    NormalizedRGBA out = new NormalizedRGBA();
                    out.red = c[0];
                    out.green = c[1];
                    out.blue = c[2];
                    out.alpha = Math.max(c[0], Math.max(c[1], c[2]));
                    return out;
                }
                case "getDeviceName": return color ? "Simulated colour sensor" : "Simulated distance sensor";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == a[0];
                default: return SimDefaults.of(m.getReturnType());
            }
        });
    }
}
