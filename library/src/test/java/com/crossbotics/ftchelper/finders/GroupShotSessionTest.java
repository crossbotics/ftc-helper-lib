package com.crossbotics.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.finders.GroupShotResults.Ball;
import com.crossbotics.ftchelper.finders.GroupShotSession.Candidate;
import com.crossbotics.ftchelper.finders.GroupShotSession.GroupRun;
import com.crossbotics.ftchelper.finders.GroupShotSession.Invalid;
import com.crossbotics.ftchelper.finders.GroupShotSession.Kind;
import com.crossbotics.ftchelper.finders.GroupShotSession.Stage;
import com.crossbotics.ftchelper.finders.GroupShotSession.Step;
import com.crossbotics.ftchelper.finders.GroupShotSession.Variant;
import com.crossbotics.ftchelper.launcher.ContinuousFire;
import com.crossbotics.ftchelper.launcher.Flywheel.SpeedControl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;

/**
 * The Group Shot Tuner's plan, entry, advice and winner, on made-up shots and groups (no robot): what comes
 * next, that every reason a group does not count leaves it unscored and runs it again, and that the advice and
 * the winner follow the rules on docs/tuning/14-group-shot-tuner.md.
 */
public class GroupShotSessionTest {

    private static final FlywheelSetting PID = new FlywheelSetting(SpeedControl.PID, 50, 0, 0, 12.08, 0, 0.0022, 200, 50, 25, 1);
    private static final FlywheelSetting BANG = PID.withBang(0.4, 0.0022, 150, 40, 30, 1);
    private static final FlywheelSetting TODAY = PID.withPid(10, 12.08);

    // Best PID (back in 0.30 s), best bang-bang (0.25 s), and today's settings.
    private static GroupShotSession.Config config() {
        GroupShotSession.Config c = new GroupShotSession.Config();
        c.spots = new double[] {27, 36, 45};
        c.dropRpm = 230;
        c.readyBandRpm = 60;
        c.today = TODAY;
        c.candidates.add(new Candidate("pid", PID, "best PID", 0.30, 40, 0.5));
        c.candidates.add(new Candidate("bang", BANG, "best bang-bang", 0.25, 60, 0.6));
        return c;
    }

    private static GroupShotSession session() {
        GroupShotSession.Config c = config();
        return new GroupShotSession(c, new GroupShotResults(c.setup()));
    }

    // ---- helpers: one shot or group, the way the OpMode drives the session ----

    private static double t = 0;

    // A, the countdown, FIRING.
    private static void countdown(GroupShotSession s) {
        Step next = s.plan();
        double spot = s.config().spots[next.spot];
        if (s.placedSpot() != next.spot) s.pressA(spot, "");      // place it
        s.pressA(spot + 0.4, "");                                   // shoot
        assertEquals(s.refusal(), Stage.COUNTDOWN, s.stage());
        t += 3;
        s.update(t);
        assertEquals(Stage.FIRING, s.stage());
    }

    private static void check(GroupShotSession s, Ball b) {
        assertEquals(Kind.CHECK, s.plan().kind);
        countdown(s);
        s.singleDone(true, "");
        assertEquals(Stage.ENTRY, s.stage());
        while (s.cursor() != b.ordinal()) s.entryMove(1);
        s.entrySave();
    }

    private static void passChecks(GroupShotSession s) {
        for (int i = 0; i < 4; i++) check(s, Ball.SCORED);
    }

    private static GroupRun good(boolean sag) {
        GroupRun r = new GroupRun();
        r.fed = true;
        r.end = ContinuousFire.End.MAX_TIME;
        r.aimedAtStart = true;
        r.worstAimDeg = 0.8;
        r.aimLimitDeg = 2.5;
        r.targetRpm = 2615;
        r.lowRpm = sag ? 2615 - 300 : 2615 - 250;
        r.feedSec = 3;
        return r;
    }

