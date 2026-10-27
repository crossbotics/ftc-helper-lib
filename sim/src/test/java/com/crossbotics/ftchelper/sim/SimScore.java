package com.crossbotics.ftchelper.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The match score, worked out from what the simulator sees (the rules file's points; BIOBUZZ: manual 10.5 and
 * table 10-2), so a team sees what a run is worth. Like a referee (10.5): HIVE TIPs count when they happen, LEAVE
 * and AUTO PARK at the end of AUTO, and the pieces in CELLs, FLOWERs and GARDENs and TELEOP PARK only at the end
 * of the match (when TeleOp stops). Until then the page shows those as "at the end, if it ended now". The score
 * starts again at "Back to start", "Reset field", when an autonomous starts, and when a TeleOp starts after a
 * match that ended (a new match).
 *
 * What counts: LEAVE and AUTO PARK at the end of AUTO (the robot is the alliance its program chose; red if none),
 * HIVE TIPs (AUTO or TELEOP by when the tip finished), pieces in each up CELL, FLOWERs (the owner's points for
 * every piece in the scoring volume, the bottom NECTAR bonus), GARDENs, TELEOP PARK, and the MAJOR FOULs the
 * simulator can see (G410: NECTAR into a FLOWER before the last 60 s). Everything else a referee calls is not
 * here. Every method runs with the robot locked (SimPieces holds it).
 */
final class SimScore {
    private final SimRobot robot;
    private final SimPieces pieces;

    /** A HIVE tip: whose HIVE and in which period. */
    private static final class Tip {
        final String alliance, period;

        Tip(String alliance, String period) {
            this.alliance = alliance;
            this.period = period;
        }
    }

    /** A foul the simulator saw: against which alliance, the rule, what, and when (physics time). */
    static final class Foul {
        final String against, rule, what;
        final double time;

        Foul(String against, String rule, String what, double time) {
            this.against = against; this.rule = rule; this.what = what; this.time = time;
        }
    }

    private final List<Tip> tips = new ArrayList<>();
    private final List<Foul> fouls = new ArrayList<>();
    // The end of AUTO: LEAVE and PARK then (null: not yet), and the alliance assessed.
    private Boolean autoLeave = null, autoPark = null;
    private String autoAlliance = "";
    private boolean autoBefore = false;     // AUTO was running at the last step
    // The end of the match (TeleOp stopped): the score then, held (null: not ended), and the time that was left.
    private Map<String, Side> ended = null;
    private double endedLeft = Double.NaN, teleopLeft = Double.NaN;
    private boolean teleopBefore = false;   // TELEOP was running at the last step

    SimScore(SimRobot robot, SimPieces pieces) {
        this.robot = robot;
        this.pieces = pieces;
    }

    /** A new match: no tips, fouls or AUTO results yet. */
    void clear() {
        tips.clear();
        fouls.clear();
        autoLeave = null;
        autoPark = null;
        autoAlliance = "";
        ended = null;
        endedLeft = Double.NaN;
    }

    /** After each physics step: an autonomous starting is a new match; its end is when LEAVE and AUTO PARK count. */
    void step() {
        if (pieces.rules == null) return;
        SimPieces.MatchClock c = pieces.clock;
        boolean autoNow = c.period().equals("AUTO") && c.secondsLeft() > 0;
        if (autoNow && !autoBefore) clear();
        if (!autoNow && autoBefore && autoLeave == null) {
            autoAlliance = ownAlliance();
            autoLeave = !touchesWall();
            SimRules.Alliance a = pieces.rules.alliances.get(autoAlliance);
            autoPark = a != null && inZone(a.loadingZone);
        }
        autoBefore = autoNow;
        boolean teleopNow = c.period().equals("TELEOP") && c.secondsLeft() > 0;
        if (teleopNow && !teleopBefore && ended != null) clear();   // a TeleOp after an ended match: a new match
        if (teleopNow) teleopLeft = c.secondsLeft();
        if (!teleopNow && teleopBefore && ended == null) {
            ended = sides();
            endedLeft = c.period().equals("TELEOP") ? 0 : teleopLeft;
        }
        teleopBefore = teleopNow;
    }

