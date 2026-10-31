package com.crossbotics.ftchelper.sim;

import com.qualcomm.hardware.dfrobot.HuskyLens;
import com.qualcomm.robotcore.hardware.I2cDeviceSynch;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A pretend HuskyLens (huskylens.KEY in the robot file). It is the SDK's own HuskyLens class with the I2C
 * reads replaced: it works out what the camera would see, and makes the same boxes (Block) the SDK's driver
 * makes, at most 6 at a time. taught=30|31|- says what was taught, in order: ID1 is tag 30, ID2 is tag 31,
 * nothing was taught as ID3. Colours and objects: taught= has piece names from the field file (POLLEN|NECTAR).
 *
 * Tags it finds report their taught number, or 0 if not taught, as the real camera does. Pieces: any piece
 * in the picture whose type was taught. Object tracking follows only the first taught type's biggest
 * piece. Not simulated: mistakes (a real colour match changes with the light), pieces hiding each other,
 * line and face modes, the buttons. The view angle's default (60) and the smallest box it finds are guesses.
 */
final class SimHuskyLens extends HuskyLens {
    private static final int MAX_BLOCKS = 6;
    // A tag or piece smaller than this in the picture is not found (a guess, not measured).
    private static final double MIN_PIECE_PIXELS = 6, TAG_DECIMATION = 2;
    final SimCamera cam;
    private final List<String> taught = new ArrayList<>();
    private volatile Algorithm algorithm = Algorithm.TAG_RECOGNITION;
    private volatile boolean open = false;
    // What closes this camera when an OpMode stops (the camera's list of open "portals").
    private final AutoCloseable closer = () -> {
        open = false;
        closed();
    };

    SimHuskyLens(SimCamera cam, Map<String, String> s) {
        super(pretendClient());
        this.cam = cam;
        if (s.containsKey("taught")) {
            for (String t : s.get("taught").split("\\|")) taught.add(t.trim());
        }
    }

    // The I2C connection the SDK's class asks for: it does nothing, and the methods below never use it.
    private static I2cDeviceSynch pretendClient() {
        return (I2cDeviceSynch) Proxy.newProxyInstance(I2cDeviceSynch.class.getClassLoader(),
                new Class<?>[] {I2cDeviceSynch.class}, (proxy, m, a) -> {
                    switch (m.getName()) {
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        case "toString": return "pretend HuskyLens I2C";
                        // The answer to "are you there?": a real answer starts with these two bytes.
                        case "read": return new byte[] {0x55, (byte) 0xAA, 0x11, 0x00, 0x2E, 0x3E};
                        default: return SimDefaults.of(m.getReturnType());
                    }
                });
    }

    private void closed() {
        cam.closed(closer);
    }

    private void opened() {
        if (open) return;
        open = true;
        cam.opened(closer);
    }

    @Override public boolean knock() { return true; }

    @Override public void selectAlgorithm(Algorithm a) {
        opened();
        algorithm = a;
    }

    /** The mode set now. */
    Algorithm algorithm() {
        return algorithm;
    }

    @Override public Block[] blocks() {
        return read(-1);
    }

    // The real camera answers this with up to 6 boxes of THIS number, even if others hide it from blocks().
    @Override public Block[] blocks(int id) {
        return read(id);
    }

    private Block[] read(int onlyId) {
        opened();
        double[][] pose = cam.pose();     // the robot's lock first, as the other pretend cameras do
        switch (algorithm) {
            case TAG_RECOGNITION: return tags(pose, onlyId);
            case COLOR_RECOGNITION: return pieces(pose, false, onlyId);
            case OBJECT_TRACKING: return pieces(pose, true, onlyId);
            default: return none();
        }
    }

    @Override public Arrow[] arrows() { return new Arrow[0]; }
    @Override public Arrow[] arrows(int id) { return new Arrow[0]; }

    private Block[] none() {
        cam.lastSeen(new ArrayList<>());
        cam.lastDetections(new ArrayList<>());
        return new Block[0];
    }

