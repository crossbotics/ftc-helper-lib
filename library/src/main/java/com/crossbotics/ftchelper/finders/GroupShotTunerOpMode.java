package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.drive.DriverControls;
import com.crossbotics.ftchelper.finders.FlywheelTuneResults.Test;
import com.crossbotics.ftchelper.finders.GroupShotResults.Ball;
import com.crossbotics.ftchelper.finders.GroupShotSession.Candidate;
import com.crossbotics.ftchelper.finders.GroupShotSession.GroupRun;
import com.crossbotics.ftchelper.finders.GroupShotSession.Kind;
import com.crossbotics.ftchelper.finders.GroupShotSession.Stage;
import com.crossbotics.ftchelper.finders.GroupShotSession.Step;
import com.crossbotics.ftchelper.finders.GroupShotSession.Variant;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.launcher.ContinuousFire;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.launcher.Flywheel.SpeedControl;
import com.crossbotics.ftchelper.launcher.ShotSight;
import com.crossbotics.ftchelper.launcher.ShotTable;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.robot.Aim;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.vision.TargetObservation;
import java.util.ArrayList;
import java.util.List;

/**
 * The Group Shot Tuner: shoots real balls, in groups of 4 fed without a break (Continuous fire), with the
 * Flywheel Tuner's best PID setting and its best bang-bang setting, and finds the one that scores every ball.
 * It uses the match's camera, its lighting test and the alliance chosen in INIT, the code's shot table (the
 * speed from the camera's distance, with no trim) and the code's {@code cameraOffset}. So a group that goes short
 * because the wheel slowed is not mixed up with a bad aim.
 *
 * <p>At each spot (the camera's distance to the goal: the table's first row, the middle, and its last row), it
 * first shoots 4 single "check shots" with today's settings, each aimed and at speed. 3 must score, or that
 * spot stops: fix the table or the aim first. Then groups of 4, taking turns between the settings, until each
 * has 3 groups that count. After each one, the person enters where the balls went. A group does not count (and
 * runs again) when the aim was off at the start, the aim moved, the camera lost the goal, a setting changed, or
 * it stopped early. Every shot and group is saved at once, so a stop or a power cycle loses nothing.
 *
 * <p>A press of A shoots, after a 3 s countdown. ANY button stops it (help and STOP too). Nothing starts again
 * without a fresh press. The sticks drive (30% power) only on the "place the robot" screen. The last screens show
 * DONE or NOT DONE and the exact lines for {@code Tunables.java}.
 *
 * <p>In "Tune and check" it is in the menu for a robot with a launcher and a camera that aims the whole robot.
 * On its own:
 * <pre>{@code
 * @TeleOp(name = "Group Shot Tuner", group = "Finders")
 * public class GroupShotTuner extends GroupShotTunerOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 *
 * IT SHOOTS: safety glasses; nobody in front of the robot; the robot turns by itself to aim.
 *
 * Docs: docs/tuning/14-group-shot-tuner.md
 */
public abstract class GroupShotTunerOpMode extends FinderOpMode {

    /** The robot in five files: {@code return new Robot();}. */
    protected abstract RobotDefinition robot();

    /** The most a shot waits for the wheel's speed and the aim before it gives up, seconds. */
    static final double SPIN_LIMIT_SEC = 10;
    /** After a check shot is fed: the time to let it fly before the power goes off, seconds. */
    static final double FLY_SEC = 0.5;

    private String problem = "";
    private RobotDefinition robotDef;
    private Launcher launcher;
    private com.crossbotics.ftchelper.launcher.Launcher part;
    private Flywheel flywheel;
    private Camera robotCamera;
    private Aim aim;
    private DriverControls driving;
    private GroupShotSession session;
    private String savedChanged = "";
    private String flywheelTunerNote = "";
    private String tunablesFile = "Tunables.java";
    // The aim offset: the code's, and the one the tuning menu saved (NaN = none saved).
    private double codeOffset = 0, savedOffset = Double.NaN;
    // The group's numbers in the code, put back after each group: the boost and the feed power.
    private double codeBoost = 0, codeFeedPower = Double.NaN, baseFeed = Double.NaN;
    private Alliance alliance = Alliance.BLUE;
    private boolean xWas = true, bWas = true;
    private boolean startedCamera = false;
    private final ShotPoints.DistanceAverage average = new ShotPoints.DistanceAverage();
    private double distanceNow = Double.NaN;
    private boolean[] was = null;
    private int page = 0, savedAt = 0;
    private boolean newSessionAsked = false, linesWritten = false, drove = false;
    // The flywheel's bad speed reading ("" = none): shown until A is pressed (the person checked the wires).
    private String encoderProblem = "";

    // The shot now: what it does, since when, and its log.
    private enum Fire { NONE, SPIN, SINGLE, GROUP }
    private Fire fire = Fire.NONE;
    private double fireSince = 0, fedAt = Double.NaN, singleTarget = Double.NaN, singleLow = Double.NaN;
    private int firedBefore = 0;
    private boolean sawFeed = false;
    private GroupRun run = null;
    private double nowSec = 0;

    /** The file it saves every shot and group to: {@code group-shot-tuner.txt} in the results folder. */
    public static java.io.File savedFile() {
        return GroupShotResults.file();
    }

    @Override protected String finderName() { return "Group Shot Tuner"; }

    // Its numbers are speeds: they depend on the battery.
    @Override protected boolean batteryMatters() { return true; }

