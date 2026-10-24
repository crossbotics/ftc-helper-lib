package com.crossbotics.ftchelper.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Game pieces and what the robot does with them (needs physics = engine). The field file stages the
 * pieces and names the goals; the robot file says which robot shapes handle them:
 *
 *   collect.NAME = shape=intake, motor=intake, holds=4    a roller (or any part) that takes in a piece within
 *                                                          reach= (0.75 in) while its motor (or crservo=) turns
 *                                                          inward (power after its direction setting above 0.2;
 *                                                          inward=- if negative power takes in). BIOBUZZ: a robot
 *                                                          may hold at most 4.
 *   shoot.NAME = shape=flywheel, angle=40, share=0.5      when the flywheel's feeder fires a shot (motor.NAME =
 *                                                          flywheel, feeder=...), a held piece leaves from this
 *                                                          shape, angle degrees up from the shape's forward, at
 *                                                          share x the wheel's rim speed; spin=1 gives it
 *                                                          backspin as if it rolled along a still hood
 *                                                          (-1 topspin); table=FILE: how the launcher really
 *                                                          throws, with scatter (SimShotTable; tools/launcher);
 *                                                          speed=IN/S: a fixed exit speed (a catapult: make
 *                                                          its motor a flywheel with feeder= itself);
 *                                                          hood=SERVO, hoodAt0=A, hoodAt1=B: the angle follows
 *                                                          a hood servo (A at position 0, B at 1)
 *                                                          scatter=off|realistic|rough: each shot differs a little
 *                                                          (scatterSpeed=%, scatterAngle=deg, scatterAim=deg,
 *                                                          scatterSpin=%; one standard deviation each);
 *                                                          wear=PIECE:%+PIECE:%: extra speed spread per piece type
 *   grab.NAME = shape=claw, servo=claw, closedBelow=0.3   a claw that holds a piece it touches while the servo
 *                                                          is below closedBelow (or above closedAbove=, for a
 *                                                          claw that closes toward 1), and lets it go when it opens
 *
 * The robot starts holding the field file's preload (BIOBUZZ: 4 POLLEN) when it can collect or shoot. A
 * piece whose centre comes into a goal (a FLOWER top) scores. With the season's rules file (SimRules) it drops
 * onto that FLOWER's stack and stays there (where SimScore counts it); without one it is taken off the field.
 * With the rules, a HIVE takes tipSeconds to tip (its CELLs carry their pieces as they turn), and each launched
 * shot gets a verdict from SimCellJudge (clean into an up CELL, or why not); hitRate() rates the robot's spot.
 *
 * Every method locks the robot (the physics step already holds it), so the page's thread and the
 * physics thread never wait for each other in the wrong order.
 */
final class SimPieces {
    private static final class Collect {
        final String name, shape, motor, crservo;
        final String[] shapes;   // shape=a+b+c: pieces come in at any of them
        final double threshold, reach;   // reach: a piece this close to the shape (inches) can be taken in
        final int holds;
        // limits=NECTAR:0+POLLEN:3: the most of a kind (0 = never that kind); a name matches every kind
        // that contains it ("NECTAR": red and blue NECTAR).
        final Map<String, Integer> limits = new LinkedHashMap<>();

        Collect(String name, Map<String, String> s) {
            this.name = name;
            shape = req(s, "shape", "collect." + name);
            shapes = shape.split("\\s*\\+\\s*");
            motor = s.get("motor");
            crservo = s.get("crservo");
            if (motor == null && crservo == null) throw new IllegalArgumentException("Robot file: collect." + name + " needs motor= or crservo=");
            threshold = 0.2 * ("-".equals(s.get("inward")) ? -1 : 1);
            holds = (int) num(s, "holds", 4, "collect." + name);
            reach = num(s, "reach", 0.75, "collect." + name);
            String l = s.get("limits");
            if (l != null && !l.trim().isEmpty()) {
                for (String part : l.split("\\s*\\+\\s*")) {
                    int c = part.lastIndexOf(':');
                    try {
                        limits.put(part.substring(0, c).trim(), Integer.parseInt(part.substring(c + 1).trim()));
                    } catch (RuntimeException e) {
                        throw new IllegalArgumentException("Robot file: collect." + name + ": limits= is PIECE:COUNT+PIECE:COUNT (e.g. limits=NECTAR:0)");
                    }
                }
            }
        }

        /** True if a piece of this kind still fits, with these pieces inside: the total and its kind's limit. */
        boolean canTake(SimField.PieceType t, List<SimField.PieceType> inside) {
            if (inside.size() >= holds) return false;
            for (Map.Entry<String, Integer> e : limits.entrySet()) {
                Map<String, Integer> one = java.util.Collections.singletonMap(e.getKey(), 0);
                if (com.crossbotics.ftchelper.robot.Intake.limitFor(one, t.name) < 0) continue;
                int n = 0;
                for (SimField.PieceType h : inside) if (com.crossbotics.ftchelper.robot.Intake.limitFor(one, h.name) >= 0) n++;
                return n < e.getValue();
            }
            return true;
        }
    }

    private static final class Shoot {
        final String name, shape, flywheel, hood;
        final double angleDeg, share, spin, fixedSpeed, hoodAt0, hoodAt1;
        final SimShotTable table;      // table=: how this launcher really throws (null: angle/share/spin)
        // scatter=off|realistic|rough and its parts (one standard deviation): how much each shot differs
        final String scatter;
        final double scatterSpeedPct, scatterAngleDeg, scatterAimDeg, scatterSpinPct;
        // wear=POLLEN:0.5+NECTAR:8: each piece's extra exit-speed scatter (%) from ball-to-ball wear and size
        final Map<String, Double> wearPct = new LinkedHashMap<>();

        Shoot(String name, Map<String, String> s, java.nio.file.Path dir) {
            this.name = name;
            shape = req(s, "shape", "shoot." + name);
            flywheel = s.get("flywheel");     // which flywheel motor fires it (needed when there are two)
            angleDeg = num(s, "angle", 40, "shoot." + name);
            share = num(s, "share", 0.5, "shoot." + name);
            // the ball's spin, as a share of "rolling": 1 = it leaves turning like a ball rolling along a still hood
            // (speed / radius) with backspin (the wheel under the ball, the hood over it); -1 topspin; 0 none
            spin = num(s, "spin", 0, "shoot." + name);
            // speed=: a fixed exit speed (in/s) instead of share x rim speed: a catapult or a spring launcher
            fixedSpeed = num(s, "speed", Double.NaN, "shoot." + name);
            // hood=SERVO: the shot angle follows that servo, hoodAt0 at position 0 and hoodAt1 at 1 (a straight line)
            hood = s.get("hood");
            hoodAt0 = num(s, "hoodAt0", Double.NaN, "shoot." + name);
            hoodAt1 = num(s, "hoodAt1", Double.NaN, "shoot." + name);
            if (hood != null && (Double.isNaN(hoodAt0) || Double.isNaN(hoodAt1))) {
                throw new IllegalArgumentException("Robot file: shoot." + name + ": hood= needs hoodAt0= and hoodAt1= (the shot angle with the servo at 0 and at 1)");
            }
            String t = s.get("table");
            table = t == null ? null : new SimShotTable(dir == null ? java.nio.file.Paths.get(t) : dir.resolve(t).normalize(),
                    "shoot." + name, name.hashCode());
            scatter = s.getOrDefault("scatter", "off").trim().toLowerCase(java.util.Locale.US);
            double[] preset = scatterPreset(scatter);
            if (preset == null) throw new IllegalArgumentException("Robot file: shoot." + name + ": scatter= is off, realistic or rough");
            scatterSpeedPct = num(s, "scatterSpeed", preset[0], "shoot." + name);
            scatterAngleDeg = num(s, "scatterAngle", preset[1], "shoot." + name);
            scatterAimDeg = num(s, "scatterAim", preset[2], "shoot." + name);
            scatterSpinPct = num(s, "scatterSpin", preset[3], "shoot." + name);
            String w = s.get("wear");
            if (w != null && !w.trim().isEmpty()) {
                for (String part : w.split("\\s*\\+\\s*")) {
                    int c = part.lastIndexOf(':');
                    try {
                        wearPct.put(part.substring(0, c).trim(), Double.parseDouble(part.substring(c + 1).trim()));
                    } catch (RuntimeException e) {
                        throw new IllegalArgumentException("Robot file: shoot." + name + ": wear= is PIECE:PERCENT+PIECE:PERCENT (e.g. wear=POLLEN:0.5+red NECTAR:8)");
                    }
                }
            }
        }
    }

    /**
     * Shot-to-shot scatter presets (one standard deviation each): exit speed %, launch angle degrees, aim
     * (left-right) degrees, spin %. "realistic" is the turret shot simulator's Realistic for a launcher without a
     * feed table (speed 1.5 %, angle 0.35 deg, aim 0.25 deg); "rough" about double. The spin share is a guess.
     * Null for an unknown name.
     */
    static double[] scatterPreset(String name) {
        switch (name) {
            case "off": return new double[] {0, 0, 0, 0};
            case "realistic": return new double[] {1.5, 0.35, 0.25, 10};
            case "rough": return new double[] {3.0, 0.8, 0.6, 20};
            default: return null;
        }
    }

    private static final class Grab {
        final String name, shape, servo;
        final double closedBelow, closedAbove, reach;   // closed when below closedBelow (or above closedAbove)
        SimField.PieceType heldType = null;   // the piece in the claw (kept off the field while held)

        Grab(String name, Map<String, String> s) {
            this.name = name;
            shape = req(s, "shape", "grab." + name);
            servo = req(s, "servo", "grab." + name);
            // A claw closes toward 0 (closedBelow) or toward 1 (closedAbove): give one of the two.
            closedAbove = s.containsKey("closedAbove") ? num(s, "closedAbove", 0.7, "grab." + name) : Double.NaN;
            closedBelow = Double.isNaN(closedAbove) ? num(s, "closedBelow", 0.3, "grab." + name) : Double.NaN;
            reach = num(s, "reach", 0.5, "grab." + name);
        }

