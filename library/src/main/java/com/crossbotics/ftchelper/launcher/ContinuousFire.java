package com.crossbotics.ftchelper.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One group of continuous fire: aimed, at speed, then the feeder runs without a break until the group
 * ends. Get it from {@link Launcher#continuous()}: the Launcher runs it in its {@code update()}, so TeleOp
 * and the tuners run the same code.
 *
 * <pre>{@code
 * ContinuousFire group = launcher.continuous();
 * launcher.setDistance(36);                               // the table's speed
 * group.start(() -> robot.aim().shotSight());             // the button went down
 * // every loop: launcher.update();
 * group.stop();                                           // the button came up: the feed stops now
 * }</pre>
 *
 * <p><b>It starts feeding only when all of this is true:</b> a fresh goal that the camera itself sees (not the
 * odometry backup: a pose can drift), a distance the table reaches
 * (measured or an estimate), the aim inside its tolerance, and the launcher READY (the steady speed, the
 * hood, every check). No time limit lets it pass: after {@link Launcher#readyTimeout} it gives up and says
 * what was missing.
 *
 * <p>When the feed starts it keeps a {@link Snapshot}: the table, distance, speed and aim offset stay
 * the same to the end. There is no wait between pieces.
 *
 * <p><b>An ended group never starts again by itself.</b> {@link #start} is the only way, and code calls it
 * once for each press.
 *
 * <p><b>It cannot count balls.</b> The wheel gets no time to be "ready" between two pieces, so its shot
 * count does not see them, and a group adds nothing to {@link Launcher#getShotsFired()}, the shot stats
 * or a piece counter's feeds. {@link #getFeedSec()} is how long the feeder was told to run, not how long
 * the pieces took.
 *
 * Docs: docs/modules/launcher.md
 */
public final class ContinuousFire {

    /** IDLE = no group; WAITING = waiting to be aimed and ready, nothing fed; FEEDING = the feeder runs. */
    public enum State { IDLE, WAITING, FEEDING }

    /**
     * Why the last group ended. Each has two plain sentences for the Driver Station: what is wrong, and
     * what to do now. They are short, for a small screen.
     */
    public enum End {
        /** No group ended since the last start. */
        NONE("", ""),
        /** The button was let go. */
        RELEASED("fire button let go", ""),
        /** stop, idle or stopShooting. */
        STOPPED("launcher was stopped", ""),
        /** The action or code that ran it was canceled. */
        HALTED("action was canceled", "Aim at the goal."),
        /** The robot paused. */
        PAUSED("robot is paused", ""),
        /** The button map opened. */
        HELP("button list is open", "Close the button list."),
        /** The tuning menu opened. */
        TUNING_MENU("tuning menu is open", "Close the tuning menu."),
        /** The camera's lighting test started again. */
        RELIGHT("camera is testing the light", "Wait until the light test ends."),
        /** An eject started. */
        EJECT("pushing a piece out", "Wait until the piece is out."),
        /** The intake pushed out. */
        INTAKE_REVERSE("intake is running backward", "Stop running the intake backward."),
        /** Another shot table. */
        TABLE_CHANGED("shot table changed", "Check which shot table is on."),
        /** The alliance was switched. */
        ALLIANCE_CHANGED("alliance changed", "Turn to face your goal."),
        /** The speed control, its numbers or where the speed comes from changed. */
        CONTROL_CHANGED("speed setting changed", "Check the speed setting."),
        /** Another fire command. */
        OTHER_FIRE("another shot was asked for", "Use one fire button at a time."),
        /** The feeder jammed. */
        JAM("feeder is jammed", "Clear the jam."),
        /** The flywheel's encoder fault. */
        ENCODER_FAULT("bad wheel speed reading", "Switch the flywheel off, then on."),
        /** The wheel fell under half of its target. */
        WHEEL_SLOW("wheel below half speed", "Check for a jam or a low battery."),
        /** No goal for too long. */
        GOAL_LOST("goal NOT seen", "Turn the robot to face the goal."),
        /** The aim was out for too long. */
        AIM_LOST("NOT aimed", "Let go of the turn stick."),
        /** The robot's distance changed too much. */
        DISTANCE_MOVED("distance changed too much", "Hold the robot still."),
        /** The most time a group may feed. */
        MAX_TIME("time limit reached", "Let go of the fire button."),
        /** It never got aimed and ready. */
        READY_TIMEOUT("NOT ready in time", "Hold the robot still."),
        /** Something else stopped the feeder. */
        FEEDER_STOPPED("feeder was turned off", "Check your other feeder buttons."),
        /** This feeder cannot feed continuously. */
        NO_FEEDER("feeder can't run nonstop", "Tap fire for one piece."),
        /** A part threw an error. */
        ERROR("a part stopped working", "Read the error, then restart the program.");

        private final String problem, doThis;

        End(String problem, String doThis) {
            this.problem = problem;
            this.doThis = doThis;
        }

        /** What is wrong, e.g. "NOT aimed". */
        public String words() {
            return problem;
        }

        /** What to do now, e.g. "Let go of the turn stick."; "" when there is nothing to do. */
        public String whatToDo() {
            return doThis;
        }

        // True when a new press can work after doing that.
        boolean pressAgainHelps() {
            return this != NO_FEEDER && this != ERROR;
        }

        // Ends somebody asked for: no warning.
        boolean isAsked() {
            return this == NONE || this == RELEASED || this == STOPPED || this == PAUSED;
        }
    }

    /** What a group keeps from the moment its feed starts. It never changes. */
    public static final class Snapshot {
        /** The shot table. */
        public final ShotTable table;
        /** The distance the speed came from, inches. */
        public final double distanceIn;
        /** The final target for piece 1: the table, the trim and the limits included, RPM. */
        public final double targetRpm;
        /** The target while it feeds: targetRpm and the boost, inside the speed limit, RPM. */
        public final double feedRpm;
        /** The speed control. */
        public final Flywheel.SpeedControl control;
        /** The flywheel's {@link Flywheel#getControlRevision} at the start. */
        public final int controlRevision;
        /** The aim's camera offset, degrees; NaN = none. */
        public final double aimOffsetDeg;
        /** The feed power asked for; NaN = the feeder's own. */
        public final double feedPower;
        /** The most seconds it may feed. */
        public final double maxSec;

        Snapshot(ShotTable table, double distanceIn, double targetRpm, double feedRpm, Flywheel.SpeedControl control,
                 int controlRevision, double aimOffsetDeg, double feedPower, double maxSec) {
            this.table = table;
            this.distanceIn = distanceIn;
            this.targetRpm = targetRpm;
            this.feedRpm = feedRpm;
            this.control = control;
            this.controlRevision = controlRevision;
            this.aimOffsetDeg = aimOffsetDeg;
            this.feedPower = feedPower;
            this.maxSec = maxSec;
        }
    }

    /** A group ends when the camera has not seen the goal this long (the odometry backup does not count), seconds. */
    public static final double GOAL_LOST_SEC = 0.5;
    /** A group ends when the aim is out (tolerance + lock margin) this long, seconds. */
    public static final double AIM_LOST_SEC = 0.3;
    /** A group ends when the distance moved more than this since it started, inches. */
    public static final double DISTANCE_MOVE_IN = 4;
    /** The most seconds one group feeds, unless {@link #maxSec} says otherwise. */
    public static final double DEFAULT_MAX_SEC = 3;
    // A little room for adding up loop times (0.1 + 0.1 + 0.1 is not exactly 0.3).
    private static final double TIME_MARGIN = 1e-9;
    /** The last line of a problem that a new press solves. */
    static final String PRESS_AGAIN = "Then press fire again.";

    private final Launcher launcher;
    private double maxSec = DEFAULT_MAX_SEC, feedPower = Double.NaN, boostRpm = 0;

    private State state = State.IDLE;
    private End end = End.NONE;
    // When the end has a more exact problem (a timeout: what was missing): that, and what to do about it.
    private String endProblem = "", endDoThis = "";
    private Supplier<ShotSight> sightFrom = null;
    private ShotSight sight = ShotSight.NONE;
    private Snapshot snapshot = null;
    private int revisionAtStart = 0;
    private double waitingSince = Double.NaN;
    private double feedAt = Double.NaN, feedEndedAt = Double.NaN;
    private double goalGoneSince = Double.NaN, aimOutSince = Double.NaN;

    ContinuousFire(Launcher launcher) {
        this.launcher = launcher;
    }

    // ---- Settings (a running group keeps the ones it started feeding with) ----

    /**
     * The most one group may feed, seconds. Default 3. It is a safety limit, not a number of pieces.
     *
     * @param seconds above 0, at most 10
     */
    public ContinuousFire maxSec(double seconds) {
        if (!(seconds > 0 && seconds <= 10)) throw new IllegalArgumentException("Wrong fire time limit: " + seconds + " seconds.\nUse more than 0, at most 10.");
        maxSec = seconds;
        return this;
    }

    /**
     * The feeder's power during a group: the last resort when full pace fails (a slower feed).
     *
     * @param power -1 to 1 but not 0, with the feeder's own sign; NaN = the feeder's power (the default)
     */
    public ContinuousFire feedPower(double power) {
        if (!Double.isNaN(power) && (power == 0 || Math.abs(power) > 1)) {
            throw new IllegalArgumentException("Wrong nonstop feed power: " + power + ".\nUse the feeder's direction.\nUse a size up to 1, but not 0.");
        }
        feedPower = power;
        return this;
    }

    /**
     * RPM added to the target from the moment the feed starts: never before piece 1 is READY, never past the
     * speed limit, and gone when the group ends. Default 0.
     *
     * @param rpm 0 or more
     */
    public ContinuousFire boostRpm(double rpm) {
        if (!(rpm >= 0) || Double.isInfinite(rpm)) throw new IllegalArgumentException("Wrong extra wheel speed: " + rpm + " RPM.\nUse 0 or more.");
        boostRpm = rpm;
        return this;
    }

    /** The time limit set with {@link #maxSec}, seconds. */
    public double getMaxSec() { return maxSec; }
    /** The power set with {@link #feedPower}; NaN = the feeder's own. */
    public double getFeedPower() { return feedPower; }
    /** The boost set with {@link #boostRpm}, RPM. */
    public double getBoostRpm() { return boostRpm; }

    // ---- Commands ----

    /** True if this launcher's feeder can feed without a break. */
    public boolean canFire() {
        return launcher.feeder().canFeedContinuously();
    }

    /**
     * Asks for one group: spins up, drops single shots still waiting, and feeds once the strict start
     * rule is true (see the class text). It never feeds in this call. Call it once for each press: calling
     * it every loop would start a new group after each end.
     *
     * @param sightEveryLoop asked once a loop for the goal, distance and aim, e.g.
     *                       {@code () -> robot.aim().shotSight()}
     * @return true if a group waits or feeds now; false if it was refused ({@link #getEnd} says why)
     */
    public boolean start(Supplier<ShotSight> sightEveryLoop) {
        if (sightEveryLoop == null) throw new IllegalArgumentException("Nonstop fire has no aim to check.\nGive start(...) the goal and aim.\nExample: robot.launcher::shotSight");
        if (isActive()) return true;
        end = End.NONE;
        endProblem = endDoThis = "";
        snapshot = null;
        feedAt = feedEndedAt = Double.NaN;
        if (!canFire()) return refuse(End.NO_FEEDER);
        if (launcher.getFlywheel().isFaulted()) return refuse(End.ENCODER_FAULT);
        launcher.dropWaitingShots();    // only now: a refused hold does not take away the taps' shots
        sightFrom = sightEveryLoop;
        sight = ShotSight.NONE;
        revisionAtStart = launcher.getFlywheel().getControlRevision();
        waitingSince = launcher.now();
        goalGoneSince = aimOutSince = Double.NaN;
        launcher.feeder().clearJam();
        launcher.clearProblem();
        launcher.spinUp();
        state = State.WAITING;
        return true;
    }

    /** The button was let go: the feed stops now, in this call. */
    public void stop() {
        stop(End.RELEASED);
    }

    /**
     * Ends the group for this reason: the feed stops now, in this call. With no group it does nothing.
     *
     * @param why shown on the Driver Station and kept in {@link #getEnd}
     */
    public void stop(End why) {
        finish(why == null ? End.STOPPED : why);
    }

    // ---- Questions ----

    /** IDLE, WAITING or FEEDING. */
    public State getState() { return state; }
    /** True while a group waits or feeds. */
    public boolean isActive() { return state != State.IDLE; }
    /** True while the feeder runs for a group (a feed stop may still be opening). */
    public boolean isFeeding() { return state == State.FEEDING; }
    /** Why the last group ended ({@link End#NONE} while one runs, and before the first). */
    public End getEnd() { return end; }

    /**
     * The last end in one line: "Firing stopped: goal NOT seen", or "Did NOT fire: NOT aimed" when it
     * never fed. "" for none.
     */
    public String getEndWords() {
        if (end == End.NONE) return "";
        return (Double.isNaN(feedAt) ? "Did NOT fire: " : "Firing stopped: ") + endProblem;
    }

    /**
     * The last end for the Driver Station, one idea a line: what is wrong, what to do, and "Then press
     * fire again." Empty for none, and for an end somebody asked for (let go, stop, pause).
     *
     * <pre>{@code
     * for (String line : group.getEndLines()) t.warn(line);
     * }</pre>
     */
    public List<String> getEndLines() {
        List<String> out = new ArrayList<>();
        if (end.isAsked()) return out;
        out.add(getEndWords());
        out.add(endDoThis);
        if (end.pressAgainHelps()) out.add(PRESS_AGAIN);
        return out;
    }

    /** The numbers the feed started with; kept after the end until the next start. Null before a feed. */
    public Snapshot getSnapshot() { return snapshot; }
    /** The sight used in the last update (for a tuner's log). */
    public ShotSight getSight() { return sight; }

    /** Seconds the feeder was commanded to run for this (or the last) group; 0 if it never fed. */
    public double getFeedSec() {
        if (Double.isNaN(feedAt)) return 0;
        return (isFeeding() ? launcher.now() : feedEndedAt) - feedAt;
    }

    /**
     * The first thing wrong while a group waits, said as a failure: "NOT aimed", "goal NOT seen". "" when
     * none waits, or nothing is wrong.
     */
    public String whyNotReady() {
        String[] wrong = firstProblem();
        return wrong == null ? "" : wrong[0];
    }

    /** What to do about {@link #whyNotReady()}, e.g. "Turn the robot to face the goal."; "" when nothing is wrong. */
    public String whatToDo() {
        String[] wrong = firstProblem();
        return wrong == null ? "" : wrong[1];
    }

    // {what is wrong, what to do}, or null. One at a time, in the order a driver fixes them.
    private String[] firstProblem() {
        if (state != State.WAITING) return null;
        // The camera itself must see the goal: an odometry pose may have drifted (the backup never starts a group).
        if (!sight.goalSeen || !sight.fromCamera) return problem(End.GOAL_LOST);
        if (!sight.usable()) return new String[] {"NO distance to the goal", "Keep the camera pointed at the goal."};
        ShotTable table = launcher.getTable();
        double shot = launcher.getShotDistance();
        if (table == null || table.points().isEmpty() || Double.isNaN(shot)) {
            return new String[] {"NO speed for this distance", "Turn on speed from distance."};
        }
        if (Math.abs(sight.distanceIn - shot) > DISTANCE_MOVE_IN) {
            return new String[] {"distance still changing", "Hold the robot still."};
        }
        if (!table.reaches(shot)) {
            return shot < table.points().get(0).distance
                    ? new String[] {"too close to the goal", "Back away from the goal."}
                    : new String[] {"too far from the goal", "Drive closer to the goal."};
        }
        if (!(launcher.getShotRpm() > 0)) return new String[] {"shot speed is 0 RPM", "Fix the shot table."};
        if (!sight.aimed) return problem(End.AIM_LOST);
        if (!launcher.wheelAtSpeed()) return new String[] {"wheel NOT at speed", "Wait. Still slow? Check the battery."};
        if (launcher.hoodMoving()) return new String[] {"hood NOT in place", "Wait. Still moving? Check the hood."};
        String check = launcher.failedCheck();
        if (!check.isEmpty()) return new String[] {"Still waiting for: " + check, "Fix that."};
        if (launcher.feeder().isBusy()) return new String[] {"last piece still feeding", "Wait a moment."};
        return null;
    }

    private static String[] problem(End end) {
        return new String[] {end.words(), end.whatToDo()};
    }

    // ---- The Launcher's loop ----

    // After the flywheel read its speed and BEFORE the feeder's update: a group that ends here feeds no more.
    void beforeFeeder(double now) {
        if (state == State.IDLE) return;
        Flywheel wheel = launcher.getFlywheel();
        if (wheel.isFaulted()) {
            finish(End.ENCODER_FAULT);
            return;
        }
        if (wheel.getControlRevision() != revisionAtStart) {
            finish(End.CONTROL_CHANGED);
            return;
        }
        ShotSight seen = sightFrom.get();
        sight = seen == null ? ShotSight.NONE : seen;
        if (state != State.FEEDING) return;
        if (!launcher.feeder().isFeedingContinuously()) {
            finish(launcher.feeder().isJammed() ? End.JAM : End.FEEDER_STOPPED);
            return;
        }
        // (Written with "!": a speed that is not a number counts as too slow.)
        // Half of piece 1's target, not the boosted one: the boost itself must not end the group.
        if (!(wheel.getRpm() >= 0.5 * snapshot.targetRpm)) {
            finish(End.WHEEL_SLOW);
            return;
        }
        // The "goal lost" clock counts the CAMERA's loss: the odometry backup may bridge the aim and the
        // distance, but it does not reset the clock.
        if (sight.usable() && sight.fromCamera) {
            goalGoneSince = Double.NaN;
        } else if (Double.isNaN(goalGoneSince)) {
            goalGoneSince = now;
        } else if (now - goalGoneSince > GOAL_LOST_SEC + TIME_MARGIN) {
            finish(End.GOAL_LOST);
            return;
        }
        if (sight.usable()) {
            if (Math.abs(sight.distanceIn - snapshot.distanceIn) > DISTANCE_MOVE_IN) {
                finish(End.DISTANCE_MOVED);
                return;
            }
            if (Math.abs(sight.aimErrorDeg) <= sight.toleranceDeg + sight.lockMarginDeg) {
                aimOutSince = Double.NaN;
            } else if (Double.isNaN(aimOutSince)) {
                aimOutSince = now;
            } else if (now - aimOutSince > AIM_LOST_SEC + TIME_MARGIN) {
                finish(End.AIM_LOST);
                return;
            }
        }
        if (now - feedAt >= snapshot.maxSec - TIME_MARGIN) finish(End.MAX_TIME);
    }

    // After the feeder's update: a jam it found, or the strict start.
    void afterFeeder(double now) {
        if (state == State.IDLE) return;
        Feeder feeder = launcher.feeder();
        if (feeder.isJammed()) {
            finish(End.JAM);
        } else if (state == State.FEEDING) {
            if (!feeder.isFeedingContinuously()) finish(End.FEEDER_STOPPED);
        } else {
            String[] wrong = firstProblem();
            if (wrong == null) beginFeeding(now);
            else if (now - waitingSince > launcher.getReadyTimeout()) finish(End.READY_TIMEOUT, wrong[0], wrong[1]);
        }
    }

    private void beginFeeding(double now) {
        Flywheel wheel = launcher.getFlywheel();
        double target = wheel.getTargetRpm();
        double top = wheel.getMaxRpm(), limit = launcher.getSpeedLimit();
        if (!Double.isNaN(limit)) top = Math.min(top, limit);
        snapshot = new Snapshot(launcher.getTable(), launcher.getShotDistance(), target,
                Math.min(top, target + boostRpm), wheel.getSpeedControl(), revisionAtStart, sight.aimOffsetDeg,
                feedPower, maxSec);
        // FEEDING first: if the feeder fails half started (one of two servos), the end still stops it.
        state = State.FEEDING;
        feedAt = now;
        if (!launcher.feeder().startContinuous(feedPower)) finish(End.FEEDER_STOPPED);
    }

    private void finish(End why) {
        finish(why, why.words(), why.whatToDo());
    }

    // Everything that ends a group comes here. The state changes first, so a stop that throws
    // still leaves no group that could feed again.
    private void finish(End why, String problem, String doThis) {
        if (state == State.IDLE) return;
        boolean wasFeeding = state == State.FEEDING;
        state = State.IDLE;
        sightFrom = null;
        end = why;
        endProblem = problem;
        endDoThis = doThis;
        double now = launcher.now();
        if (wasFeeding) feedEndedAt = now;
        if (!why.isAsked()) launcher.showProblem(getEndLines());
        // A group that only waited never ran the feeder: a single shot still finishing is left alone.
        if (wasFeeding) {
            launcher.groupFed(now);
            launcher.feeder().stopContinuous();
        }
    }

    private boolean refuse(End why) {
        end = why;
        endProblem = why.words();
        endDoThis = why.whatToDo();
        launcher.showProblem(getEndLines());
        return false;
    }
}
