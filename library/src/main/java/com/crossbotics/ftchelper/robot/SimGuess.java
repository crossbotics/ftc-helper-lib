package com.crossbotics.ftchelper.robot;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the simulator robot file's guessed shapes go, for the parts with no {@code looks(...)}. It starts
 * from what the code knows: the chassis ({@code chassisMm}, else 18 x 18 x 4 in, the simulator's guess),
 * the robot's height ({@code heightMm}), the parts on the robot (a claw goes on the end of the lift, the
 * arm or the wrist; a flywheel rides the turret) and the parts catalog. The places are a typical FTC robot's:
 * the intake at the front, low (pieces it drives onto touch its roller), a lift at the back, a turret on
 * top in the middle. Every guess fits the 18 in start cube and, at full reach, 18 x 24 x 29 in, and the
 * guesses do not overlap at the start. Every line stays marked "TODO measure".
 *
 * <p>Only for the simulator robot file: nothing here changes the robot.
 */
final class SimGuess {
    /** The robot's size rules, inches: the start cube, and how tall at full reach. */
    static final double CUBE = 18, TALL = 29, LONG = 24;
    // A little room from each limit, inches.
    private static final double GAP = 0.25;

    /** A typical intake roller: 2 in across, 14 in long (a goBILDA intake's width). */
    static final double ROLLER_DIA = 2, ROLLER_LEN = 14;
    /** The common flywheel wheel the launcher's guess is drawn as (its size from the parts catalog). */
    static final String FLYWHEEL_PART = "gobilda-hogback-96";
    static final double TURRET_SIZE = 6, TURRET_HEIGHT = 2;
    static final double SLIDE_DEPTH = 2, SLIDE_WIDTH = 8;
    static final double ARM_THICK = 1.5;
    static final double WRIST_LEN = 2.5, WRIST_THICK = 1.5;
    static final double CLAW_DEPTH = 2, CLAW_FINGER = 0.5, CLAW_HEIGHT = 2;
    // How far a part riding a joint can reach from where it sits (a claw: its palm and fingers; a wrist with a claw).
    private static final double CLAW_REACH = Math.hypot(CLAW_DEPTH / 2, CLAW_HEIGHT) + 0.05;

    /** One guessed shape: what it rides on (null = the chassis), where, how big, and its note. */
    static final class Spot {
        final String parent;
        final double[] at, size;
        final String note;

        Spot(String parent, double[] at, double[] size, String note) {
            this.parent = parent;
            this.at = at;
            this.size = size;
            this.note = note;
        }

        /** "shape.NAME = TYPE, on=P, at=F,L,U, size=FxLxU, DRIVER   # NOTE". */
        String line(String name, String type, String driver) {
            StringBuilder sb = new StringBuilder("shape.").append(name).append(" = ").append(type);
            if (parent != null) sb.append(", on=").append(parent);
            sb.append(", at=").append(Shape.n(at[0])).append(',').append(Shape.n(at[1])).append(',').append(Shape.n(at[2]));
            sb.append(", size=").append(Shape.n(size[0])).append('x').append(Shape.n(size[1])).append('x').append(Shape.n(size[2]));
            if (driver != null && !driver.isEmpty()) sb.append(", ").append(driver);
            return sb.append("   # ").append(note).toString();
        }
    }

    /** The chassis, inches: length (front to back), width, height. */
    final double length, width, height;
    /** How tall the robot may be at the start: 18 in, or less with {@code heightMm}. */
    final double startTop;
    /** A lift's guessed travel, inches (0 when there is none). */
    private final Map<Module<?>, Double> travel = new IdentityHashMap<>();
    private final Map<Module<?>, Spot> spots = new IdentityHashMap<>();

    /**
     * @param chassisIn {length, width, height} from {@code chassisMm}, or null
     * @param heightIn  the robot's height from {@code heightMm}, or NaN
     * @param modules   the robot's parts (their children are looked at too)
     */
    SimGuess(double[] chassisIn, double heightIn, List<Module<?>> modules) {
        length = chassisIn != null ? chassisIn[0] : CUBE;
        width = chassisIn != null ? chassisIn[1] : CUBE;
        height = chassisIn != null ? chassisIn[2] : 4;
        startTop = Double.isNaN(heightIn) ? CUBE : Math.max(height + 2, Math.min(CUBE, heightIn));
        plan(all(modules));
    }