        boolean isClosed(double servoPosition) {
            return Double.isNaN(closedAbove) ? servoPosition < closedBelow : servoPosition > closedAbove;
        }
    }

    private final SimRobot robot;
    private final SimWorld world;
    private final List<Collect> collects = new ArrayList<>();

    /**
     * Which way of turning takes pieces in for this motor or CR servo: +1 (positive power), -1
     * (inward=-), or 0 if no collect. line uses it. For the picture (green in, orange out).
     */
    int inwardSign(String motor, String crservo) {
        for (Collect c : collects) {
            boolean same = (motor != null && motor.equals(c.motor)) || (crservo != null && crservo.equals(c.crservo));
            if (same) return c.threshold < 0 ? -1 : 1;
        }
        return 0;
    }
    private final List<Shoot> shoots = new ArrayList<>();
    private final List<Grab> grabs = new ArrayList<>();
    // Pieces inside the robot (collected or preloaded), by type, in the order they came in.
    private final List<SimField.PieceType> stored = new ArrayList<>();
    private final Map<String, Integer> scored = new LinkedHashMap<>();
    private int launched = 0;
    private int leftField = 0;                                      // pieces that went over a wall (taken off)
    private final Map<String, Integer> inCells = new LinkedHashMap<>();   // HIVE name -> pieces in its up CELL now
    // How long each piece has been slow (seconds): a piece counts in a CELL once it has rested there a moment, so a
    // shot slowing at the top of its flight does not tip the HIVE.
    private final Map<SimWorld.Ball, Double> slowFor = new java.util.HashMap<>();
    private double lastStepTime = 0;
    // The field's stacks (pieces inside FLOWERs and CELLs): the types in each now, lowest first. Each stays in
    // its slots from the field file: when the lowest is taken, the others drop one slot.
    private final List<List<SimField.PieceType>> stackNow = new ArrayList<>();
    // How far a stack's lowest piece sits inside its element's block (inches): an intake against the block
    // reaches in that far through the opening.
    private final List<Double> stackDepth = new ArrayList<>();
    // When each stack's next piece has dropped into the lowest slot (physics time, s).
    private final List<Double> stackReady = new ArrayList<>();
    // Whether a stack's lowest slot is open (no solid over the piece: a FLOWER's Retrieval Opening). Its
    // lowest piece is then a real piece the robot can knock out; the next one drops into the slot.
    private final List<Boolean> stackOpen = new ArrayList<>();
    // The real piece in each open stack's lowest slot (null: none there now), and where it rests.
    private final List<SimWorld.Ball> stackBall = new ArrayList<>();
    private final List<double[]> stackRest = new ArrayList<>();
    // The season's rules (null: none; see SimRules). With them a FLOWER is a stack the pieces sit in.
    final SimRules rules;
    // Each goal's stack (the FLOWER it is the top of; -1: none), and each stack's goal (-1: none).
    private int[] goalStack = new int[0], stackGoal = new int[0];
    // The game's score, from what happens here (see SimScore).
    final SimScore score;

    SimPieces(SimRobot robot, SimWorld world, Properties p, Map<String, Map<String, String>> collectLines,
              Map<String, Map<String, String>> shootLines, Map<String, Map<String, String>> grabLines) {
        this.robot = robot;
        this.world = world;
        this.rules = robot.field.rules;
        for (Map.Entry<String, Map<String, String>> e : collectLines.entrySet()) collects.add(new Collect(e.getKey(), e.getValue()));
        for (Map.Entry<String, Map<String, String>> e : shootLines.entrySet()) shoots.add(new Shoot(e.getKey(), e.getValue(), robot.dir));
        for (Map.Entry<String, Map<String, String>> e : grabLines.entrySet()) grabs.add(new Grab(e.getKey(), e.getValue()));
        for (Collect c : collects) {
            for (String sh : c.shapes) check(sh, "collect." + c.name);
            if (c.motor != null && !robot.motors.containsKey(c.motor)) {
                throw new IllegalArgumentException("Robot file: collect." + c.name + ": no motor." + c.motor
                        + (robot.crServos.containsKey(c.motor) ? " (it is a CR servo: write crservo=" + c.motor + ")" : ""));
            }
            if (c.crservo != null && !robot.crServos.containsKey(c.crservo)) throw new IllegalArgumentException("Robot file: collect." + c.name + ": no crservo." + c.crservo);
        }
        for (Shoot s : shoots) {
            check(s.shape, "shoot." + s.name);
            if (s.flywheel != null && !robot.motors.containsKey(s.flywheel)) throw new IllegalArgumentException("Robot file: shoot." + s.name + ": no motor." + s.flywheel);
            if (s.hood != null && !robot.servos.containsKey(s.hood)) throw new IllegalArgumentException("Robot file: shoot." + s.name + ": no servo." + s.hood + " (hood=)");
            if (s.flywheel == null && shoots.size() > 1) throw new IllegalArgumentException("Robot file: shoot." + s.name + ": with two launchers, say which flywheel fires each (flywheel=NAME)");
        }
        for (Grab g : grabs) {
            check(g.shape, "grab." + g.name);
            if (!robot.servos.containsKey(g.servo)) throw new IllegalArgumentException("Robot file: grab." + g.name + ": no servo." + g.servo);
        }
        // physics = simple (or no drivetrain): no game pieces; the collect/shoot/grab lines are checked, then unused.
        for (SimField.Goal g : robot.field.goals) scored.put(g.name, 0);
        // preload = off in the robot file: the robot starts empty (the page's "Preloaded" changes it)
        String pre = p.getProperty("preload", "on").trim().toLowerCase(java.util.Locale.US);
        if (!pre.equals("on") && !pre.equals("off")) throw new IllegalArgumentException("Robot file: preload must be on or off");
        preloaded = pre.equals("on");
        for (SimField.Stack st : robot.field.stacks) {
            stackNow.add(new ArrayList<>());
            double depth = 0;
            if (!st.pieces.isEmpty()) {
                SimField.StagedPiece low = st.pieces.get(0);
                for (SimField.Element e : robot.field.elements) {
                    // How deep the piece sits inside the element's outline: a solid over it counts even when
                    // the piece is in an opening under it (a FLOWER's Retrieval Opening).
                    if (e.hull.size() >= 3 && low.z <= e.zMax && inside(e.hull, low.x, low.y)) {
                        double r = robot.field.pieceTypes.get(low.type).diameterIn / 2;
                        depth = Math.max(depth, edgeDistance(e.hull, low.x, low.y) - r);
                    }
                }
            }
            stackDepth.add(Math.max(0, depth));
            stackReady.add(0.0);
            stackOpen.add(st.take.equals("bottom") && openSlot(st));
            stackBall.add(null);
            stackRest.add(null);
        }
        // A goal right over a stack's lowest piece is that stack's top (a FLOWER: its top opening and its column).
        goalStack = new int[robot.field.goals.size()];
        stackGoal = new int[robot.field.stacks.size()];
        java.util.Arrays.fill(stackGoal, -1);
        for (int g = 0; g < goalStack.length; g++) {
            SimField.Goal goal = robot.field.goals.get(g);
            goalStack[g] = -1;
            for (int i = 0; i < robot.field.stacks.size(); i++) {
                SimField.Stack st = robot.field.stacks.get(i);
                if (st.pieces.isEmpty() || inAHive(st)) continue;
                if (Math.hypot(st.pieces.get(0).x - goal.x, st.pieces.get(0).y - goal.y) < goal.radiusIn + 1) {
                    goalStack[g] = i;
                    stackGoal[i] = g;
                }
            }
        }
        score = new SimScore(robot, this);
        reset();
    }

    private void check(String shape, String line) {
        if (!robot.shapes.shapes.containsKey(shape)) throw new IllegalArgumentException("Robot file: " + line + ": no shape." + shape);
    }

    /** Puts the field's pieces where the match starts them and fills the robot with its preload. */
    void reset() {
        synchronized (robot) {
            if (world == null) return;
            for (SimWorld.Ball b : world.balls()) world.removeBall(b);
            for (Grab g : grabs) g.heldType = null;
            stored.clear();
            lastIn = null;
            // the HIVEs back as the match starts them
            robot.field.resetHives();
            for (SimField.Hive h : robot.field.hives) world.placeHive(h);
            inCells.clear();
            slowFor.clear();
            leftField = 0;
            tipStart.clear();
            tracking.clear();
            verdicts.clear();
            java.util.Arrays.fill(verdictCounts, 0);
            shotRandom = new java.util.Random(8938);
            if (score != null) score.clear();
            for (SimField.StagedPiece sp : robot.field.pieces) {
                world.addBall(robot.field.pieceTypes.get(sp.type), sp.x, sp.y, sp.z, 0, 0, 0);
            }
            for (int i = 0; i < stackNow.size(); i++) {
                stackNow.get(i).clear();
                stackReady.set(i, 0.0);
                SimField.Stack st = robot.field.stacks.get(i);
                if (inAHive(st)) {
                    // pieces staged in a CELL that can tip are real pieces: they rest in it and fall out when it tips
                    for (SimField.StagedPiece sp : st.pieces) world.addBall(robot.field.pieceTypes.get(sp.type), sp.x, sp.y, sp.z, 0, 0, 0);
                    continue;
                }
                for (SimField.StagedPiece sp : st.pieces) stackNow.get(i).add(robot.field.pieceTypes.get(sp.type));
                stackBall.set(i, null);
                if (stackOpen.get(i)) dropIntoSlot(i);
            }
            // The preload (manual 10.3.1: in or on the robot, or on the tiles touching it): inside a robot that
            // can take in or shoot pieces; otherwise on the tiles in a row behind it. None when it is turned off.
            if (!preloaded) {
                // no preload: the robot starts empty
            } else if (!collects.isEmpty() || !shoots.isEmpty()) {
                // Never more than the intake holds, nor a kind it cannot take (collect.NAME = ..., holds=3 takes 3
                // of a 4-piece preload): the rest waits on the tiles behind the robot, touching it, as the rules
                // allow (manual 10.3.1).
                int k = 0;
                for (Map.Entry<String, Integer> e : robot.field.preload.entrySet()) {
                    SimField.PieceType t = robot.field.pieceTypes.get(e.getKey());
                    for (int i = 0; i < e.getValue(); i++) {
                        boolean fits = collects.isEmpty();
                        for (Collect c : collects) if (c.canTake(t, stored)) fits = true;
                        if (fits) {
                            stored.add(t);
                        } else {
                            double[] at = toField(new double[] {-robot.lengthIn / 2 - t.diameterIn / 2 - 0.1,
                                    (k - 1.5) * (t.diameterIn + 0.2), t.diameterIn / 2});
                            world.addBall(t, at[0], at[1], at[2], 0, 0, 0);
                            k++;
                        }
                    }
                }
            } else if (!grabs.isEmpty()) {
                int k = 0;
                for (Map.Entry<String, Integer> e : robot.field.preload.entrySet()) {
                    SimField.PieceType t = robot.field.pieceTypes.get(e.getKey());
                    for (int i = 0; i < e.getValue(); i++, k++) {
                        double[] at = toField(new double[] {-robot.lengthIn / 2 - t.diameterIn / 2 - 0.1,
                                (k - 1.5) * (t.diameterIn + 0.2), t.diameterIn / 2});
                        world.addBall(t, at[0], at[1], at[2], 0, 0, 0);
                    }
                }
            }
            for (String k : scored.keySet()) scored.put(k, 0);
            launched = 0;
        }
    }

