package com.crossbotics.ftchelper.robot;

import com.qualcomm.hardware.rev.RevBlinkinLedDriver;
import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.match.MatchTimer;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * A colored light the drivers can see: green when the launcher is ready to fire, red blinking when
 * it is jammed, a white flash when a shot goes, yellow in the last 20 seconds. You write rules; every
 * loop the light shows the first rule that is true.
 *
 * <pre>{@code
 * lights = add(Lights.gobilda("light"));          // Robot.java: a goBILDA RGB Indicator Light (a servo port)
 *
 * // Controls.java, in buttons(...): the most important rule first
 * robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);    // time to park
 * robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);    // at speed: fire
 * robot.lights.show(LightColor.BLUE).when(() -> robot.turret.followMode().isOn());
 * robot.lights.otherwise(LightColor.OFF);                                // when no rule is true
 * robot.lights.show(LightColor.WHITE).whenCountGoesUp(() -> robot.launcher.part().getShotsFired());   // a 0.5 s flash per shot
 * }</pre>
 *
 * <ul>
 *   <li><b>First true rule wins:</b> rules are checked in the order you wrote them. Put the most
 *       important one first.</li>
 *   <li><b>A flash beats every rule</b> while it lasts ({@link #flash(LightColor)} from a button or
 *       an action, or {@link Rule#whenStarts} or {@link Rule#whenCountGoesUp}).</li>
 *   <li><b>Never waits:</b> blinking and flashes are worked out from the clock each loop.</li>
 *   <li><b>Any true/false works</b> as a rule: a part's method ({@code robot.intake::isRunning}), a
 *       {@link Mode} ({@code () -> robot.turret.followMode().isOn()}), or your own
 *       ({@code () -> robot.lift.position() > 1000}).</li>
 *   <li>Actions do not "claim" the light (see {@link Action}): anything may flash it at any time.</li>
 * </ul>
 *
 * <p><b>The alliance at INIT</b> (on by default): before START the light shows the alliance the drivers
 * chose, solid {@code RED} or {@code BLUE}, so a wrong pick is seen before the match; until one is
 * chosen it blinks white once a second ("choose the alliance"). At START it flashes the alliance for
 * 1 s (TeleOp after autonomous: the alliance it got), and again when the alliance is switched during
 * TeleOp. Then your rules take over. Off: {@code allianceAtInit = false} in this light's Tunables
 * section, or {@link #allianceAtInit(boolean)}. Other colors: {@link #allianceColors}; a Blinkin
 * pattern of your own for a color: {@link #blinkinPattern}.
 *
 * <p>Game rules (FTC 2026-27 manual, R202 J and F): lights that flash faster than 5 times a second, or
 * very bright lights, can be turned off by the referees. So {@link Rule#blinking(double)} stops at 5,
 * and the light never changes color twice within {@link #MIN_CHANGE_SEC} (0.1 s), whatever the rules,
 * flashes and blinking ask: a rule that turns on and off every loop, or flash after flash, still
 * switches the light at most 5 times on and off a second. A light in the alliance's color is allowed
 * (no rule against it); the ROBOT SIGNS must still show the alliance color (R402). Keep your other
 * rules' colors away from red and blue, so nobody reads them as the alliance.
 *
 * Docs: docs/modules/robot.md (section "Lights")
 */
public final class Lights extends Module<Lights> {
    /** A flash's length when none is given: half a second. */
    public static final double FLASH_SEC = 0.5;
    /** Blinks per second when none is given. */
    public static final double BLINK_HZ = 2;
    /** The most blinks per second allowed (FTC R202 J: faster than 5 per second invites the referees). */
    public static final double MAX_BLINK_HZ = 5;
    /**
     * The shortest time between two color changes, seconds: 0.1 s, so the light switches on and off
     * at most 5 times a second (FTC R202 J) even when a rule changes every loop. A change asked for
     * sooner waits (a flash shorter than this may not show at all).
     */
    public static final double MIN_CHANGE_SEC = 0.1;
    /** How long the alliance color flashes at START and when the alliance is switched, seconds. */
    public static final double ALLIANCE_FLASH_SEC = 1.0;
    /** Blinks per second of "choose the alliance" in INIT (slow: well under the 5 per second limit). */
    public static final double CHOOSE_HZ = 1;

    private enum Kind { GOBILDA, BLINKIN }

    private final Kind kind;
    // What the light is made to show: one method, so a test (or a new kind of light) can stand in.
    interface Output {
        void show(LightColor color);
    }

    private Output output;
    // The time in nanoseconds (tests use a pretend clock).
    LongSupplier clock = System::nanoTime;

    private final List<Rule> rules = new ArrayList<>();
    private final List<Rule> watches = new ArrayList<>();
    private LightColor otherwise = LightColor.OFF;

    // The flash now showing (null = none), when it started and ends (nanoseconds), and its blink rate.
    private LightColor flashColor = null;
    private double flashHz = 0;
    private long flashStart = 0, flashEnd = 0;

    // The rule showing now (null = "otherwise"), and since when: a blink starts "on" when its rule wins.
    private Rule winner = null;
    private long winnerSince = 0;
    // When START was pressed (for inLastSeconds); -1 = not yet.
    private long startedAt = -1;
    // The last color written to the light (null = nothing yet), and the words for the Driver Station.
    private LightColor written = null;
    private LightColor showing = LightColor.OFF;
    // When the light last changed color (nanoseconds; changedAtSet false = not yet, so the first one goes).
    private long changedAt = 0;
    private boolean changedAtSet = false;
    private String why = "";
    private String problem = "";
    // The alliance at INIT (see the class comment): on unless turned off, its colors, and the alliance
    // shown last after START (to flash a switch). chooseSince: when "choose" blinking started.
    private boolean allianceAtInit = true;
    private LightColor blueColor = LightColor.BLUE, redColor = LightColor.RED;
    private final java.util.Map<LightColor, RevBlinkinLedDriver.BlinkinPattern> patterns = new java.util.EnumMap<>(LightColor.class);
    private com.crossbotics.ftchelper.match.Alliance shownAlliance = null;
    private long chooseSince = -1;
    // In INIT: the alliance color (or the "choose" white) shown instead of the rules; null otherwise.
    private LightColor initPick = null;
    // True between the first INIT loop and START (the alliance at INIT shows only then).
    private boolean inInit = false;

    private Lights(String device, Kind kind) {
        super(device);
        this.kind = kind;
    }

    /**
     * A goBILDA RGB Indicator Light (3118-0808-0002), plugged into a servo port. In the robot
     * configuration it is a "Servo". Several lights chained on one cable show the same color.
     *
     * @param servoName its configuration name, e.g. "light"
     */
    public static Lights gobilda(String servoName) {
        return new Lights(servoName, Kind.GOBILDA);
    }

    /**
     * A REV Blinkin LED Driver (REV-11-1105) on a servo port, driving an LED strip (5 V addressable
     * or 12 V). In the robot configuration it is a "REV Blinkin LED Driver".
     *
     * @param name its configuration name, e.g. "blinkin"
     */
    public static Lights blinkin(String name) {
        return new Lights(name, Kind.BLINKIN);
    }

    // ----------------------------- rules -----------------------------

    /**
     * Starts a rule: the color to show. Finish it with {@link Rule#when}, {@link Rule#whenStarts}, {@link Rule#whenCountGoesUp} or
     * {@link Rule#inLastSeconds}.
     *
     * @param color e.g. {@code LightColor.GREEN}
     * @return the rule, to finish
     */
    public Rule show(LightColor color) {
        if (color == null) throw new IllegalArgumentException("Lights: show(null): pick a LightColor, e.g. LightColor.GREEN");
        return new Rule(color);
    }

    /**
     * The color when no rule is true (OFF if you never say). Written anywhere: it is always last.
     *
     * @param color e.g. {@code LightColor.BLUE}
     * @return this module
     */
    public Lights otherwise(LightColor color) {
        if (color == null) throw new IllegalArgumentException("Lights: otherwise(null): pick a LightColor, e.g. LightColor.OFF");
        otherwise = color;
        return this;
    }

    /**
     * The alliance at INIT on or off (on by default; see the class comment). {@code allianceAtInit} in
     * this light's Tunables section wins over this.
     *
     * @param on false = the light shows your rules in INIT too, and no alliance flashes
     */
    public Lights allianceAtInit(boolean on) {
        allianceAtInit = on;
        return this;
    }

    /**
     * The colors for the alliance at INIT, if your light shows red or blue badly.
     *
     * @param blue e.g. {@code LightColor.BLUE}
     * @param red  e.g. {@code LightColor.RED}
     */
    public Lights allianceColors(LightColor blue, LightColor red) {
        if (blue == null || red == null) throw new IllegalArgumentException("Lights: allianceColors(null): pick two LightColors");
        blueColor = blue;
        redColor = red;
        return this;
    }

    /**
     * A REV Blinkin only: show this pattern for this color (e.g. a strip whose plain blue is too dark:
     * {@code blinkinPattern(LightColor.BLUE, BlinkinPattern.BLUE_VIOLET)}). Other lights ignore it.
     *
     * @param color   the LightColor your rules use
     * @param pattern the Blinkin pattern to show for it (its names are on REV's Blinkin page)
     */
    public Lights blinkinPattern(LightColor color, RevBlinkinLedDriver.BlinkinPattern pattern) {
        if (color == null || pattern == null) throw new IllegalArgumentException("Lights: blinkinPattern(null, ...): give a LightColor and a pattern");
        patterns.put(color, pattern);
        return this;
    }

    /** True while the light shows the alliance in INIT (not turned off). */
    public boolean showsAllianceAtInit() {
        return allianceAtInit;
    }

    /** Takes away every rule (the "otherwise" color stays). */
    public void clearRules() {
        rules.clear();
        watches.clear();
        winner = null;
    }

    /**
     * Flashes a color now for half a second, over every rule: from a button
     * ({@code g2.a.press(() -> robot.lights.flash(LightColor.WHITE))}) or at the end of an action
     * ({@code .then(() -> r.lights.flash(LightColor.GREEN))}). A new flash replaces one still showing.
     *
     * @param color e.g. {@code LightColor.WHITE}
     */
    public void flash(LightColor color) {
        flash(color, FLASH_SEC);
    }

    /**
     * Flashes a color now for this long, over every rule.
     *
     * @param color   e.g. {@code LightColor.WHITE}
     * @param seconds how long, more than 0 (e.g. 0.5)
     */
    public void flash(LightColor color, double seconds) {
        startFlash(color, seconds, 0);
    }

    /** True while a flash is showing. */
    public boolean isFlashing() {
        return flashColor != null && clock.getAsLong() < flashEnd;
    }

    /**
     * The color the rules pick now (a blinking color counts even in its dark half). What the light
     * really shows this instant is {@link #showing()}.
     */
    public LightColor color() {
        if (initPick != null) return initPick;
        if (isFlashing()) return flashColor;
        return winner != null ? winner.color : otherwise;
    }

    /** What the light shows right now: OFF in a blink's dark half. Updated every loop. */
    public LightColor showing() {
        return showing;
    }

    // ----------------------------- one rule -----------------------------

    /**
     * One rule, started with {@link Lights#show(LightColor)}. Add {@code blinking()} or
     * {@code forSeconds(...)} if you like, then finish it with when, whenStarts, whenCountGoesUp or inLastSeconds.
     */
    public final class Rule {
        final LightColor color;
        double hz = 0;
        double seconds = FLASH_SEC;
        BooleanSupplier condition;
        String what;
        Boolean was = null;   // whenStarts: true/false last loop (null = not read yet)
        java.util.function.IntSupplier count;   // whenCountGoesUp: the number watched (null for the others)
        Integer lastCount = null;   // its value last loop (null = not read yet)

        private Rule(LightColor color) {
            this.color = color;
        }

        /** Blinks on and off, 2 times a second. */
        public Rule blinking() {
            return blinking(BLINK_HZ);
        }

        /**
         * Blinks on and off this many times a second.
         *
         * @param timesPerSecond more than 0 (e.g. 1 = once a second, 0.5 = every 2 s), at most 5 (FTC R202 J:
         *                       faster blinking can be stopped by the referees)
         */
        public Rule blinking(double timesPerSecond) {
            if (!(timesPerSecond > 0) || timesPerSecond > MAX_BLINK_HZ) {
                throw new IllegalArgumentException("Lights: blinking(" + timesPerSecond + "): give more than 0 and at most 5 times a second, e.g. 2 "
                        + "(FTC rule R202 J: lights that flash faster than 5 times a second can be stopped by the referees)");
            }
            hz = timesPerSecond;
            return this;
        }

        /**
         * For {@link #whenStarts} and {@link #whenCountGoesUp}: how long the flash lasts (default 0.5 s).
         *
         * @param seconds more than 0
         */
        public Rule forSeconds(double seconds) {
            if (!(seconds > 0)) throw new IllegalArgumentException("Lights: forSeconds(" + seconds + "): give a time above 0");
            this.seconds = seconds;
            return this;
        }

        /**
         * Shows the color while this is true (checked every loop, in the order the rules were written).
         *
         * @param condition e.g. {@code robot.intake::isRunning} or {@code () -> robot.lift.position() > 1000}
         * @return the light, for the next rule
         */
        public Lights when(BooleanSupplier condition) {
            if (condition == null) throw new IllegalArgumentException("Lights: when(null): give a true/false, e.g. robot.intake::isRunning");
            this.condition = condition;
            this.what = "rule " + (rules.size() + 1);
            rules.add(this);
            return Lights.this;
        }

        /**
         * Flashes the color (0.5 s, or {@link #forSeconds}) each time this turns from false to true:
         * a piece picked up, the launcher starting to shoot. Something already true at the start does not
         * flash. For one flash per piece fired use {@link #whenCountGoesUp} with the launcher's shot count.
         *
         * @param condition e.g. {@code robot.intake::isRunning}
         * @return the light, for the next rule
         */
        public Lights whenStarts(BooleanSupplier condition) {
            if (condition == null) throw new IllegalArgumentException("Lights: whenStarts(null): give a true/false, e.g. robot.launcher::isShooting");
            this.condition = condition;
            watches.add(this);
            return Lights.this;
        }

        /**
         * Flashes the color (0.5 s, or {@link #forSeconds}) each time this number goes up: one flash per
         * piece really fired, even when {@code fire(3)} fires three in a row ({@link #whenStarts} with
         * {@code isShooting} flashes once for all three). The number it has at the start does not flash.
         *
         * @param count e.g. {@code () -> robot.launcher.part().getShotsFired()} (pieces fed into the wheel)
         * @return the light, for the next rule
         */
        public Lights whenCountGoesUp(java.util.function.IntSupplier count) {
            if (count == null) {
                throw new IllegalArgumentException("Lights: whenCountGoesUp(null): give a number, e.g. () -> robot.launcher.part().getShotsFired()");
            }
            this.count = count;
            watches.add(this);
            return Lights.this;
        }

        /**
         * Shows the color in the last seconds of TeleOp's 2:00, counted from START (like
         * {@link MatchTimer}: a reminder, about a second off from the field clock).
         *
         * @param seconds e.g. 20 for "the last 20 seconds"
         * @return the light, for the next rule
         */
        public Lights inLastSeconds(final double seconds) {
            if (!(seconds > 0)) throw new IllegalArgumentException("Lights: inLastSeconds(" + seconds + "): give a time above 0");
            when(() -> startedAt >= 0 && (clock.getAsLong() - startedAt) / 1e9 >= MatchTimer.TELEOP_SECONDS - seconds);
            what = "last " + fmt("%.0f", seconds) + " s";
            return Lights.this;
        }
    }

    // ----------------------------- the library's -----------------------------

    private void startFlash(LightColor color, double seconds, double hz) {
        if (color == null) throw new IllegalArgumentException("Lights: flash(null): pick a LightColor, e.g. LightColor.WHITE");
        if (!(seconds > 0)) throw new IllegalArgumentException("Lights: flash for " + seconds + " s: give a time above 0");
        flashColor = color;
        flashHz = hz;
        flashStart = clock.getAsLong();
        flashEnd = flashStart + (long) (seconds * 1e9);
    }

    @Override
    void build(Devices devices, TunableSection t) {
        // Only find the device: nothing is shown until the first loop.
        if (kind == Kind.GOBILDA) {
            final Servo servo = devices.get(Servo.class, device);
            output = c -> servo.setPosition(c.gobildaPosition());
        } else {
            final RevBlinkinLedDriver blinkin = devices.get(RevBlinkinLedDriver.class, device);
            output = c -> blinkin.setPattern(patterns.containsKey(c) ? patterns.get(c) : c.blinkinPattern());
        }
        // Optional: no "missing" note when Tunables does not have it.
        if (t != null && t.has("allianceAtInit")) allianceAtInit = t.flag("allianceAtInit", true);
    }

    /** For tests: a pretend clock (nanoseconds). */
    void useClock(LongSupplier clockNanos) {
        clock = clockNanos;
    }

    @Override
    void start() {
        inInit = false;
        startedAt = clock.getAsLong();
        // The alliance in use, flashed at START: TeleOp after autonomous shows the one it got.
        shownAlliance = allianceNow();
        if (allianceAtInit && shownAlliance != null) startFlash(allianceColor(shownAlliance), ALLIANCE_FLASH_SEC, 0);
    }

    // The alliance in use: chosen in INIT (null = not yet) before START; the robot's after START.
    private com.crossbotics.ftchelper.match.Alliance allianceNow() {
        if (inInit) return com.crossbotics.ftchelper.match.MatchMemory.chosen();
        return robot != null ? robot.alliance() : com.crossbotics.ftchelper.match.MatchMemory.chosen();
    }

    private LightColor allianceColor(com.crossbotics.ftchelper.match.Alliance a) {
        return a == com.crossbotics.ftchelper.match.Alliance.RED ? redColor : blueColor;
    }

    // In INIT too: the drivers see the light work (and a "preloaded" rule) before START.
    @Override
    void initLoop(RobotTelemetry t) {
        inInit = true;
        update();
    }

    @Override
    public void update() {
        long now = clock.getAsLong();
        problem = "";
        // 1. A whenStarts rule that just turned true, or a whenCountGoesUp number that went up, starts a flash.
        for (Rule w : watches) {
            if (w.count != null) {
                // whenCountGoesUp: a flash each time the number is higher than last loop.
                Integer n = readCount(w);
                if (n != null && w.lastCount != null && n > w.lastCount) startFlash(w.color, w.seconds, w.hz);
                if (n != null) w.lastCount = n;
                continue;
            }
            boolean v = read(w);
            if (w.was != null && v && !w.was) startFlash(w.color, w.seconds, w.hz);
            w.was = v;
        }
        // 2. The first true rule (always worked out, so a blink restarts when its rule wins again).
        Rule first = null;
        for (Rule r : rules) {
            if (read(r)) {
                first = r;
                break;
            }
        }
        if (first != winner) {
            winner = first;
            winnerSince = now;
        }
        // The alliance switched during TeleOp: flash the new one.
        if (allianceAtInit && startedAt >= 0) {
            com.crossbotics.ftchelper.match.Alliance a = allianceNow();
            if (a != null && shownAlliance != null && a != shownAlliance) startFlash(allianceColor(a), ALLIANCE_FLASH_SEC, 0);
            if (a != null) shownAlliance = a;
        }
        // 3. A flash beats the rules while it lasts. In INIT (alliance at INIT on): the alliance instead
        // of the rules, or a slow blink until it is chosen.
        LightColor c;
        double hz;
        long since;
        com.crossbotics.ftchelper.match.Alliance initAlliance = allianceAtInit && inInit ? allianceNow() : null;
        initPick = null;
        if (allianceAtInit && inInit && !(flashColor != null && now < flashEnd)) {
            if (initAlliance == null) {
                if (chooseSince < 0) chooseSince = now;
                c = LightColor.WHITE;
                hz = CHOOSE_HZ;
                since = chooseSince;
                why = "choose the alliance";
                initPick = c;
            } else {
                chooseSince = -1;
                c = allianceColor(initAlliance);
                hz = 0;
                since = now;
                why = "alliance " + initAlliance;
                initPick = c;
            }
        } else if (flashColor != null && now < flashEnd) {
            c = flashColor;
            hz = flashHz;
            since = flashStart;
            why = "flash";
        } else {
            flashColor = null;
            c = winner != null ? winner.color : otherwise;
            hz = winner != null ? winner.hz : 0;
            since = winnerSince;
            why = winner != null ? winner.what : "otherwise";
        }
        // Blinking: on for the first half of each blink, off for the second.
        if (hz > 0) {
            double periodSec = 1 / hz;
            double into = ((now - since) / 1e9) % periodSec;
            if (into >= periodSec / 2) c = LightColor.OFF;
            why += ", blinking";
        }
        // At most one change every 0.1 s (FTC R202 J: no flashing faster than 5 times a second). A
        // change asked for sooner keeps the color showing now; it comes on a later loop if still wanted.
        if (c != showing) {
            if (changedAtSet && now - changedAt < (long) (MIN_CHANGE_SEC * 1e9)) {
                c = showing;
            } else {
                showing = c;
                changedAt = now;
                changedAtSet = true;
            }
        }
        // Only send a change (a Blinkin writes a log line for each command).
        if (output != null && c != written) {
            output.show(c);
            written = c;
        }
    }

    // A rule's true/false. A rule that fails (an error in it) counts as false, with a warning: a light
    // must never stop the robot.
    private boolean read(Rule r) {
        try {
            return r.condition.getAsBoolean();
        } catch (RuntimeException e) {
            problem = "a light rule failed (" + e + "): it counts as false";
            return false;
        }
    }

    // A whenCountGoesUp number, or null (with a warning) if reading it fails.
    private Integer readCount(Rule r) {
        try {
            return r.count.getAsInt();
        } catch (RuntimeException e) {
            problem = "a light rule failed (" + e + "): it is skipped this loop";
            return null;
        }
    }

    // STOP: the light goes dark.
    @Override
    public void stop() {
        if (output != null) output.show(LightColor.OFF);
        written = LightColor.OFF;
        showing = LightColor.OFF;
        changedAtSet = false;
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        LightColor c = color();
        t.line(name(), c + (why.isEmpty() ? "" : " (" + why + ")"));
        if (!problem.isEmpty()) t.warn(name() + ": " + problem);
    }

    // A light has no shape of its own in the simulator's 3D view (the page shows its color instead).
    @Override
    boolean makesDefaultShape() {
        return false;
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        out.add("light." + device + " = " + (kind == Kind.GOBILDA ? "gobilda" : "blinkin"));
    }
}