    // One group that counts, entered as these balls (scored, short, long, left, right).
    private static Variant group(GroupShotSession s, GroupRun run, int... balls) {
        Step next = s.plan();
        assertEquals(Kind.GROUP, next.kind);
        countdown(s);
        s.groupDone(run);
        assertEquals(Stage.ENTRY, s.stage());
        for (int i = 0; i < balls.length; i++) {
            while (s.cursor() != i) s.entryMove(1);
            for (int n = 0; n < balls[i]; n++) s.entryChange(1);
        }
        s.entrySave();
        assertEquals(s.refusal(), "", s.refusal());
        return next.variant;
    }

    // Plays every group of the round at every spot, the balls chosen by the setting's key and the spot.
    private interface Balls { int[] of(Variant v, int spot); }

    private static void playRound(GroupShotSession s, Balls balls, java.util.function.Predicate<Variant> sag) {
        int round = s.plan().round;
        while (s.plan().kind != Kind.FINISHED && s.plan().round == round) {
            Step n = s.plan();
            if (n.kind == Kind.CHECK) {
                check(s, Ball.SCORED);
                continue;
            }
            group(s, good(sag.test(n.variant)), balls.of(n.variant, n.spot));
        }
    }

    // ---------------------------------------------------------------- the plan

    @Test public void theCheckShotsComeFirstThenTheGroupsTakeTurns() {
        GroupShotSession s = session();
        Step first = s.plan();
        assertEquals(Kind.CHECK, first.kind);
        assertEquals(0, first.spot);
        assertTrue("the robot is placed first", s.needsPlace());
        passChecks(s);
        List<String> order = new ArrayList<>();
        for (int i = 0; i < 6; i++) order.add(group(s, good(false), 4).key());
        assertEquals(Arrays.asList("pid:0:100", "bang:0:100", "pid:0:100", "bang:0:100", "pid:0:100", "bang:0:100"), order);
        Step next = s.plan();
        assertEquals("then the next spot's check shots", Kind.CHECK, next.kind);
        assertEquals(1, next.spot);
        assertTrue("placed again at the new spot", s.needsPlace());
    }

    @Test public void checkShotsThatFailStopThatSpotUntilTheyAreShotAgain() {
        GroupShotSession s = session();
        check(s, Ball.SCORED);
        check(s, Ball.SHORT);
        check(s, Ball.SCORED);
        check(s, Ball.LEFT);
        assertEquals("2 of 4 is not enough", Kind.CHECK_FAILED, s.plan().kind);
        assertTrue(s.note(), s.note().contains("only 2 of 4 scored"));
        s.pressA(27, "");                 // A = shoot 4 again
        assertEquals(Kind.CHECK, s.plan().kind);
        assertEquals("a new set of 4", 0, s.checkSetNow(0)[0]);
        for (int i = 0; i < 3; i++) check(s, Ball.SCORED);
        check(s, Ball.LONG);
        assertEquals("3 of 4 passes", Kind.GROUP, s.plan().kind);
    }

    @Test public void noWinnerInTheFlywheelTunerTestsOnlyWhatThereIs() {
        GroupShotSession.Config c = config();
        c.candidates.clear();
        c.candidates.add(new Candidate("today", TODAY, "today's (PID)", Double.NaN, Double.NaN, Double.NaN));
        GroupShotSession s = new GroupShotSession(c, new GroupShotResults(c.setup()));
        passChecks(s);
        assertEquals("today's", group(s, good(false), 4).cand.key, "today");
        assertEquals("only one setting: the same one again", "today:0:100", s.plan().variant.key());
    }

    // A group "lost speed" when it fell more than one ball's drop (measured by the check shots) and the wobble.
    @Test public void theCheckShotsMeasureOneBallsDropForTheGroups() {
        GroupShotSession s = session();
        for (int i = 0; i < 4; i++) {
            countdown(s);
            s.singleDone(true, "", 300);
            s.entrySave();
        }
        assertEquals(300, s.oneBallDrop(0), 1e-9);
        assertEquals("no check shot there yet: shotDropRpm", 230, s.oneBallDrop(1), 1e-9);
        int[] shortOne = {3, 1, 0, 0, 0};
        GroupShotResults.Group held = new GroupShotResults.Group(1, 0, "pid:0:100", null, shortOne, 2615 - 340, 2615, 3, 0.5, "");
        GroupShotResults.Group sagged = new GroupShotResults.Group(1, 0, "pid:0:100", null, shortOne, 2615 - 370, 2615, 3, 0.5, "");
        assertFalse("340 under: inside one ball (300) and the wobble (60)", s.shortWithSag(held));
        assertTrue("370 under: it did not get back between two balls", s.shortWithSag(sagged));
        GroupShotResults back = GroupShotResults.parse(s.results().lines());
        assertEquals("the drop is saved", 300, back.checks().get(0).dropRpm, 1e-9);
    }

