package com.crossbotics.ftchelper.sim;

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
    /** A roller or wheel in the picture turns at most this fast (turns per second); see update(). */
    static final double PICTURE_TURNS_PER_SEC = 1.2;

    static final class Shape {
        final String name, type, parent, color, axis;
        final double[] at;              // f, l, u in the parent's frame
        final double sf, sl, su;        // size: forward, left-right, up
        final String motor, servo, crservo;
        final boolean reversed;         // reversed=yes: a roller that turns the other way from its motor (geared)
        final double tilt;              // tilt=DEG: mounted turned about its parent's left-right line (front up +)
        final boolean soft;             // soft=yes: a soft roller (tubing): only game pieces touch it, not the field
        final String model, baseModel;  // CAD files (see SimCad): model= rides the moving part, baseModel= a slide's fixed stage
        final Double cadPose;           // the value the CAD was drawn at (cadPose=); null: the simulator's start
        // How the driver's position becomes inches (slide) or degrees (arm, turret) or a gap (claw):
        // motors: (shaft - zeroTicks) / perUnit + start; servos: pos0 + position * (pos1 - pos0).
        final double perUnit, zeroTicks, start, pos0, pos1;
        final double min, max;          // the range of motion (for the "fully extended" size check)
        // passive=yes: moved by gravity or springs, not by the code. drop=SERVO>0.6 (or <): when that servo
        // passes the value, the part falls to fallsTo= (its hard stop) and stays there.
        final boolean passive;
        final String dropServo;
        final double dropAt, fallsTo;
        final boolean dropAbove;
        boolean dropped = false;
        double value = 0;               // now: inches, degrees, gap inches; spin degrees for rollers
        double rate = 0;                // for the picture: turns per second (rollers), or value per second

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
            reversed = s.containsKey("reversed") && s.get("reversed").equalsIgnoreCase("yes");
            tilt = s.containsKey("tilt") ? num(s, "tilt", name) : 0;
            soft = s.containsKey("soft") && s.get("soft").equalsIgnoreCase("yes");
            model = s.get("model");
            baseModel = s.get("baseModel");
            if (baseModel != null && !type.equals("slide")) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": baseModel= is a slide's fixed stage; other shapes have one model=");
            }
            cadPose = s.containsKey("cadPose") ? num(s, "cadPose", name) : null;
            if (motor != null && !robot.motors.containsKey(motor)) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": no motor." + motor + " in the robot file"
                        + (robot.crServos.containsKey(motor) ? " (it is a CR servo: write crservo=" + motor + ")" : ""));
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
            if (s.containsKey("travel")) { lo = start; hi = start + num(s, "travel", name); }
            if (s.containsKey("minDeg")) lo = num(s, "minDeg", name);
            if (s.containsKey("maxDeg")) hi = num(s, "maxDeg", name);
            min = Math.min(lo, hi);
            max = Math.max(lo, hi);
            if (moves() && !(max > min)) {
                // A moving part needs its range: without it the 3D view could not move it and the
                // full-reach size check could not see how far it goes.
                throw new IllegalArgumentException("Robot file: shape." + name + " moves but has no range of motion. "
                        + (motor != null ? "Give motor." + motor + " its hard stops (min= and max=), or write "
                        : servo != null ? "Give pos0= and pos1= different values, or write " : "Give it a motor= or servo=, and ")
                        + (type.equals("slide") ? "travel=" : type.equals("claw") ? "pos0=/pos1=" : "minDeg= and maxDeg=") + " on the shape.");
            }
            passive = s.containsKey("passive") && s.get("passive").equalsIgnoreCase("yes");
            if (passive && (motor != null || servo != null)) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": passive=yes is for a part nothing drives; remove motor=/servo=");
            }
            if (moves() && motor == null && servo == null && !passive) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": a " + type + " needs motor= or servo= (what moves it), "
                        + "or passive=yes (gravity or springs move it)");
            }
            String drop = s.get("drop");
            if (drop != null) {
                java.util.regex.Matcher dm = java.util.regex.Pattern.compile("\\s*([^<>\\s]+)\\s*([<>])\\s*([-0-9.]+)\\s*").matcher(drop);
                if (!passive || !dm.matches()) {
                    throw new IllegalArgumentException("Robot file: shape." + name + ": drop= goes with passive=yes and reads like "
                            + "drop=intake_deploy>0.6 (a servo, > or <, a position 0 to 1)");
                }
                if (!robot.servos.containsKey(dm.group(1))) {
                    throw new IllegalArgumentException("Robot file: shape." + name + ": drop=: no servo." + dm.group(1) + " in the robot file");
                }
                dropServo = dm.group(1);
                dropAbove = dm.group(2).equals(">");
                dropAt = Double.parseDouble(dm.group(3));
                fallsTo = s.containsKey("fallsTo") ? num(s, "fallsTo", name) : (Math.abs(min - start) > Math.abs(max - start) ? min : max);
                if (fallsTo < min || fallsTo > max) {
                    throw new IllegalArgumentException("Robot file: shape." + name + ": fallsTo=" + fallsTo + " is outside its range " + min + ".." + max);
                }
            } else {
                dropServo = null;
                dropAbove = false;
                dropAt = 0;
                fallsTo = start;
            }
            if (type.equals("turret") && s.containsKey("axis") && !axis.equals("up")) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": a turret always turns about up (leave out axis=, or use an arm)");
            }
            if (type.equals("arm") && (axis.equals("forward") || axis.equals("back"))) {
                throw new IllegalArgumentException("Robot file: shape." + name + ": an arm reaches forward from its hinge, so its hinge axis "
                        + "can't be forward/back (use left/right to swing up and down, or up/down to swing sideways)");
            }
            value = type.equals("claw") ? min : start;
        }

        /** The unit of value: inches along a slide, degrees for an arm or turret, the gap for a claw. */
        String unit() {
            return type.equals("slide") ? "in up its slide" : type.equals("claw") ? "in apart" : "deg";
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
                // the servo's range: from its catalog or team part, or its own rangeDeg= in the robot file
                double range = robot.servos.get(servo).rangeDeg;
                return Double.isNaN(range) ? 180 : range;
            }
            if (type.equals("claw")) return 3;          // a 3 in gap when fully open
            return 1;
        }
    }

    /** One box (or cylinder) to draw: its center and axes in the robot frame, and its size. */
    static final class Piece {
        final String shape, kind, color;
        String part = "main";                  // which piece of its shape: main, base (a slide's fixed stage), finger
        SimCad.Mesh mesh = null;               // the CAD drawn in its place (and used for its size), or null
        double[][] meshFrame = null;           // where the mesh is now: robot frame from its start-pose coordinates
        double[] sweep = null;                 // a spinning mesh (roller): radius, and its ends along its axis
        final double[] center, ef, el, eu;     // robot frame; ef/el/eu = the piece's own forward/left/up
        final double sf, sl, su;
        final double spinDeg;
        // For the picture: turns per second (rollers, flywheels), +1 taking pieces in / -1 pushing out /
        // 0 not an intake, and true while a passive part is moving.
        double run = 0;
        int flow = 0;
        boolean moving = false;
        boolean soft = false;                  // a soft roller: the field does not stop it (pieces still touch it)

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

        /** The points that decide the robot's size: the CAD's outermost points, or the box's corners. */
        List<double[]> points() {
            if (mesh == null) return corners();
            if (sweep != null) {
                // a roller turns: everywhere its mesh can reach is a cylinder (a 16-sided one round it)
                List<double[]> out = new ArrayList<>(32);
                double r = sweep[0] / Math.cos(Math.PI / 16);
                for (int k = 0; k < 16; k++) {
                    double a = r * Math.cos(Math.PI * k / 8), c = r * Math.sin(Math.PI * k / 8);
                    for (int end = 1; end <= 2; end++) {
                        double b = sweep[end];
                        out.add(new double[] {center[0] + ef[0] * a + el[0] * b + eu[0] * c,
                                center[1] + ef[1] * a + el[1] * b + eu[1] * c, center[2] + ef[2] * a + el[2] * b + eu[2] * c});
                    }
                }
                return out;
            }
            List<double[]> out = new ArrayList<>(mesh.hull.length);
            for (double[] h : mesh.hull) out.add(apply(meshFrame, h));
            return out;
        }

        // The piece's own frame (its spin too, for a roller): rotation columns forward, left, up, then its centre.
        double[][] frame() {
            double[][] f = new double[3][4];
            double c = Math.cos(Math.toRadians(spinDeg)), sn = Math.sin(Math.toRadians(spinDeg));
            for (int i = 0; i < 3; i++) {
                // turned about its own left line, the way the page turns a roller (three.js makeRotationY)
                f[i][0] = ef[i] * c - eu[i] * sn;
                f[i][1] = el[i];
                f[i][2] = ef[i] * sn + eu[i] * c;
                f[i][3] = center[i];
            }
            return f;
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
        double back, front, right, left, top, bottom;

        double lengthIn() { return front - back; }
        double widthIn() { return left - right; }

        /** Fits a box (any way round on the floor) of a by b, and height h. */
        boolean fits(double a, double b, double h) {
            double l = lengthIn(), w = widthIn();
            return top <= h + 1e-6 && ((l <= a + 1e-6 && w <= b + 1e-6) || (l <= b + 1e-6 && w <= a + 1e-6));
        }

        @Override public String toString() {
            return String.format(Locale.US, "%.1f x %.1f in, %.1f in tall", lengthIn(), widthIn(), top)
                    + (bottom < -FLOOR_SLACK ? String.format(Locale.US, ", %.1f in below the tiles", -bottom) : "");
        }

        /** True if nothing goes below the tiles (a part there would really hit the floor: check the shapes). */
        boolean aboveFloor() {
            return bottom >= -FLOOR_SLACK;
        }

        /** What breaks the match size rule (18 x 24 in, 29 in tall) or the floor, in words; "" if nothing. */
        String problems() {
            List<String> p = new ArrayList<>();
            if (top > 29 + 1e-6) p.add(String.format(Locale.US, "%.1f in tall: more than 29", top));
            double l = lengthIn(), w = widthIn();
            if (!((l <= 24 + 1e-6 && w <= 18 + 1e-6) || (l <= 18 + 1e-6 && w <= 24 + 1e-6))) {
                p.add(String.format(Locale.US, "%.1f x %.1f in on the floor: more than 18 x 24", l, w));
            }
            if (bottom < -FLOOR_SLACK) p.add(String.format(Locale.US, "%.1f in below the tiles", -bottom));
            return String.join("; ", p);
        }

        // How far it is from fitting a by b by h (0 or less = fits): for showing the worst pose.
        double overflow(double a, double b, double h) {
            double l = lengthIn(), w = widthIn();
            double flat = Math.min(Math.max(l - a, w - b), Math.max(l - b, w - a));
            return Math.max(Math.max(flat, top - h), -bottom - FLOOR_SLACK);
        }
    }

    // A part may touch the tiles; more than this below them is a mistake in the shapes (inches).
    static final double FLOOR_SLACK = 0.1;

    final Map<String, Shape> shapes = new LinkedHashMap<>();
    final double chassisHeightIn;
    /** The CAD models (robot.model, model=, baseModel=), or null when the robot file has none. */
    final SimCad cad;
    // shape|part -> its mesh, and that piece's frame in the pose the CAD was drawn in
    private final Map<String, SimCad.Mesh> meshes = new LinkedHashMap<>();
    private final Map<String, double[][]> cadStart = new LinkedHashMap<>();
    private final Map<String, double[]> cadBox = new LinkedHashMap<>();
    private final Map<String, double[]> cadSweep = new LinkedHashMap<>();
    private final SimRobot robot;
    /**
     * At full reach: when some pose does not fit, the worst one (furthest from fitting 18 x 24 x 29 in);
     * when every pose fits, the biggest the robot gets in each direction (not always all in one pose).
     * Worked out once.
     */
    final Extent most;
    /** True if EVERY pose tried fits 18 x 24 x 29 in and stays above the tiles. */
    final boolean mostOk;
    /** The worst pose, as "name=value" text. */
    final String mostPose;
    /** How many poses were tried, and the step used for hinges (degrees). */
    final int posesTried;
    final double hingeStepDeg;

    SimShapes(SimRobot robot, java.util.Properties p, Map<String, Map<String, String>> lines, java.nio.file.Path dir) {
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
        // CAD: each mesh, and where its piece was when the CAD was drawn (cadPose=, or the start).
        String robotModel = p.getProperty("robot.model", "").trim();
        boolean anyCad = !robotModel.isEmpty();
        for (Shape s : shapes.values()) anyCad |= s.model != null || s.baseModel != null;
        cad = anyCad ? new SimCad(p, dir) : null;
        if (cad != null) {
            if (!robotModel.isEmpty()) meshes.put("chassis|main", cad.load(robotModel, "robot.model"));
            for (Shape s : shapes.values()) {
                if (s.model != null) meshes.put(s.name + "|main", cad.load(s.model, "shape." + s.name + " model="));
                if (s.baseModel != null) meshes.put(s.name + "|base", cad.load(s.baseModel, "shape." + s.name + " baseModel="));
            }
            Map<String, Double> keepNow = new LinkedHashMap<>();
            for (Shape s : shapes.values()) keepNow.put(s.name, s.value);
            update(0);
            for (Shape s : shapes.values()) if (s.cadPose != null) s.value = s.cadPose;
            for (Piece pc : pieces()) {
                String key = pc.shape + "|" + pc.part;
                if (meshes.containsKey(key) && !cadStart.containsKey(key)) {
                    cadStart.put(key, pc.frame());
                    cadBox.put(key, new double[] {pc.sf, pc.sl, pc.su});
                    if (pc.kind.equals("cyl")) {
                        // a roller's mesh in its own frame: how far from its axis, and from where to where along it
                        double[][] back = invert(pc.frame());
                        double[] w = {0, Double.MAX_VALUE, -Double.MAX_VALUE};
                        for (double[] h : meshes.get(key).hull) {
                            double[] q = apply(back, h);
                            w[0] = Math.max(w[0], Math.hypot(q[0], q[2]));
                            w[1] = Math.min(w[1], q[1]);
                            w[2] = Math.max(w[2], q[1]);
                        }
                        cadSweep.put(key, w);
                    }
                }
            }
            for (Shape s : shapes.values()) s.value = keepNow.get(s.name);
        }
        // The robot's worst pose: try every combination of each moving part's important values. A slide
        // or claw is biggest at one of its two ends. A hinge (arm, turret) reaches farthest at an end of
        // its range, at a multiple of 90 degrees, or (when it rides on another hinge) part way between:
        // so hinges are tried every few degrees too (5 or finer when there are few parts; the step grows
        // with more parts to keep it under 50,000 poses). The step is reported with the result.
        List<Shape> moving = new ArrayList<>();
        for (Shape s : shapes.values()) if (s.moves()) moving.add(s);
        double step = 1;
        List<double[]> values;
        while (true) {
            values = new ArrayList<>();
            long combos = 1;
            for (Shape s : moving) {
                double[] v = candidates(s, step);
                values.add(v);
                combos *= v.length;
            }
            if (combos <= 50_000 || step >= 45) break;
            step *= 1.5;
        }
        hingeStepDeg = step;
        double[] keep = new double[moving.size()];
        for (int i = 0; i < moving.size(); i++) keep[i] = moving.get(i).value;
        int[] idx = new int[moving.size()];
        Extent worst = null, reach = null;
        double worstScore = -Double.MAX_VALUE;
        String worstPose = "";
        boolean allOk = true;
        int tried = 0;
        while (true) {
            for (int i = 0; i < moving.size(); i++) moving.get(i).value = values.get(i)[idx[i]];
            Extent e = measure(pieces());
            tried++;
            if (reach == null) {
                reach = new Extent();     // a copy: "worst" may keep e itself
                reach.back = e.back; reach.front = e.front; reach.right = e.right; reach.left = e.left;
                reach.top = e.top; reach.bottom = e.bottom;
            } else {
                reach.back = Math.min(reach.back, e.back);
                reach.front = Math.max(reach.front, e.front);
                reach.right = Math.min(reach.right, e.right);
                reach.left = Math.max(reach.left, e.left);
                reach.top = Math.max(reach.top, e.top);
                reach.bottom = Math.min(reach.bottom, e.bottom);
            }
            double score = e.overflow(18, 24, 29);
            if (score > 1e-6) allOk = false;
            if (score > worstScore) {
                worstScore = score;
                worst = e;
                StringBuilder pose = new StringBuilder();
                for (Shape sh : moving) pose.append(String.format(Locale.US, "%s %.1f %s, ", sh.name, sh.value, sh.unit()));
                String why = e.problems();
                worstPose = pose.toString().replaceAll(", $", "") + (why.isEmpty() ? "" : " (" + why + ")");
            }
            int i = 0;
            while (i < idx.length && ++idx[i] >= values.get(i).length) idx[i++] = 0;
            if (i == idx.length) break;
        }
        for (int i = 0; i < moving.size(); i++) moving.get(i).value = keep[i];
        most = allOk ? reach : worst;
        mostOk = allOk;
        mostPose = allOk ? "" : worstPose;
        posesTried = tried;
    }

    // The values to try for one moving part (see above).
    private static double[] candidates(Shape s, double stepDeg) {
        java.util.TreeSet<Double> v = new java.util.TreeSet<>();
        v.add(s.min);
        v.add(s.max);
        if (s.type.equals("arm") || s.type.equals("turret")) {
            for (double a = Math.ceil(s.min / 90) * 90; a < s.max; a += 90) v.add(a);
            int n = (int) Math.ceil((s.max - s.min) / stepDeg);
            for (int k = 1; k < n; k++) v.add(s.min + (s.max - s.min) * k / n);
        }
        double[] out = new double[v.size()];
        int i = 0;
        for (double d : v) out[i++] = d;
        return out;
    }

    /** Back to the start of a match: passive parts that dropped are put back (by hand, as on a real robot). */
    void resetPassive() {
        for (Shape s : shapes.values()) {
            if (!s.passive) continue;
            s.dropped = false;
            s.value = s.start;
        }
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
                if (s.reversed) turnsPerSec = -turnsPerSec;
                s.rate = turnsPerSec;
                // The picture turns at most PICTURE_TURNS_PER_SEC: the page shows about 10 pictures a
                // second, and a wheel turning faster would look like it turns backward (like a car wheel
                // in a film). The direction is right; the real speed is in the telemetry.
                double shown = Math.max(-PICTURE_TURNS_PER_SEC, Math.min(PICTURE_TURNS_PER_SEC, turnsPerSec));
                s.value = (s.value + shown * 360 * dt) % 360;
                continue;
            }
            double before = s.value;
            if (s.passive) {
                // Pushed over: it falls to its stop (fast: about half a turn a second, or 20 in/s) and stays.
                if (s.dropServo != null && !s.dropped) {
                    double pos = robot.servos.get(s.dropServo).actual;
                    s.dropped = s.dropAbove ? pos > s.dropAt : pos < s.dropAt;
                }
                if (s.dropped) {
                    double step = (s.type.equals("slide") ? 20 : 180) * dt;
                    s.value = Math.abs(s.fallsTo - s.value) <= step ? s.fallsTo : s.value + Math.signum(s.fallsTo - s.value) * step;
                }
            } else if (s.motor != null) {
                s.value = s.start + (robot.motors.get(s.motor).shaft() - s.zeroTicks) / s.perUnit;
            } else if (s.servo != null) {
                s.value = s.pos0 + robot.servos.get(s.servo).actual * (s.pos1 - s.pos0);
            }
            if (s.moves()) s.value = Math.max(s.min, Math.min(s.max, s.value));
            s.rate = dt > 0 ? (s.value - before) / dt : 0;
        }
    }

    /** Everything to draw now: the chassis and each shape's boxes, in the robot frame. */
    List<Piece> pieces() {
        // one moment: the physics changes the shapes' values while it holds the robot
        synchronized (robot) {
            return piecesNow();
        }
    }

    private List<Piece> piecesNow() {
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
                int first = out.size();
                double[][] f = mul(parent, translate(s.at));
                if (s.tilt != 0) f = mul(f, rotate("left", s.tilt));   // mounted at an angle on its parent
                switch (s.type) {
                    case "slide": {
                        // the fixed stage, and the moving stage pushed out along the axis
                        // the stage frame's "up" runs along the axis; size= is in robot directions
                        double[][] dirFrame = mul(f, axisFrame(s.axis));
                        double[][] turn = axisFrame(s.axis);
                        double len = along(s, column(turn, 2)), a = along(s, column(turn, 0)), b = along(s, column(turn, 1));
                        out.add(new Piece(s.name, "box", s.color, dirFrame, new double[] {0, 0, len / 2}, a, b, len, 0));
                        out.get(out.size() - 1).part = "base";
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
                        out.get(out.size() - 1).part = "finger";
                        out.get(out.size() - 2).part = "finger";
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
                // For the picture: how it is moving now (the page colours a running roller, a moving part).
                boolean spinner = s.type.equals("roller") || s.type.equals("flywheel");
                int inward = spinner && robot.pieces != null ? robot.pieces.inwardSign(s.motor, s.crservo) : 0;
                for (int k = first; k < out.size(); k++) {
                    Piece pc = out.get(k);
                    pc.run = spinner ? s.rate : 0;
                    // In or out goes by the motor's way of turning (a reversed roller still takes pieces in).
                    double motorWay = s.reversed ? -s.rate : s.rate;
                    pc.flow = inward == 0 || Math.abs(motorWay) < 1e-9 ? 0 : (Math.signum(motorWay) == inward ? 1 : -1);
                    pc.moving = !spinner && s.passive && Math.abs(s.rate) > 1e-6;
                    pc.soft = s.soft;
                }
            }
        }
        // A CAD mesh goes where its piece goes: (the piece now) x (the piece when the CAD was drawn)^-1.
        if (!cadStart.isEmpty()) {
            for (Piece pc : out) {
                double[][] start = cadStart.get(pc.shape + "|" + pc.part);
                if (start == null) continue;
                pc.mesh = meshes.get(pc.shape + "|" + pc.part);
                pc.meshFrame = mul(pc.frame(), invert(start));
                pc.sweep = cadSweep.get(pc.shape + "|" + pc.part);
            }
        }
        return out;
    }

    /**
     * Each CAD mesh next to the box it covers, in that box's own directions (forward, left, up) at the pose
     * the CAD was drawn in: its size, and how far its middle is from the box's middle. A mesh far from its
     * box means cad.axes or cad.origin (or the at=) is wrong; a different size, that size= could match it.
     */
    List<String> cadReport() {
        List<String> out = new ArrayList<>();
        if (cad == null) {
            out.add("no CAD models (robot.model, model=, baseModel=)");
            return out;
        }
        out.add(cad.frame.words);
        for (Map.Entry<String, double[][]> e : cadStart.entrySet()) {
            String[] key = e.getKey().split("\\|");
            SimCad.Mesh m = meshes.get(e.getKey());
            double[][] back = invert(e.getValue());
            double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (double[] h : m.hull) {
                double[] q = apply(back, h);
                for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], q[k]); hi[k] = Math.max(hi[k], q[k]); }
            }
            double[] box = cadBox.get(e.getKey());
            double off = Math.sqrt(Math.pow((lo[0] + hi[0]) / 2, 2) + Math.pow((lo[1] + hi[1]) / 2, 2) + Math.pow((lo[2] + hi[2]) / 2, 2));
            String what = key[0].equals("chassis") ? "robot.model" : "shape." + key[0] + (key[1].equals("base") ? " baseModel" : " model");
            String line = String.format(Locale.US, "%s %s: CAD %.1f x %.1f x %.1f in, its middle %.1f in from the box's; box %.1f x %.1f x %.1f in",
                    what, m.file, hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2], off, box[0], box[1], box[2]);
            if (m.triangles.length / 9 < m.readTriangles) {
                line += String.format(Locale.US, " (drawn with %d of its %d triangles)", m.triangles.length / 9, m.readTriangles);
            }
            double big = Math.max(box[0], Math.max(box[1], box[2]));
            double cadBig = Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2]));
            if (cadBig * 5 < big) line += String.format(Locale.US, " (%.0f times smaller than its box: check cad.units)", big / cadBig);
            else if (cadBig > big * 5) line += String.format(Locale.US, " (%.0f times bigger than its box: check cad.units)", cadBig / big);
            if (off > big / 2 + 1) {
                line += " (FAR from its box: check cad.origin, cad.axes and at="
                        + (key[0].equals("chassis") ? "; for robot.model it is almost always cad.origin)" : ")");
            }
            out.add(line);
        }
        return out;
    }

    // A rigid frame's inverse: the rotation turned back (transposed), and the position undone.
    private static double[][] invert(double[][] f) {
        double[][] r = new double[3][4];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) r[i][j] = f[j][i];
            r[i][3] = -(f[0][i] * f[0][3] + f[1][i] * f[1][3] + f[2][i] * f[2][3]);
        }
        return r;
    }

    /** How big the robot is with these pieces. */
    static Extent extent(List<Piece> pieces) {
        Extent e = new Extent();
        e.back = e.right = Double.MAX_VALUE;
        e.front = e.left = e.top = -Double.MAX_VALUE;
        e.bottom = Double.MAX_VALUE;
        for (Piece p : pieces) {
            double m = p.mesh == null ? 0 : p.mesh.margin;     // a mesh's outside is kept to within this
            for (double[] c : p.points()) {
                e.back = Math.min(e.back, c[0] - m);
                e.front = Math.max(e.front, c[0] + m);
                e.right = Math.min(e.right, c[1] - m);
                e.left = Math.max(e.left, c[1] + m);
                e.top = Math.max(e.top, c[2] + m);
                e.bottom = Math.min(e.bottom, c[2] - m);
            }
        }
        return e;
    }

    /** How big the robot is now. */
    Extent now() {
        return measure(pieces());
    }

    /**
     * How big these pieces make the robot, and never shorter than robot.tallestIn when the robot file
     * gives it (a tall part the shapes do not draw still counts for the size rules).
     */
    Extent measure(List<Piece> pieces) {
        Extent e = extent(pieces);
        if (!Double.isNaN(robot.tallestIn)) e.top = Math.max(e.top, robot.tallestIn);
        return e;
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
     * Turns by deg about an axis. About "up", + turns left, and about "forward", + rolls the left side up
     * (both the right-hand rule). About "left", + RAISES THE FRONT: that is the left-hand sense, chosen so
     * a positive arm angle means "up" (axisFrame depends on it; do not "fix" the sign). "back", "right"
     * and "down" turn the other way.
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
