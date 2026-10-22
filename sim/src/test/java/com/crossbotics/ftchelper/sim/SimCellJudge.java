package com.crossbotics.ftchelper.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Did a shot go cleanly into a HIVE's up CELL? A line-by-line port of the turret shot simulator's judge()
 * (Octobots 2026, turret/v7/sim/sim_core.js), so the simulator judges shots by the same rules as the page the
 * team designed its turret with. SimCellJudgeTest checks it against judge() on 600 shared shots.
 *
 * A shot is CLEAN when it comes through the CELL's opening (inside the opening's outline), coming down at
 * least {@code minDescentDeg} (10 degrees: a flatter shot skims the roof), clears the rim by its own radius,
 * and touched neither the frame (or our own CELL from outside) nor the other HIVE before it got there. The
 * other verdicts say why not: MISS, RIM (it touched the rim going in: it may bounce out), OTHER_HIVE, FRAME.
 *
 * Units inside are metres and seconds. The same rules run two ways: {@link #judge} flies a shot itself (air
 * drag and spin lift, as judge() does: one forward step every millisecond), and a {@link Flight} can be fed
 * the positions of a piece the physics engine flies (the verdict the page shows for each real shot).
 */
final class SimCellJudge {
    static final int CLEAN = 0, MISS = 1, RIM = 2, OTHER_HIVE = 3, FRAME = 4;
    /** Words for each verdict, for the page and reports. */
    static final String[] WORDS = {"clean", "miss", "rim", "other HIVE", "frame"};
    static final double IN = 0.0254;

    /** A CELL as a solid block (the cross-section, grown by the frame allowance, along the arm from a0 to a1). */
    static final class Prism {
        final double[] piv, ax, up, ex;
        final double xc, a0, a1;
        final double[][] sec;
        final double bx0, bx1, bs0, bs1;      // the cross-section's box (a quick "too far" test)

        Prism(double[] piv, double[] ax, double[] up, double[] ex, double xc, double a0, double a1, double[][] sec) {
            this.piv = piv; this.ax = ax; this.up = up; this.ex = ex; this.xc = xc; this.a0 = a0; this.a1 = a1; this.sec = sec;
            double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, s0 = Double.POSITIVE_INFINITY, s1 = Double.NEGATIVE_INFINITY;
            for (double[] q : sec) { x0 = Math.min(x0, q[0]); x1 = Math.max(x1, q[0]); s0 = Math.min(s0, q[1]); s1 = Math.max(s1, q[1]); }
            bx0 = x0; bx1 = x1; bs0 = s0; bs1 = s1;
        }

        // as sim_core.js inPrism(): the ball (centre p, radius r) overlaps the block
        boolean hits(double[] p, double r) {
            double[] q = sub(p, piv);
            double a = dot(q, ax), s = dot(q, up), x = dot(q, ex) - xc;
            if (!(a > a0 - r && a < a1 + r && x > bx0 - r && x < bx1 + r && s > bs0 - r && s < bs1 + r)) return false;
            return inPoly(sec, x, s) || polyDist(sec, x, s) < r;
        }
    }

    /** A field part as a straight-up prism (its outline from zMin to zMax, metres): the HIVE frame from the field file. */
    static final class Solid {
        final List<double[]> hull;
        final double zMin, zMax;

        Solid(List<double[]> hull, double zMin, double zMax) {
            this.hull = hull; this.zMin = zMin; this.zMax = zMax;
        }

        boolean hits(double[] p, double r) {
            double dz = Math.max(0, Math.max(zMin - p[2], p[2] - zMax));
            if (dz >= r) return false;
            double dh = inside(hull, p[0], p[1]) ? 0 : polyDist(hull.toArray(new double[0][]), p[0], p[1]);
            return dh * dh + dz * dz < r * r;
        }

