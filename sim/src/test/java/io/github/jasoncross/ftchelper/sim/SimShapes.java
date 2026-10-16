package io.github.jasoncross.ftchelper.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The robot drawn as simple shapes (shape.NAME lines in the robot file): a chassis box plus parts
 * from a short menu, each moved by its motor or servo. Used for the 3D view and the size rules.
 *
 * Robot frame: inches from the robot's center on the floor; f = forward, l = left, u = up.
 *
 * Types (see docs/guides/simulator.md, "Robot shapes"):
 *   box      a part that does not move
 *   slide    moves in a straight line along "axis" (lift, extension); children ride on its moving stage
 *   arm      turns about a hinge ("axis" = the hinge line); its box reaches forward from the hinge
 *   turret   turns about the up axis
 *   claw     two fingers that a servo opens and closes
 *   roller   an intake roller (a cylinder across the robot) that spins with its motor or CR servo
 *   flywheel a launcher wheel that spins with its motor
 */
final class SimShapes {
    static final List<String> TYPES = java.util.Arrays.asList("box", "slide", "arm", "turret", "claw", "roller", "flywheel");

    /** One shape from the robot file. */
    static final class Shape {
        final String name, type, parent, color, axis;
        final double[] at;              // f, l, u in the parent's frame
        final double sf, sl, su;        // size: forward, left-right, up
        final String motor, servo, crservo;
        // How the driver's position becomes inches (slide) or degrees (arm, turret) or a gap (claw):
        // motors: (shaft - zeroTicks) / perUnit + start; servos: pos0 + position * (pos1 - pos0).
        final double perUnit, zeroTicks, start, pos0, pos1;
        final double min, max;          // the range of motion (for the "fully extended" size check)
        double value = 0;               // now: inches, degrees, gap inches; spin degrees for rollers

