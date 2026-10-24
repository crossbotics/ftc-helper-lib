package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Counts the game pieces the robot holds and knows the kind of the NEXT one to fire. A
 * {@link PieceSensor} at the intake counts each piece coming in (and its kind), and each piece pushed
 * back out through the intake; every feed of an {@link Indexer} counts one going out once it really
 * went (the ready sensor sees it leave, or the feed ended without a jam); an optional sensor at the
 * "ready to fire" slot says what is next for sure.
 *
 * <pre>{@code
 * public Pieces pieces;                                                   // Robot.java
 * pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
 *         .comingIn(PieceSensor.color("intake_color"))
 *         .readyToFire(PieceSensor.color("ready_color"))                 // optional
 *         .max(4)                                                         // the game's limit
 *         .startsWith(4, "POLLEN"));                                      // the preload
 *
 * robot.pieces.count();      // 3
 * robot.pieces.next();       // "POLLEN", Pieces.UNKNOWN or Pieces.NONE
 * robot.pieces.isFull();     // true at max
 * }</pre>
 *
 * <p>Hooks for other parts: {@link #nextKind()} (a {@code Supplier<String>}, e.g. to pick the shot
 * table), {@link #whenFull()}, {@link #whenEmpty()}, {@link #whenNextIs(String)} (BooleanSuppliers,
 * e.g. for LEDs or {@code Intake.stopsWhen}). No Tunables of its own (its sensors have theirs).
 * Suggested button: hold operator left-stick button 1 s = count 0 (the count went wrong).
 *
 * <p>The count is a helper, not a guarantee: a miscount happens (two touching pieces, a piece that
 * bounced out). The drivers, not the count, are responsible for the game's limit (BIOBUZZ G407) until
 * the robot has a hardware stop.
 *
 * Docs: docs/modules/robot.md (section "Pieces and PieceSensor")
 */
public final class Pieces extends Module<Pieces> {
    /** No piece. */
    public static final String NONE = "NONE";
    /** A piece the sensor could not tell (not one of the kinds, or not tuned yet). */
    public static final String UNKNOWN = "UNKNOWN";

    private final List<String> kinds;
    private PieceSensor in = null, ready = null;
    private int max = 0;                 // 0 = no limit
    // The intake's numbers (Intake.holds), used when max(...) is not given; kind limits always.
    private int intakeHolds = 0;
    private java.util.Map<String, Integer> kindLimits = new java.util.LinkedHashMap<>();

    /** The most pieces the game allows a robot at a time (BIOBUZZ G407). */
    public static final int GAME_LIMIT = 4;
    private final List<String> startWith = new ArrayList<>();
    // The pieces held, oldest first: the first one is the next to fire.
    private final List<String> held = new ArrayList<>();
    // The intake sensor's last steady answer, to see a NEW piece arrive.
    private String lastIn = NONE;
    private int totalIn = 0, totalOut = 0;
    // Just fired: the ready sensor may still see the piece that left, so it is not trusted until it sees
    // the slot empty, or a new steady reading that began after (the next piece). A feed that ends with
    // nothing counted (a jam) trusts it again at once.
    private boolean readyStale = false;
    private long staleFrom = 0;
    // A feed started and not yet counted: did the ready sensor see a piece in it, and did it see it leave?
    private int feeding = 0;
    private boolean feedSawPiece = false, feedLeft = false;
    // The intake sensor's arrivals already counted, and the kind written down for the latest one.
    private int lastArrivals = 0;
    private String inRecorded = NONE;
    // The intakes (pieces leave through them while they push out) and when one last pushed out (ns).
    private final List<Intake> intakes = new ArrayList<>();
    private long lastOutAt = Long.MIN_VALUE;
    private int pushedOut = 0;
    /** Pieces still roll out this long after the intake stops pushing out. */
    static final long OUT_COAST_NS = 300_000_000L;
    LongSupplier clock = System::nanoTime;

    private Pieces(List<String> kinds) {
        super("pieces");
        this.kinds = kinds;
    }

    /**
     * The kinds of piece this season, as names: letters, digits and _ only (they are also the
     * Tunables fields of the sensors), e.g. {@code Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")}.
     */
    public static Pieces kinds(String... names) {
        return new Pieces(checkKinds(names));
    }

    /** The sensor that sees each piece come in (at or just past the intake). Each new piece there counts one. */
    public Pieces comingIn(PieceSensor sensor) {
        in = takeSensor(sensor, "intake");
        return this;
    }

    /** The sensor at the "ready to fire" slot: it says for sure what fires next. Optional. */
    public Pieces readyToFire(PieceSensor sensor) {
        ready = takeSensor(sensor, "ready");
        return this;
    }