        private static boolean inside(List<double[]> h, double x, double y) {
            for (int i = 0; i < h.size(); i++) {
                double[] a = h.get(i), b = h.get((i + 1) % h.size());
                if ((b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]) < 0) return false;
            }
            return true;
        }
    }

    // ---- the target CELL (our HIVE's up CELL)
    final double[] piv, ax, nup, ex, b3, norm;
    final double[][] pent, sec;
    final double[][][] rim;
    final double aIn, aOut, minDescDeg;
    // ---- what a shot may not touch before it is in
    final List<Prism> other = new ArrayList<>(), own = new ArrayList<>();
    final List<double[][]> otherBars = new ArrayList<>(), rods = new ArrayList<>();   // {a, b, {radius}}
    final List<Solid> solids = new ArrayList<>();
    final double frameTube;
    private final double bx0, bx1, bs0, bs1;
    // ---- the air (as judge(): density, drag coefficient, gravity)
    final double rho, cd, g;

    /**
     * The target: our HIVE's pivot, the up CELL's arm direction (toward its opening), across its floor, and along
     * the pivot axis (unit vectors); its opening outline (along ex, along nup from the floor), the floor's offset
     * from the pivot line (s0), where the CELL starts and ends along the arm (aIn, aOut) and the frame allowance
     * (frameTube: the CELL's walls and frame grown by this much; metres). b3: the opening's bottom centre, or null
     * to work it out (pivot + aOut along the arm + s0 across the floor).
     */
    SimCellJudge(double[] piv, double[] ax, double[] nup, double[] ex, double[][] pent, double s0, double aIn, double aOut,
                 double frameTube, double minDescDeg, double rho, double cd, double g, double[] b3) {
        this.piv = piv; this.ax = ax; this.nup = nup; this.ex = ex; this.pent = pent;
        this.aIn = aIn; this.aOut = aOut; this.frameTube = frameTube; this.minDescDeg = minDescDeg;
        this.rho = rho; this.cd = cd; this.g = g;
        // the opening's bottom centre, and the opening's plane faces along the arm
        this.b3 = b3 != null ? b3 : add(piv, add(mul(ax, aOut), mul(nup, s0)));
        norm = ax;
        rim = new double[pent.length][][];
        for (int i = 0; i < pent.length; i++) {
            double[] q = pent[i], q2 = pent[(i + 1) % pent.length];
            rim[i] = new double[][] {add(add(this.b3, mul(ex, q[0])), mul(nup, q[1])), add(add(this.b3, mul(ex, q2[0])), mul(nup, q2[1]))};
        }
        double[][] shifted = new double[pent.length][];
        for (int i = 0; i < pent.length; i++) shifted[i] = new double[] {pent[i][0], s0 + pent[i][1]};
        sec = offsetConvex(shifted, frameTube);
        double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
        for (double[] q : sec) { x0 = Math.min(x0, q[0]); x1 = Math.max(x1, q[0]); y0 = Math.min(y0, q[1]); y1 = Math.max(y1, q[1]); }
        bx0 = x0; bx1 = x1; bs0 = y0; bs1 = y1;
    }

    /** A CELL block on any HIVE (same cross-section as ours): for our down CELL and the other HIVE's CELLs. */
    Prism prism(double[] pivot, double[] cellAx, double[] cellUp, double xc) {
        return new Prism(pivot, cellAx, cellUp, ex, xc, aIn - 0.5 * IN, aOut + 0.5 * IN, sec);
    }

    // as inUpBody(): inside our up CELL's block (from outside it is solid; only the opening lets a shot in)
    boolean inUpBody(double[] p, double r) {
        double[] q = sub(p, piv);
        double a = dot(q, ax), s = dot(q, nup), x = dot(q, ex);
        if (!(a > aIn - 0.5 * IN - r && a < aOut && x > bx0 - r && x < bx1 + r && s > bs0 - r && s < bs1 + r)) return false;
        return inPoly(sec, x, s) || polyDist(sec, x, s) < r;
    }

    /**
     * One shot's verdict as it flies (judge()'s checks, step by step). Feed it the piece's centre and velocity
     * after each step (metres, metres per second) until {@link #step} says it is decided.
     */
    final class Flight {
        final double r;
        int why = -1, blocked = 0;
        double rimMin = 9;
        boolean entered = false, done = false;
        double prev;

        Flight(double[] p0, double r) {
            this.r = r;
            prev = dot(sub(p0, b3), norm);
        }

        /** The checks after one step. Returns true while the shot is still undecided ("live"). */
        boolean step(double[] p, double[] v) {
            if (done) return false;
            double f = dot(sub(p, b3), norm);
            for (double[][] seg : rim) rimMin = Math.min(rimMin, segDist(p, seg[0], seg[1]));
            boolean hitN = false, hitF = false;
            for (Prism pr : other) if (!hitN && pr.hits(p, r)) hitN = true;
            for (double[][] b : otherBars) if (!hitN && segDist(p, b[0], b[1]) < r + frameTube) hitN = true;
            for (double[][] rod : rods) if (!hitF && segDist(p, rod[0], rod[1]) < r + rod[2][0]) hitF = true;
            for (Prism pr : own) if (!hitF && pr.hits(p, r)) hitF = true;
            for (Solid s : solids) if (!hitF && s.hits(p, r)) hitF = true;
            if (!entered) {
                if (hitN && blocked == 0) blocked = OTHER_HIVE;
                if (hitF && blocked == 0) blocked = FRAME;
            }
            boolean cross = prev > 0 && f <= 0;
            boolean inside = false;
            if (cross) {
                double[] q = sub(p, b3);
                inside = inPoly(pent, dot(q, ex), dot(q, nup));
            }
            if (cross && !inside && why < 0) why = MISS;
            double desc = Math.atan2(-v[2], Math.hypot(v[0], v[1])) * 180 / Math.PI;
            if (cross && inside && desc < minDescDeg && why < 0) why = MISS;
            if (cross && inside && desc >= minDescDeg) entered = true;
            // our own up CELL is solid from outside: behind the opening's plane without having come through it
            if (!entered && blocked == 0 && inUpBody(p, r)) blocked = FRAME;
            if (entered && f < -r && why < 0) why = rimMin < r ? RIM : CLEAN;
            boolean live = why < 0;
            live = live && !(v[2] < 0 && p[2] < b3[2] - 0.3) && Math.abs(p[0] - piv[0]) < 6 && Math.abs(p[1] - piv[1]) < 6;
            prev = f;
            if (!live) done = true;
            return live;
        }

        /** The verdict (call when it is no longer live, or when the piece is gone: then undecided = MISS). */
        int verdict() {
            int w = why < 0 ? MISS : why;
            if (w == CLEAN && blocked > 0) w = blocked;
            return w;
        }
    }

    /**
     * Flies one shot (as judge(): drag, gravity and spin lift from each step's starting velocity, one forward
     * step every dt seconds, for at most tmax) and returns its verdict. p0, v0 metres and metres per second;
     * the ball's mass (kg) and radius (m); spin = its turning, radians per second about x, y, z (null: none).
     */
    int judge(double[] p0, double[] v0, double massKg, double radius, double[] spin, double dt, double tmax) {
        double r = radius;
        double cm = 0.5 * rho * Math.PI * r * r * r / massKg;
        double k = 0.5 * rho * cd * Math.PI * r * r / massKg;
        double[] p = p0.clone(), v = v0.clone();
        Flight fl = new Flight(p, r);
        int nsteps = (int) (tmax / dt);
        for (int it = 0; it < nsteps; it++) {
            double sp = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            double m0 = spin != null ? cm * (spin[1] * v[2] - spin[2] * v[1]) : 0;
            double m1 = spin != null ? cm * (spin[2] * v[0] - spin[0] * v[2]) : 0;
            double m2 = spin != null ? cm * (spin[0] * v[1] - spin[1] * v[0]) : 0;
            v[0] += (m0 - k * sp * v[0]) * dt;
            v[1] += (m1 - k * sp * v[1]) * dt;
            v[2] += (m2 - g - k * sp * v[2]) * dt;
            p = new double[] {p[0] + v[0] * dt, p[1] + v[1] * dt, p[2] + v[2] * dt};
            if (!fl.step(p, v)) break;
        }
        return fl.verdict();
    }

    // ---------------------------------------------------------------- small vector and outline helpers

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] sub(double[] a, double[] b) { return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] add(double[] a, double[] b) { return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] mul(double[] a, double k) { return new double[] {a[0] * k, a[1] * k, a[2] * k}; }

    static double segDist(double[] p, double[] a, double[] b) {
        double[] ab = sub(b, a);
        double t = Math.min(1, Math.max(0, dot(sub(p, a), ab) / dot(ab, ab)));
        double[] c = add(a, mul(ab, t));
        double dx = p[0] - c[0], dy = p[1] - c[1], dz = p[2] - c[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // a convex counterclockwise outline grown by d with sharp (mitred) corners, as Shapely's buffer(join_style=mitre)
    static double[][] offsetConvex(double[][] poly, double d) {
        int n = poly.length;
        double[][] lp = new double[n][], ld = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] a = poly[i], b = poly[(i + 1) % n];
            double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
            double nx = dy / len, ny = -dx / len;
            lp[i] = new double[] {a[0] + nx * d, a[1] + ny * d};
            ld[i] = new double[] {dx, dy};
        }
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            int j = (i - 1 + n) % n;
            double den = ld[j][0] * ld[i][1] - ld[j][1] * ld[i][0];
            double t = ((lp[i][0] - lp[j][0]) * ld[i][1] - (lp[i][1] - lp[j][1]) * ld[i][0]) / den;
            out[i] = new double[] {lp[j][0] + ld[j][0] * t, lp[j][1] + ld[j][1] * t};
        }
        return out;
    }

    // strictly inside a convex counterclockwise outline
    static boolean inPoly(double[][] poly, double x, double y) {
        for (int i = 0; i < poly.length; i++) {
            double[] a = poly[i], b = poly[(i + 1) % poly.length];
            if ((b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]) <= 0) return false;
        }
        return true;
    }

    static double polyDist(double[][] poly, double x, double y) {
        double m = Double.POSITIVE_INFINITY;
        for (int i = 0; i < poly.length; i++) {
            double[] a = poly[i], b = poly[(i + 1) % poly.length];
            double dx = b[0] - a[0], dy = b[1] - a[1];
            double t = Math.min(1, Math.max(0, ((x - a[0]) * dx + (y - a[1]) * dy) / (dx * dx + dy * dy)));
            m = Math.min(m, Math.hypot(x - a[0] - dx * t, y - a[1] - dy * t));
        }
        return m;
    }

    // ---------------------------------------------------------------- the simulator's field

    /**
     * The judge for shots at one HIVE's up CELL on the simulator's field, as the field is now: the other HIVE's
     * two CELLs (as it is tipped now), our down CELL and the field elements named as the HIVE frame (the rules
     * file's cleanEntry.frameElements) are what a clean shot must not touch first. Differences from the turret's
     * judge: the frame is the field file's prisms (not its tubes), and the arm between the CELLs is not there.
     */
    static SimCellJudge forField(SimField field, SimField.Hive h, SimRules.CleanEntry rules) {
        double[][] ax = h.cellAxes(h.up);
        double[] piv = mul(h.pivot, IN);
        double[][] pent = new double[h.opening.size()][];
        for (int i = 0; i < pent.length; i++) pent[i] = new double[] {h.opening.get(i)[0] * IN, h.opening.get(i)[1] * IN};
        SimCellJudge j = new SimCellJudge(piv, ax[0], ax[2], ax[1], pent, h.floor * IN, h.aIn * IN, h.aOut * IN,
                rules.frameAllowanceIn * IN, rules.minDescentDeg, SimWorld.AIR_DENSITY, 0.5, 9.81, null);
        double[][] down = h.cellAxes(-h.up);
        j.own.add(j.prism(piv, down[0], down[2], 0));
        for (SimField.Hive o : field.hives) {
            if (o == h) continue;
            double[] op = mul(o.pivot, IN);
            for (int s : new int[] {1, -1}) {
                double[][] oa = o.cellAxes(s);
                // the other HIVE's CELLs have their own pivot: measure across them from it (xc = 0)
                j.other.add(new Prism(op, oa[0], oa[2], oa[1], 0, j.aIn - 0.5 * IN, j.aOut + 0.5 * IN, j.sec));
            }
        }
        for (SimField.Element e : field.elements) {
            if (!rules.frameElements.contains(e.name) || e.hull.size() < 3) continue;
            List<double[]> hull = new ArrayList<>();
            for (double[] q : e.hull) hull.add(new double[] {q[0] * IN, q[1] * IN});
            j.solids.add(new Solid(hull, e.zMin * IN, e.zMax * IN));
        }
        return j;
    }
}