        Shape(String name, Map<String, String> s, SimRobot robot) {
            this.name = name;
            type = s.getOrDefault("", "box").toLowerCase(Locale.US);
            if (!TYPES.contains(type)) throw new IllegalArgumentException("Robot file: shape." + name + ": the type \"" + type + "\" must be one of " + TYPES);
            parent = s.getOrDefault("on", "chassis");
            color = s.getOrDefault("color", defaultColor(type));
            at = triple(s.getOrDefault("at", "0,0,0"), "at", name);
            double[] size = size(s.getOrDefault("size", defaultSize(type)), name);
            sf = size[0];
            sl = size[1];
            su = size[2];
            axis = s.getOrDefault("axis", defaultAxis(type)).toLowerCase(Locale.US);
            if (!java.util.Arrays.asList("up", "down", "forward", "back", "left", "right").contains(axis)) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": axis must be up, down, forward, back, left or right");
            }
            motor = s.get("motor");
            servo = s.get("servo");
            crservo = s.get("crservo");
            if (motor != null && !robot.motors.containsKey(motor)) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": no motor." + motor + " in the robot file");
            }
            if (servo != null && !robot.servos.containsKey(servo)) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": no servo." + servo + " in the robot file");
            }
            if (crservo != null && !robot.crServos.containsKey(crservo)) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": no crservo." + crservo + " in the robot file");
            }
            SimMotor m = motor == null ? null : robot.motors.get(motor);
            // Motor: counts per inch (slide) or per degree (arm, turret). A slide can give its spool
            // diameter instead: one turn of the spool pulls pi x diameter of string.
            double per;
            if (s.containsKey("ticksPerIn")) per = num(s, "ticksPerIn", name);
            else if (s.containsKey("ticksPerDeg")) per = num(s, "ticksPerDeg", name);
            else if (s.containsKey("spoolIn") && m != null) per = m.ticksPerRev / (Math.PI * num(s, "spoolIn", name));
            else if (m != null && !type.equals("slide")) per = m.ticksPerRev * (s.containsKey("gear") ? num(s, "gear", name) : 1) / 360.0;
            else if (m != null) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": a slide on a motor needs ticksPerIn= or spoolIn=");
            } else per = 1;
            perUnit = per;
            zeroTicks = m != null && m.minTicks > -1e300 ? m.minTicks : 0;
            start = s.containsKey("start") ? num(s, "start", name) : 0;
            // Servo: the value at position 0 and at position 1 (inches, degrees, or the claw's gap).
            pos0 = s.containsKey("pos0") ? num(s, "pos0", name) : 0;
            pos1 = s.containsKey("pos1") ? num(s, "pos1", name) : defaultPos1(type, robot, servo);
            // The range of motion: written, or from the motor's hard stops, or the servo's two ends.
            double lo = start, hi = start;
            if (servo != null) {
                lo = pos0;
                hi = pos1;
            } else if (m != null && m.maxTicks < 1e300) {
                hi = start + (m.maxTicks - zeroTicks) / perUnit;    // from the bottom stop to the top stop
            }
            if (s.containsKey("travel")) { lo = 0; hi = num(s, "travel", name); }
            if (s.containsKey("minDeg")) lo = num(s, "minDeg", name);
            if (s.containsKey("maxDeg")) hi = num(s, "maxDeg", name);
            min = Math.min(lo, hi);
            max = Math.max(lo, hi);
            value = type.equals("claw") ? min : start;
        }

        boolean moves() {
            return type.equals("slide") || type.equals("arm") || type.equals("turret") || type.equals("claw");
        }

        private static String defaultColor(String type) {
            switch (type) {
                case "slide": return "#c9a227";
                case "arm": return "#8a6fd1";
                case "turret": return "#3f9e8f";
                case "claw": return "#e07a2e";
                case "roller": return "#2fa84f";
                case "flywheel": return "#d6453d";
                default: return "#8c95a1";
            }
        }

        private static String defaultSize(String type) {
            switch (type) {
                case "slide": return "2x2x12";
                case "arm": return "10x2x2";
                case "turret": return "8x8x2";
                case "claw": return "3x1x3";
                case "roller": return "2x12x2";
                case "flywheel": return "4x2x4";
                default: return "4x4x4";
            }
        }

        private static String defaultAxis(String type) {
            switch (type) {
                case "slide": return "up";
                case "turret": return "up";
                case "roller":
                case "flywheel":
                case "arm": return "left";
                default: return "up";
            }
        }

        private static double defaultPos1(String type, SimRobot robot, String servo) {
            // An arm or turret on a catalog servo: position 1 is the servo's whole range.
            if ((type.equals("arm") || type.equals("turret")) && servo != null) {
                String part = robot.servos.get(servo).partId;
                if (!part.isEmpty()) {
                    double range = SimParts.catalog().servos.containsKey(part) ? SimParts.catalog().servo(part).rangeDeg : Double.NaN;
                    if (!Double.isNaN(range)) return range;
                }
                return 180;
            }
            if (type.equals("claw")) return 3;          // a 3 in gap when fully open
            return 1;
        }
    }

    /** One box (or cylinder) to draw: its center and axes in the robot frame, and its size. */
    static final class Piece {
        final String shape, kind, color;
        final double[] center, ef, el, eu;     // robot frame; ef/el/eu = the piece's own forward/left/up
        final double sf, sl, su;
        final double spinDeg;

        Piece(String shape, String kind, String color, double[][] frame, double[] localCenter,
              double sf, double sl, double su, double spinDeg) {
            this.shape = shape;
            this.kind = kind;
            this.color = color;
            this.center = apply(frame, localCenter);
            this.ef = new double[] {frame[0][0], frame[1][0], frame[2][0]};
            this.el = new double[] {frame[0][1], frame[1][1], frame[2][1]};
            this.eu = new double[] {frame[0][2], frame[1][2], frame[2][2]};
            this.sf = sf;
            this.sl = sl;
            this.su = su;
            this.spinDeg = spinDeg;
        }

        /** The 8 corners in the robot frame. */
        List<double[]> corners() {
            List<double[]> out = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double a = ((i & 1) == 0 ? -0.5 : 0.5) * sf, b = ((i & 2) == 0 ? -0.5 : 0.5) * sl, c = ((i & 4) == 0 ? -0.5 : 0.5) * su;
                out.add(new double[] {center[0] + ef[0] * a + el[0] * b + eu[0] * c,
                        center[1] + ef[1] * a + el[1] * b + eu[1] * c,
                        center[2] + ef[2] * a + el[2] * b + eu[2] * c});
            }
            return out;
        }
    }

    /** How big the robot is (inches, robot frame). */
    static final class Extent {
        double back, front, right, left, top;

        double lengthIn() { return front - back; }
        double widthIn() { return left - right; }

        /** Fits a box (any way round on the floor) of a by b, and height h. */
        boolean fits(double a, double b, double h) {
            double l = lengthIn(), w = widthIn();
            return top <= h + 1e-6 && ((l <= a + 1e-6 && w <= b + 1e-6) || (l <= b + 1e-6 && w <= a + 1e-6));
        }

        @Override public String toString() {
            return String.format(Locale.US, "%.1f x %.1f in, %.1f in tall", lengthIn(), widthIn(), top);
        }
    }

    final Map<String, Shape> shapes = new LinkedHashMap<>();
    final double chassisHeightIn;
    private final SimRobot robot;
    /** The biggest the robot can get: every moving part over its whole range (worked out once). */
    final Extent most;
    /** The shapes at the biggest pose (for the page), as "name=value" text. */
    final String mostPose;

    SimShapes(SimRobot robot, java.util.Properties p, Map<String, Map<String, String>> lines) {
        this.robot = robot;
        for (Map.Entry<String, Map<String, String>> e : lines.entrySet()) {
            if (e.getKey().equals("chassis")) throw new IllegalArgumentException("Robot file: \"chassis\" is the robot's base; give the shape another name");
            shapes.put(e.getKey(), new Shape(e.getKey(), e.getValue(), robot));
        }
        // Every shape sits on the chassis or on a shape listed in the file, with no loops.
        for (Shape s : shapes.values()) {
            String up = s.parent;
            int steps = 0;
            while (!up.equals("chassis")) {
                Shape parent = shapes.get(up);
                if (parent == null) throw new IllegalArgumentException("Robot file: shape." + s.name + ": on=" + up + ", but there is no shape." + up);
                up = parent.parent;
                if (++steps > shapes.size()) throw new IllegalArgumentException("Robot file: shape." + s.name + ": the on= chain goes round in a loop");
            }
        }
        double defaultChassis = shapes.isEmpty() ? robot.heightIn : 4;
        chassisHeightIn = p.containsKey("robot.chassisHeightIn") ? Double.parseDouble(p.getProperty("robot.chassisHeightIn").trim()) : defaultChassis;
        // The biggest the robot gets: try each moving part at both ends of its range and in between
        // (an arm can reach farthest part way round), up to about 4000 combinations.
        List<Shape> moving = new ArrayList<>();
        for (Shape s : shapes.values()) if (s.moves() && s.max > s.min) moving.add(s);
        int steps = moving.isEmpty() ? 1 : Math.max(2, Math.min(9, (int) Math.floor(Math.pow(4000, 1.0 / moving.size()))));
        Extent best = null;
        String bestPose = "";
        int combos = (int) Math.pow(steps, moving.size());
        double[] keep = new double[moving.size()];
        for (int i = 0; i < moving.size(); i++) keep[i] = moving.get(i).value;
        Extent all = null;
        for (int c = 0; c < combos; c++) {
            int k = c;
            StringBuilder pose = new StringBuilder();
            for (Shape s : moving) {
                int step = k % steps;
                k /= steps;
                s.value = s.min + (s.max - s.min) * step / (steps - 1.0);
                pose.append(String.format(Locale.US, "%s=%.1f ", s.name, s.value));
            }
            Extent e = extent(pieces());
            if (all == null) all = e;
            all.back = Math.min(all.back, e.back);
            all.front = Math.max(all.front, e.front);
            all.right = Math.min(all.right, e.right);
            all.left = Math.max(all.left, e.left);
            if (e.top > all.top) bestPose = pose.toString().trim();
            all.top = Math.max(all.top, e.top);
        }
        for (int i = 0; i < moving.size(); i++) moving.get(i).value = keep[i];
        most = all;
        mostPose = bestPose;
    }

    /** Reads each shape's driver (motor, servo, CR servo) into its value. */
    void update(double dt) {
        for (Shape s : shapes.values()) {
            if (s.type.equals("roller") || s.type.equals("flywheel")) {
                // Spin for the picture only: degrees turned since the start.
                double turnsPerSec = 0;
                if (s.motor != null) {
                    SimMotor m = robot.motors.get(s.motor);
                    turnsPerSec = m.shaftSpeed() / Math.max(1, m.ticksPerRev);
                } else if (s.crservo != null) {
                    double[] cr = robot.crServos.get(s.crservo);
                    turnsPerSec = cr[0] * (cr[1] == 1 ? -1 : 1) * 2.5;    // a CR servo: about 150 RPM
                }
                s.value = (s.value + turnsPerSec * 360 * dt) % 360;
                continue;
            }
            if (s.motor != null) {
                s.value = s.start + (robot.motors.get(s.motor).shaft() - s.zeroTicks) / s.perUnit;
            } else if (s.servo != null) {
                s.value = s.pos0 + robot.servos.get(s.servo).actual * (s.pos1 - s.pos0);
            }
            if (s.moves()) s.value = Math.max(s.min, Math.min(s.max, s.value));
        }
    }

    /** Everything to draw now: the chassis and each shape's boxes, in the robot frame. */
    List<Piece> pieces() {
        List<Piece> out = new ArrayList<>();
        double[][] base = identity();
        out.add(new Piece("chassis", "box", "#1f6feb", base, new double[] {0, 0, chassisHeightIn / 2},
                robot.lengthIn, robot.widthIn, chassisHeightIn, 0));
        Map<String, double[][]> mounts = new LinkedHashMap<>();
        mounts.put("chassis", base);
        // Parents before children: keep going round until every shape is placed.
        List<Shape> todo = new ArrayList<>(shapes.values());
        while (!todo.isEmpty()) {
            for (java.util.Iterator<Shape> it = todo.iterator(); it.hasNext(); ) {
                Shape s = it.next();
                double[][] parent = mounts.get(s.parent);
                if (parent == null) continue;
                it.remove();
                double[][] f = mul(parent, translate(s.at));
                switch (s.type) {
                    case "slide": {
                        // the fixed stage, and the moving stage pushed out along the axis
                        // the stage frame's "up" runs along the axis; size= is in robot directions
                        double[][] dirFrame = mul(f, axisFrame(s.axis));
                        double[][] turn = axisFrame(s.axis);
                        double len = along(s, column(turn, 2)), a = along(s, column(turn, 0)), b = along(s, column(turn, 1));
                        out.add(new Piece(s.name, "box", s.color, dirFrame, new double[] {0, 0, len / 2}, a, b, len, 0));
                        double[][] carriage = mul(dirFrame, translate(new double[] {0, 0, s.value}));
                        out.add(new Piece(s.name, "box", lighter(s.color), carriage, new double[] {0, 0, len / 2},
                                a * 0.8, b * 0.8, len, 0));
                        mounts.put(s.name, mul(f, translate(scale(axisVector(s.axis), s.value))));
                        break;
                    }
                    case "arm":
                    case "turret": {
                        String hinge = s.type.equals("turret") ? "up" : s.axis;
                        double[][] h = mul(f, rotate(hinge, s.value));
                        double[] c = s.type.equals("arm") ? new double[] {s.sf / 2, 0, 0} : new double[] {0, 0, s.su / 2};
                        out.add(new Piece(s.name, "box", s.color, h, c, s.sf, s.sl, s.su, 0));
                        mounts.put(s.name, h);
                        break;
                    }
                    case "claw": {
                        // a palm and two fingers, "value" inches apart
                        double gap = s.value, finger = Math.max(0.3, s.sl);
                        out.add(new Piece(s.name, "box", s.color, f, new double[] {0, 0, s.su * 0.15}, s.sf, gap + 2 * finger, s.su * 0.3, 0));
                        out.add(new Piece(s.name, "box", s.color, f, new double[] {0, (gap + finger) / 2, s.su / 2}, s.sf, finger, s.su, 0));
                        out.add(new Piece(s.name, "box", s.color, f, new double[] {0, -(gap + finger) / 2, s.su / 2}, s.sf, finger, s.su, 0));
                        mounts.put(s.name, f);
                        break;
                    }
                    case "roller":
                    case "flywheel": {
                        // a cylinder whose length runs along "axis" (its own left-right line)
                        double[][] r = mul(f, cylinderFrame(s.axis));
                        double len = along(s, column(cylinderFrame(s.axis), 1));
                        double dia = Math.max(along(s, column(cylinderFrame(s.axis), 0)), along(s, column(cylinderFrame(s.axis), 2)));
                        out.add(new Piece(s.name, "cyl", s.color, r, new double[] {0, 0, 0}, dia, len, dia, s.value));
                        mounts.put(s.name, f);
                        break;
                    }
                    default:
                        out.add(new Piece(s.name, "box", s.color, f, new double[] {0, 0, s.su / 2}, s.sf, s.sl, s.su, 0));
                        mounts.put(s.name, f);
                }
            }
        }
        return out;
    }

    /** How big the robot is with these pieces. */
    static Extent extent(List<Piece> pieces) {
        Extent e = new Extent();
        e.back = e.right = Double.MAX_VALUE;
        e.front = e.left = e.top = -Double.MAX_VALUE;
        for (Piece p : pieces) {
            for (double[] c : p.corners()) {
                e.back = Math.min(e.back, c[0]);
                e.front = Math.max(e.front, c[0]);
                e.right = Math.min(e.right, c[1]);
                e.left = Math.max(e.left, c[1]);
                e.top = Math.max(e.top, c[2]);
            }
        }
        return e;
    }

    /** How big the robot is now. */
    Extent now() {
        return extent(pieces());
    }

    // ---------------------------------------------------------------- frames (3x4: rotation | position)

    private static double[][] identity() {
        return new double[][] {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}};
    }

    private static double[][] translate(double[] t) {
        return new double[][] {{1, 0, 0, t[0]}, {0, 1, 0, t[1]}, {0, 0, 1, t[2]}};
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][4];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                double v = j == 3 ? a[i][3] : 0;
                for (int k = 0; k < 3; k++) v += a[i][k] * b[k][j];
                r[i][j] = v;
            }
        }
        return r;
    }

    static double[] apply(double[][] f, double[] p) {
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) r[i] = f[i][0] * p[0] + f[i][1] * p[1] + f[i][2] * p[2] + f[i][3];
        return r;
    }

    private static double[] axisVector(String axis) {
        switch (axis) {
            case "forward": return new double[] {1, 0, 0};
            case "back": return new double[] {-1, 0, 0};
            case "left": return new double[] {0, 1, 0};
            case "right": return new double[] {0, -1, 0};
            case "down": return new double[] {0, 0, -1};
            default: return new double[] {0, 0, 1};
        }
    }

    // The shape's size along a direction given in robot axes (f, l, u).
    private static double along(Shape s, double[] dir) {
        return Math.abs(dir[0]) * s.sf + Math.abs(dir[1]) * s.sl + Math.abs(dir[2]) * s.su;
    }

    private static double[] column(double[][] f, int j) {
        return new double[] {f[0][j], f[1][j], f[2][j]};
    }

    private static double[] scale(double[] v, double k) {
        return new double[] {v[0] * k, v[1] * k, v[2] * k};
    }

    // A frame whose "up" points along the axis (for a slide's stages).
    private static double[][] axisFrame(String axis) {
        switch (axis) {
            case "forward": return rotate("left", -90);   // up -> forward
            case "back": return rotate("left", 90);
            case "left": return rotate("forward", -90);   // up -> left
            case "right": return rotate("forward", 90);
            case "down": return rotate("left", 180);
            default: return identity();
        }
    }

    // A frame whose "left" points along the axis (a cylinder runs along its own left-right line).
    private static double[][] cylinderFrame(String axis) {
        switch (axis) {
            case "forward":
            case "back": return rotate("up", -90);        // left -> forward
            case "up":
            case "down": return rotate("forward", 90);    // left -> up
            default: return identity();
        }
    }

    /**
     * Turns by deg about an axis, right-hand rule: about "left", + raises the front (an arm going up);
     * about "up", + turns left; about "forward", + rolls the left side up... the other names turn the
     * other way.
     */
    static double[][] rotate(String axis, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        switch (axis) {
            case "forward": return new double[][] {{1, 0, 0, 0}, {0, c, -s, 0}, {0, s, c, 0}};
            case "back": return rotate("forward", -deg);
            case "left":
                // about +l with + = front up: f -> f cos + u sin
                return new double[][] {{c, 0, -s, 0}, {0, 1, 0, 0}, {s, 0, c, 0}};
            case "right": return rotate("left", -deg);
            case "down": return rotate("up", -deg);
            default: return new double[][] {{c, -s, 0, 0}, {s, c, 0, 0}, {0, 0, 1, 0}};
        }
    }

    private static String lighter(String hex) {
        try {
            int v = Integer.parseInt(hex.replace("#", ""), 16);
            int r = (v >> 16) & 255, g = (v >> 8) & 255, b = v & 255;
            r = r + (255 - r) / 3;
            g = g + (255 - g) / 3;
            b = b + (255 - b) / 3;
            return String.format("#%02x%02x%02x", r, g, b);
        } catch (NumberFormatException e) {
            return hex;
        }
    }

    // ---------------------------------------------------------------- reading the robot file

    private static double num(Map<String, String> s, String key, String shape) {
        try {
            return Double.parseDouble(s.get(key).trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Robot file: shape." + shape + ": " + key + "=" + s.get(key) + " needs a number");
        }
    }

    private static double[] triple(String text, String key, String shape) {
        String[] p = text.trim().split("\\s*[,/ ]\\s*");
        if (p.length != 3) throw new IllegalArgumentException("Robot file: shape." + shape + ": " + key + "= needs 3 numbers (forward/left/up)");
        try {
            return new double[] {Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: shape." + shape + ": " + key + "=" + text + " needs 3 numbers");
        }
    }

    private static double[] size(String text, String shape) {
        String[] p = text.trim().toLowerCase(Locale.US).split("\\s*x\\s*");
        if (p.length != 3) throw new IllegalArgumentException("Robot file: shape." + shape + ": size= needs forward x left-right x up, e.g. 4x2x12");
        try {
            double[] r = {Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])};
            for (double v : r) if (v <= 0) throw new NumberFormatException();
            return r;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: shape." + shape + ": size=" + text + " needs 3 numbers above 0");
        }
    }
}