    private PieceSensor takeSensor(PieceSensor s, String spot) {
        if (s == in || s == ready) throw new IllegalStateException("Robot.java: " + s.device + " is used twice in Pieces");
        if (s.kindNames().isEmpty()) s.kinds(kinds.toArray(new String[0]));
        s.spot = spot;
        return s;
    }

    /**
     * The most pieces the robot may hold (BIOBUZZ G407: 4). {@link #isFull()} is true at this count.
     *
     * @param pieces at least 1
     */
    public Pieces max(int pieces) {
        if (pieces < 1) throw new IllegalArgumentException("Robot.java: Pieces.max needs at least 1");
        max = pieces;
        return this;
    }

    /**
     * The pieces in the robot at INIT (a preload), e.g. {@code startsWith(4, "POLLEN")}.
     *
     * @param count how many
     * @param kind  their kind (one of the kinds, or UNKNOWN)
     */
    public Pieces startsWith(int count, String kind) {
        checkKind(kind);
        for (int i = 0; i < count; i++) startWith.add(kind);
        return this;
    }

    @Override
    List<Module<?>> children() {
        List<Module<?>> out = new ArrayList<>();
        if (in != null) out.add(in);
        if (ready != null) out.add(ready);
        return out;
    }

    @Override
    List<String> deviceNames() {
        return Collections.emptyList();   // its sensors have their own
    }

    @Override
    void build(Devices devices, TunableSection t) {
        held.clear();
        held.addAll(startWith);
        // Every feed of an indexer (alone or in a launcher) sends one piece out; an intake that pushes
        // out sends pieces out the other way.
        if (robot != null) {
            Indexer.FeedWatcher watcher = new Indexer.FeedWatcher() {
                @Override public void started() { feedStarted(); }
                @Override public void ended(boolean fired) { feedEnded(fired); }
            };
            for (Module<?> m : robot.modules()) {
                List<Module<?>> group = new ArrayList<>(m.children());
                group.add(m);
                for (Module<?> g : group) {
                    if (g instanceof Indexer) ((Indexer) g).watchFeeds(watcher);
                    if (g instanceof Intake) intakes.add((Intake) g);
                }
            }
        }
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        plan.operator().leftStickButton.heldFor(1.0, this::clear).says("piece count = 0");
    }

    // ----------------------------- every loop -----------------------------

    @Override
    public void update() {
        if (in != null) {
            in.update();
            // A sensor with no recent reading (unplugged, missing) counts nothing.
            if (in.isWorking()) watchIntake();
        }
        if (ready != null) {
            ready.update();
            if (ready.isWorking()) watchReady();
        }
    }

    // The intake sensor: each new piece counts one in; while the intake pushes out, each piece that
    // leaves counts one out (the newest), and nothing counts in.
    private void watchIntake() {
        String now = in.kind();
        int arrivals = in.arrivals();
        int newOnes = arrivals - lastArrivals;
        lastArrivals = arrivals;
        if (runningOut()) {
            int out = 0;
            // A new piece behind one that was there (a different kind, or a distance dip): that one left.
            if (newOnes > 0) out += lastIn.equals(NONE) ? newOnes - 1 : newOnes;
            if (!lastIn.equals(NONE) && now.equals(NONE)) out++;
            for (int i = 0; i < out && !held.isEmpty(); i++) {
                held.remove(held.size() - 1);
                pushedOut++;
            }
            inRecorded = NONE;
        } else {
            for (int i = 0; i < newOnes; i++) pieceIn(now);
            if (newOnes > 0) inRecorded = held.isEmpty() ? NONE : held.get(held.size() - 1);
            // The same piece, now told apart (or its colour after a dip counted it): write its kind down.
            else if (!now.equals(NONE) && !now.equals(UNKNOWN) && !now.equals(inRecorded) && !inRecorded.equals(NONE)
                    && !held.isEmpty() && held.get(held.size() - 1).equals(inRecorded)) {
                held.set(held.size() - 1, now);
                inRecorded = now;
            }
        }
        if (now.equals(NONE)) inRecorded = NONE;
        lastIn = now;
    }

    // True while an intake pushes out, and a moment after (pieces still roll).
    private boolean runningOut() {
        long now = clock.getAsLong();
        for (Intake i : intakes) {
            if (i.isPushingOut()) {
                lastOutAt = now;
                return true;
            }
        }
        return lastOutAt != Long.MIN_VALUE && now - lastOutAt < OUT_COAST_NS;
    }