    /**
     * Where the match is: "auto", "afterAuto" (AUTO done, TeleOp not started), "teleop", "ended" (TeleOp stopped:
     * the score is held), or "none" (free play, nothing run since the last reset).
     */
    String matchState() {
        SimPieces.MatchClock c = pieces.clock;
        if (c.period().equals("AUTO") && c.secondsLeft() > 0) return "auto";
        if (c.period().equals("TELEOP") && c.secondsLeft() > 0) return "teleop";
        if (ended != null) return "ended";
        return autoLeave != null ? "afterAuto" : "none";
    }

    /** A HIVE finished tipping. */
    void tipped(SimField.Hive h) {
        SimPieces.MatchClock c = pieces.clock;
        tips.add(new Tip(h.alliance, c.period().equals("AUTO") && c.secondsLeft() > 0 ? "AUTO" : "TELEOP"));
    }

    /** A NECTAR went into a FLOWER (G410: only in the last 60 s; before that, a MAJOR FOUL for each). */
    void nectarIntoFlower(SimField.PieceType t, String flower) {
        SimRules r = pieces.rules;
        SimPieces.MatchClock c = pieces.clock;
        if (r == null || Double.isNaN(r.nectarLastSec)) return;
        String p = c.period();
        boolean early = p.equals("AUTO") || (p.equals("TELEOP") && c.secondsLeft() > r.nectarLastSec);
        if (early) fouls.add(new Foul(ownAlliance(), "G410", t.name + " into " + flower + " with more than "
                + fmt(r.nectarLastSec) + " s left", robot.world == null ? 0 : robot.world.time()));
    }

    // The robot's alliance: its program's choice (the INIT menu), red if none.
    private static String ownAlliance() {
        com.crossbotics.ftchelper.match.Alliance a = com.crossbotics.ftchelper.match.MatchMemory.chosen();
        return a == com.crossbotics.ftchelper.match.Alliance.BLUE ? "blue" : "red";
    }

    // LEAVE (10.5.4): the robot touches the perimeter wall (a corner within 0.25 in of it).
    private boolean touchesWall() {
        for (double[] c : robot.footprint(robot.x, robot.y, robot.headingDeg)) {
            if (Math.abs(c[0]) >= robot.field.wallHalf - 0.25 || Math.abs(c[1]) >= robot.field.wallHalf - 0.25) return true;
        }
        return false;
    }

    // PARK (10.5.4): at least partly in this zone.
    private boolean inZone(String zone) {
        for (SimField.Element e : robot.field.elements) {
            if (e.name.equals(zone) && e.hull.size() >= 3 && SimField.overlap(robot.footprint(robot.x, robot.y, robot.headingDeg), e.hull)) return true;
        }
        return false;
    }

    /** One alliance's points, by kind (AUTO and TELEOP apart where the manual gives both). */
    static final class Side {
        double leave, parkAuto, tipAuto, parkTeleop, tipTeleop, cell, flower, bottomNectar, garden, fouls;
        int tipsAuto, tipsTeleop, inCells, inFlowers, inGarden, flowersOwned;

        double total() {
            return leave + parkAuto + tipAuto + parkTeleop + tipTeleop + cell + flower + bottomNectar + garden + fouls;
        }

        /** AUTO's points (LEAVE, AUTO PARK, AUTO TIPs). */
        double autoTotal() {
            return leave + parkAuto + tipAuto;
        }

        /** The points counted only at the end of the match: CELLs, FLOWERs, the bottom NECTAR bonus, GARDENs, TELEOP PARK. */
        double atEnd() {
            return cell + flower + bottomNectar + garden + parkTeleop;
        }
    }