    @Override
    protected void setup() {
        robotDef = robot();
        for (Module<?> m : robotDef.modules()) {
            if (m instanceof Launcher) { launcher = (Launcher) m; break; }
        }
        if (launcher == null) {
            problem = "No launcher in Robot.java.\nPress STOP. Use another tool.";
            return;
        }
        // The whole robot is set up, but not started: nothing moves, and its start actions do not run.
        try {
            robotDef.setup(hardwareMap);
        } catch (RuntimeException e) {
            problem = "Robot.java did not set up. Fix this first:\n" + e.getMessage();
            return;
        }
        if (robotDef.tunablesClass() != null) tunablesFile = robotDef.tunablesClass().getSimpleName() + ".java";
        aim = robotDef.aim();
        if (aim == null) {
            problem = "No camera aim in Robot.java.\nThis tool needs a camera with goal(...).";
            return;
        }
        robotCamera = aim.camera();
        String why = robotProblem();
        if (!why.isEmpty()) {
            problem = why;
            return;
        }
        part = launcher.part();
        flywheel = part.getFlywheel();
        // The alliance last chosen (BLUE if none): only its goal counts. X / B in INIT change it.
        alliance = com.crossbotics.ftchelper.match.MatchMemory.allianceOr(Alliance.BLUE);
        robotDef.setAlliance(alliance);
        driving = DriverControls.create(hardwareMap, robotDef.robotConfig().drive(), null);
        // The robot turns to the goal the way TeleOp does (the code's cameraOffset); nothing else steers it.
        driving.turnAssist(aim::turnFor);
        // A shot is fed only while the camera sees the goal and the aim is inside its tolerance (no margin).
        part.requireReady("aimed at the goal", this::strictlyAimed);
        part.stop();
        readOffsets();
        codeBoost = part.continuous().getBoostRpm();
        codeFeedPower = part.continuous().getFeedPower();
        baseFeed = launcher.nonstopFeedPower();
        loadSession();
        results.notDone("the groups are not finished.");
    }

    // The session for the robot as it is now (and the alliance chosen): the saved one when it was made with the
    // same setup, else a new start (the old file is kept on the next A).
    private void loadSession() {
        GroupShotSession.Config c = config();
        GroupShotResults saved = GroupShotResults.load();
        savedChanged = "";
        if (!saved.isEmpty() && !saved.setup().equals(c.setup())) {
            savedChanged = "settings, alliance or Flywheel Tuner results";
            saved = new GroupShotResults(c.setup());
        } else if (saved.isEmpty()) {
            saved = new GroupShotResults(c.setup());
        }
        session = new GroupShotSession(c, saved);
    }

    // Why this robot cannot use the tool ("" = it can).
    private String robotProblem() {
        if (!launcher.isPresent() || launcher.part() == null) return launcher.name() + " is not connected.\nAdd it to the robot configuration.";
        if (robotCamera == null || !robotCamera.knowsGoal()) {
            return "No camera aims at a goal in Robot.java.\nAdd goal(...) to the camera. See the docs.";
        }
        if (aim.turretAims()) return "Your turret aims, and this tool turns\nthe whole robot. It cannot run here.";
        if (!launcher.part().continuous().canFire()) {
            return "The feeder cannot feed nonstop.\nGroups need a roller or a spinning servo.";
        }
        if (!launcher.usesDistance()) {
            return "The speed does not come from the camera.\nAdd speedFromDistance(camera) in Robot.java.";
        }
        ShotTable table = launcher.table(launcher.tableName());
        if (table == null || table.points().size() < 2) {
            return "No shot table with 2 or more rows.\nRun the Shot Tuner first.";
        }
        return "";
    }

    // The code's cameraOffset, and the saved one when the tuning menu saved another.
    private void readOffsets() {
        codeOffset = codeValue(robotDef.tunablesClass(), "Aim", "cameraOffset", 0);
        savedOffset = Double.NaN;
        for (Tunable t : Tunable.all()) {
            if (t.name().equals("Aim/cameraOffset") && Math.abs(t.get() - t.defaultValue()) > 1e-9) savedOffset = t.get();
        }
    }

    // The robot's numbers the plan uses: the spots, the settings to test, one ball's drop.
    private GroupShotSession.Config config() {
        GroupShotSession.Config c = new GroupShotSession.Config();
        ShotTable table = launcher.table(launcher.tableName());
        List<ShotTable.Shot> rows = table.points();
        double first = rows.get(0).distance, last = rows.get(rows.size() - 1).distance;
        // The first row, the middle, the last row: inside the measured rows, so no shot uses an estimate.
        c.spots = new double[] {first, Math.round((first + last) / 2 * 2) / 2.0, last};
        c.dropRpm = FlywheelTunerOpMode.pretendDrop(launcher);
        c.readyBandRpm = flywheel.getTolerance();
        c.today = FlywheelSetting.of(flywheel);
        flywheelTunerNote = addCandidates(c, FlywheelTuneResults.load(), FlywheelTunerOpMode.config(launcher).setup());
        c.extra = extra(rows, codeOffset, baseFeed, part.continuous().getMaxSec(), alliance);
        return c;
    }