    // Whether the robot starts a match holding the field file's preload (the page's "Preloaded", set preload).
    private boolean preloaded = true;

    boolean preloaded() {
        synchronized (robot) {
            return preloaded;
        }
    }

    /**
     * Preloaded or not: from the next reset, and now too: a robot that holds pieces and is set to "not
     * preloaded" is emptied; an empty one set to "preloaded" gets the preload.
     */
    void setPreloaded(boolean on) {
        synchronized (robot) {
            preloaded = on;
            if (world == null || (collects.isEmpty() && shoots.isEmpty())) return;
            if (!on) {
                stored.clear();
            } else if (stored.isEmpty()) {
                for (Map.Entry<String, Integer> e : robot.field.preload.entrySet()) {
                    for (int i = 0; i < e.getValue(); i++) stored.add(robot.field.pieceTypes.get(e.getKey()));
                }
            }
        }
    }

    // A ball (centre p, radius r) that overlaps a plate (centre, three directions, sizes; see SimField.Hive.plates):
    // where it must go to just touch the plate's face on its own side; null if it does not overlap.
    static double[] pushOut(double[] pl, double[] p, double r) {
        double[] d = {p[0] - pl[0], p[1] - pl[1], p[2] - pl[2]};
        double[] q = new double[3];
        double out2 = 0;
        for (int k = 0; k < 3; k++) {
            double c = d[0] * pl[3 + 3 * k] + d[1] * pl[4 + 3 * k] + d[2] * pl[5 + 3 * k];
            q[k] = c;
            double over = Math.abs(c) - pl[12 + k] / 2;
            if (over > 0) out2 += over * over;
        }
        if (out2 >= r * r) return null;
        // out through the thin side (the third direction), to the side the centre is on
        double side = q[2] >= 0 ? 1 : -1, move = side * (pl[14] / 2 + r + 0.05) - q[2];
        return new double[] {p[0] + move * pl[9], p[1] + move * pl[10], p[2] + move * pl[11]};
    }

    // True when nothing solid sits over the stack's lowest piece (it may rest on a thin floor plate, like a
    // FLOWER's lip): the piece is in the open and can be knocked out.
    private boolean openSlot(SimField.Stack st) {
        if (st.pieces.isEmpty() || inAHive(st)) return false;
        SimField.StagedPiece low = st.pieces.get(0);
        double r = robot.field.pieceTypes.get(low.type).diameterIn / 2;
        for (SimField.Element e : robot.field.elements) {
            if (e.hull.size() < 3 || !inside(e.hull, low.x, low.y)) continue;
            double overlap = Math.min(low.z + r, e.zMax) - Math.max(low.z - r, e.zMin);
            if (overlap > 0.5) return false;
        }
        return true;
    }

    // The stack's next piece becomes a real piece in its lowest slot.
    private void dropIntoSlot(int i) {
        List<SimField.PieceType> now = stackNow.get(i);
        if (now.isEmpty() || stuck(now.get(0))) return;     // a NECTAR at the bottom stays in (it cannot come out)
        SimField.StagedPiece slot = robot.field.stacks.get(i).pieces.get(0);
        SimField.PieceType type = now.remove(0);
        double[] at = restingSpot(new double[] {slot.x, slot.y, slot.z}, type.diameterIn / 2);
        stackBall.set(i, world.addBall(type, at[0], at[1], at[2], 0, 0, 0));
        stackRest.set(i, at);
    }

    // Where a piece put at p rests without cutting into the field: on top of a thin floor plate under it
    // (a FLOWER's lip), and moved off a wall beside it (the FLOWER's closed back).
    private double[] restingSpot(double[] p, double r) {
        double[] q = p.clone();
        for (SimField.Element e : robot.field.elements) {
            if (e.hull.size() < 3) continue;
            if (Math.min(q[2] + r, e.zMax) - Math.max(q[2] - r, e.zMin) <= 0) continue;
            if (inside(e.hull, q[0], q[1])) {
                if (e.zMax - (q[2] - r) <= 0.5) q[2] = e.zMax + r + 0.01;
                continue;
            }
            double[] n = closestOnHull(e.hull, q[0], q[1]);
            double d = Math.hypot(q[0] - n[0], q[1] - n[1]);
            if (d < r && d > 1e-9) {
                q[0] += (q[0] - n[0]) / d * (r + 0.01 - d);
                q[1] += (q[1] - n[1]) / d * (r + 0.01 - d);
            }
        }
        return q;
    }