    // ---------------------------------------------------------------- starting a shot

    @Test public void aShotStartsOnlyAtTheSpotWithTheGoalSeenAndNothingBlocking() {
        GroupShotSession s = session();
        s.pressA(Double.NaN, "");
        assertTrue(s.refusal(), s.refusal().contains("does NOT see the goal"));
        s.pressA(30, "");
        assertTrue(s.refusal(), s.refusal().contains("NOT at the spot: 30.0 in"));
        assertTrue(s.refusal(), s.refusal().contains("Drive 3.0 in CLOSER to the goal."));
        s.pressA(25, "");
        assertTrue(s.refusal(), s.refusal().contains("Back 2.0 in AWAY from the goal."));
        assertTrue(s.needsPlace());
        s.pressA(28.4, "");
        assertFalse("within 1.5 in: placed", s.needsPlace());
        assertEquals(Stage.READY, s.stage());
        s.pressA(27, "NO shots count: the saved aim offset");
        assertEquals("a saved offset that differs blocks every shot", Stage.READY, s.stage());
        assertTrue(s.refusal().startsWith("NO shots count"));
        s.pressA(29, "");
        assertTrue("moved away: place it again", s.needsPlace());
        assertTrue(s.refusal(), s.refusal().contains("The robot moved"));
        s.pressA(27, "");
        s.pressA(27, "");
        assertEquals(Stage.COUNTDOWN, s.stage());
    }

    @Test public void aStopInTheCountdownOrTheShotNeedsAFreshPress() {
        GroupShotSession s = session();
        s.pressA(27, "");
        s.pressA(27, "");
        assertTrue(s.moving());
        s.stop(GroupShotSession.Stop.BUTTON);
        assertEquals(Stage.STOPPED, s.stage());
        t += 10;
        s.update(t);
        assertEquals("nothing starts by itself", Stage.STOPPED, s.stage());
        s.pressA(27, "");
        assertEquals(Stage.READY, s.stage());
        countdown(s);
        s.stop(GroupShotSession.Stop.HELP);
        assertEquals(Stage.STOPPED, s.stage());
        assertEquals(GroupShotSession.Stop.HELP, s.stopWhy());
        assertTrue("nothing was saved", s.results().isEmpty());
    }

    @Test public void aCheckShotThatWasNotFedSaysWhyAndSavesNothing() {
        GroupShotSession s = session();
        countdown(s);
        s.singleDone(false, "NOT aimed at the goal");
        assertEquals(Stage.READY, s.stage());
        assertTrue(s.note(), s.note().startsWith("No shot: NOT aimed at the goal."));
        assertTrue(s.results().isEmpty());
    }

    // ---------------------------------------------------------------- groups that do not count