    /** The score now ("if the match ended now"), by alliance (red, blue). Empty without a rules file. */
    Map<String, Side> sides() {
        Map<String, Side> m = new LinkedHashMap<>();
        SimRules r = pieces.rules;
        if (r == null) return m;
        for (String a : r.alliances.keySet()) m.put(a, new Side());
        // AUTO: LEAVE and PARK at its end
        Side own = m.get(autoAlliance);
        if (own != null && autoLeave != null) {
            own.leave = autoLeave ? r.leave : 0;
            own.parkAuto = autoPark ? r.parkAuto : 0;
        }
        // HIVE TIPs
        for (Tip t : tips) {
            Side s = m.get(t.alliance);
            if (s == null) continue;
            if (t.period.equals("AUTO")) { s.tipsAuto++; s.tipAuto += r.tipAuto; } else { s.tipsTeleop++; s.tipTeleop += r.tipTeleop; }
        }
        // pieces in each up CELL (10.5.1: counted at the end)
        Map<String, Integer> cells = pieces.inCells();
        for (SimField.Hive h : robot.field.hives) {
            Side s = m.get(h.alliance);
            if (s == null) continue;
            int n = cells.getOrDefault(h.name, 0);
            s.inCells += n;
            s.cell += n * r.inCell;
        }
        // FLOWERs (10.5.2): the owner (top-most NECTAR in the scoring volume) gets points for every piece in it; the
        // alliance with the bottom-most NECTAR in it gets the bonus
        for (List<Object[]> f : pieces.flowers().values()) {
            String owner = null, bottom = null;
            int n = 0;
            for (Object[] c : f) {
                if (!(Boolean) c[2]) continue;
                n++;
                String a = r.nectarOf((String) c[0]);
                if (a == null) continue;
                if (bottom == null) bottom = a;
                owner = a;
            }
            if (owner != null && m.containsKey(owner)) {
                m.get(owner).flower += n * r.inOwnedFlower;
                m.get(owner).inFlowers += n;
                m.get(owner).flowersOwned++;
            }
            if (bottom != null && m.containsKey(bottom)) m.get(bottom).bottomNectar += r.bottomNectar;
        }
        // GARDENs (10.5.3): pieces at least partly in the zone, for the GARDEN's alliance
        for (Map.Entry<String, SimRules.Alliance> e : r.alliances.entrySet()) {
            SimField.Element g = element(e.getValue().garden);
            if (g == null || robot.world == null) continue;
            for (SimWorld.Ball b : robot.world.balls()) {
                double[] p = robot.world.ballPos(b);
                if (p[2] < r.gardenMaxZ && touches(g.hull, p[0], p[1], b.type.diameterIn / 2)) {
                    m.get(e.getKey()).inGarden++;
                    m.get(e.getKey()).garden += r.inGarden;
                }
            }
        }
        // TELEOP PARK (assessed at the end of the match: here, where the robot is now)
        String a = ownAlliance();
        if (m.containsKey(a) && r.alliances.get(a) != null && inZone(r.alliances.get(a).loadingZone)) m.get(a).parkTeleop = r.parkTeleop;
        // MAJOR FOULs: their points go to the other alliance
        for (Foul f : fouls) {
            for (Map.Entry<String, Side> e : m.entrySet()) if (!e.getKey().equals(f.against)) e.getValue().fouls += r.majorFoul;
        }
        return m;
    }

    List<Foul> fouls() {
        return new ArrayList<>(fouls);
    }

    private SimField.Element element(String name) {
        for (SimField.Element e : robot.field.elements) if (e.name.equals(name) && e.hull.size() >= 3) return e;
        return null;
    }