    // The ready sensor: a feed's piece leaving, and the last word on the next piece.
    private void watchReady() {
        String raw = ready.rawKind();
        if (feeding > 0 && !feedLeft) {
            if (!raw.equals(NONE)) {
                feedSawPiece = true;
            } else if (feedSawPiece && (ready.rawRun() >= 2 || ready.kind().equals(NONE))) {
                // The piece being fed has left the slot: count it out now.
                feedLeft = true;
                takeFirst();
                markReadyStale();
            }
        }
        String r = ready.kind();
        // Trusted again once the slot reads empty, or a new steady reading began (the next piece).
        if (readyStale && (r.equals(NONE) || ready.steadyFromNs() > staleFrom)) readyStale = false;
        if (readyStale || (feeding > 0 && !feedLeft)) return;
        // A piece in the ready slot that was never counted (missed, or the robot was loaded by hand).
        if (!r.equals(NONE) && held.isEmpty()) held.add(r);
        // The ready sensor knows best: correct the first piece's kind.
        if (!r.equals(NONE) && !r.equals(UNKNOWN) && !held.isEmpty()) held.set(0, r);
    }

    private boolean readyWorks() {
        return ready != null && ready.isWorking();
    }

    private void markReadyStale() {
        if (ready == null) return;
        readyStale = true;
        staleFrom = ready.nowNs();
    }

    // An indexer started a feed: the piece is counted out when it leaves (or the feed ends normally).
    void feedStarted() {
        feeding++;
        feedLeft = false;
        feedSawPiece = readyWorks() && !ready.rawKind().equals(NONE);
    }

    // The feed ended. Not yet counted: count it if it fired (and, with a working ready sensor, if there
    // was a piece to fire). A jam or a stop part way: the piece is still in, nothing changes.
    void feedEnded(boolean fired) {
        if (feeding == 0) return;
        feeding--;
        boolean counted = feedLeft;
        if (!feedLeft && fired && (!readyWorks() || feedSawPiece)) {
            takeFirst();
            counted = true;
            // The ready sensor may still show the piece that just went (its steady answer lags by
            // steadyMs): not trusted until it reads empty or a new piece, or that old answer would put
            // the piece just counted out back in.
            markReadyStale();
        }
        if (counted) totalOut++;
        feedLeft = false;
        feedSawPiece = false;
        // Nothing went out (a jam, a stop part way): what the ready sensor sees is the piece still there.
        // A counted feed keeps the stale guard until the sensor confirms (see watchReady).
        if (!counted) readyStale = false;
    }

    private void takeFirst() {
        if (!held.isEmpty()) held.remove(0);
    }

    // ----------------------------- reading -----------------------------

    /** How many pieces the robot holds now (a piece being fed counts until it has gone). */
    public int count() {
        return held.size();
    }

    /** How many of this kind it holds. */
    public int count(String kind) {
        int n = 0;
        for (String k : held) if (k.equals(kind)) n++;
        return n;
    }

    /**
     * The kind of the next piece to fire: a kind, {@link #UNKNOWN}, or {@link #NONE} when empty. While a
     * feed is sending one out, the one after it.
     */
    public String next() {
        if (feeding > 0 && !feedLeft) return held.size() < 2 ? NONE : held.get(1);
        if (ready != null && !readyStale && ready.seesPiece() && !ready.kind().equals(UNKNOWN)) return ready.kind();
        return held.isEmpty() ? NONE : held.get(0);
    }

    /** True if the next piece to fire is this kind. */
    public boolean nextIs(String kind) {
        return next().equals(kind);
    }

    /**
     * True when it holds {@link #max} pieces (or more); without a max, the intake's {@code holds(n)}. False
     * with neither.
     */
    public boolean isFull() {
        int limit = limit();
        return limit > 0 && held.size() >= limit;
    }

    /**
     * True when no more of this kind fits: the robot is full, or it holds as many of this kind as the
     * intake's {@code holds(n, kind)} allows (0 = never this kind).
     *
     * @param kind e.g. "RED_NECTAR"
     */
    public boolean isFullFor(String kind) {
        if (isFull()) return true;
        for (java.util.Map.Entry<String, Integer> e : kindLimits.entrySet()) {
            java.util.Map<String, Integer> one = java.util.Collections.singletonMap(e.getKey(), 0);
            if (Intake.limitFor(one, kind) < 0) continue;
            int n = 0;
            for (String h : held) if (Intake.limitFor(one, h) >= 0) n++;   // red and blue NECTAR count together
            if (n >= e.getValue()) return true;   // every limit that names this kind counts
        }
        return false;
    }

    // The limit in use: max(...), else the intake's holds(n); 0 = none.
    private int limit() {
        return max > 0 ? max : intakeHolds;
    }

    /** Set up by the robot (RobotDefinition): the intake's holds(n) and its kind limits. */
    void fromIntake(int holds, java.util.Map<String, Integer> limits) {
        intakeHolds = holds;
        kindLimits = new java.util.LinkedHashMap<>(limits);
    }

    /** True when it holds none. */
    public boolean isEmpty() {
        return held.isEmpty();
    }

    /** The pieces held, next to fire first (a copy). */
    public List<String> held() {
        return new ArrayList<>(held);
    }