    private static List<Module<?>> all(List<Module<?>> modules) {
        List<Module<?>> out = new ArrayList<>();
        for (Module<?> m : modules) {
            out.addAll(m.children());
            out.add(m);
        }
        return out;
    }

    /** The guessed shape for this part (it has no looks(...)), or null if the part draws none. */
    Spot spot(Module<?> m) {
        return spots.get(m);
    }

    /** A lift's guessed travel, inches. */
    double travelIn(Module<?> lift) {
        Double t = travel.get(lift);
        return t == null ? 1 : t;
    }

    // ----------------------------- the plan -----------------------------

    private static <T> T first(List<Module<?>> all, Class<T> kind) {
        for (Module<?> m : all) if (kind.isInstance(m)) return kind.cast(m);
        return null;
    }

    private static boolean guessed(Module<?> m) {
        return m != null && m.looks.isEmpty();
    }

    // Where a shape riding on this part's first shape sits: the end of an arm, the top of a slide's
    // moving stage (axis up). Null when the part's shape is of another kind (the rider goes elsewhere).
    private double[] endOf(Module<?> carrier) {
        if (carrier == null) return null;
        if (guessed(carrier)) {
            Spot s = spots.get(carrier);
            if (s == null) return null;
            if (carrier instanceof Lift) return new double[] {s.size[0] / 2, 0, s.size[2]};
            return new double[] {s.size[0], 0, 0};
        }
        Shape s = carrier.looks.get(0);
        double[] size = s.sizeInOrNull();
        if (size == null) return null;
        if (s.type.equals("arm") && (s.axisOrNull() == null || s.axisOrNull().equals("left") || s.axisOrNull().equals("right"))) {
            return new double[] {size[0], 0, 0};
        }
        if (s.type.equals("slide") && (s.axisOrNull() == null || s.axisOrNull().equals("up"))) return new double[] {size[0] / 2, 0, size[2]};
        return null;
    }