    /**
     * Adds the settings to test: the Flywheel Tuner's best PID and best bang-bang, and today's settings when they
     * differ. The Flywheel Tuner's results count only when they were made with the robot as it is now (the same
     * setup line): else only today's settings are tested.
     *
     * @param c          the config to add to (its {@code today} set)
     * @param stage1     the Flywheel Tuner's saved results
     * @param todaySetup the Flywheel Tuner's setup line for the robot now
     * @return a note for the screen ("" = nothing to say)
     */
    static String addCandidates(GroupShotSession.Config c, FlywheelTuneResults stage1, String todaySetup) {
        boolean current = !stage1.tests().isEmpty() && stage1.setup().equals(todaySetup);
        // Too small a pretend shot to time: its "back at speed" time does not rank the settings here either.
        boolean timed = current && stage1.recoveryRanks();
        List<String> none = new ArrayList<>();
        if (current) {
            for (SpeedControl sc : SpeedControl.values()) {
                Test w = stage1.winnerTest(sc);
                String name = sc == SpeedControl.PID ? "best PID" : "best bang-bang";
                if (w == null) {
                    none.add(sc == SpeedControl.PID ? "PID" : "bang-bang");
                    continue;
                }
                c.candidates.add(new Candidate(sc == SpeedControl.PID ? "pid" : "bang", w.setting, name,
                        timed ? w.number("recovery") : Double.NaN, w.number("over"), w.number("fullSec")));
            }
        }
        boolean todayTested = false;
        for (Candidate k : c.candidates) if (k.setting.sameAs(c.today)) todayTested = true;
        if (!todayTested) {
            Test t = current ? stage1.find(FlywheelTuneResults.Battery.FULL, "today") : null;
            boolean same = t != null && t.setting != null && t.setting.sameAs(c.today);
            c.candidates.add(new Candidate("today", c.today, "today's (" + (c.today.control == SpeedControl.PID ? "PID" : "bang-bang") + ")",
                    same && timed ? t.number("recovery") : Double.NaN, same ? t.number("over") : Double.NaN,
                    same ? t.number("fullSec") : Double.NaN));
        }
        if (!stage1.tests().isEmpty() && !current) {
            return "Flywheel Tuner results are for other settings.\nRun it again. Now it tests only today's settings.";
        }
        if (!current || none.size() == 2) return "Flywheel Tuner: no best setting yet.\nIt tests only today's settings.";
        if (!none.isEmpty()) return "Flywheel Tuner: no best " + none.get(0) + ".\nIt tests what there is, and today's settings.";
        return "";
    }

    /**
     * What else the saved shots depend on, as text: the table (distance, speed and hood angle of each row), the
     * code's aim offset, the feed power, the feed time and the alliance. A change in any of them starts a new
     * session.
     */
    static String extra(List<ShotTable.Shot> rows, double offset, double feed, double maxSec, Alliance alliance) {
        StringBuilder t = new StringBuilder("table=");
        for (ShotTable.Shot s : rows) {
            t.append(FinderResults.num(s.distance, 1)).append(':').append(FinderResults.num(s.rpm, 1));
            if (!Double.isNaN(s.hoodDeg)) t.append(':').append(FinderResults.num(s.hoodDeg, 2));
            t.append(',');
        }
        return t + " offset=" + FinderResults.num(offset, 2) + " feed=" + FinderResults.num(feed, 3)
                + " time=" + FinderResults.num(maxSec, 2) + " alliance=" + alliance;
    }

    @Override protected void bind(ControllerBindings c) {
        // Every button is read in step(): while the robot may move or shoot, ANY button stops it.
    }

    // ---------------------------------------------------------------- every loop

    @Override
    protected void step() {
        if (session == null) return;
        nowSec = System.nanoTime() / 1e9;
        if (!startedCamera) {
            startedCamera = true;
            robotCamera.started();   // an unfinished lighting test keeps its best setting, as in a match
            // The speed from the camera's distance and the shot table, as TeleOp's START turns it on (no spin here).
            launcher.speedMode().on();
        }
        robotCamera.update();
        readDistance();
        buttons();
        session.update(nowSec);
        if (session.stage() == Stage.FIRING && fire != Fire.NONE && flywheel.isFaulted()) encoderFault();
        if (session.stage() == Stage.FIRING) shoot();
        else if (fire != Fire.NONE) outputsOff();
        // The launcher's own loop: the distance, the code's table, the speed, the feeder. The same code as TeleOp.
        launcher.update();
        if (session.stage() == Stage.FIRING) afterLauncher();
        drive();
        if (session.saves() != savedAt) {
            savedAt = session.saves();
            session.results().saveInBackground();
            linesWritten = false;
        }
        if (session.stage() == Stage.FINISHED && !linesWritten) writeResults();
    }

    // The camera's distance to the goal (this alliance's, fresh pictures only), averaged over half a second.
    private void readDistance() {
        TargetObservation g = aim.goal();
        if (g != null && !g.fromOdometry() && g.hasDistance()) average.add(nowSec, g.distanceIn, "camera");
        distanceNow = average.count(nowSec) >= 3 ? average.average(nowSec) : Double.NaN;
    }

    // Strict: a fresh goal and the aim inside toleranceDeg now (no margin, no time limit).
    private boolean strictlyAimed() {
        ShotSight s = aim.shotSight();
        return s.goalSeen && s.aimed;
    }

    // Why no shot may count now ("" = none): the screen shows the fix.
    private String blocked() {
        if (!Double.isNaN(savedOffset)) return "NO shots count: the saved aim offset\nis NOT the code's. The fix is above.";
        if (launcher.allianceTrim() != 0) return "NO shots count: the code adds a speed\ntrim for " + alliance + ". The fix is above.";
        // A table that does not continue past its ends: the end spots must be just inside it.
        ShotTable table = launcher.table(launcher.tableName());
        if (table != null && !Double.isNaN(distanceNow) && !table.reaches(distanceNow)) {
            List<ShotTable.Shot> rows = table.points();
            double first = rows.get(0).distance, last = rows.get(rows.size() - 1).distance;
            return "Outside the shot table (" + FinderResults.num(first, 1) + " to " + FinderResults.num(last, 1) + " in).\n"
                    + GroupShotSession.moveWords(distanceNow < first ? distanceNow - first - 0.3 : distanceNow - last + 0.3);
        }
        return "";
    }

    // ---------------------------------------------------------------- the shot