    // Every reason: not scored (nothing to enter), kept as "not counted", and the same group runs again.
    @Test public void everyInvalidGroupIsNotScoredAndRunsAgain() {
        List<Consumer<GroupRun>> bad = new ArrayList<>();
        List<Invalid> expected = new ArrayList<>();
        bad.add(r -> r.aimedAtStart = false);                        expected.add(Invalid.NOT_AIMED_AT_START);
        bad.add(r -> r.worstAimDeg = 3.1);                           expected.add(Invalid.AIM_DRIFT);
        bad.add(r -> r.end = ContinuousFire.End.AIM_LOST);           expected.add(Invalid.AIM_DRIFT);
        bad.add(r -> r.goalMissed = true);                           expected.add(Invalid.GOAL_LOST);
        bad.add(r -> r.end = ContinuousFire.End.GOAL_LOST);          expected.add(Invalid.GOAL_LOST);
        bad.add(r -> r.end = ContinuousFire.End.CONTROL_CHANGED);    expected.add(Invalid.SETTING_CHANGED);
        bad.add(r -> r.end = ContinuousFire.End.TABLE_CHANGED);      expected.add(Invalid.SETTING_CHANGED);
        bad.add(r -> r.end = ContinuousFire.End.ALLIANCE_CHANGED);   expected.add(Invalid.SETTING_CHANGED);
        for (ContinuousFire.End e : new ContinuousFire.End[] {ContinuousFire.End.RELEASED, ContinuousFire.End.STOPPED,
                ContinuousFire.End.PAUSED, ContinuousFire.End.JAM, ContinuousFire.End.ENCODER_FAULT, ContinuousFire.End.WHEEL_SLOW,
                ContinuousFire.End.DISTANCE_MOVED, ContinuousFire.End.FEEDER_STOPPED, ContinuousFire.End.EJECT,
                ContinuousFire.End.INTAKE_REVERSE, ContinuousFire.End.RELIGHT, ContinuousFire.End.ERROR}) {
            bad.add(r -> r.end = e);
            expected.add(Invalid.ENDED_EARLY);
        }
        for (Invalid i : Invalid.values()) assertTrue("each reason is tested: " + i, expected.contains(i));
        for (int i = 0; i < bad.size(); i++) {
            GroupShotSession s = session();
            passChecks(s);
            Step before = s.plan();
            GroupRun r = good(false);
            bad.get(i).accept(r);
            assertEquals(expected.get(i), GroupShotSession.judge(r));
            countdown(s);
            s.groupDone(r);
            assertEquals("no entry: it is not scored", Stage.READY, s.stage());
            assertTrue(s.note(), s.note().startsWith("NOT counted: " + expected.get(i).words));
            assertEquals("kept as not counted", 1, s.notCounted());
            assertEquals(0, s.validGroups(1, before.variant).size());
            Step again = s.plan();
            assertEquals("the same group runs again", before.variant.key(), again.variant.key());
            assertEquals(before.spot, again.spot);
            assertEquals(Kind.GROUP, again.kind);
        }
    }

    @Test public void aGroupThatNeverFedIsNoShotAndSavesNothing() {
        GroupShotSession s = session();
        passChecks(s);
        countdown(s);
        GroupRun r = new GroupRun();
        r.fed = false;
        r.endWords = "NOT aimed";
        s.groupDone(r);
        assertEquals(Stage.READY, s.stage());
        assertTrue(s.note(), s.note().startsWith("No shot: NOT aimed."));
        assertTrue(s.note(), s.note().contains("The balls are still in."));
        assertEquals(0, s.results().groups().size());
    }

    // ---------------------------------------------------------------- the entry

    @Test public void aGroupsEntryNeedsAll4BallsAndNotSureRepeatsIt() {
        GroupShotSession s = session();
        passChecks(s);
        countdown(s);
        s.groupDone(good(false));
        s.entryChange(1);
        s.entryChange(1);
        s.entryChange(1);
        s.entrySave();
        assertEquals(Stage.ENTRY, s.stage());
        assertEquals("Enter all 4 balls: 3 so far.", s.refusal());
        s.entryMove(1);               // short
        s.entryChange(1);
        s.entryChange(1);             // a fifth ball: refused
        assertTrue(s.refusal(), s.refusal().startsWith("That is all 4 balls."));
        s.entryChange(-1);
        s.entryChange(1);
        assertEquals(Arrays.toString(s.counts()), 4, GroupShotSession.total(s.counts()));
        s.entryUnsure();
        assertEquals(Stage.READY, s.stage());
        assertEquals("nothing saved", 0, s.results().groups().size());
        assertEquals("the same group again", "pid:0:100", s.plan().variant.key());
        group(s, good(false), 3, 1);
        assertEquals(1, s.results().groups().size());
        assertEquals("Saved: 3 scored, 1 short.", s.note());
    }

    // ---------------------------------------------------------------- winner and advice