    private void plan(List<Module<?>> all) {
        double L = length, W = width, H = height;
        double front = L / 2, back = -L / 2;
        Lift lift = first(all, Lift.class);
        Arm arm = first(all, Arm.class);
        Wrist wrist = first(all, Wrist.class);
        Claw claw = first(all, Claw.class);
        int turrets = 0;
        Turret turret = null;
        for (Module<?> m : all) if (m instanceof Turret) { turret = (Turret) m; turrets++; }
        if (turrets != 1) turret = null;

        // What rides what (only guessed riders: a part with looks(...) says where it is itself).
        boolean wristOnArm = guessed(wrist) && arm != null && (guessed(arm) || endOf(arm) != null);
        boolean wristOnLift = guessed(wrist) && !wristOnArm && lift != null && (guessed(lift) || endOf(lift) != null);
        boolean clawOnWrist = guessed(claw) && wrist != null && (guessed(wrist) || endOf(wrist) != null);
        boolean clawOnArm = guessed(claw) && !clawOnWrist && arm != null && (guessed(arm) || endOf(arm) != null);
        boolean clawOnLift = guessed(claw) && !clawOnWrist && !clawOnArm && lift != null && (guessed(lift) || endOf(lift) != null);
        // How far the guessed riders reach from where they sit on a joint.
        double wristReach = WRIST_LEN + (clawOnWrist ? CLAW_REACH : WRIST_THICK / 2);
        double armRiders = wristOnArm ? wristReach : clawOnArm ? CLAW_REACH : 0;
        double liftRiders = wristOnLift ? wristReach : clawOnLift ? CLAW_HEIGHT : 0;

        // The middle of the top: a turret, a flywheel on it (or alone).
        double middleTop = H;
        Parts.Wheel fw = Parts.wheel(FLYWHEEL_PART);
        double flyDia = fw.diameterMm / 25.4, flyLen = fw.widthMm / 25.4;
        if (guessed(turret)) {
            double size = Math.min(TURRET_SIZE, Math.min(L, W) - 2 * SLIDE_DEPTH - 1);
            spots.put(turret, new Spot(null, new double[] {0, 0, H}, new double[] {size, size, TURRET_HEIGHT},
                    "TODO measure: place and size (in the middle, on top)"));
            middleTop = H + TURRET_HEIGHT;
        }
        List<Launcher> launchers = new ArrayList<>();
        for (Module<?> m : all) if (m instanceof Launcher && guessed(m)) launchers.add((Launcher) m);
        String flyNote = "size from the catalog (" + FLYWHEEL_PART + ", a common flywheel wheel), TODO measure: place and size";
        for (int i = 0; i < launchers.size(); i++) {
            double left = (i - (launchers.size() - 1) / 2.0) * (flyLen + 0.5);
            double[] size = {flyDia, flyLen, flyDia};
            if (turret != null) {
                // On the turret, on top of it: it turns with the turret (the shots go where it points).
                double[] tsize = guessed(turret) ? spots.get(turret).size : turret.looks.get(0).sizeInOrNull();
                double up = (tsize != null ? tsize[2] : 0) + flyDia / 2;
                spots.put(launchers.get(i), new Spot(turret.firstShapeName(), new double[] {0, left, up}, size, flyNote + " (on the turret)"));
                if (guessed(turret)) middleTop = Math.max(middleTop, H + up + flyDia / 2);
            } else {
                spots.put(launchers.get(i), new Spot(null, new double[] {0, left, H + flyDia / 2}, size, flyNote + " (in the middle, on top)"));
                middleTop = Math.max(middleTop, H + flyDia);
            }
        }

        // Intakes: a roller across the front, low; a second one at the back.
        int n = 0;
        for (Module<?> m : all) {
            if (!(m instanceof Intake) || !guessed(m)) continue;
            double r = ROLLER_DIA / 2;
            double len = Math.max(4, Math.min(ROLLER_LEN, W - 2));
            double f = n % 2 == 0 ? front - r : back + r;
            double up = Math.max(2, r + 0.1) + (n / 2) * (ROLLER_DIA + 0.5);
            spots.put(m, new Spot(null, new double[] {f, 0, up}, new double[] {ROLLER_DIA, len, ROLLER_DIA},
                    "TODO measure: place and size (a " + Shape.n(ROLLER_DIA) + " in roller, " + Shape.n(len) + " in long, at the "
                            + (n % 2 == 0 ? "front" : "back") + ", low: it takes in pieces the robot drives onto)"));
            n++;
        }

        // A lift at the back: its stage as tall as the start allows (with what rides on it), its travel up
        // to 29 in at the top.
        if (guessed(lift)) {
            double len = clamp(startTop - H - liftRiders - GAP, 3, 14);
            double t = Math.max(1, TALL - GAP - H - len - liftRiders);
            travel.put(lift, t);
            spots.put(lift, new Spot(null, new double[] {back + SLIDE_DEPTH / 2, 0, H},
                    new double[] {SLIDE_DEPTH, Math.min(SLIDE_WIDTH, W - 2), len},
                    "TODO measure: place, size and travel (at the back; made to fit the 18 in start cube and 29 in at the top)"));
        }

        // An arm: its hinge in the middle, above the parts there; as long as fits the start cube and 29 in
        // over its whole range (Tunables min to max, degrees), with what rides on its end.
        if (guessed(arm)) {
            double[] range = arm.simRange();
            double lo = Math.min(range[0], range[1]), hi = Math.max(range[0], range[1]);
            double hingeUp = middleTop + ARM_THICK / 2 + GAP;
            double len = 2;
            for (double l = 9; l >= 2; l -= 0.25) {
                if (armFits(l, armRiders, hingeUp, lo, hi)) {
                    len = l;
                    break;
                }
            }
            spots.put(arm, new Spot(null, new double[] {0, 0, hingeUp}, new double[] {len, ARM_THICK, ARM_THICK},
                    "TODO measure: place and length (the hinge in the middle; made to fit the 18 in start cube and 29 in)"));
        }

        // A wrist: on the end of the arm, else on top of the lift, else at the front on top.
        if (guessed(wrist)) {
            double[] size = {WRIST_LEN, WRIST_THICK, WRIST_THICK};
            if (wristOnArm) {
                spots.put(wrist, new Spot(arm.firstShapeName(), endOf(arm), size, "TODO measure: place and size (on the end of the arm)"));
            } else if (wristOnLift) {
                double[] e = endOf(lift);
                spots.put(wrist, new Spot(lift.firstShapeName(), e, size, "TODO measure: place and size (on top of the lift)"));
            } else {
                double up = Math.max(Math.max(H, middleTop) + WRIST_THICK / 2 + GAP, wristReach + 0.2);
                spots.put(wrist, new Spot(null, new double[] {front - wristReach - GAP, 0, up}, size,
                        "TODO measure: place and size (at the front, on top)"));
            }
        }

        // A claw: on the end of the wrist, the arm or the lift, else at the front on top.
        if (guessed(claw)) {
            double[] size = {CLAW_DEPTH, CLAW_FINGER, CLAW_HEIGHT};
            if (clawOnWrist) {
                spots.put(claw, new Spot(wrist.firstShapeName(), endOf(wrist), size, "TODO measure: place and size (on the end of the wrist)"));
            } else if (clawOnArm) {
                spots.put(claw, new Spot(arm.firstShapeName(), endOf(arm), size, "TODO measure: place and size (on the end of the arm)"));
            } else if (clawOnLift) {
                double[] e = endOf(lift).clone();
                e[0] += CLAW_DEPTH / 2;
                spots.put(claw, new Spot(lift.firstShapeName(), e, size, "TODO measure: place and size (on top of the lift)"));
            } else {
                spots.put(claw, new Spot(null, new double[] {front - CLAW_DEPTH / 2 - GAP, 0, H}, size,
                        "TODO measure: place and size (at the front, on top)"));
            }
        }
        spares(all, middleTop);
    }

