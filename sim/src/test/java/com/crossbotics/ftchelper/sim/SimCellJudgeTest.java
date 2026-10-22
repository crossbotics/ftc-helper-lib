package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The CELL judge (SimCellJudge) against the turret shot simulator's judge() (sim_core.js), on the same 600 shots:
 * 120 of each verdict, from spots all around the HIVE, both balls, the other HIVE both ways, some with backspin.
 * The fixture (resources/judge/turret-judge-shots.json) holds the turret's geometry and judge()'s verdicts;
 * make_judge_fixture.mjs next to it makes it again. Like the turret's own validate.mjs, across languages.
 */
public class SimCellJudgeTest {
    private static final double C30 = Math.cos(Math.PI / 6), S30 = 0.5;

    @Test public void agreesWithTheTurretSimulatorsJudgeOnEveryShot() throws Exception {
        Map<String, Object> root = SimJsonReader.object(SimJsonReader.parse(read("/judge/turret-judge-shots.json")), "fixture");
        Map<String, Object> g = SimJsonReader.object(root.get("geom"), "geom");
        List<Object> shots = SimJsonReader.array(root.get("shots"), "shots");
        int[] seen = new int[5];
        List<String> wrong = new ArrayList<>();
        for (Object o : shots) {
            Map<String, Object> s = SimJsonReader.object(o, "shot");
            String ball = SimJsonReader.string(s, "ball", ""), state = SimJsonReader.string(s, "state", "");
            Map<String, Object> bm = SimJsonReader.object(SimJsonReader.object(g.get("balls"), "balls").get(ball), ball);
            double m = SimJsonReader.number(bm, "m", 0), r = SimJsonReader.number(bm, "r", 0);
            double[] p0 = vec(s.get("p0")), v0 = vec(s.get("v0"));
            double rpm = SimJsonReader.number(s, "spinRpm", 0);
            double[] spin = null;
            if (rpm != 0) {
                // as sim_core.js spinVec(): about the level line square to the launch, the top of the ball moving back
                double len = Math.hypot(v0[0], v0[1]), w = rpm * 2 * Math.PI / 60;
                if (len == 0) len = 1;
                spin = new double[] {v0[1] / len * w, -v0[0] / len * w, 0};
            }
            int want = (int) SimJsonReader.number(s, "why", -1);
            int got = turretJudge(g, state).judge(p0, v0, m, r, spin, 1e-3, 2.0);
            seen[want]++;
            if (got != want) wrong.add(ball + "/" + state + " judge() " + want + ", Java " + got);
        }
        for (int k = 0; k < 5; k++) assertTrue("the shots cover verdict " + SimCellJudge.WORDS[k], seen[k] >= 100);
        assertTrue(wrong.size() + " of " + shots.size() + " shots judged differently: " + wrong, wrong.isEmpty());
    }

    // The judge with the turret simulator's HIVE (its frame: x along the pivot axis, u out toward our up CELL, z up;
    // our pivot at (0, 0, pivot_z)) and the other HIVE tipped the same way as ours ("up") or the other way ("down").
    private static SimCellJudge turretJudge(Map<String, Object> g, String state) {
        double pz = SimJsonReader.number(g, "pivot_z", 0), ft = SimJsonReader.number(g, "frame_tube", 0);
        double[] piv = {0, 0, pz}, ax = {0, C30, S30}, nup = {0, -S30, C30}, ex = {1, 0, 0};
        List<Object> pl = SimJsonReader.array(g.get("pent"), "pent");
        double[][] pent = new double[pl.size()][];
        for (int i = 0; i < pent.length; i++) pent[i] = vec(pl.get(i));
        SimCellJudge j = new SimCellJudge(piv, ax, nup, ex, pent, SimJsonReader.number(g, "s0", 0),
                SimJsonReader.number(g, "a_in", 0), SimJsonReader.number(g, "a_out", 0), ft,
                SimJsonReader.number(g, "min_desc", 10), SimJsonReader.number(g, "rho", 1.2), SimJsonReader.number(g, "cd", 0.5),
                SimJsonReader.number(g, "g", 9.81), vec(g.get("B")));
        double nx = SimJsonReader.number(g, "neigh_x", 0);
        // our down CELL (as sim_core.js own), the other HIVE's two CELLs and its bar, the frame tubes
        j.own.add(j.prism(piv, new double[] {0, -C30, -S30}, nup, 0));
        double[] nax = state.equals("down") ? new double[] {0, C30, -S30} : ax, nupN = state.equals("down") ? new double[] {0, S30, C30} : nup;
        j.other.add(j.prism(piv, nax, nupN, nx));
        j.other.add(j.prism(piv, new double[] {-nax[0], -nax[1], -nax[2]}, nupN, nx));
        for (Object bar : SimJsonReader.array(SimJsonReader.object(g.get("neigh_bars"), "bars").get(state), "bars")) {
            List<Object> ab = SimJsonReader.array(bar, "bar");
            j.otherBars.add(new double[][] {vec(ab.get(0)), vec(ab.get(1))});
        }
        for (Object rod : SimJsonReader.array(g.get("rods"), "rods")) {
            List<Object> ab = SimJsonReader.array(rod, "rod");
            j.rods.add(new double[][] {vec(ab.get(0)), vec(ab.get(1)), {((Number) ab.get(2)).doubleValue()}});
        }
        return j;
    }

    // On the simulator's own field: a shot dropped straight into the red up CELL's opening is clean; the same shot
    // moved 30 in sideways misses; a shot along the floor never gets in.
    @Test public void judgesShotsOnTheSimulatorsField() throws Exception {
        SimField f = SimField.load("biobuzz-2026");
        SimField.Hive red = f.hives.get(0);
        SimCellJudge j = SimCellJudge.forField(f, red, f.rules.cleanEntry);
        // 6 in outside the opening's middle (along the arm), 6 in up from its floor, going in along the arm (the arm
        // rises 30 deg toward the opening, so in along it is 30 deg down)
        double[][] ax = red.cellAxes(red.up);
        double in = SimCellJudge.IN, a = red.aOut + 6, hh = red.floor + 6;
        double[] p0 = new double[3], v0 = new double[3];
        for (int k = 0; k < 3; k++) {
            p0[k] = (red.pivot[k] + a * ax[0][k] + hh * ax[2][k]) * in;
            v0[k] = -ax[0][k] * 100 * in;
        }
        assertEquals("into the opening, going down", SimCellJudge.CLEAN, j.judge(p0, v0, 0.025, 1.4 * in, null, 1e-3, 2.0));
        double[] aside = {p0[0], p0[1] + 30 * in, p0[2]};
        assertEquals("beside the opening", SimCellJudge.MISS, j.judge(aside, v0, 0.025, 1.4 * in, null, 1e-3, 2.0));
        assertEquals("rolling on the tiles", SimCellJudge.MISS,
                j.judge(new double[] {1.2, -0.3, 0.04}, new double[] {-2, 0, 0}, 0.025, 1.4 * in, null, 1e-3, 2.0));
    }

    private static double[] vec(Object o) {
        List<Object> l = SimJsonReader.array(o, "vector");
        double[] v = new double[l.size()];
        for (int i = 0; i < v.length; i++) v[i] = ((Number) l.get(i)).doubleValue();
        return v;
    }

    private static String read(String name) throws Exception {
        try (InputStream in = SimCellJudgeTest.class.getResourceAsStream(name)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }
}