    @Test public void theWinnerScoredEveryBallAndATieGoesToTheQuickerRecovery() {
        GroupShotSession s = session();
        playRound(s, (v, spot) -> new int[] {4}, v -> false);
        Step end = s.plan();
        assertEquals(Kind.FINISHED, end.kind);
        assertEquals(Stage.FINISHED, s.stage());
        assertEquals("both scored every ball: bang-bang recovered quicker in the Flywheel Tuner", "bang:0:100", end.winners.get(0).key());
        assertEquals(2, end.winners.size());
        assertEquals("84 balls: 3 spots x (4 check + 2 settings x 3 groups x 4)", 3 * 4 + 3 * 2 * 3 * 4,
                s.results().checks().size() + 4 * s.results().groups().size());
    }

    @Test public void oneMissAtOneSpotLosesAndTheOtherWins() {
        GroupShotSession s = session();
        playRound(s, (v, spot) -> v.cand.key.equals("bang") && spot == 2 ? new int[] {3, 0, 0, 1} : new int[] {4}, v -> false);
        Step end = s.plan();
        assertEquals(Kind.FINISHED, end.kind);
        assertEquals(1, end.winners.size());
        assertEquals("pid:0:100", end.winners.get(0).key());
    }

    @Test public void shortWithSagTriesABoostThenTheBoostWins() {
        GroupShotSession s = session();
        // Round 1: both go short at 45 in while the wheel lost speed.
        playRound(s, (v, spot) -> spot == 2 ? new int[] {3, 1} : new int[] {4}, v -> true);
        Step next = s.plan();
        assertEquals(2, next.round);
        List<String> keys = new ArrayList<>();
        for (Variant v : next.variants) keys.add(v.key());
        assertEquals(Arrays.asList("pid:50:100", "bang:50:100"), keys);
        assertEquals(Arrays.asList("Short, and the wheel lost speed.", "Needs a quicker recovery, or a boost."),
                s.advice(1, s.rounds().get(0).get(0)));
        assertEquals("the spots' check shots are not shot again", Kind.GROUP, next.kind);
        // Round 2: the boosted PID scores everything; the boosted bang-bang still goes short.
        playRound(s, (v, spot) -> v.cand.key.equals("bang") && spot == 2 ? new int[] {3, 1} : new int[] {4}, v -> true);
        Step end = s.plan();
        assertEquals(Kind.FINISHED, end.kind);
        assertEquals("pid:50:100", end.winners.get(0).key());
    }

    @Test public void longWithNoBoostPointsAtTheTableAndAMissWithSpeedHeldAtTheFeed() {
        GroupShotSession s = session();
        playRound(s, (v, spot) -> v.cand.key.equals("pid") ? new int[] {3, 0, 1} : new int[] {3, 0, 0, 0, 1}, v -> false);
        List<Variant> r1 = s.rounds().get(0);
        assertEquals(Arrays.asList("Long with no boost: not the speed setting.", "Check the shot table at that spot."), s.advice(1, r1.get(0)));
        assertEquals(Arrays.asList("Missed with the speed and aim held:", "the feed or the launcher. Same speed."), s.advice(1, r1.get(1)));
        // A short miss with the speed held (no sag) is the same: the feed or the launcher, no boost.
        GroupShotSession s2 = session();
        playRound(s2, (v, spot) -> new int[] {3, 1}, v -> false);
        assertEquals(Arrays.asList("Missed with the speed and aim held:", "the feed or the launcher. Same speed."),
                s2.advice(1, s2.rounds().get(0).get(0)));
        // No full-pace step is left: a slower feed, last.
        List<String> keys = new ArrayList<>();
        for (Variant v : s.plan().variants) keys.add(v.key());
        assertEquals(Arrays.asList("pid:0:90", "bang:0:90"), keys);
    }

    @Test public void aBoostThatGoesLongSaysLessBoost() {
        GroupShotSession s = session();
        playRound(s, (v, spot) -> spot == 0 ? new int[] {3, 1} : new int[] {4}, v -> true);
        playRound(s, (v, spot) -> spot == 0 ? new int[] {3, 0, 1} : new int[] {4}, v -> false);
        assertEquals(Arrays.asList("Long: less boost."), s.advice(2, s.rounds().get(1).get(0)));
    }