    // A second (third...) lift, arm, wrist, claw or turret: the same kind of place as the first one's on
    // the chassis, moved to the side (left, then right, then further), with nothing riding on it.
    private void spares(List<Module<?>> all, double middleTop) {
        double L = length, W = width, H = height;
        int k = 0;
        for (Module<?> m : all) {
            if (!guessed(m) || spots.containsKey(m)) continue;
            boolean known = m instanceof Lift || m instanceof Arm || m instanceof Wrist || m instanceof Claw || m instanceof Turret;
            if (!known) continue;
            k++;
            double side = (k % 2 == 1 ? 1 : -1) * ((k + 1) / 2);   // 1, -1, 2, -2...
            String note = "TODO measure: place and size (one more " + m.name() + ", to the side)";
            if (m instanceof Lift) {
                double len = clamp(startTop - H - GAP, 3, 14);
                travel.put(m, Math.max(1, TALL - GAP - H - len));
                double left = clamp(side * (W / 2 - 1 - GAP), -(W / 2 - 1), W / 2 - 1);
                spots.put(m, new Spot(null, new double[] {-L / 2 + SLIDE_DEPTH / 2, left, H}, new double[] {SLIDE_DEPTH, 2, len}, note));
            } else if (m instanceof Arm) {
                spots.put(m, new Spot(null, new double[] {0, clamp(side * 3, -(W / 2 - 1), W / 2 - 1), middleTop + ARM_THICK / 2 + GAP},
                        new double[] {2, ARM_THICK, ARM_THICK}, note));
            } else if (m instanceof Wrist) {
                spots.put(m, new Spot(null, new double[] {L / 2 - WRIST_LEN - 1, clamp(side * 3, -(W / 2 - 1), W / 2 - 1),
                        Math.max(H, middleTop) + WRIST_LEN + GAP}, new double[] {WRIST_LEN, WRIST_THICK, WRIST_THICK}, note));
            } else if (m instanceof Claw) {
                spots.put(m, new Spot(null, new double[] {L / 2 - CLAW_DEPTH / 2 - GAP, clamp(side * 4.5, -(W / 2 - 2), W / 2 - 2), H},
                        new double[] {CLAW_DEPTH, CLAW_FINGER, CLAW_HEIGHT}, note));
            } else {
                double size = 3;
                spots.put(m, new Spot(null, new double[] {0, clamp(side * (TURRET_SIZE / 2 + size / 2 + 0.5), -(W / 2 - 2), W / 2 - 2), H},
                        new double[] {size, size, TURRET_HEIGHT}, note));
            }
        }
    }

    // True if an arm of this length (hinge in the middle at hingeUp, riders reaching r from its end) fits
    // the start cube at its first angle, and 18 x 24 x 29 in and the tiles over its whole range.
    private boolean armFits(double len, double r, double hingeUp, double lo, double hi) {
        double edge = r + ARM_THICK / 2 + GAP;
        for (double a = lo; ; a = Math.min(hi, a + 2.5)) {
            double x = len * Math.cos(Math.toRadians(a)), z = hingeUp + len * Math.sin(Math.toRadians(a));
            double fr = Math.max(length / 2, x + edge), bk = Math.min(-length / 2, x - edge);
            if (fr - bk > LONG || z + edge > TALL || z - edge < 0) return false;
            if (a == lo && (fr - bk > CUBE || z + edge > startTop)) return false;
            if (a >= hi) return true;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