    /** What the intake sensor sees now (NONE without one). */
    public String atIntake() {
        return in == null ? NONE : in.kind();
    }

    /** What the ready sensor sees now (NONE without one). */
    public String atReady() {
        return ready == null ? NONE : ready.kind();
    }

    /** The sensor given with {@link #comingIn(PieceSensor)}, or null. */
    public PieceSensor comingInSensor() {
        return in;
    }

    /** The sensor given with {@link #readyToFire(PieceSensor)}, or null. */
    public PieceSensor readyToFireSensor() {
        return ready;
    }

    /** The limit in use: {@link #max(int)}, else the intake's {@code holds(n)} (0 = none). */
    public int maxPieces() {
        return limit();
    }

    // ----------------------------- hooks -----------------------------

    /** {@link #next()} as a {@code Supplier<String>}, for a part that asks every loop (a shot table chooser). */
    public Supplier<String> nextKind() {
        return this::next;
    }

    /** {@link #isFull()} as a condition (for LEDs, {@code Intake.stopsWhen}, an action's wait). */
    public BooleanSupplier whenFull() {
        return this::isFull;
    }

    /** {@link #isEmpty()} as a condition. */
    public BooleanSupplier whenEmpty() {
        return this::isEmpty;
    }

    /** "The next piece is this kind" as a condition, e.g. {@code whenNextIs("POLLEN")} for a yellow LED. */
    public BooleanSupplier whenNextIs(String kind) {
        checkKind(kind);
        return () -> nextIs(kind);
    }

    // ----------------------------- changing the count by hand -----------------------------

    /** One more piece of this kind came in (the intake sensor calls this; a claw can too). */
    public void pieceIn(String kind) {
        held.add(kind == null || kind.equals(NONE) ? UNKNOWN : kind);
        totalIn++;
    }

    /** One piece went out, the next to fire (for a part that sends pieces out itself). Nothing if it holds none. */
    public void pieceOut() {
        takeFirst();
        totalOut++;
        markReadyStale();
    }

    /** Empties the count (e.g. a button after the robot was emptied by hand; its suggested button). */
    public void clear() {
        held.clear();
        readyStale = false;
    }

    /** Sets what it holds, next to fire first, e.g. {@code set("POLLEN", "POLLEN")}. */
    public void set(String... kinds) {
        held.clear();
        for (String k : kinds) {
            checkKind(k);
            held.add(k);
        }
    }

    @Override
    public void stop() {
        // Its sensors are not on the robot's list of parts: stop them here (a camera closes).
        if (in != null) in.stop();
        if (ready != null) ready.stop();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        int limit = limit();
        if (limit > 0 && held.size() > limit) t.warn("Pieces: holding " + held.size() + ", more than " + limit);
        for (java.util.Map.Entry<String, Integer> e : kindLimits.entrySet()) {
            int n = 0;
            for (String h : held) if (Intake.limitFor(java.util.Collections.singletonMap(e.getKey(), 0), h) >= 0) n++;
            if (n > e.getValue()) t.warn("Pieces: holding " + n + " " + e.getKey() + ", the intake holds " + e.getValue());
        }
        t.line("Pieces", held.size() + (limit > 0 ? "/" + limit : "") + ", next " + next());
        t.debug("  held", held.toString());
        t.debug("  in/fired/pushed out", totalIn + " / " + totalOut + " / " + pushedOut);
        if (in != null) in.addTelemetry(t);
        if (ready != null) ready.addTelemetry(t);
    }

    @Override
    boolean makesDefaultShape() {
        return false;
    }

    // ----------------------------- names -----------------------------

    private void checkKind(String kind) {
        if (kind == null || (!kinds.contains(kind) && !kind.equals(UNKNOWN))) {
            throw new IllegalArgumentException("Pieces: \"" + kind + "\" is not one of the kinds " + kinds
                    + " (capitals count)");
        }
    }

    // Kind names must be usable as Tunables fields, and not NONE/UNKNOWN.
    static List<String> checkKinds(String... names) {
        if (names == null || names.length == 0) throw new IllegalArgumentException("Robot.java: give at least one kind, e.g. kinds(\"POLLEN\")");
        List<String> out = new ArrayList<>();
        for (String n : names) {
            if (n == null || !n.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Robot.java: the kind \"" + n + "\" must be letters, digits and _ only "
                        + "(it is also a Tunables field name): e.g. RED_NECTAR");
            }
            if (n.equals(NONE) || n.equals(UNKNOWN)) throw new IllegalArgumentException("Robot.java: " + n + " cannot be a kind (the library uses it)");
            if (out.contains(n)) throw new IllegalArgumentException("Robot.java: the kind " + n + " is given twice");
            out.add(n);
        }
        return out;
    }
}