    private Block[] tags(double[][] pose, int onlyId) {
        List<SimCamera.Seen> seen = new ArrayList<>(cam.visibleTags(cam.robot.field, TAG_DECIMATION, pose));
        // Big ones first: with more than 6, the driver's limit leaves out the small ones.
        seen.sort(Comparator.comparingDouble((SimCamera.Seen s) -> -s.pixels));
        List<Block> out = new ArrayList<>();
        List<double[]> ids = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (SimCamera.Seen s : seen) {
            if (out.size() >= MAX_BLOCKS) break;
            int id = taught.indexOf(String.valueOf(s.tag.id)) + 1;     // 0 = not taught
            if (onlyId >= 0 && id != onlyId) continue;
            out.add(block(s.cx, s.cy, s.pixels, id));
            ids.add(new double[] {s.tag.id});
            notes.add(String.format(Locale.US, "tag %d: taught number %d, box at x %.0f, y %.0f, %.0f pixels", s.tag.id, id, s.cx, s.cy, s.pixels));
        }
        cam.lastSeen(ids);
        cam.lastDetections(notes);
        return out.toArray(new Block[0]);
    }

    private Block[] pieces(double[][] c, boolean onlyOne, int onlyId) {
        List<double[]> found = new ArrayList<>();      // {taught number, x, y, pixels}
        if (cam.robot.pieces != null && cam.lightingOk()) {
            double f = cam.focalPx(), tanH = Math.tan(Math.toRadians(cam.hfovDeg) / 2), tanV = tanH * cam.height / cam.width;
            for (Object[] p : cam.robot.pieces.drawn()) {
                SimField.PieceType type = (SimField.PieceType) p[0];
                int id = taught.indexOf(type.name) + 1;
                if (id == 0 || (onlyOne && id != 1) || (onlyId >= 0 && id != onlyId)) continue;
                double[] d = {(Double) p[1] - c[0][0], (Double) p[2] - c[0][1], (Double) p[3] - c[0][2]};
                double x = SimCamera.dot(d, c[1]), y = SimCamera.dot(d, c[2]), z = SimCamera.dot(d, c[3]);
                if (z < 1 || Math.abs(x / z) > tanH || Math.abs(y / z) > tanV) continue;
                double pixels = type.diameterIn * f / z;
                if (pixels < MIN_PIECE_PIXELS) continue;
                found.add(new double[] {id, cam.width / 2.0 + f * x / z, cam.height / 2.0 + f * y / z, pixels});
            }
        }
        found.sort(Comparator.comparingDouble((double[] b) -> -b[3]));
        List<Block> out = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (double[] b : found) {
            if (out.size() >= (onlyOne ? 1 : MAX_BLOCKS)) break;
            out.add(block(b[1], b[2], b[3], (int) b[0]));
            notes.add(String.format(Locale.US, "%s: taught number %.0f, box at x %.0f, y %.0f, %.0f pixels", taught.get((int) b[0] - 1), b[0], b[1], b[2], b[3]));
        }
        cam.lastSeen(new ArrayList<>());
        cam.lastDetections(notes);
        return out.toArray(new Block[0]);
    }

    // The SDK's own box, from the 16 bytes the camera would send (each number low byte first, from byte 5).
    private Block block(double x, double y, double pixels, int id) {
        byte[] b = new byte[16];
        put(b, 5, (int) Math.round(x));
        put(b, 7, (int) Math.round(y));
        put(b, 9, (int) Math.round(pixels));
        put(b, 11, (int) Math.round(pixels));
        put(b, 13, id);
        return new Block(b);
    }

    private static void put(byte[] b, int at, int value) {
        b[at] = (byte) value;
        b[at + 1] = (byte) (value >> 8);
    }

    @Override public Manufacturer getManufacturer() { return Manufacturer.Other; }
    @Override public String getDeviceName() { return "Pretend HuskyLens"; }
    @Override public String getConnectionInfo() { return "simulator"; }
    @Override public int getVersion() { return 1; }
    @Override public void resetDeviceConfigurationForOpMode() { }
    @Override public void close() {
        try {
            closer.close();
        } catch (Exception ignored) {
            // closing is best effort
        }
    }
}
