package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.auto.AutoRoutine;
import com.crossbotics.ftchelper.auto.EasyAuto;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Watches a run of the Built Auto for the page's auto builder: when START was pressed (on the physics
 * clock, so the page can put its "plan" robot in step with the real one), where the robot really was
 * when each step ended, the report, and where it ended. The physics thread calls {@link #tick()} 100
 * times a second; the page reads {@link #json()} in the state.
 *
 * <p>When a step ends, the library's {@code AutoRoutine} adds its report line, so a new line means "a step
 * just ended": the robot's pose then is that step's real end. The routine is private to EasyAuto, so it is
 * read with reflection (a test checks the field is still there).
 */
final class BuiltRun {
    private final SimEngine sim;
    // One run: a new INIT of the Built Auto starts a new one.
    private int id = 0;
    private int initSeen = -1;
    private String phase = "none";            // none, init, running, done
    private double startT = Double.NaN;       // physics seconds at START
    private double endT = Double.NaN;
    private final List<double[]> ends = new ArrayList<>();   // {x, y, heading, seconds since START} per report line
    private List<String> report = Collections.emptyList();
    private double[] end = null;
    private String alliance = "";
    private String robotClass = "";

    BuiltRun(SimEngine sim) {
        this.sim = sim;
    }

    /** Called by the physics thread after each step. */
    synchronized void tick() {
        SimDriverStation ds = sim.ds;
        SimDriverStation.Entry e = ds.current();
        if (e == null || e.type != BuiltAuto.class) {
            // Another program now: a run that was going is over.
            if (phase.equals("running") || phase.equals("init")) finish();
            return;
        }
        if (ds.initCount != initSeen) {
            initSeen = ds.initCount;
            id++;
            phase = "init";
            startT = endT = Double.NaN;
            ends.clear();
            report = Collections.emptyList();
            end = null;
            robotClass = BuiltAuto.robotClass == null ? "" : BuiltAuto.robotClass.getName();
        }
        SimDriverStation.Phase p = ds.phase();
        if (p == SimDriverStation.Phase.RUNNING && phase.equals("init")) {
            phase = "running";
            startT = sim.simSeconds();
            com.crossbotics.ftchelper.match.Alliance a = com.crossbotics.ftchelper.match.MatchMemory.chosen();
            alliance = a == null ? "" : a.name();
        }
        if (phase.equals("running")) {
            List<String> now = report(ds.opMode());
            if (now.size() > ends.size()) {
                double t = sim.simSeconds() - startT;
                SimRobot r = sim.robot;
                while (ends.size() < now.size()) ends.add(new double[] {r.x, r.y, r.headingDeg, t});
            }
            report = now;
            if (p == SimDriverStation.Phase.STOPPED || p == SimDriverStation.Phase.IDLE) finish();
        } else if (phase.equals("init") && (p == SimDriverStation.Phase.STOPPED || p == SimDriverStation.Phase.IDLE)) {
            phase = "done";   // stopped in INIT (an error there): nothing ran
        }
    }

    private void finish() {
        if (phase.equals("running")) {
            List<String> last = com.crossbotics.ftchelper.match.MatchMemory.lastReport();
            if (last.size() >= report.size()) report = last;
            endT = sim.simSeconds() - startT;
            SimRobot r = sim.robot;
            end = new double[] {r.x, r.y, r.headingDeg};
        }
        phase = "done";
    }

    // The routine's report so far (empty if it cannot be read: before START, or another kind of OpMode).
    static List<String> report(OpMode op) {
        if (!(op instanceof EasyAuto)) return Collections.emptyList();
        try {
            java.lang.reflect.Field f = EasyAuto.class.getDeclaredField("routine");
            f.setAccessible(true);
            AutoRoutine routine = (AutoRoutine) f.get(op);
            if (routine == null) return Collections.emptyList();
            // The OpMode's thread adds lines while this reads: copy it, and try again if it changed under us.
            for (int k = 0; k < 3; k++) {
                try {
                    return new ArrayList<>(routine.getReport());
                } catch (java.util.ConcurrentModificationException again) {
                    // a line was being added: read it once more
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not readable: nothing to show
        }
        return Collections.emptyList();
    }

    /** The run for the page, as JSON. Poses are the field's (seen from the red wall), like the robot's. */
    synchronized String json() {
        StringBuilder b = new StringBuilder(256);
        b.append("{\"id\":").append(id).append(",\"phase\":\"").append(phase).append('"');
        b.append(",\"t0\":").append(num(startT)).append(",\"elapsed\":").append(num(endT));
        b.append(",\"alliance\":\"").append(alliance).append("\",\"robotClass\":\"").append(robotClass).append('"');
        b.append(",\"ends\":[");
        for (int i = 0; i < ends.size(); i++) {
            double[] e = ends.get(i);
            if (i > 0) b.append(',');
            b.append('[').append(num(e[0])).append(',').append(num(e[1])).append(',').append(num(e[2])).append(',').append(num(e[3])).append(']');
        }
        b.append("],\"report\":[");
        for (int i = 0; i < report.size(); i++) {
            if (i > 0) b.append(',');
            SimJson.string(b, report.get(i));
        }
        b.append("],\"end\":");
        if (end == null) b.append("null");
        else b.append('[').append(num(end[0])).append(',').append(num(end[1])).append(',').append(num(end[2])).append(']');
        return b.append('}').toString();
    }

    private static String num(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? "null" : String.format(Locale.US, "%.3f", v);
    }
}