    @Test public void theFullPaceWinsOverASlowerFeedAndNothingWinsAtLastSaysNoWinner() {
        GroupShotSession s = session();
        playRound(s, (v, spot) -> new int[] {2, 0, 0, 2}, v -> false);           // round 1: sideways misses
        assertEquals(0.9, s.plan().variants.get(0).feedShare, 1e-9);
        playRound(s, (v, spot) -> new int[] {2, 0, 0, 2}, v -> false);           // round 2: 90% still misses
        assertEquals(0.8, s.plan().variants.get(0).feedShare, 1e-9);
        playRound(s, (v, spot) -> v.cand.key.equals("pid") && spot == 1 ? new int[] {3, 0, 0, 1} : new int[] {2, 2}, v -> false);
        Step end = s.plan();
        assertEquals(Kind.FINISHED, end.kind);
        assertTrue("no winner", end.winners.isEmpty());
        Object[] best = s.bestScore();
        assertNotNull(best);
        assertEquals("the best score of all", "pid:0:80", ((Variant) best[2]).key());
        assertEquals(21, best[0]);
        assertEquals(36, best[1]);
        // A slower feed only wins when no full-pace setting does: rank them directly.
        Candidate c = config().candidates.get(0);
        List<Variant> two = new ArrayList<>(Arrays.asList(new Variant(c, 0, 0.9), new Variant(c, 100, 1)));
        two.sort(GroupShotSession.BETTER_FIRST);
        assertEquals("the faster commanded pace first, even with a boost", 1, two.get(0).feedShare, 1e-9);
    }

    // PID misses sideways, bang-bang goes short with sag: round 2 is bang-bang with a boost only. When that
    // misses too, the slower feed is tried for EVERY setting, PID too (it was left out of round 2).
    @Test public void aSlowerFeedIsTriedForEverySettingAfterABoostRoundLeftOneOut() {
        GroupShotSession s = session();
        playRound(s, (v, spot) -> v.cand.key.equals("pid") ? new int[] {2, 0, 0, 2} : spot == 2 ? new int[] {3, 1} : new int[] {4},
                v -> v.cand.key.equals("bang"));
        List<String> r2 = new ArrayList<>();
        for (Variant v : s.plan().variants) r2.add(v.key());
        assertEquals(Arrays.asList("bang:50:100"), r2);
        playRound(s, (v, spot) -> new int[] {3, 0, 0, 1}, v -> false);     // the boost misses sideways
        List<String> r3 = new ArrayList<>();
        for (Variant v : s.plan().variants) r3.add(v.key());
        assertEquals(Arrays.asList("pid:0:90", "bang:0:90"), r3);
    }

    // ---- what the Group Shot Tuner takes from the Flywheel Tuner, and what its saved groups depend on ----

    private static FlywheelTuneResults stage1(String setup) {
        FlywheelTuneResults r = new FlywheelTuneResults(setup);
        java.util.Map<String, Double> m = new java.util.LinkedHashMap<>();
        m.put("recovery", 0.3);
        for (FlywheelSetting w : new FlywheelSetting[] {PID, BANG}) {
            String id = w == PID ? "pid:kP=50" : "bang:full=150:hyst=30:max=1";
            r.add(new FlywheelTuneResults.Test(FlywheelTuneResults.Battery.FULL, id, FlywheelTuneResults.Verdict.PASS, "", w, m));
            r.add(new FlywheelTuneResults.Test(FlywheelTuneResults.Battery.LOW, id, FlywheelTuneResults.Verdict.PASS, "", w, m));
            r.add(new FlywheelTuneResults.Test(FlywheelTuneResults.Battery.LOW, "drive:" + id, FlywheelTuneResults.Verdict.PASS, "", w, m));
        }
        return r;
    }