    // A circle (centre x, y; radius r) at least partly in a convex outline.
    private static boolean touches(List<double[]> hull, double x, double y, double r) {
        boolean in = true;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < hull.size(); i++) {
            double[] a = hull.get(i), b = hull.get((i + 1) % hull.size());
            double dx = b[0] - a[0], dy = b[1] - a[1];
            if (dx * (y - a[1]) - dy * (x - a[0]) < 0) in = false;
            double t = Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / (dx * dx + dy * dy)));
            best = Math.min(best, Math.hypot(x - a[0] - dx * t, y - a[1] - dy * t));
        }
        return in || best < r;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ---------------------------------------------------------------- for the page

    /**
     * The game's state for the page, as one JSON object (SimJson puts it under "game"): the clock, the score by
     * alliance, each FLOWER's stack and owner, each HIVE's tip progress, the last shots' verdicts, the scatter and
     * the fouls. {"rules": false} without a rules file.
     */
    String json() {
        StringBuilder b = new StringBuilder(1024);
        SimRules r = pieces.rules;
        if (r == null) return "{\"rules\":false}";
        SimPieces.MatchClock c = pieces.clock;
        b.append("{\"rules\":true,\"clock\":{\"period\":").append(q(c.period())).append(",\"secondsLeft\":").append(num(c.secondsLeft()))
                .append(",\"nectarAllowed\":").append(!c.period().equals("AUTO") && !(c.period().equals("TELEOP") && c.secondsLeft() > r.nectarLastSec)).append('}');
        b.append(",\"score\":{");
        int i = 0;
        String state = matchState();
        boolean over = state.equals("ended");
        for (Map.Entry<String, Side> e : (over ? ended : sides()).entrySet()) {
            Side s = e.getValue();
            if (i++ > 0) b.append(',');
            // counted: what a referee has counted so far (all of it once the match ended); atEnd: the end-of-match
            // points (if it ended now, until it has)
            b.append(q(e.getKey())).append(":{\"total\":").append(num(s.total()))
                    .append(",\"counted\":").append(num(over ? s.total() : s.total() - s.atEnd())).append(",\"atEnd\":").append(num(s.atEnd()))
                    .append(",\"autoTotal\":").append(num(s.autoTotal())).append(",\"teleopTotal\":").append(num(s.tipTeleop + s.atEnd()))
                    .append(",\"rp\":{\"swarm\":").append(num(s.leave + s.parkAuto + s.parkTeleop)).append(",\"tips\":").append(s.tipsAuto + s.tipsTeleop).append('}')
                    .append(",\"auto\":{\"leave\":").append(num(s.leave)).append(",\"park\":").append(num(s.parkAuto))
                    .append(",\"tips\":").append(s.tipsAuto).append(",\"tipPoints\":").append(num(s.tipAuto)).append('}')
                    .append(",\"teleop\":{\"park\":").append(num(s.parkTeleop)).append(",\"tips\":").append(s.tipsTeleop)
                    .append(",\"tipPoints\":").append(num(s.tipTeleop)).append(",\"inCells\":").append(s.inCells)
                    .append(",\"cellPoints\":").append(num(s.cell)).append(",\"flowersOwned\":").append(s.flowersOwned)
                    .append(",\"inFlowers\":").append(s.inFlowers).append(",\"flowerPoints\":").append(num(s.flower))
                    .append(",\"bottomNectar\":").append(num(s.bottomNectar)).append(",\"inGarden\":").append(s.inGarden)
                    .append(",\"gardenPoints\":").append(num(s.garden)).append('}')
                    .append(",\"foulPoints\":").append(num(s.fouls)).append('}');
        }
        b.append("},\"match\":{\"state\":").append(q(state)).append(",\"endedLeft\":").append(num(over ? endedLeft : Double.NaN)).append('}');
        b.append(",\"rp\":{\"swarm\":").append(num(r.rpSwarm)).append(",\"pollinator1\":").append(num(r.rpPollinator1))
                .append(",\"pollinator2\":").append(num(r.rpPollinator2)).append('}');
        b.append(",\"flowers\":[");
        i = 0;
        for (Map.Entry<String, List<Object[]>> e : pieces.flowers().entrySet()) {
            if (i++ > 0) b.append(',');
            String owner = "";
            for (Object[] p : e.getValue()) {
                String a = r.nectarOf((String) p[0]);
                if ((Boolean) p[2] && a != null) owner = a;
            }
            b.append("{\"name\":").append(q(e.getKey())).append(",\"owner\":").append(q(owner)).append(",\"pieces\":[");
            int k = 0;
            for (Object[] p : e.getValue()) {
                if (k++ > 0) b.append(',');
                b.append("{\"type\":").append(q((String) p[0])).append(",\"z\":").append(num((Double) p[1]))
                        .append(",\"scoring\":").append(p[2]).append('}');
            }
            b.append("]}");
        }
        b.append("],\"hives\":[");
        i = 0;
        for (Map.Entry<String, Double> e : pieces.tipProgress().entrySet()) {
            SimField.Hive h = null;
            for (SimField.Hive x : robot.field.hives) if (x.name.equals(e.getKey())) h = x;
            if (i++ > 0) b.append(',');
            b.append("{\"name\":").append(q(e.getKey())).append(",\"tipping\":").append(e.getValue() > 0)
                    .append(",\"progress\":").append(num(e.getValue())).append(",\"turnDeg\":").append(num(h == null ? 0 : h.turnDeg))
                    .append(",\"tipSeconds\":").append(num(r.tipSeconds)).append('}');
        }
        b.append("],\"shots\":[");
        i = 0;
        for (SimPieces.ShotVerdict v : pieces.verdicts()) {
            if (i++ > 0) b.append(',');
            b.append("{\"id\":").append(v.id).append(",\"t\":").append(num(v.time)).append(",\"piece\":").append(q(v.piece))
                    .append(",\"verdict\":").append(q(v.verdict)).append(",\"hive\":").append(q(v.hive))
                    .append(",\"ball\":").append(v.ball).append('}');
        }
        b.append("],\"verdictCounts\":{");
        i = 0;
        for (Map.Entry<String, Integer> e : pieces.verdictCounts().entrySet()) {
            if (i++ > 0) b.append(',');
            b.append(q(e.getKey())).append(':').append(e.getValue());
        }
        b.append("},\"scatter\":").append(q(pieces.scatter())).append(",\"fouls\":[");
        i = 0;
        for (Foul f : fouls) {
            if (i++ > 0) b.append(',');
            b.append("{\"against\":").append(q(f.against)).append(",\"rule\":").append(q(f.rule)).append(",\"what\":").append(q(f.what))
                    .append(",\"t\":").append(num(f.time)).append('}');
        }
        b.append("]}");
        return b.toString();
    }

    /** A hit rate as JSON (the /hit-rate answer). */
    static String json(SimPieces.HitRate h) {
        StringBuilder b = new StringBuilder(256);
        b.append("{\"n\":").append(h.n).append(",\"clean\":").append(h.clean).append(",\"rate\":").append(num(h.rate))
                .append(",\"plusMinus\":").append(num(h.plusMinus)).append(",\"verdict\":").append(q(h.verdict))
                .append(",\"piece\":").append(q(h.piece)).append(",\"hive\":").append(q(h.hive)).append(",\"scatter\":").append(q(h.scatter))
                .append(",\"notLaunched\":").append(h.notLaunched).append(",\"why\":{");
        for (int i = 0; i < h.why.length; i++) {
            if (i > 0) b.append(',');
            b.append(q(SimCellJudge.WORDS[i])).append(':').append(h.why[i]);
        }
        b.append("},\"note\":").append(q(h.note)).append('}');
        return b.toString();
    }

    private static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "null";
        return String.format(java.util.Locale.US, "%.3f", v).replaceAll("\\.?0+$", "");
    }

    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
            else b.append(ch);
        }
        return b.append('"').toString();
    }
}
