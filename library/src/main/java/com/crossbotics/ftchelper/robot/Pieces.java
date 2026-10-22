package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Counts the game pieces the robot holds and knows the kind of the NEXT one to fire. A
 * {@link PieceSensor} at the intake counts each piece coming in (and its kind); every feed of an
 * {@link Indexer} counts one going out; an optional sensor at the "ready to fire" slot says what is
 * next for sure.
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
 * e.g. for LEDs or {@code Intake.stopsWhen}). No Tunables of its own (its sensors have theirs). No
 * suggested buttons.
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
    private final List<String> startWith = new ArrayList<>();
    // The pieces held, oldest first: the first one is the next to fire.
    private final List<String> held = new ArrayList<>();
    // The intake sensor's last steady answer, to see a NEW piece arrive.
    private String lastIn = NONE;
    private int totalIn = 0, totalOut = 0;
    // Just fired: the ready sensor may still see the piece that left, so it is not trusted until it sees
    // the slot empty once (or a feed that left nothing there).
    private boolean readyStale = false;

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
        // Every feed of an indexer (alone or in a launcher) sends one piece out.
        if (robot != null) {
            for (Module<?> m : robot.modules()) {
                List<Module<?>> group = new ArrayList<>(m.children());
                group.add(m);
                for (Module<?> g : group) if (g instanceof Indexer) ((Indexer) g).onFeed(this::removed);
            }
        }
    }

    // ----------------------------- every loop -----------------------------

    @Override
    public void update() {
        if (in != null) {
            in.update();
            String now = in.kind();
            boolean newPiece = !now.equals(NONE) && (lastIn.equals(NONE)
                    || (!lastIn.equals(UNKNOWN) && !now.equals(UNKNOWN) && !now.equals(lastIn)));
            if (newPiece) added(now);
            // The same piece, now told apart: write its kind down.
            else if (!now.equals(NONE) && !now.equals(UNKNOWN) && !held.isEmpty() && held.get(held.size() - 1).equals(UNKNOWN)) {
                held.set(held.size() - 1, now);
            }
            lastIn = now;
        }
        if (ready != null) {
            ready.update();
            String r = ready.kind();
            if (r.equals(NONE)) readyStale = false;
            if (readyStale) return;
            // A piece in the ready slot that was never counted (missed, or the robot was loaded by hand).
            if (!r.equals(NONE) && held.isEmpty()) held.add(r);
            // The ready sensor knows best: correct the first piece's kind.
            if (!r.equals(NONE) && !r.equals(UNKNOWN) && !held.isEmpty()) held.set(0, r);
        }
    }

    // ----------------------------- reading -----------------------------

    /** How many pieces the robot holds now. */
    public int count() {
        return held.size();
    }

    /** How many of this kind it holds. */
    public int count(String kind) {
        int n = 0;
        for (String k : held) if (k.equals(kind)) n++;
        return n;
    }

    /** The kind of the next piece to fire: a kind, {@link #UNKNOWN}, or {@link #NONE} when empty. */
    public String next() {
        if (ready != null && !readyStale && ready.seesPiece() && !ready.kind().equals(UNKNOWN)) return ready.kind();
        return held.isEmpty() ? NONE : held.get(0);
    }

    /** True if the next piece to fire is this kind. */
    public boolean nextIs(String kind) {
        return next().equals(kind);
    }

    /** True when it holds {@link #max} pieces (or more). False with no max. */
    public boolean isFull() {
        return max > 0 && held.size() >= max;
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

    /** The intake sensor, or null. */
    public PieceSensor intakeSensor() {
        return in;
    }

    /** The ready-to-fire sensor, or null. */
    public PieceSensor readySensor() {
        return ready;
    }

    /** The limit from {@link #max(int)} (0 = none). */
    public int maxPieces() {
        return max;
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
    public void added(String kind) {
        held.add(kind == null || kind.equals(NONE) ? UNKNOWN : kind);
        totalIn++;
    }

    /** One piece went out (each indexer feed calls this). Nothing if it holds none. */
    public void removed() {
        if (!held.isEmpty()) held.remove(0);
        totalOut++;
        readyStale = ready != null;
    }

    /** Empties the count (e.g. a button after the robot was emptied by hand). */
    public void clear() {
        held.clear();
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
        if (max > 0 && held.size() > max) t.warn("Pieces: holding " + held.size() + ", more than " + max);
        t.line("Pieces", held.size() + (max > 0 ? "/" + max : "") + ", next " + next());
        t.debug("  held", held.toString());
        t.debug("  in/out", totalIn + " / " + totalOut);
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