    @Test public void flywheelTunerResultsForOtherSettingsAreNotUsed() {
        GroupShotSession.Config same = new GroupShotSession.Config();
        same.today = TODAY;
        assertEquals("", GroupShotTunerOpMode.addCandidates(same, stage1("setup now"), "setup now"));
        assertEquals(3, same.candidates.size());
        GroupShotSession.Config other = new GroupShotSession.Config();
        other.today = TODAY;
        String note = GroupShotTunerOpMode.addCandidates(other, stage1("setup then"), "setup now");
        assertTrue(note, note.startsWith("Flywheel Tuner results are for other settings.\nRun it again."));
        assertEquals("only today's settings", 1, other.candidates.size());
        assertEquals("today", other.candidates.get(0).key);
    }

    @Test public void theSavedGroupsDependOnTheHoodAnglesAndTheAlliance() {
        List<com.crossbotics.ftchelper.launcher.ShotTable.Shot> a = Arrays.asList(
                new com.crossbotics.ftchelper.launcher.ShotTable.Shot(27, 2500, 30), new com.crossbotics.ftchelper.launcher.ShotTable.Shot(45, 2918, 40));
        List<com.crossbotics.ftchelper.launcher.ShotTable.Shot> b = Arrays.asList(
                new com.crossbotics.ftchelper.launcher.ShotTable.Shot(27, 2500, 30), new com.crossbotics.ftchelper.launcher.ShotTable.Shot(45, 2918, 42));
        String base = GroupShotTunerOpMode.extra(a, 0, 1, 3, com.crossbotics.ftchelper.match.Alliance.BLUE);
        assertFalse("another hood angle", base.equals(GroupShotTunerOpMode.extra(b, 0, 1, 3, com.crossbotics.ftchelper.match.Alliance.BLUE)));
        assertFalse("another alliance", base.equals(GroupShotTunerOpMode.extra(a, 0, 1, 3, com.crossbotics.ftchelper.match.Alliance.RED)));
        assertEquals(base, GroupShotTunerOpMode.extra(a, 0, 1, 3, com.crossbotics.ftchelper.match.Alliance.BLUE));
    }

    @Test public void aTieGoesToTheGentlerSettingAfterTheRecovery() {
        Candidate a = new Candidate("pid", PID, "best PID", 0.3, 80, 0.5);
        Candidate b = new Candidate("bang", BANG, "best bang-bang", 0.3, 40, 0.5);
        List<Variant> vs = new ArrayList<>(Arrays.asList(new Variant(a, 50, 1), new Variant(a, 0, 1), new Variant(b, 0, 1)));
        vs.sort(GroupShotSession.BETTER_FIRST);
        assertEquals("no boost, then less overshoot", "bang:0:100", vs.get(0).key());
        assertEquals("pid:0:100", vs.get(1).key());
        assertEquals("pid:50:100", vs.get(2).key());
    }

    // ---------------------------------------------------------------- saved and read back

    @Test public void theSavedFileReadsBackToTheSamePlace() {
        GroupShotSession s = session();
        passChecks(s);
        group(s, good(true), 3, 1);
        countdown(s);
        GroupRun r = good(false);
        r.goalMissed = true;
        r.endWords = "goal NOT seen";
        s.groupDone(r);
        GroupShotResults back = GroupShotResults.parse(s.results().lines());
        assertEquals(s.results().setup(), back.setup());
        assertEquals(4, back.checks().size());
        assertEquals(2, back.groups().size());
        assertEquals(Invalid.GOAL_LOST, back.groups().get(1).invalid);
        assertEquals("goal NOT seen", back.groups().get(1).endWords);
        assertNull(back.groups().get(0).invalid);
        assertEquals(1, back.groups().get(0).count(Ball.SHORT));
        GroupShotSession again = new GroupShotSession(config(), back);
        assertEquals(s.plan().variant.key(), again.plan().variant.key());
        assertTrue("a new OpMode places the robot again", again.needsPlace());
        assertFalse("another setup is another session", config().setup().equals(configWithOtherTable().setup()));
    }

    private static GroupShotSession.Config configWithOtherTable() {
        GroupShotSession.Config c = config();
        c.extra = "table=27:2500,45:2918";
        return c;
    }
}