    // The point of an outline's edge nearest to (x, y).
    private static double[] closestOnHull(List<double[]> hull, double x, double y) {
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int k = 0; k < hull.size(); k++) {
            double[] a = hull.get(k), b = hull.get((k + 1) % hull.size());
            double dx = b[0] - a[0], dy = b[1] - a[1], len2 = dx * dx + dy * dy;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / len2));
            double px = a[0] + t * dx, py = a[1] + t * dy, d = Math.hypot(x - px, y - py);
            if (d < bestD) { bestD = d; best = new double[] {px, py}; }
        }
        return best;
    }

    // A stack in a HIVE's up CELL (the staged NECTAR), when that HIVE can tip.
    private boolean inAHive(SimField.Stack st) {
        if (st.pieces.isEmpty()) return false;
        SimField.StagedPiece p = st.pieces.get(0);
        for (SimField.Hive h : robot.field.hives) if (h.inside(h.up, new double[] {p.x, p.y, p.z})) return true;
        return false;
    }

    /** True when shots need a piece (the robot shoots real pieces), false when shots are only counted. */
    boolean shootsPieces() {
        return world != null && !shoots.isEmpty();
    }

    /** A shot from a flywheel: a stored piece leaves from the shoot shape. False if there was none to shoot. */
    boolean shoot(SimMotor flywheel) {
        synchronized (robot) {
            if (!shootsPieces() || stored.isEmpty()) return false;
            Shoot s = shootFor(flywheel);
            if (s == null || pieceOf(s.shape) == null) return false;
            SimField.PieceType type = stored.remove(0);
            Launch l = launch(s, flywheel, type, null, scatterOf(s));
            SimWorld.Ball b = world.addBall(type, l.at[0], l.at[1], l.at[2], l.vel[0], l.vel[1], l.vel[2]);
            if (l.spin != null) world.spin(b, l.spin[0], l.spin[1], l.spin[2]);
            b.ignoreRobotUntil = world.time() + 0.2;
            if (l.shot) {
                launched++;
                track(b);
            }
            return true;
        }
    }

    // The launcher a flywheel fires (its flywheel=, or the only one).
    private Shoot shootFor(SimMotor flywheel) {
        Shoot s = null;
        for (Shoot x : shoots) if (flywheel.name.equals(x.flywheel)) s = x;
        if (s == null && shoots.size() == 1) s = shoots.get(0);
        return s;
    }

    /** Where and how a piece leaves the launcher (field inches, inches per second, spin in radians per second). */
    static final class Launch {
        double[] at, vel, spin;      // spin null: none
        boolean shot = true;         // false: a feed that did not become a shot (it drops out)
    }

    // A shot's start: from the launcher's shape, at its angle (or hood's) and speed, with the shot table's numbers
    // and the scatter (one standard deviation each: speed %, angle deg, aim deg, spin %). random: null = the live
    // shots' own (the table's and this class's); the hit rate passes its own so live shots do not change.
    private Launch launch(Shoot s, SimMotor flywheel, SimField.PieceType type, java.util.Random random, double[] scatter) {
        java.util.Random rnd = random != null ? random : shotRandom;
        SimShapes.Piece piece = pieceOf(s.shape);
        double dia = Math.max(piece.sf, piece.su);
        double rimSpeed = Math.abs(flywheel.shaftSpeed()) / Math.max(1, flywheel.ticksPerRev) * Math.PI * dia;   // in/s
        double angle = s.angleDeg, speed = Double.isNaN(s.fixedSpeed) ? rimSpeed * s.share : s.fixedSpeed, backspin = s.spin * speed / (type.diameterIn / 2);
        double gap = dia / 2 + type.diameterIn / 2 + 0.3;
        double[] exit = null;
        double rpm = flywheel.shaftSpeed() / Math.max(1, flywheel.ticksPerRev) * 60;
        SimShotTable.Shot pick = s.table == null ? null
                : random != null ? s.table.pick(type.name, rpm, random) : s.table.pick(type.name, rpm);
        // A hood: the shot angle from where its servo really is now (not where it was sent).
        double hoodAngle = s.hood == null ? Double.NaN
                : s.hoodAt0 + (s.hoodAt1 - s.hoodAt0) * robot.servos.get(s.hood).actual;
        if (!Double.isNaN(hoodAngle)) angle = hoodAngle;
        Launch out = new Launch();
        if (pick != null) {
            // With a hood, the table's scatter goes around the hood's angle.
            angle = Double.isNaN(hoodAngle) ? pick.angleDeg : hoodAngle + pick.angleDeg - pick.angleMeanDeg;
            speed = pick.shot ? pick.speedIn : 0;          // a feed that did not become a shot drops out
            backspin = pick.shot ? pick.backspin : 0;
            out.shot = pick.shot;
            exit = new double[3];
            for (int k = 0; k < 3; k++) exit[k] = piece.center[k] + pick.exitForward * piece.ef[k] + pick.exitUp * piece.eu[k];
        }
        // Scatter: a shot table already scatters speed, angle and spin; the rest (aim, wear) comes on top.
        double aim = 0;
        if (scatter != null) {
            if (pick == null) {
                speed *= 1 + rnd.nextGaussian() * scatter[0] / 100;
                angle += rnd.nextGaussian() * scatter[1];
                backspin *= 1 + rnd.nextGaussian() * scatter[3] / 100;
            }
            aim = rnd.nextGaussian() * scatter[2];
            Double wear = s.wearPct.get(type.name);
            if (wear != null) speed *= 1 + rnd.nextGaussian() * wear / 100;
        }
        double a = Math.toRadians(angle);
        double[] dir = new double[3];
        for (int k = 0; k < 3; k++) dir[k] = Math.cos(a) * piece.ef[k] + Math.sin(a) * piece.eu[k];
        double[] start = exit != null ? exit
                : new double[] {piece.center[0] + dir[0] * gap, piece.center[1] + dir[1] * gap, piece.center[2] + dir[2] * gap};
        double[] d = toFieldDir(dir), rv = world.robotVel();
        if (aim != 0) {
            // aim scatter: turned about the up line (left-right)
            double y = Math.toRadians(aim), c = Math.cos(y), sn = Math.sin(y);
            d = new double[] {d[0] * c - d[1] * sn, d[0] * sn + d[1] * c, d[2]};
        }
        out.at = toField(start);
        out.vel = new double[] {d[0] * speed + rv[0], d[1] * speed + rv[1], d[2] * speed};
        if (backspin != 0) {
            // backspin turns about (direction x up); its sign along the wheel's axle when the axle is square to the shot
            double[] el = toFieldDir(piece.el);
            double[] axle = {-el[0], -el[1], -el[2]};          // straight up: backspin turns about the shape's right
            double[] back = {d[1], -d[0], 0};                                   // d x up
            double n = Math.hypot(back[0], back[1]);
            double[] ax = n > 1e-6 ? new double[] {back[0] / n, back[1] / n, 0} : axle;
            out.spin = new double[] {ax[0] * backspin, ax[1] * backspin, ax[2] * backspin};
        }
        return out;
    }

    // ---------------------------------------------------------------- shot scatter (the page's switch)

    // The live shots' random numbers (seeded, so a run can be repeated; started again at each reset).
    private java.util.Random shotRandom = new java.util.Random(8938);
    // The page's scatter setting for every launcher: null = each launcher's scatter= from the robot file.
    private String scatterMode = null;

    /** Scatter for every launcher from now on: "off", "realistic", "rough", or "file" (each launcher's own). */
    void setScatter(String mode) {
        synchronized (robot) {
            String m = mode == null ? "file" : mode.trim().toLowerCase(java.util.Locale.US);
            if (!m.equals("file") && scatterPreset(m) == null) throw new IllegalArgumentException("scatter is off, realistic, rough or file");
            scatterMode = m.equals("file") ? null : m;
        }
    }

    /** The page's scatter setting (off, realistic, rough), or null: each launcher's scatter= (the test recorder). */
    String scatterSetting() {
        synchronized (robot) {
            return scatterMode;
        }
    }

    /** The scatter now: the page's setting, or the first launcher's scatter= ("off" without one). */
    String scatter() {
        synchronized (robot) {
            if (scatterMode != null) return scatterMode;
            return shoots.isEmpty() ? "off" : shoots.get(0).scatter;
        }
    }

    // A launcher's scatter numbers now (null: none).
    private double[] scatterOf(Shoot s) {
        if ("off".equals(scatterMode)) return null;
        double[] v = scatterMode != null ? scatterPreset(scatterMode)
                : new double[] {s.scatterSpeedPct, s.scatterAngleDeg, s.scatterAimDeg, s.scatterSpinPct};
        boolean any = v[0] != 0 || v[1] != 0 || v[2] != 0 || v[3] != 0 || !s.wearPct.isEmpty();
        return any ? v : null;
    }

    /** After each physics step: intakes take in pieces, claws hold and let go, goals score. */
    void step() {
        synchronized (robot) {
            if (world == null) return;
            for (SimWorld.Ball b : world.balls()) {
                boolean taken = false;
                double[] bp = world.ballPos(b);
                for (Collect c : collects) {
                    if (!c.canTake(b.type, stored) || !(touchesAny(b, c.shapes) || nearAny(c.shapes, bp, b.type.diameterIn / 2, c.reach))) continue;
                    // the power after the motor's (or CR servo's) direction setting, for both
                    double power = c.motor != null ? robot.motors.get(c.motor).appliedPower
                            : robot.crServos.get(c.crservo)[0] * (robot.crServos.get(c.crservo)[1] == 1 ? -1 : 1);
                    if (c.threshold > 0 ? power >= c.threshold : power <= c.threshold) {
                        collected(b.type);
                        world.removeBall(b);
                        taken = true;
                        break;
                    }
                }
                if (taken) continue;
                for (Grab g : grabs) {
                    if (g.heldType != null || !(b.touchingParts.contains(g.shape) || near(g.shape, bp, b.type.diameterIn / 2, g.reach))) continue;
                    if (g.isClosed(robot.servos.get(g.servo).actual)) {
                        g.heldType = b.type;
                        world.removeBall(b);
                        taken = true;
                        break;
                    }
                }
                if (taken) continue;
                double[] p = world.ballPos(b);
                for (int gi = 0; gi < robot.field.goals.size(); gi++) {
                    SimField.Goal goal = robot.field.goals.get(gi);
                    // In the opening: its centre over the opening, and low enough to be sitting in it or on
                    // its rim (the simulator's FLOWER is a solid block whose backstop stands 1.3 in above the
                    // opening; a piece there would drop in), not going up.
                    // A piece resting on the block with its edge over the opening would roll in: it counts.
                    // A shot flying past (fast) does not.
                    double r = b.type.diameterIn / 2;
                    double[] v = world.ballVel(b);
                    double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                    if (Math.hypot(p[0] - goal.x, p[1] - goal.y) <= goal.radiusIn + r && p[2] <= goal.z + r + 1.3
                            && p[2] >= goal.z - 6 && v[2] <= 1 && speed <= 60) {
                        // With the rules, it drops into the FLOWER's stack (if there is room: a full one leaves it on top).
                        int si = stacking() ? goalStack[gi] : -1;
                        if (si >= 0 && !roomFor(si, b.type)) continue;
                        if (si >= 0) intoFlower(si, b.type);
                        scored.put(goal.name, scored.get(goal.name) + 1);
                        world.removeBall(b);
                        taken = true;
                        break;
                    }
                }
                // over a wall (or through the floor): off the field, counted
                double wall = robot.field.wallHalf + 2;
                if (!taken && (p[2] < -12 || Math.abs(p[0]) > wall || Math.abs(p[1]) > wall)) {
                    world.removeBall(b);
                    leftField++;
                }
            }
            // The HIVEs: what is in each up CELL; enough weight there tips it (the pieces in its CELLs turn with it
            // and, the CELL now facing down, roll out).
            double tNow = world.time(), dt = Math.max(0, tNow - lastStepTime);
            lastStepTime = tNow;
            if (!robot.field.hives.isEmpty()) {
                java.util.Set<SimWorld.Ball> alive = new java.util.HashSet<>(world.balls());
                slowFor.keySet().retainAll(alive);
                for (SimWorld.Ball b : alive) {
                    double[] v = world.ballVel(b);
                    boolean slow = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) < 10;
                    slowFor.put(b, slow ? slowFor.getOrDefault(b, 0.0) + dt : 0.0);
                }
            }
            for (SimField.Hive h : robot.field.hives) {
                // tipping now (the rules' tipSeconds): it turns on, carrying its pieces, until it gets there
                Double t0 = tipStart.get(h);
                if (t0 != null) {
                    turnOn(h, t0);
                    continue;
                }
                double grams = 0;
                int n = 0;
                List<SimWorld.Ball> load = new ArrayList<>();
                for (SimWorld.Ball b : world.balls()) {
                    double[] bp = world.ballPos(b);
                    // counted once it has rested in the CELL for 0.3 s (a shot passing through does not tip it)
                    boolean resting = slowFor.getOrDefault(b, 0.0) >= 0.3;
                    if (h.inside(h.up, bp) && resting) { grams += b.type.massG; n++; }
                    if (h.inside(h.up, bp) || h.inside(-h.up, bp)) load.add(b);
                }
                inCells.put(h.name, n);
                if (grams >= h.tipGrams) {
                    if (rules != null && rules.tipSeconds > 0) {
                        tipStart.put(h, world.time());       // it starts to turn (manual 10.5.1: TIPPED once it gets there)
                        continue;
                    }
                    List<double[]> to = new ArrayList<>();
                    for (SimWorld.Ball b : load) to.add(h.tipped(world.ballPos(b)));
                    robot.field.tip(h);
                    world.placeHive(h);
                    for (int k = 0; k < load.size(); k++) world.moveBall(load.get(k), to.get(k));
                    // a piece next to the HIVE (not in a CELL) that a moved plate now cuts through: out to the side
                    // the plate faces, still
                    for (SimWorld.Ball b : world.balls()) {
                        if (load.contains(b)) continue;
                        double[] bp = world.ballPos(b);
                        double r = b.type.diameterIn / 2;
                        for (int s : new int[] {1, -1}) {
                            for (double[] pl : h.plates(s)) {
                                double[] out = pushOut(pl, bp, r);
                                if (out != null) { bp = out; world.moveBall(b, bp); }
                            }
                        }
                    }
                    inCells.put(h.name, 0);
                    score.tipped(h);
                }
            }
            followShots();
            // A stack's lowest piece: an intake running inward (or a closed claw) at the opening takes it.
            // In an open slot it is a real piece: knocked out (or taken), the next one drops into the slot.
            List<SimWorld.Ball> loose = world.balls();
            for (int i = 0; i < stackNow.size(); i++) {
                List<SimField.PieceType> now = stackNow.get(i);
                SimField.Stack st = robot.field.stacks.get(i);
                if (!st.take.equals("bottom") || st.pieces.isEmpty()) continue;
                SimField.StagedPiece slot = st.pieces.get(0);
                double[] p = {slot.x, slot.y, slot.z};
                // the next one falls one slot (from rest, under gravity) before it can be taken
                double drop = st.pieces.size() > 1 ? st.pieces.get(1).z - slot.z : 0;
                double fall = Math.sqrt(2 * Math.max(0, drop) / 386.1);
                SimWorld.Ball inSlot = null;
                if (stackOpen.get(i)) {
                    SimWorld.Ball sb = stackBall.get(i);
                    if (sb != null) {
                        double[] q = loose.contains(sb) ? world.ballPos(sb) : null;
                        double[] rest = stackRest.get(i);
                        if (q == null || Math.hypot(q[0] - rest[0], q[1] - rest[1]) > sb.type.diameterIn / 2) {
                            stackBall.set(i, null);                     // knocked out or taken in
                            stackReady.set(i, world.time() + fall);
                        } else {
                            inSlot = sb;
                            // it stays put in the opening until the robot or another piece comes near it
                            if (!disturbed(sb, q)) world.hold(sb, stackRest.get(i));
                        }
                    }
                    if (stackBall.get(i) == null && !now.isEmpty() && world.time() >= stackReady.get(i) && slotClear(p, now.get(0))) {
                        dropIntoSlot(i);
                        inSlot = stackBall.get(i);
                    }
                    if (inSlot == null) continue;
                } else if (now.isEmpty() || world.time() < stackReady.get(i) || stuck(now.get(0))) {
                    continue;
                }
                SimField.PieceType type = inSlot != null ? inSlot.type : now.get(0);
                double r = type.diameterIn / 2, depth = stackDepth.get(i);
                boolean taken = false;
                for (Collect c : collects) {
                    if (!c.canTake(type, stored) || !nearAny(c.shapes, p, r, c.reach + depth)) continue;
                    double power = c.motor != null ? robot.motors.get(c.motor).appliedPower
                            : robot.crServos.get(c.crservo)[0] * (robot.crServos.get(c.crservo)[1] == 1 ? -1 : 1);
                    if (c.threshold > 0 ? power >= c.threshold : power <= c.threshold) {
                        collected(takeFromStack(i, inSlot));
                        stackReady.set(i, world.time() + fall);
                        taken = true;
                        break;
                    }
                }
                if (taken) continue;
                for (Grab g : grabs) {
                    if (g.heldType != null || !near(g.shape, p, r, g.reach + depth)) continue;
                    if (g.isClosed(robot.servos.get(g.servo).actual)) {
                        g.heldType = takeFromStack(i, inSlot);
                        stackReady.set(i, world.time() + fall);
                        break;
                    }
                }
            }
            // A claw that opens lets its piece go where it is.
            for (Grab g : grabs) {
                if (g.heldType == null || g.isClosed(robot.servos.get(g.servo).actual)) continue;
                double[] at = toField(heldAt(g));
                double[] rv = world.robotVel();
                SimWorld.Ball b = world.addBall(g.heldType, at[0], at[1], at[2], rv[0], rv[1], 0);
                b.ignoreRobotUntil = world.time() + 0.3;
                g.heldType = null;
            }
            spitOut();
            score.step();
        }
    }


    // An intake running OUT (its motor or CR servo turning the other way past the threshold) pushes the held
    // pieces back out, the newest first (it is nearest the rollers), one every SPIT_EVERY_SEC.
    static final double SPIT_EVERY_SEC = 0.3;
    private double nextSpit = 0;

    private void spitOut() {
        if (stored.isEmpty()) return;
        for (Collect c : collects) {
            double power = c.motor != null ? robot.motors.get(c.motor).appliedPower
                    : robot.crServos.get(c.crservo)[0] * (robot.crServos.get(c.crservo)[1] == 1 ? -1 : 1);
            boolean out = c.threshold > 0 ? power <= -c.threshold : power >= -c.threshold;
            if (!out || world.time() < nextSpit) continue;
            SimShapes.Piece piece = pieceOf(c.shapes[0]);
            double[] at = piece == null ? new double[] {robot.lengthIn / 2, 0, 0} : piece.center.clone();
            // straight out from the robot's middle, past the rollers
            double len = Math.max(1e-6, Math.hypot(at[0], at[1]));
            double ox = at[0] / len, oy = at[1] / len;
            SimField.PieceType t = stored.remove(stored.size() - 1);
            double r = t.diameterIn / 2;
            double reach = piece == null ? 1 : Math.max(piece.sf, 1) / 2;
            double[] f = toField(new double[] {at[0] + ox * (r + reach + 0.5), at[1] + oy * (r + reach + 0.5), Math.max(at[2], r)});
            double[] push = toFieldDir(new double[] {ox * 20, oy * 20, 0});
            double[] rv = world.robotVel();
            SimWorld.Ball b = world.addBall(t, f[0], f[1], f[2], rv[0] + push[0], rv[1] + push[1], 0);
            b.ignoreRobotUntil = world.time() + 0.3;
            nextSpit = world.time() + SPIT_EVERY_SEC;
            return;
        }
    }

    // ---------------------------------------------------------------- FLOWER stacks (with the rules)

    // True when the rules make each FLOWER a stack the pieces sit in (manual 9.7, 10.5.2).
    private boolean stacking() {
        return rules != null && rules.flowerStack;
    }

    // A piece too tall for the Retrieval Opening (BIOBUZZ: NECTAR) cannot leave a FLOWER by the bottom (9.7, G418).
    private boolean stuck(SimField.PieceType t) {
        return rules != null && t.diameterIn > rules.retrievalHeight;
    }

    /**
     * A FLOWER stack now, lowest first: {type, centre height (inches), real?}. The real piece in the lowest slot
     * where it is; the others each resting on the one below (or, the lowest, in or on the lower ring).
     */
    private List<Object[]> column(int i) {
        List<Object[]> out = new ArrayList<>();
        SimWorld.Ball sb = stackBall.get(i);
        double z = Double.NaN, rPrev = 0;
        if (sb != null && world.balls().contains(sb)) {
            z = world.ballPos(sb)[2];
            rPrev = sb.type.diameterIn / 2;
            out.add(new Object[] {sb.type, z, true});
        }
        for (SimField.PieceType t : stackNow.get(i)) {
            double r = t.diameterIn / 2;
            z = Double.isNaN(z) ? lowestZ(i, t) : z + rPrev + r + rules.gap;
            rPrev = r;
            out.add(new Object[] {t, z, false});
        }
        return out;
    }

    // Where a piece sits at the bottom of a FLOWER: on the tiles through the lower ring's hole, or on its rim when it
    // is too wide to reach the tiles (the ring is lowerRingTop tall, its hole lowerRingHole across).
    private double lowestZ(int i, SimField.PieceType t) {
        double r = t.diameterIn / 2, hole = rules.lowerRingHole / 2;
        double onRim = r > hole ? rules.lowerRingTop + Math.sqrt(r * r - hole * hole) : 0;
        return Math.max(robot.field.stacks.get(i).pieces.get(0).z, Math.max(r, onRim));
    }

    // Room in this FLOWER for one more piece: it would sit at least partly below the top opening.
    private boolean roomFor(int i, SimField.PieceType t) {
        List<Object[]> col = column(i);
        double r = t.diameterIn / 2, z;
        if (col.isEmpty()) {
            z = lowestZ(i, t);
        } else {
            Object[] top = col.get(col.size() - 1);
            z = (Double) top[1] + ((SimField.PieceType) top[0]).diameterIn / 2 + r + rules.gap;
        }
        return z - r < robot.field.goals.get(stackGoal[i]).z;
    }

    // A piece came in through a FLOWER's top: it drops onto the stack. NECTAR before the last 60 s of the match
    // is a MAJOR FOUL (G410).
    private void intoFlower(int i, SimField.PieceType t) {
        stackNow.get(i).add(t);
        if (stackOpen.get(i) && stackBall.get(i) == null) {
            // the lowest slot is empty: it falls the whole way first
            double drop = Math.max(0, robot.field.goals.get(stackGoal[i]).z - robot.field.stacks.get(i).pieces.get(0).z);
            stackReady.set(i, Math.max(stackReady.get(i), world.time() + Math.sqrt(2 * drop / 386.1)));
        }
        if (rules.nectarOf(t.name) != null) score.nectarIntoFlower(t, robot.field.goals.get(stackGoal[i]).name);
    }

    /**
     * Each FLOWER now (with the rules): its name and the pieces in it, lowest first: {type name, centre height
     * (inches), in the scoring volume?}. Empty without the rules.
     */
    Map<String, List<Object[]>> flowers() {
        synchronized (robot) {
            Map<String, List<Object[]>> m = new LinkedHashMap<>();
            if (!stacking() || world == null) return m;
            for (int i = 0; i < stackNow.size(); i++) {
                if (stackGoal[i] < 0) continue;
                List<Object[]> l = new ArrayList<>();
                for (Object[] c : column(i)) {
                    SimField.PieceType t = (SimField.PieceType) c[0];
                    double z = (Double) c[1], r = t.diameterIn / 2;
                    // 10.5.2: at least partly between the middle ring and the top ring
                    l.add(new Object[] {t.name, z, z + r > rules.scoringZMin && z - r < rules.scoringZMax});
                }
                m.put(robot.field.goals.get(stackGoal[i]).name, l);
            }
            return m;
        }
    }

    // ---------------------------------------------------------------- HIVE tips that take time

    // HIVEs turning now, and when each started (physics time, s).
    private final Map<SimField.Hive, Double> tipStart = new java.util.HashMap<>();

    // A tipping HIVE turns on: smoothly (slow, fast, slow) over tipSeconds; at the end it is TIPPED (10.5.1).
    private void turnOn(SimField.Hive h, double t0) {
        double secs = rules.tipSeconds, x = Math.min(1, (world.time() - t0) / secs);
        if (x >= 1) {
            h.turnDeg = 0;
            robot.field.tip(h);
            world.placeHive(h);
            tipStart.remove(h);
            inCells.put(h.name, 0);
            score.tipped(h);
            return;
        }
        double deg = h.up * h.tipDeg * (1 - Math.cos(Math.PI * x)) / 2;
        double rate = h.up * h.tipDeg * Math.PI / (2 * secs) * Math.sin(Math.PI * x);
        h.turnDeg = deg;
        world.turnHive(h, deg, rate);
    }

    /** How far each HIVE is through a tip now: 0 at rest, up to 1 (the page can draw it turning). */
    Map<String, Double> tipProgress() {
        synchronized (robot) {
            Map<String, Double> m = new LinkedHashMap<>();
            for (SimField.Hive h : robot.field.hives) {
                Double t0 = tipStart.get(h);
                m.put(h.name, t0 == null || world == null ? 0.0 : Math.min(1, (world.time() - t0) / rules.tipSeconds));
            }
            return m;
        }
    }

    // ---------------------------------------------------------------- each shot's verdict (clean entry or not)

    /** One shot's verdict: which HIVE (or "") and why (SimCellJudge.WORDS). */
    static final class ShotVerdict {
        final int id;
        final double time;
        final String piece, verdict, hive;
        int ball = -1;      // which loose piece it was (SimWorld.Ball id), for the page

        ShotVerdict(int id, double time, String piece, String verdict, String hive) {
            this.id = id; this.time = time; this.piece = piece; this.verdict = verdict; this.hive = hive;
        }
    }

    // A shot in the air: its judge for each HIVE's up CELL.
    private static final class Tracked {
        final int id;
        final double t0;
        final List<SimField.Hive> hives = new ArrayList<>();
        final List<SimCellJudge.Flight> flights = new ArrayList<>();

        Tracked(int id, double t0) {
            this.id = id;
            this.t0 = t0;
        }
    }

    private final Map<SimWorld.Ball, Tracked> tracking = new LinkedHashMap<>();
    private final java.util.ArrayDeque<ShotVerdict> verdicts = new java.util.ArrayDeque<>();
    private final int[] verdictCounts = new int[SimCellJudge.WORDS.length];
    private int shotIds = 0;
    static final int VERDICTS_KEPT = 20;

    /** Judges this piece's flight from now on (each launched shot; tests too): clean into an up CELL, or why not. */
    void track(SimWorld.Ball b) {
        synchronized (robot) {
            if (rules == null || robot.field.hives.isEmpty() || world == null) return;
            Tracked t = new Tracked(++shotIds, world.time());
            double[] p = world.ballPos(b);
            for (SimField.Hive h : robot.field.hives) {
                SimCellJudge j = SimCellJudge.forField(robot.field, h, rules.cleanEntry);
                t.hives.add(h);
                t.flights.add(j.new Flight(new double[] {p[0] * SimCellJudge.IN, p[1] * SimCellJudge.IN, p[2] * SimCellJudge.IN},
                        b.type.diameterIn / 2 * SimCellJudge.IN));
            }
            tracking.put(b, t);
        }
    }

    // Each shot in the air: its judges see where it is now; once all have decided (or it is gone), its verdict.
    private void followShots() {
        if (tracking.isEmpty()) return;
        java.util.Set<SimWorld.Ball> alive = new java.util.HashSet<>(world.balls());
        java.util.Iterator<Map.Entry<SimWorld.Ball, Tracked>> it = tracking.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SimWorld.Ball, Tracked> e = it.next();
            Tracked t = e.getValue();
            boolean live = false;
            if (alive.contains(e.getKey()) && world.time() - t.t0 < 3) {
                double[] p = world.ballPos(e.getKey()), v = world.ballVel(e.getKey());
                double k = SimCellJudge.IN;
                double[] pm = {p[0] * k, p[1] * k, p[2] * k}, vm = {v[0] * k, v[1] * k, v[2] * k};
                for (SimCellJudge.Flight f : t.flights) if (f.step(pm, vm)) live = true;
            }
            if (live) continue;
            // the verdict: clean into any HIVE; else any that it went into (rim, other HIVE, frame first); else a miss
            int best = SimCellJudge.MISS;
            String hive = "";
            for (int i = 0; i < t.flights.size(); i++) {
                int w = t.flights.get(i).verdict();
                if (w != SimCellJudge.MISS && (best == SimCellJudge.MISS || w == SimCellJudge.CLEAN)) {
                    best = w;
                    hive = t.hives.get(i).name;
                }
            }
            verdictCounts[best]++;
            ShotVerdict v = new ShotVerdict(t.id, t.t0, e.getKey().type.name, SimCellJudge.WORDS[best], hive);
            v.ball = e.getKey().id;          // the piece's key on the page (SimView.frame): its trail takes the verdict's colour
            verdicts.addLast(v);
            while (verdicts.size() > VERDICTS_KEPT) verdicts.removeFirst();
            it.remove();
        }
    }

    /** The last shots' verdicts (oldest first; up to 20). */
    List<ShotVerdict> verdicts() {
        synchronized (robot) {
            return new ArrayList<>(verdicts);
        }
    }

    /** How many shots got each verdict since the start (by SimCellJudge.WORDS). */
    Map<String, Integer> verdictCounts() {
        synchronized (robot) {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (int i = 0; i < verdictCounts.length; i++) m.put(SimCellJudge.WORDS[i], verdictCounts[i]);
            return m;
        }
    }

    // ---------------------------------------------------------------- hit rate

    /** N pretend shots from where the robot is now, and how many go cleanly into an up CELL. */
    static final class HitRate {
        String piece = "", hive = "", scatter = "", note = "";
        int n, clean, notLaunched;
        final int[] why = new int[SimCellJudge.WORDS.length];
        double rate, plusMinus;      // the share that went in cleanly, and its 95 % range (+/-)
        String verdict = "";         // Good (80 % or more), Risky (50-80 %), Poor (under 50 %), as the turret simulator
    }

    /**
     * Fires n pretend shots (not on the field) from the robot's launcher as it is now (pose, wheel speed, hood,
     * the robot's own speed), with the scatter (off: "realistic" is used, and the note says so), and judges
     * each one like a real shot. piece: which piece type (null: the next one held, or the preload's).
     * Seeded, so the same robot gives the same answer. Never moves anything.
     */
    HitRate hitRate(int n, String piece, long seed) {
        HitRate out = new HitRate();
        out.n = Math.max(1, Math.min(5000, n));
        List<Launch> launches = new ArrayList<>();
        List<SimCellJudge> judges = new ArrayList<>();
        List<String> names = new ArrayList<>();
        SimField.PieceType type;
        synchronized (robot) {
            if (world == null || shoots.isEmpty()) { out.note = "no launcher (a shoot. line) or no game pieces (physics = engine)"; return out; }
            if (rules == null) { out.note = "no rules file for this field (sim/fields/<name>.rules.json)"; return out; }
            Shoot s = shoots.get(0);
            SimMotor fw = null;
            for (SimMotor m : robot.motors.values()) if (m.kind == SimMotor.Kind.FLYWHEEL && (s.flywheel == null || s.flywheel.equals(m.name))) { fw = m; break; }
            if (fw == null || pieceOf(s.shape) == null) { out.note = "no flywheel motor for the launcher"; return out; }
            if (piece != null && !piece.isEmpty()) type = robot.field.pieceTypes.get(piece);
            else if (!stored.isEmpty()) type = stored.get(0);
            else type = robot.field.preload.isEmpty() ? null : robot.field.pieceTypes.get(robot.field.preload.keySet().iterator().next());
            if (type == null) { out.note = "no such piece type: " + piece; return out; }
            out.piece = type.name;
            double[] sc = scatterOf(s);
            out.scatter = scatter();
            if (sc == null) {
                sc = scatterPreset("realistic");
                out.scatter = "realistic";
                out.note = "scatter is off: rated with realistic scatter";
            }
            if (Double.isNaN(s.fixedSpeed) && Math.abs(fw.shaftSpeed()) < 100) out.note = (out.note.isEmpty() ? "" : out.note + "; ") + "the flywheel is not spinning";
            java.util.Random rnd = new java.util.Random(seed);
            for (int i = 0; i < out.n; i++) launches.add(launch(s, fw, type, rnd, sc));
            for (SimField.Hive h : robot.field.hives) {
                if (tipStart.containsKey(h)) continue;          // turning now: no up CELL to shoot at
                judges.add(SimCellJudge.forField(robot.field, h, rules.cleanEntry));
                names.add(h.name);
            }
        }
        // the judging happens outside the lock (the physics goes on meanwhile)
        double k = SimCellJudge.IN, m = type.massG / 1000, r = type.diameterIn / 2 * k;
        int[][] byHive = new int[judges.size()][SimCellJudge.WORDS.length];
        for (Launch l : launches) {
            if (!l.shot) { out.notLaunched++; out.why[SimCellJudge.MISS]++; continue; }
            double[] p0 = {l.at[0] * k, l.at[1] * k, l.at[2] * k}, v0 = {l.vel[0] * k, l.vel[1] * k, l.vel[2] * k};
            int best = SimCellJudge.MISS;
            for (int j = 0; j < judges.size(); j++) {
                int w = judges.get(j).judge(p0, v0, m, r, l.spin, 1e-3, 2.0);
                byHive[j][w]++;
                if (w != SimCellJudge.MISS && (best == SimCellJudge.MISS || w == SimCellJudge.CLEAN)) best = w;
            }
            out.why[best]++;
        }
        out.clean = out.why[SimCellJudge.CLEAN];
        // the HIVE the shots go to: the one most of them got into (clean or not)
        int most = -1;
        for (int j = 0; j < judges.size(); j++) {
            int in = out.n - byHive[j][SimCellJudge.MISS];
            if (in > 0 && (most < 0 || in > out.n - byHive[most][SimCellJudge.MISS])) most = j;
        }
        out.hive = most < 0 ? "" : names.get(most);
        out.rate = (double) out.clean / out.n;
        out.plusMinus = 1.96 * Math.sqrt(out.rate * (1 - out.rate) / out.n);
        out.verdict = out.rate >= 0.8 ? "Good" : out.rate >= 0.5 ? "Risky" : "Poor";
        return out;
    }

    // ---------------------------------------------------------------- the match clock

    /** Where the match is, for the rules: the period ("AUTO", "TELEOP"; "" = no OpMode running) and the seconds left in it. */
    interface MatchClock {
        String period();

        double secondsLeft();
    }

    /** No match: free play (no fouls, no AUTO). */
    static final MatchClock FREE_PLAY = new MatchClock() {
        @Override public String period() { return ""; }
        @Override public double secondsLeft() { return Double.NaN; }
    };

    /** The match clock (SimEngine sets it from the Driver Station; tests may set their own). */
    volatile MatchClock clock = FREE_PLAY;


    // True if a piece (centre at p, field inches; radius r) is within reach of one of the shape's boxes.
    // Takes a stack's lowest piece: the real one in its slot, or the first staged one.
    private SimField.PieceType takeFromStack(int i, SimWorld.Ball inSlot) {
        if (inSlot == null) return stackNow.get(i).remove(0);
        world.removeBall(inSlot);
        stackBall.set(i, null);
        return inSlot.type;
    }

    /**
     * A spinning part's surface speed at a field point (inches per second, field frame), or null: a roller
     * or flywheel turns about its own left-right line (right hand), as the picture shows it.
     */
    double[] surfaceSpeed(String shape, double[] at) {
        for (SimShapes.Piece pc : robot.shapes.pieces()) {
            if (!pc.shape.equals(shape) || !pc.kind.equals("cyl") || Math.abs(pc.run) < 1e-6) continue;
            double[] q = toRobot(at), d = {q[0] - pc.center[0], q[1] - pc.center[1], q[2] - pc.center[2]};
            double w = pc.run * 2 * Math.PI, ax = pc.el[0] * w, ay = pc.el[1] * w, az = pc.el[2] * w;
            return toFieldDir(new double[] {ay * d[2] - az * d[1], az * d[0] - ax * d[2], ax * d[1] - ay * d[0]});
        }
        return null;
    }

    // A robot part or another piece near this piece (it may be pushed).
    private boolean disturbed(SimWorld.Ball b, double[] q) {
        double r = b.type.diameterIn / 2;
        if (!b.touchingParts.isEmpty()) return true;
        for (SimShapes.Piece piece : robot.shapes.pieces()) if (near(piece.shape, q, r, 0.3)) return true;
        for (SimWorld.Ball o : world.balls()) {
            if (o == b) continue;
            double[] p = world.ballPos(o);
            if (Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]))
                    < r + o.type.diameterIn / 2 + 0.3) return true;
        }
        return false;
    }

    // No real piece (and no robot part) in the way of a piece dropping into this slot.
    private boolean slotClear(double[] p, SimField.PieceType next) {
        for (SimShapes.Piece piece : robot.shapes.pieces()) if (near(piece.shape, p, next.diameterIn / 2, 0)) return false;
        for (SimWorld.Ball b : world.balls()) {
            double[] q = world.ballPos(b);
            double d = Math.sqrt((q[0] - p[0]) * (q[0] - p[0]) + (q[1] - p[1]) * (q[1] - p[1]) + (q[2] - p[2]) * (q[2] - p[2]));
            if (d < (b.type.diameterIn + next.diameterIn) / 2 * 0.95) return false;
        }
        return true;
    }

    private static boolean touchesAny(SimWorld.Ball b, String[] shapes) {
        for (String s : shapes) if (b.touchingParts.contains(s)) return true;
        return false;
    }

    private boolean nearAny(String[] shapes, double[] p, double r, double reach) {
        for (String s : shapes) if (near(s, p, r, reach)) return true;
        return false;
    }

    private boolean near(String shape, double[] p, double r, double reach) {
        double[] q = toRobot(p);
        for (SimShapes.Piece piece : robot.shapes.pieces()) {
            if (!piece.shape.equals(shape)) continue;
            double[] d = {q[0] - piece.center[0], q[1] - piece.center[1], q[2] - piece.center[2]};
            double[][] axes = {piece.ef, piece.el, piece.eu};
            double[] half = {piece.sf / 2, piece.sl / 2, piece.su / 2};
            double out = 0;
            for (int k = 0; k < 3; k++) {
                double a = d[0] * axes[k][0] + d[1] * axes[k][1] + d[2] * axes[k][2];
                double over = Math.abs(a) - half[k];
                if (over > 0) out += over * over;
            }
            if (Math.sqrt(out) - r <= reach) return true;
        }
        return false;
    }

    // Field (inches) to the robot frame (f, l, u).
    private double[] toRobot(double[] p) {
        double h, x, y;
        synchronized (robot) {
            h = Math.toRadians(robot.headingDeg);
            x = robot.x;
            y = robot.y;
        }
        double dx = p[0] - x, dy = p[1] - y;
        // forward = (-sin h, cos h); left = (-cos h, -sin h)
        return new double[] {-Math.sin(h) * dx + Math.cos(h) * dy, -Math.cos(h) * dx - Math.sin(h) * dy, p[2]};
    }

    // Where a claw holds its piece: the middle of its fingers (robot frame).
    private double[] heldAt(Grab g) {
        List<SimShapes.Piece> ps = new ArrayList<>();
        for (SimShapes.Piece p : robot.shapes.pieces()) if (p.shape.equals(g.shape)) ps.add(p);
        if (ps.isEmpty()) return new double[] {0, 0, 0};
        SimShapes.Piece palm = ps.get(0);
        double up = palm.su / 2 + (ps.size() > 1 ? ps.get(1).su / 2 : 1);
        return new double[] {palm.center[0] + palm.eu[0] * up, palm.center[1] + palm.eu[1] * up, palm.center[2] + palm.eu[2] * up};
    }

    private SimShapes.Piece pieceOf(String shape) {
        for (SimShapes.Piece p : robot.shapes.pieces()) if (p.shape.equals(shape)) return p;
        return null;
    }

    // Robot frame (f, l, u inches from the robot's centre on the tiles) to the field.
    private double[] toField(double[] r) {
        double h, x, y;
        synchronized (robot) {
            h = Math.toRadians(robot.headingDeg);
            x = robot.x;
            y = robot.y;
        }
        double[] d = dir(h, r);
        return new double[] {x + d[0], y + d[1], r[2]};
    }

    private double[] toFieldDir(double[] r) {
        double h;
        synchronized (robot) { h = Math.toRadians(robot.headingDeg); }
        return dir(h, r);
    }

    private static double[] dir(double h, double[] r) {
        // forward = (-sin h, cos h); left = (-cos h, -sin h)
        return new double[] {-Math.sin(h) * r[0] - Math.cos(h) * r[1], Math.cos(h) * r[0] - Math.sin(h) * r[1], r[2]};
    }

    // The last piece an intake took in, and when (physics time): a pretend sensor at the intake sees it a moment.
    private SimField.PieceType lastIn = null;
    private double lastInTime = -1;
    /** How long (s) a pretend sensor at the intake sees each piece that comes in. */
    static final double SEEN_AT_INTAKE_SEC = 0.25;

    // An intake took a piece in: it is held, and the intake's pretend sensor sees it for a moment.
    private void collected(SimField.PieceType type) {
        stored.add(type);
        lastIn = type;
        lastInTime = world == null ? 0 : world.time();
    }

    /**
     * The piece a pretend sensor sees at a spot, or null: "ready" = the next to fire (the oldest held),
     * "intake" = a piece that came in during the last {@link #SEEN_AT_INTAKE_SEC} seconds.
     */
    SimField.PieceType atSpot(String spot) {
        synchronized (robot) {
            if (spot.equals("ready")) return stored.isEmpty() ? null : stored.get(0);
            if (world == null || lastIn == null) return null;
            return world.time() - lastInTime <= SEEN_AT_INTAKE_SEC ? lastIn : null;
        }
    }

    // ---------------------------------------------------------------- for the page and scenarios

    /** Pieces held inside the robot, by type. */
    Map<String, Integer> stored() {
        synchronized (robot) {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (SimField.PieceType t : stored) m.merge(t.name, 1, Integer::sum);
            for (Grab g : grabs) if (g.heldType != null) m.merge(g.heldType.name, 1, Integer::sum);
            return m;
        }
    }

    /** How many pieces the robot can hold: its biggest collect. holds= (and one per claw); 0 if it cannot. */
    int capacity() {
        synchronized (robot) {
            int n = 0;
            for (Collect c : collects) n = Math.max(n, c.holds);
            if (n == 0 && !shoots.isEmpty()) for (Map.Entry<String, Integer> e : robot.field.preload.entrySet()) n += e.getValue();
            return n + grabs.size();
        }
    }

    int storedCount() {
        synchronized (robot) {
            int n = stored.size();
            for (Grab g : grabs) if (g.heldType != null) n++;
            return n;
        }
    }

    Map<String, Integer> scored() {
        synchronized (robot) {
            return new LinkedHashMap<>(scored);
        }
    }

    /** Pieces in each HIVE's up CELL now (they score if they are still there at the end). */
    Map<String, Integer> inCells() {
        synchronized (robot) {
            return new LinkedHashMap<>(inCells);
        }
    }

    /** How many times each HIVE has tipped since the start. */
    Map<String, Integer> tips() {
        synchronized (robot) {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (SimField.Hive h : robot.field.hives) m.put(h.name, h.tips);
            return m;
        }
    }

    /** Pieces that went over a wall since the start (taken off the field). */
    int leftField() {
        synchronized (robot) {
            return leftField;
        }
    }

    int launched() {
        synchronized (robot) {
            return launched;
        }
    }

    /** Every piece to draw: {type, x, y, z} (field inches), including pieces held in a claw. */
    List<Object[]> drawn() {
        synchronized (robot) {
            List<Object[]> out = new ArrayList<>();
            if (world == null) return out;
            for (SimWorld.Ball b : world.balls()) {
                double[] p = world.ballPos(b);
                out.add(new Object[] {b.type, p[0], p[1], p[2]});
            }
            for (Grab g : grabs) {
                if (g.heldType == null) continue;
                double[] p = toField(heldAt(g));
                out.add(new Object[] {g.heldType, p[0], p[1], p[2]});
            }
            for (int i = 0; i < stackNow.size(); i++) {
                List<SimField.StagedPiece> slots = robot.field.stacks.get(i).pieces;
                if (stacking() && stackGoal[i] >= 0) {
                    // a FLOWER stack: each piece resting on the one below (the real lowest one is drawn above)
                    SimField.StagedPiece low = slots.get(0);
                    for (Object[] c : column(i)) if (!(Boolean) c[2]) out.add(new Object[] {c[0], low.x, low.y, c[1]});
                    continue;
                }
                int up = stackOpen.get(i) ? 1 : 0;      // an open stack's lowest slot holds a real piece (or none yet)
                for (int k = 0; k < stackNow.get(i).size() && k + up < slots.size(); k++) {
                    SimField.StagedPiece sp = slots.get(k + up);
                    out.add(new Object[] {stackNow.get(i).get(k), sp.x, sp.y, sp.z});
                }
            }
            return out;
        }
    }

    /** How many pieces are still in the field's stacks (inside FLOWERs and CELLs). */
    int inStacks() {
        synchronized (robot) {
            int n = 0;
            for (List<SimField.PieceType> l : stackNow) n += l.size();
            for (SimWorld.Ball b : stackBall) if (b != null) n++;      // still in its slot
            return n;
        }
    }

    // Inside a counterclockwise convex outline?
    private static boolean inside(List<double[]> hull, double x, double y) {
        for (int i = 0; i < hull.size(); i++) {
            double[] a = hull.get(i), b = hull.get((i + 1) % hull.size());
            if ((b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]) < 0) return false;
        }
        return true;
    }

    // From a point inside a convex outline to its nearest edge.
    private static double edgeDistance(List<double[]> hull, double x, double y) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < hull.size(); i++) {
            double[] a = hull.get(i), b = hull.get((i + 1) % hull.size());
            double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
            if (len < 1e-9) continue;
            best = Math.min(best, Math.abs(dx * (y - a[1]) - dy * (x - a[0])) / len);
        }
        return best;
    }

    /** Puts a piece on the field (scenarios and the page). */
    void place(String type, double x, double y, double z) {
        synchronized (robot) {
            SimField.PieceType t = robot.field.pieceTypes.get(type);
            if (t == null) throw new IllegalArgumentException("no piece type \"" + type + "\" in the field file (" + robot.field.pieceTypes.keySet() + ")");
            if (world == null) throw new IllegalArgumentException("game pieces need physics = engine and a drivetrain");
            world.addBall(t, x, y, Math.max(z, t.diameterIn / 2), 0, 0, 0);
        }
    }

    private static String req(Map<String, String> s, String key, String line) {
        String v = s.get(key);
        if (v == null || v.isEmpty()) throw new IllegalArgumentException("Robot file: " + line + " needs " + key + "=");
        return v;
    }

    private static double num(Map<String, String> s, String key, double fallback, String line) {
        if (!s.containsKey(key)) return fallback;
        try {
            return Double.parseDouble(s.get(key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: " + line + ": " + key + "=" + s.get(key) + " needs a number");
        }
    }

    // ---- For the page's smooth picture and its layers (SimView). Read only: nothing here changes the game. ----

    /**
     * Every piece to draw, with a key that stays the same while it is the same piece (so the page can glide it
     * between updates): {key, type number (the field file's order), x, y, z (field inches), qw, qx, qy, qz (how it is
     * turned; 1,0,0,0 for a piece in a claw or a stack)}. Keys: a loose piece its own number (1, 2, ...); a piece in
     * claw g: -1 - g; a piece in stack i, slot k: 1000000 + 1000 i + k.
     */
    List<double[]> drawnForView() {
        synchronized (robot) {
            List<double[]> out = new ArrayList<>();
            if (world == null) return out;
            List<String> names = new ArrayList<>(robot.field.pieceTypes.keySet());
            for (SimWorld.Ball b : world.balls()) {
                double[] p = world.ballPos(b);
                org.ode4j.math.DQuaternionC q = b.body.getQuaternion();
                out.add(new double[] {b.id, names.indexOf(b.type.name), p[0], p[1], p[2], q.get0(), q.get1(), q.get2(), q.get3()});
            }
            for (int g = 0; g < grabs.size(); g++) {
                Grab gr = grabs.get(g);
                if (gr.heldType == null) continue;
                double[] p = toField(heldAt(gr));
                out.add(new double[] {-1 - g, names.indexOf(gr.heldType.name), p[0], p[1], p[2], 1, 0, 0, 0});
            }
            for (int i = 0; i < stackNow.size(); i++) {
                List<SimField.StagedPiece> slots = robot.field.stacks.get(i).pieces;
                int up = stackOpen.get(i) ? 1 : 0;
                for (int k = 0; k < stackNow.get(i).size() && k + up < slots.size(); k++) {
                    SimField.StagedPiece sp = slots.get(k + up);
                    out.add(new double[] {1000000 + 1000 * i + k, names.indexOf(stackNow.get(i).get(k).name), sp.x, sp.y, sp.z, 1, 0, 0, 0});
                }
            }
            return out;
        }
    }

    /** One launcher's next shot as it would leave now, without the shot table's scatter (see shotsNow). */
    static final class ShotNow {
        String launcher, piece, why = "";   // why: empty when it would shoot, else why not ("flywheel stopped")
        double[] at, vel, spin;             // field inches, inches per second, radians per second
        double speedIn, angleDeg, diameterIn, massG, dragCd;
    }

    /**
     * Where each launcher would send its next piece if it fired now (the same numbers shoot() uses, with a shot
     * table's average instead of a random pick): for the page's "shot arc" layer. A launcher with nothing in the
     * robot uses the first piece type its table has, or the field's first piece type.
     */
    List<ShotNow> shotsNow() {
        synchronized (robot) {
            List<ShotNow> out = new ArrayList<>();
            if (world == null) return out;
            for (Shoot s : shoots) {
                ShotNow n = new ShotNow();
                n.launcher = s.name;
                SimMotor flywheel = s.flywheel != null ? robot.motors.get(s.flywheel) : null;
                if (flywheel == null) {
                    // one launcher and no flywheel= named: the robot's flywheel motor fires it (as in shoot())
                    for (SimMotor m : robot.motors.values()) if (flywheel == null && "FLYWHEEL".equals(m.kind.name())) flywheel = m;
                }
                SimShapes.Piece piece = pieceOf(s.shape);
                SimField.PieceType type = !stored.isEmpty() ? stored.get(0)
                        : robot.field.pieceTypes.isEmpty() ? null : robot.field.pieceTypes.values().iterator().next();
                if (piece == null || type == null) continue;
                n.piece = type.name;
                n.diameterIn = type.diameterIn;
                n.massG = type.massG;
                n.dragCd = type.dragCd;
                double dia = Math.max(piece.sf, piece.su);
                double shaft = flywheel == null ? 0 : flywheel.shaftSpeed(), perRev = flywheel == null ? 1 : Math.max(1, flywheel.ticksPerRev);
                double rimSpeed = Math.abs(shaft) / perRev * Math.PI * dia;
                double angle = s.angleDeg, speed = Double.isNaN(s.fixedSpeed) ? rimSpeed * s.share : s.fixedSpeed;
                double backspin = s.spin * speed / (type.diameterIn / 2);
                double[] exit = null;
                SimShotTable.Shot mean = s.table == null ? null : s.table.mean(type.name, shaft / perRev * 60);
                double hoodAngle = s.hood == null || !robot.servos.containsKey(s.hood) ? Double.NaN
                        : s.hoodAt0 + (s.hoodAt1 - s.hoodAt0) * robot.servos.get(s.hood).actual;
                if (!Double.isNaN(hoodAngle)) angle = hoodAngle;
                if (mean != null) {
                    angle = Double.isNaN(hoodAngle) ? mean.angleDeg : hoodAngle;
                    speed = mean.speedIn;
                    backspin = mean.backspin;
                    exit = new double[3];
                    for (int k = 0; k < 3; k++) exit[k] = piece.center[k] + mean.exitForward * piece.ef[k] + mean.exitUp * piece.eu[k];
                }
                double a = Math.toRadians(angle);
                double[] dir = new double[3];
                for (int k = 0; k < 3; k++) dir[k] = Math.cos(a) * piece.ef[k] + Math.sin(a) * piece.eu[k];
                double gap = dia / 2 + type.diameterIn / 2 + 0.3;
                double[] start = exit != null ? exit
                        : new double[] {piece.center[0] + dir[0] * gap, piece.center[1] + dir[1] * gap, piece.center[2] + dir[2] * gap};
                double[] d = toFieldDir(dir), rv = world.robotVel();
                n.at = toField(start);
                n.vel = new double[] {d[0] * speed + rv[0], d[1] * speed + rv[1], d[2] * speed};
                n.speedIn = speed;
                n.angleDeg = angle;
                n.spin = new double[3];
                double[] back = {d[1], -d[0], 0};
                double len = Math.hypot(back[0], back[1]);
                if (backspin != 0 && len > 1e-6) n.spin = new double[] {back[0] / len * backspin, back[1] / len * backspin, 0};
                if (speed < 1) n.why = flywheel == null ? "no flywheel motor found" : "the flywheel is stopped";
                out.add(n);
            }
            return out;
        }
    }
}