    // The flywheel's speed reading failed (bang-bang's check): everything stops, the screen says to check the
    // encoder. A group that fed does not count. Only a fresh A clears it (the next shot's spin-up).
    private void encoderFault() {
        encoderProblem = flywheel.getFault();
        if (fire == Fire.GROUP && sawFeed) {
            part.continuous().stop(ContinuousFire.End.ENCODER_FAULT);
            groupEnded();
            return;
        }
        if (fire == Fire.SINGLE && !Double.isNaN(fedAt)) {
            finishSingleOrGroup(true, "");
            return;
        }
        session.stop(GroupShotSession.Stop.ENCODER);
        outputsOff();
    }

    // FIRING, before the launcher's loop: set up, then wait for the speed and the aim, then shoot.
    private void shoot() {
        Step s = session.firing();
        if (s == null) return;
        if (fire == Fire.NONE) {
            // The setting goes on while the wheel is stopped (a change of control needs that).
            launcher.stop();
            FlywheelSetting setting = s.kind == Kind.GROUP ? s.variant.cand.setting : session.config().today;
            if (!setting.applyTo(flywheel)) {
                finishSingleOrGroup(false, "the speed setting did NOT change");
                return;
            }
            if (s.kind == Kind.GROUP) {
                part.continuous().boostRpm(s.variant.boostRpm)
                        .feedPower(s.variant.fullPace() || Double.isNaN(baseFeed) ? codeFeedPower : s.variant.feedShare * baseFeed);
            }
            aim.mode().on();     // the robot turns to the goal (the driver's stick is not read here)
            aim.relock();        // each shot must get inside toleranceDeg again
            // A person pressed A and the countdown ended: the feed stop may now hold and open for the feed, and an
            // always-on feed wheel may run (the robot was set up but not started, so nothing moved in INIT).
            launcher.startFeedForTuner();
            // The robot's own spin-up (not the part's): the feed wheel and the feed stop know the wheel is on,
            // and a bad-speed-reading stop clears here, on this fresh press.
            launcher.spinUp();
            fire = Fire.SPIN;
            fireSince = nowSec;
            sawFeed = false;
            fedAt = Double.NaN;
            run = new GroupRun();
            return;
        }
        if (fire == Fire.SPIN) {
            boolean ready = part.isReady() && part.getShotRpm() > 0;
            if (ready) {
                if (s.kind == Kind.GROUP) {
                    // A fresh press of the fire button, with the wheel at speed: the group's strict start.
                    if (!part.continuous().start(launcher::shotSight)) {
                        finishSingleOrGroup(false, part.continuous().getEnd().words());
                        return;
                    }
                    fire = Fire.GROUP;
                } else {
                    firedBefore = part.getShotsFired();
                    part.shoot(1);
                    fire = Fire.SINGLE;
                }
                fireSince = nowSec;
            } else if (nowSec - fireSince > SPIN_LIMIT_SEC) {
                finishSingleOrGroup(false, notReady());
            }
        }
    }

    // What kept the shot from going, as a failure: "NOT aimed at the goal", "Flywheel NOT at speed".
    private String notReady() {
        if (aim.goal() == null) return "the camera does NOT see the goal";
        List<String> w = notReadyWords(part.whyNotReady());
        return w.isEmpty() ? "NOT ready in " + FinderResults.num(SPIN_LIMIT_SEC, 0) + " s" : w.get(0) + " in " + FinderResults.num(SPIN_LIMIT_SEC, 0) + " s";
    }

    // FIRING, after the launcher's loop: watch the single shot, or log the group every loop.
    private void afterLauncher() {
        if (fire == Fire.SINGLE) {
            if (part.getShotsFired() > firedBefore && Double.isNaN(fedAt)) {
                fedAt = nowSec;
                singleTarget = flywheel.getTargetRpm();
                singleLow = flywheel.getRpm();
            }
            if (!Double.isNaN(fedAt)) {
                // How far the ball pulls the wheel under its target: one ball's real drop, for the groups' judge.
                singleLow = Math.min(singleLow, flywheel.getRpm());
                if (!part.isShooting() && nowSec - fedAt >= FLY_SEC) finishSingleOrGroup(true, "");
            } else if (!part.isShooting()) {
                // It gave up waiting (the launcher says why).
                String last = part.getLastProblem();
                finishSingleOrGroup(false, last.isEmpty() ? "NOT ready in time" : last.replace("gave up: ", "NOT ready: "));
            }
            return;
        }
        if (fire != Fire.GROUP) return;
        ContinuousFire group = part.continuous();
        if (group.isFeeding()) {
            ShotSight sight = group.getSight();
            if (!sawFeed) {
                sawFeed = true;
                run.aimedAtStart = sight.goalSeen && sight.aimed;
                run.targetRpm = group.getSnapshot().targetRpm;
                run.aimLimitDeg = sight.toleranceDeg + sight.lockMarginDeg;
                run.worstAimDeg = 0;
            }
            double rpm = flywheel.getRpm();
            if (Double.isNaN(run.lowRpm) || rpm < run.lowRpm) run.lowRpm = rpm;
            if (!sight.goalSeen || Double.isNaN(sight.aimErrorDeg)) run.goalMissed = true;
            else run.worstAimDeg = Math.max(run.worstAimDeg, Math.abs(sight.aimErrorDeg));
        } else if (!group.isActive()) {
            groupEnded();
        }
    }

    private void groupEnded() {
        ContinuousFire group = part.continuous();
        // The group ended on the flywheel's bad speed reading: the screen says how to fix it.
        if (flywheel.isFaulted()) encoderProblem = flywheel.getFault();
        run.fed = sawFeed;
        run.end = group.getEnd();
        run.endWords = group.getEnd().words();
        run.feedSec = group.getFeedSec();
        if (!sawFeed) {
            // It never fed: the first thing that was missing, as the group said it.
            String w = group.getEndWords();
            run.endWords = w.startsWith("Did NOT fire: ") ? w.substring(14) : w;
        }
        outputsOff();
        session.groupDone(run);
    }

    private void finishSingleOrGroup(boolean fed, String why) {
        Step s = session.firing();
        outputsOff();
        if (s != null && s.kind == Kind.GROUP) {
            GroupRun r = run == null ? new GroupRun() : run;
            r.fed = false;
            r.endWords = why;
            session.groupDone(r);
        } else {
            session.singleDone(fed, why, fed && !Double.isNaN(fedAt) ? singleTarget - singleLow : Double.NaN);
        }
    }

    // The flywheel, the feeder and the turn stop; the group's numbers go back to the code's.
    private void outputsOff() {
        fire = Fire.NONE;
        RuntimeException first = null;
        try { if (part != null) launcher.stop(); } catch (RuntimeException e) { first = e; }
        try { if (aim != null) aim.mode().off(); } catch (RuntimeException e) { if (first == null) first = e; }
        try { if (driving != null) driving.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        try {
            if (part != null) part.continuous().boostRpm(codeBoost).feedPower(codeFeedPower);
        } catch (RuntimeException e) {
            if (first == null) first = e;
        }
        drove = false;
        if (first != null) throw first;
    }

    // The sticks drive only on the "place the robot" screen (30%); while it shoots, only the aim turns it.
    private void drive() {
        if (session.stage() == Stage.FIRING && fire != Fire.NONE) {
            driving.drive(0, 0, aim.turnFor(0), 1);
            drove = true;
        } else if (session.needsPlace() && inputsReady()) {
            driving.update(gamepad1, GroupShotSession.PLACE_POWER);
            drove = true;
        } else if (drove) {
            driving.stop();
            drove = false;
        }
    }

    // ---------------------------------------------------------------- buttons

    private static final int A = 0, B = 1, X = 2, UP = 6, DOWN = 7, LEFT = 8, RIGHT = 9;

    // ANY new press stops a countdown or a shot. Otherwise A, X and the D-pad do what the screen says.
    private void buttons() {
        boolean[] now = FlywheelTunerOpMode.pressedNow(gamepad1, gamepad2);
        boolean[] before = was;
        was = now;
        // Held through START, or through help closing: nothing counts until a fresh press.
        if (before == null || !inputsReady()) return;
        boolean any = false;
        for (int i = 0; i < now.length; i++) if (now[i] && !before[i]) any = true;
        if (!any) return;
        if (session.moving()) {
            stopByButton();
            return;
        }
        if (session.stage() == Stage.FINISHED) {
            if (fresh(now, before, X)) {
                // X two times: a new session (the old file is kept). Once is not enough.
                if (newSessionAsked) newSession();
                else newSessionAsked = true;
                return;
            }
            newSessionAsked = false;
            if (fresh(now, before, RIGHT)) page = Math.min(page + 1, PAGES - 1);
            else if (fresh(now, before, LEFT)) page = Math.max(page - 1, 0);
            return;
        }
        if (session.stage() == Stage.ENTRY) {
            if (fresh(now, before, UP)) session.entryMove(-1);
            else if (fresh(now, before, DOWN)) session.entryMove(1);
            else if (fresh(now, before, RIGHT)) session.entryChange(1);
            else if (fresh(now, before, LEFT)) session.entryChange(-1);
            else if (fresh(now, before, A)) session.entrySave();
            else if (fresh(now, before, X)) session.entryUnsure();
            return;
        }
        if (fresh(now, before, A)) {
            if (!encoderProblem.isEmpty()) {
                // The person read the encoder screen and checked the wires: back to the shot screen. The next A
                // shoots again (its spin-up clears the flywheel's fault).
                encoderProblem = "";
                if (session.stage() == Stage.STOPPED) session.pressA(distanceNow, blocked());
                return;
            }
            if (!savedChanged.isEmpty()) {
                // A new session: the old file is kept under another name (a button press, not the loop).
                GroupShotResults.keepOld();
                savedChanged = "";
                return;
            }
            session.pressA(distanceNow, blocked());
        }
    }

    // A button while it moves: everything stops now. A check shot that already flew still asks where it went;
    // a group that already fed does not count.
    private void stopByButton() {
        if (fire == Fire.SINGLE && !Double.isNaN(fedAt)) {
            finishSingleOrGroup(true, "");
            return;
        }
        if (fire == Fire.GROUP && sawFeed) {
            part.continuous().stop(ContinuousFire.End.STOPPED);
            groupEnded();
            return;
        }
        session.stop(GroupShotSession.Stop.BUTTON);
        outputsOff();
    }

    private static boolean fresh(boolean[] now, boolean[] before, int i) {
        return now[i] && !before[i];
    }

    private static final int PAGES = 4;

    // A new, empty session for the robot as it is now; the finished one is kept as group-shot-tuner-old.txt.
    private void newSession() {
        newSessionAsked = false;
        GroupShotResults.keepOld();
        session.restart(new GroupShotResults(session.config().setup()));
        results.removeStartingWith("group:");
        results.notDone("the groups are not finished.");
        linesWritten = false;
        page = 0;
    }

    // ---------------------------------------------------------------- the lines to copy

    /** The exact Tunables.java lines for a winner: the control, its numbers, holding fire, the boost and feed. */
    List<String[]> winnerFields(Variant v) {
        List<String[]> out = new ArrayList<>();
        FlywheelSetting s = v.cand.setting;
        out.add(new String[] {"speedControl", s.control == SpeedControl.PID ? "\"PID\"" : "\"BANG_BANG\""});
        for (String line : FlywheelTunerOpMode.tunablesLines(s)) {
            // "public static double kP = 50;" -> {"kP", "50"}
            String body = line.substring(line.indexOf("double ") + 7, line.length() - 1);
            out.add(new String[] {body.substring(0, body.indexOf(" = ")), body.substring(body.indexOf(" = ") + 3)});
        }
        out.add(new String[] {"continuousOnHold", "true"});
        out.add(new String[] {"continuousBoostRpm", FinderResults.num(v.boostRpm, 0)});
        if (!v.fullPace() && !Double.isNaN(baseFeed)) {
            out.add(new String[] {"continuousFeedPower", FinderResults.exact(v.feedShare * baseFeed, 2)});
        }
        return out;
    }

    private void writeResults() {
        linesWritten = true;
        results.removeStartingWith("group:");
        Step end = session.plan();
        if (end.kind != Kind.FINISHED) return;
        if (end.winners.isEmpty()) {
            results.notDone("no setting scored every ball. Keep today's settings.");
            return;
        }
        for (String[] f : winnerFields(end.winners.get(0))) results.setTunable("group:" + f[0], launcher.name(), f[0], f[1]);
        results.done();
    }

    // ---------------------------------------------------------------- the screens

    @Override
    protected void showInit(RobotTelemetry t) {
        warnLines(t, problem);
        t.warn("It SHOOTS real balls. Safety glasses.");
        t.warn("Nobody in front of the robot.");
        t.warn("It turns the robot to the goal by itself.");
        t.text("Group Shot Tuner: groups of 4 balls with");
        t.text("each speed setting. Finds the one that scores.");
        if (session == null) return;
        t.text("Have 12 balls ready.");
        // X = BLUE, B = RED (a fresh press; not with START held: START + B pairs a gamepad).
        boolean x = gamepad1.x && !gamepad1.start, b = gamepad1.b && !gamepad1.start;
        Alliance was = alliance;
        if (x && !xWas) alliance = Alliance.BLUE;
        if (b && !bWas) alliance = Alliance.RED;
        xWas = x;
        bWas = b;
        robotDef.setAlliance(alliance);
        // Another alliance: the saved groups count only for the alliance they were shot on.
        if (alliance != was) loadSession();
        t.line("Alliance", alliance + "  ({X} = blue, {B} = red)");
        robotCamera.checkInInit(t);
        showOffset(t);
        showTrim(t);
        textLines(t, flywheelTunerNote);
        if (!savedChanged.isEmpty()) t.text("Saved groups used other settings: a new start.");
        else if (session.stage() == Stage.FINISHED) t.text("Saved: it is finished. {PLAY} = results.");
        else if (!session.results().isEmpty()) t.text("Saved groups: it goes on where it stopped.");
        t.text("After {PLAY} the sticks drive (30% power).");
        t.text("Nothing else moves until you press {A}.");
        t.text("ANY button stops it.");
    }

    // The aim offset: the code's; a saved one that differs means no shot counts, and the fix.
    private void showOffset(RobotTelemetry t) {
        if (Double.isNaN(savedOffset)) {
            t.line("Aim offset", AimTunerOpMode.side(codeOffset) + ", from the code: OK");
            return;
        }
        t.warn("Aim offset NOT the same. In the code:");
        t.warn("  " + AimTunerOpMode.side(codeOffset) + ". Saved in the tuning menu:");
        t.warn("  " + AimTunerOpMode.side(savedOffset) + ".");
        t.text("Your drivers' last number is the saved one.");
        t.text("Copy it into " + tunablesFile + ", install, then");
        t.text("erase the saved numbers in the tuning menu.");
        t.text("(This tool changes neither.)");
    }

    // A speed trim for this alliance in the code: no shot counts until it is 0 for the test.
    private void showTrim(RobotTelemetry t) {
        double trim = launcher.allianceTrim();
        if (trim == 0) return;
        String field = alliance == Alliance.RED ? "redTrimPercent" : "blueTrimPercent";
        t.warn("The code makes " + alliance + " shots " + FinderResults.num(trim, 1) + "% faster.");
        t.text("The table is tested with no trim.");
        t.text("Set " + field + " = 0 for this test.");
    }

    @Override
    protected String stoppedWords() {
        return "Power is OFF. The flywheel may still spin.";
    }

    @Override
    protected boolean showsResults() {
        return false;
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (session == null) {
            warnLines(t, problem);
            return;
        }
        if (!session.results().isEmpty() && !GroupShotResults.lastSaveWorked()) {
            t.warn("Could NOT save on the Control Hub.");
            t.warn("Take a photo of each result screen.");
        }
        if (!savedChanged.isEmpty()) {
            t.section("Group Shot Tuner: settings changed");
            t.text("The saved groups used other settings");
            t.text("(" + savedChanged + ").");
            t.text("Those groups cannot be used now.");
            t.text("Next: {A} = start a new session.");
            t.text("(The old file is kept: group-shot-tuner-old.txt)");
            return;
        }
        if (!encoderProblem.isEmpty() && !session.moving() && session.stage() != Stage.ENTRY) {
            t.warn("Power is OFF. The flywheel may still spin.");
            t.section("Group Shot Tuner: STOPPED");
            t.warn("The flywheel had power, but its speed");
            t.warn("read 0 or backward.");
            // The guard's own words, without a second period at the end.
            t.text("(" + encoderProblem.replaceAll("[.\\s]+$", "") + ")");
            t.text("Next: check the flywheel encoder cable.");
            t.text("Still wrong? Press STOP, run \"Encoder Check\".");
            t.text("{A} = I checked it (the next {A} shoots).");
            return;
        }
        switch (session.stage()) {
            case COUNTDOWN: showCountdown(t); break;
            case FIRING: showFiring(t); break;
            case ENTRY: showEntry(t); break;
            case STOPPED: showStopped(t); break;
            case FINISHED: showResults(t); break;
            default: showReady(t);
        }
    }

    private String spotWords(int spot) {
        return "spot " + (spot + 1) + " of " + session.config().spots.length + " ("
                + FinderResults.num(session.config().spots[spot], 1) + " in)";
    }

    private void showReady(RobotTelemetry t) {
        Step s = session.plan();
        if (s.kind == Kind.CHECK_FAILED) {
            int[] set = session.checkSetNow(s.spot);
            t.warn("Only " + set[1] + " of " + GroupShotSession.CHECK_SHOTS + " check shots scored.");
            t.section("Group Shot Tuner: " + spotWords(s.spot) + ": STOPPED");
            t.text("Groups cannot be judged until single");
            t.text("shots score. Fix the shot table or the aim");
            t.text("first (Shot Tuner, Aim Tuner).");
            t.text("Next: {A} = 4 check shots again here.");
            return;
        }
        if (session.needsPlace()) {
            t.warn("The STICKS drive (30% power).");
            t.warn("Clear floor around the robot.");
            t.section("Group Shot Tuner: " + spotWords(s.spot));
            t.text("Next: drive until the camera is "
                    + FinderResults.num(session.config().spots[s.spot], 1) + " in");
            t.text("from the goal, facing it. Then {A}.");
            showDistance(t, s.spot);
            textLines(t, session.refusal());
            textLines(t, session.note());
            textLines(t, flywheelTunerNote);
            return;
        }
        boolean group = s.kind == Kind.GROUP;
        t.warn("{A} = SHOOT " + (group ? "4 balls" : "1 ball") + ", after a 3 s countdown.");
        t.warn("ANY button stops it. Nobody in front.");
        t.section("Group Shot Tuner: " + spotWords(s.spot) + (group && s.round > 1 ? ", round " + s.round : ""));
        if (group) {
            int[] g = session.groupsAt(s.round, s.spot, s.variants);
            t.text("Group " + (g[0] + 1) + " of " + g[1] + ". Setting: " + s.variant.label() + ".");
            t.text("Next: load exactly 4 balls. Then {A}.");
            if (s.round > 1) t.text("Round " + s.round + ": no setting scored every ball yet.");
            int bad = session.notCounted();
            if (bad > 0) t.line("Not counted so far", bad + " (each one runs again)");
        } else {
            int[] set = session.checkSetNow(s.spot);
            t.text("Check shot " + (set[0] + 1) + " of " + GroupShotSession.CHECK_SHOTS + ", with today's settings.");
            t.text("Next: load ONE ball. Then {A}.");
            if (set[0] > 0) t.line("Check shots", set[1] + " of " + set[0] + " scored (3 of 4 needed)");
            t.text("4 shots is a small sample: 3 must score.");
        }
        showDistance(t, s.spot);
        textLines(t, session.refusal());
        textLines(t, session.note());
        String b = blocked();
        if (!b.isEmpty()) warnLines(t, b);
        if (!Double.isNaN(savedOffset)) showOffset(t);
        else if (launcher.allianceTrim() != 0) showTrim(t);
    }

    // "Camera distance : 27.4 in: OK (spot 27.0, within 1.5)"
    private void showDistance(RobotTelemetry t, int spot) {
        if (Double.isNaN(distanceNow)) {
            t.line("Camera distance", "goal NOT seen");
            return;
        }
        double off = distanceNow - session.config().spots[spot];
        boolean ok = Math.abs(off) <= GroupShotSession.SPOT_TOLERANCE_IN;
        t.line("Camera distance", FinderResults.num(distanceNow, 1) + " in: " + (ok ? "OK" : "NOT at the spot")
                + " (within " + FinderResults.num(GroupShotSession.SPOT_TOLERANCE_IN, 1) + ")");
        if (!ok) t.text(GroupShotSession.moveWords(off));
    }

    private void showCountdown(RobotTelemetry t) {
        Step s = session.firing();
        t.warn("Starting in " + session.countdownLeft() + " ...   ANY button = cancel");
        t.warn("Nobody in front. The robot turns by itself.");
        if (s == null) return;
        t.section("Group Shot Tuner: " + spotWords(s.spot));
        t.text(s.kind == Kind.GROUP ? "It shoots 4 balls: " + s.variant.label() + "." : "It shoots 1 check shot.");
    }

    private void showFiring(RobotTelemetry t) {
        Step s = session.firing();
        t.warn("SHOOTING. ANY button stops it.");
        t.warn("Nobody in front. The robot turns by itself.");
        if (s == null) return;
        t.section("Group Shot Tuner: " + spotWords(s.spot));
        String doing = fire == Fire.SPIN ? "speeding up and aiming"
                : fire == Fire.SINGLE ? (Double.isNaN(fedAt) ? "waiting to shoot" : "shot fired")
                : part.continuous().isFeeding() ? "feeding the balls, no break" : "waiting to start the feed";
        t.line("Doing", doing);
        t.line("Speed", FlywheelTunerOpMode.speedWords(flywheel.getRpm(), flywheel.getTargetRpm(), flywheel.getTolerance()));
        ShotSight sight = aim.shotSight();
        if (!sight.goalSeen) {
            t.line("Aim", "goal NOT seen");
        } else {
            double off = Math.abs(sight.aimErrorDeg);
            t.line("Aim", FinderResults.num(off, 1) + " deg off: " + (sight.aimed ? "OK" : "NOT aimed")
                    + " (limit " + FinderResults.num(sight.toleranceDeg, 1) + ")");
        }
    }

    private void showEntry(RobotTelemetry t) {
        Step s = session.firing();
        boolean group = session.entryIsGroup();
        t.warn("Power is OFF. Nothing moves now.");
        t.section(group ? "Group Shot Tuner: where did the 4 go?" : "Group Shot Tuner: where did it go?");
        if (s != null) t.text((group ? "Group: " + s.variant.label() : "Check shot") + ", " + spotWords(s.spot) + ".");
        if (group) {
            t.text("D-pad up/down = choose a line.");
            t.text("D-pad right = add a ball. Left = take one off.");
        } else {
            t.text("D-pad up/down = choose. {A} = save.");
        }
        int[] c = session.counts();
        for (Ball b : Ball.values()) {
            String arrow = session.cursor() == b.ordinal() ? "> " : "  ";
            t.text(arrow + b.words + (group ? " : " + c[b.ordinal()] : ""));
        }
        if (group) {
            int sum = GroupShotSession.total(c);
            t.line("Entered", sum + " of " + GroupShotSession.BALLS + (sum == GroupShotSession.BALLS ? ": {A} = save" : ""));
            t.text("Missed two ways? Enter its bigger miss.");
        }
        textLines(t, session.refusal());
        t.text("{X} = not sure: " + (group ? "repeat the group." : "shoot it again."));
    }

    private void showStopped(RobotTelemetry t) {
        t.warn("Power is OFF. The flywheel may still spin.");
        t.section("Group Shot Tuner: STOPPED");
        if (session.stopWhy() == GroupShotSession.Stop.HELP) t.text("Help was opened, so it stopped.");
        else t.text("You pressed a button, so it stopped.");
        t.text("The balls that did not fly are still in.");
        t.text("Saved shots and groups are kept.");
        t.text("Next: {A} = go on.");
    }

    private void showResults(RobotTelemetry t) {
        Step end = session.plan();
        String[] titles = {"result", "the lines to copy", "every setting", "what it cannot show"};
        t.section("Group Shot Tuner: " + titles[page] + " (page " + (page + 1) + " of " + PAGES + ")");
        if (page == 0) {
            if (end.winners.isEmpty()) {
                t.text("NOT DONE: no setting scored every ball.");
                t.text("Keep today's settings. Change nothing.");
                Object[] best = session.bestScore();
                if (best != null) {
                    t.text("Best: " + ((Variant) best[2]).label() + ":");
                    t.text("  " + best[0] + " of " + best[1] + " balls scored.");
                }
                t.text("Page 3: what the misses say.");
            } else {
                Variant w = end.winners.get(0);
                t.text("DONE");
                t.text("Winner: " + w.label() + ".");
                t.text("Every ball scored, 3 groups at each spot.");
                t.line("Feed", (w.fullPace() ? "full pace" : Math.round(w.feedShare * 100) + "% pace")
                        + ", up to " + FinderResults.num(part.continuous().getMaxSec(), 1) + " s a group");
                t.text("(the time the feeder was told to run)");
                t.text("3 groups is a small sample: check it");
                t.text("on the field before a match.");
            }
        } else if (page == 1) {
            if (end.winners.isEmpty()) {
                t.text("No winner: nothing to copy.");
                t.text("Keep today's settings.");
            } else {
                t.text("Open " + tunablesFile + ", find class " + launcher.name() + ".");
                t.text("Change these lines (not there? add them):");
                for (String[] f : winnerFields(end.winners.get(0))) {
                    for (String l : FinderResults.javaLines(f[0], f[1])) t.text("  " + l);
                }
            }
        } else if (page == 2) {
            List<Variant> seen = new ArrayList<>();
            List<List<Variant>> rounds = session.rounds();
            for (int r = 1; r <= rounds.size(); r++) {
                for (Variant v : rounds.get(r - 1)) {
                    int[] sum = GroupShotSession.sums(session.validGroups(r, v));
                    int balls = GroupShotSession.total(sum);
                    if (balls == 0) continue;
                    t.text(v.label() + ": " + sum[0] + " of " + balls + " scored.");
                    for (String a : session.advice(r, v)) t.text("  " + a);
                    seen.add(v);
                }
            }
            if (seen.isEmpty()) t.text("No group counted.");
        } else {
            t.text("Why a ball missed: you saw it, it cannot.");
            t.text("No ball sensor: it judges whole groups,");
            t.text("never one ball of a group.");
            t.text("The simulator cannot show real scoring.");
            t.text("Saved on the Control Hub: group-shot-tuner.txt");
        }
        t.text("D-pad right/left = next/back page.");
        t.text(newSessionAsked ? "Press {X} again: a NEW session (old one kept)."
                : "{X} two times = start a new session.");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Shoots groups of 4 with each speed setting.",
            "Safety glasses. Nobody in front.",
            "Place screen: sticks drive (30%). {A} = here.",
            "{A} = shoot (3 s countdown). Robot aims itself.",
            "ANY button stops it. {A} again goes on.",
            "Check shots: 4 singles, 3 must score.",
            "Groups: load exactly 4. 3 groups per setting.",
            "Entry: D-pad up/down = line, right = add a ball.",
            "{A} = save. {X} = not sure: repeat.",
            "A group with the aim off or the goal lost,",
            "or that stopped early, does not count.",
            "End: D-pad right = the lines to copy.",
            "Every shot is saved: it goes on after a stop.",
        };
    }

    // Help opened: everything stops, exactly like a button.
    @Override
    protected void pause() {
        if (session != null && session.moving()) {
            if (fire == Fire.GROUP && sawFeed) {
                part.continuous().stop(ContinuousFire.End.STOPPED);
                groupEnded();
            } else {
                session.stop(GroupShotSession.Stop.HELP);
            }
        }
        if (part != null) outputsOff();
    }

    @Override
    protected void stopAll() {
        if (session != null) session.stop(GroupShotSession.Stop.BUTTON);
        RuntimeException first = null;
        try { if (part != null) outputsOff(); } catch (RuntimeException e) { first = e; }
        try { if (robotDef != null && robotDef.all() != null) robotDef.all().stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        // The last saved shots, written now (the end of the OpMode: a short wait is fine here).
        if (session != null && !session.results().isEmpty()) session.results().saveNow();
        if (first != null) throw first;
    }
}
