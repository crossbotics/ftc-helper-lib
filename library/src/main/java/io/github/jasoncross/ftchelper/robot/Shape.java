package io.github.jasoncross.ftchelper.robot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a part looks and where it sits, for the simulator's 3D view and its size checks: written in
 * {@code Robot.java} next to the part, so the simulator robot file comes from the code
 * ({@code ./gradlew :sim:fromRobot}).
 *
 * <pre>{@code
 * intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-speed")
 *         .looks(Shape.arm().atMm(140, 0, 110).sizeMm(120, 300, 20)
 *                 .angle("stowed", 95).angle("deployed", -10)));
 * intake = add(Intake.motor("intake").part("gobilda-5203-117").holds(4)
 *         .looks(Shape.roller("offset-tubing-90", "offset-tubing-60").on(intakeDeploy).atMm(120, 0, 0).lengthMm(240)));
 * }</pre>
 *
 * <p><b>Name the part, give only what is not known.</b> A roller or flywheel named by its parts catalog
 * id ({@link Parts}) takes its diameter (and width, when the seller gives it) from the catalog. Places
 * are always the robot's own: {@code atMm(forward, left, up)} from the parent (the middle of the
 * chassis on the floor, or the hinge of the arm it rides on).
 *
 * <p>The part that owns the shape moves it: an intake spins its rollers, a positioner swings its arm.
 * A {@link #box} does not move ({@code fixed(...)} in Robot.java, or any part's {@code looks}).
 *
 * Docs: docs/modules/robot.md (section "Shapes"); the shape types: docs/guides/simulator.md#robot-shapes
 */
public final class Shape {
    private static final List<String> AXES = Arrays.asList("up", "down", "forward", "back", "left", "right");

    final String type;
    private final List<String> parts;
    String name;
    private double[] at = {0, 0, 0};
    private double[] size;              // inches: forward x left x up
    private double diameterIn = Double.NaN, lengthIn = Double.NaN;
    private String axis;
    Module<?> onModule;
    String onShape;
    // An arm on a servo: its angle (degrees, 0 = level forward, +90 = up) at named servo positions.
    final Map<String, Double> angles = new LinkedHashMap<>();
    private String note, color;
    // A passive part (gravity or springs): its range, where it starts, and what makes it drop.
    boolean passive = false;
    private double rangeMin = Double.NaN, rangeMax = Double.NaN, startAt = Double.NaN, travelIn = Double.NaN;
    ServoPositions<?> dropBy;
    // The part whose looks(...) holds this shape (one only), or null.
    Module<?> owner;
    private String dropPosition;
    private double fallsTo = Double.NaN;

    private Shape(String type, String... parts) {
        this.type = type;
        this.parts = Collections.unmodifiableList(Arrays.asList(parts));
        for (String p : parts) Parts.wheel(p);    // a typo stops here, with the known ids
    }

    // ----------------------------- the kinds -----------------------------

    /**
     * A roller (a cylinder; by default its axle runs across the robot, left-right). Several parts
     * (for example 90 mm and 60 mm rollers taking turns on one shaft) are drawn as one cylinder as big
     * as the biggest. Its length: {@link #lengthMm}, or the part's width if the catalog has it.
     *
     * @param partIds parts catalog ids, e.g. "offset-tubing-90"; none = give {@link #diameterMm}
     */
    public static Shape roller(String... partIds) {
        return new Shape("roller", partIds);
    }

    /** A launcher wheel, e.g. {@code Shape.flywheel("gobilda-hogback-96")}. */
    public static Shape flywheel(String partId) {
        if (partId == null) throw new IllegalArgumentException("Shape.flywheel(null): give a catalog id, or use Shape.flywheel() with .diameterMm(...) and .lengthMm(...)");
        return new Shape("flywheel", partId);
    }

    /** A launcher wheel not in the parts catalog: give {@link #diameterMm} and {@link #lengthMm}. */
    public static Shape flywheel() {
        return new Shape("flywheel");
    }

    /**
     * A part that swings about a hinge (an intake that folds, an arm). {@code atMm} is the hinge;
     * {@code sizeMm(length, width, thickness)} is the box that reaches forward from it at 0 degrees.
     * On a servo, give its angle at two named positions with {@link #angle}.
     */
    public static Shape arm() {
        return new Shape("arm");
    }

    /**
     * A part that moves in a straight line along its {@link #axis} (default up): {@code sizeMm} is one
     * stage, {@code atMm} the bottom of the fixed stage. Parts on it ride its moving stage. Here it is
     * for a {@link #passive} slide (springs or gravity), with {@link #travelMm}.
     */
    public static Shape slide() {
        return new Shape("slide");
    }

    /**
     * A part that does not move (a ramp, a tower, a hood). {@code atMm} is the middle of its bottom.
     *
     * @param name its name in the robot file, e.g. "ramp"
     */
    public static Shape box(String name) {
        Shape s = new Shape("box");
        s.name = name;
        return s;
    }

    // ----------------------------- settings -----------------------------

    /** Its name in the robot file (default: the part's configuration name, then name_2, name_3...). */
    public Shape name(String name) {
        this.name = name;
        return this;
    }

    /** Where it is, in mm from its parent: forward, left, up (the parent: the chassis, or {@link #on}). */
    public Shape atMm(double forward, double left, double up) {
        at = new double[] {forward / 25.4, left / 25.4, up / 25.4};
        return this;
    }

    /** {@link #atMm} in inches. */
    public Shape atIn(double forward, double left, double up) {
        at = new double[] {forward, left, up};
        return this;
    }

    /** Its box in mm: forward x left-right x up (an arm: length x width x thickness at 0 degrees). */
    public Shape sizeMm(double forward, double left, double up) {
        size = new double[] {forward / 25.4, left / 25.4, up / 25.4};
        return this;
    }

    /** {@link #sizeMm} in inches. */
    public Shape sizeIn(double forward, double left, double up) {
        size = new double[] {forward, left, up};
        return this;
    }

    /** A roller's length along its axle, mm (needed when the catalog does not give the part's width). */
    public Shape lengthMm(double mm) {
        lengthIn = mm / 25.4;
        return this;
    }

    /** A roller's or flywheel's diameter, mm, for a part not in the catalog. */
    public Shape diameterMm(double mm) {
        diameterIn = mm / 25.4;
        return this;
    }

    /**
     * The line it turns about: "up", "down", "forward", "back", "left" or "right". A roller's axle
     * (default left-right; "up" for a wheel lying flat), an arm's hinge (default "left").
     */
    public Shape axis(String axis) {
        String a = axis.toLowerCase(Locale.US);
        if (!AXES.contains(a)) throw new IllegalArgumentException("Shape axis must be one of " + AXES + ", not \"" + axis + "\"");
        this.axis = a;
        return this;
    }

    /** Rides on another part's (first) shape: it moves with it, and {@code atMm} is from that shape's hinge or middle. */
    public Shape on(Module<?> part) {
        onModule = part;
        onShape = null;
        return this;
    }

    /** Rides on a shape by its name in the robot file (a {@link #box}, or a part's second shape). */
    public Shape on(String shapeName) {
        onShape = shapeName;
        onModule = null;
        return this;
    }

    /**
     * An arm on a servo: its angle at a named position (from Tunables.java). Give two; the simulator
     * draws every position between them. 0 = level forward, +90 = straight up.
     *
     * @param position e.g. "stowed"
     * @param degrees  e.g. 95
     */
    public Shape angle(String position, double degrees) {
        angles.put(position, degrees);
        return this;
    }

    /**
     * Moved by gravity or springs, not by the code (a floating intake, a sprung flywheel mount). The
     * simulator draws it at {@link #startAt} and checks the size rules over its whole range: give
     * {@link #range} (arm) or {@link #travelMm} (slide), the hard stops a real robot needs too.
     */
    public Shape passive() {
        passive = true;
        return this;
    }

    /** A passive arm's hard stops, in degrees (0 = level forward, +90 = straight up). */
    public Shape range(double minDegrees, double maxDegrees) {
        rangeMin = minDegrees;
        rangeMax = maxDegrees;
        return this;
    }

    /** Where a passive arm is at the start of a match, degrees. Default: the bottom of its range. */
    public Shape startAt(double degrees) {
        startAt = degrees;
        return this;
    }

    /** How far a passive slide can move, mm (from where it rests). */
    public Shape travelMm(double mm) {
        travelIn = mm / 25.4;
        return this;
    }

    /**
     * A passive arm that a servo pushes over (an intake folded up at the start): when that servo
     * reaches the named position (three quarters of the way there), it falls to {@code toDegrees}
     * (its hard stop) and stays, as gravity holds it there on the robot.
     *
     * @param servoPart the part whose servo pushes it, e.g. intakeDeploy
     * @param position  its position that pushes it over, e.g. "deployed"
     * @param toDegrees where it falls to, e.g. -10
     */
    public Shape dropsWhen(ServoPositions<?> servoPart, String position, double toDegrees) {
        passive = true;
        dropBy = servoPart;
        dropPosition = position;
        fallsTo = toDegrees;
        return this;
    }

    /** Where a number came from, written after the line ("est. from the CAD", "measured 2026-10-20"). */
    public Shape note(String note) {
        this.note = note;
        return this;
    }

    /** Its colour in the 3D view, e.g. "#2fa84f". */
    public Shape color(String color) {
        this.color = color;
        return this;
    }

    // ----------------------------- the library's -----------------------------

    /** True for a part that spins (roller, flywheel); false for one that swings or does not move. */
    boolean spins() {
        return type.equals("roller") || type.equals("flywheel");
    }

    /**
     * The robot file line: "shape.NAME = TYPE, on=..., at=..., size=..., axis=..., DRIVER   # note".
     *
     * @param lineName the shape's name in the file
     * @param parent   the parent shape's name, or null for the chassis
     * @param driver   what moves it ("motor=intake", "servo=x, pos0=95, pos1=-10"), or null
     */
    String line(String lineName, String parent, String driver) {
        checkSettings(lineName, driver);
        StringBuilder sb = new StringBuilder("shape.").append(lineName).append(" = ").append(type);
        if (parent != null) sb.append(", on=").append(parent);
        sb.append(", at=").append(n(at[0])).append(',').append(n(at[1])).append(',').append(n(at[2]));
        double[] s = boxSize(lineName);
        sb.append(", size=").append(n(s[0])).append('x').append(n(s[1])).append('x').append(n(s[2]));
        if (axis != null) sb.append(", axis=").append(axis);
        if (driver != null) sb.append(", ").append(driver);
        if (passive) sb.append(passiveWords(lineName));
        if (color != null) sb.append(", color=").append(color);
        List<String> notes = new ArrayList<>();
        if (!parts.isEmpty()) notes.add(joined(parts, " + "));
        if (note != null) notes.add(note);
        if (!notes.isEmpty()) sb.append("   # ").append(joined(notes, ". "));
        return sb.toString();
    }

    /**
     * The name of the shape this one rides on (null = the chassis). A part with no looks(...) and no
     * shape of its own (a Positioner, an Indexer, a camera) has nothing to ride on: say so here, not
     * as a broken robot file.
     */
    String parentName(String lineName) {
        if (onModule == null) return onShape;
        if (onModule.looks.isEmpty() && !onModule.makesDefaultShape()) {
            throw new IllegalStateException("Robot.java: " + lineName + " rides on " + onModule.name()
                    + ", but " + onModule.name() + " has no looks(...) to ride on");
        }
        return onModule.firstShapeName();
    }

    // A setting this kind of shape does not use is a mistake: stop with its name instead of ignoring it.
    private void checkSettings(String lineName, String driver) {
        String what = "Robot.java: shape " + lineName + " (" + type + (passive ? ", passive" : "") + "): ";
        boolean servoArm = type.equals("arm") && !passive && driver != null && driver.startsWith("servo=");
        if (!angles.isEmpty() && !servoArm) {
            throw new IllegalStateException(what + ".angle(...) is for an arm moved by a servo part (a Positioner); "
                    + (passive ? "a passive arm takes .range(...) and .startAt(...)" : "this one is not"));
        }
        if (angles.size() > 2) throw new IllegalStateException(what + "give exactly two .angle(...), not " + angles.size());
        boolean passiveArm = type.equals("arm") && passive;
        if ((!Double.isNaN(rangeMin) || !Double.isNaN(startAt)) && !passiveArm) {
            throw new IllegalStateException(what + ".range(...) and .startAt(...) are for a passive arm only");
        }
        if (!Double.isNaN(travelIn) && !(type.equals("slide") && passive)) {
            throw new IllegalStateException(what + ".travelMm(...) is for a passive slide only");
        }
        if ((!Double.isNaN(lengthIn) || !Double.isNaN(diameterIn)) && !spins()) {
            throw new IllegalStateException(what + ".lengthMm/.diameterMm are for a roller or flywheel; use .sizeMm(...)");
        }
    }

    // ", start=95, minDeg=-10, maxDeg=95, passive=yes, drop=intake_deploy>0.6, fallsTo=-10"
    private String passiveWords(String lineName) {
        StringBuilder sb = new StringBuilder();
        if (type.equals("slide")) {
            if (Double.isNaN(travelIn)) throw new IllegalStateException("Robot.java: passive slide " + lineName + " needs .travelMm(...)");
            sb.append(", travel=").append(n(travelIn));
        } else if (type.equals("arm")) {
            if (Double.isNaN(rangeMin)) throw new IllegalStateException("Robot.java: passive arm " + lineName + " needs .range(minDeg, maxDeg)");
            double start = Double.isNaN(startAt) ? rangeMin : startAt;
            sb.append(", start=").append(n(start)).append(", minDeg=").append(n(rangeMin)).append(", maxDeg=").append(n(rangeMax));
        } else {
            throw new IllegalStateException("Robot.java: shape " + lineName + ": only an arm or a slide can be passive");
        }
        sb.append(", passive=yes");
        if (dropBy != null) sb.append(", drop=").append(dropBy.dropWords(dropPosition)).append(", fallsTo=").append(n(fallsTo));
        return sb.toString();
    }

    // forward x left x up, inches.
    private double[] boxSize(String lineName) {
        if (!spins()) {
            if (size == null && type.equals("slide")) throw new IllegalStateException("Robot.java: slide " + lineName + " needs .sizeMm(...) (one stage)");
            if (size == null) throw new IllegalStateException("Robot.java: shape " + lineName + " needs its size: .sizeMm(forward, left, up)");
            return size;
        }
        double d = diameterIn, len = lengthIn;
        for (String p : parts) {
            Parts.Wheel w = Parts.wheel(p);
            if (Double.isNaN(d) || w.diameterMm / 25.4 > d) d = w.diameterMm / 25.4;
        }
        if (Double.isNaN(len) && parts.size() == 1 && Parts.wheel(parts.get(0)).hasWidth()) len = Parts.wheel(parts.get(0)).widthMm / 25.4;
        if (Double.isNaN(d)) throw new IllegalStateException("Robot.java: shape " + lineName + " needs a part id or .diameterMm(...)");
        if (Double.isNaN(len)) {
            throw new IllegalStateException("Robot.java: shape " + lineName + " needs .lengthMm(...): the catalog has no width for "
                    + (parts.isEmpty() ? "it" : joined(parts, " + ")));
        }
        String a = axis != null ? axis : "left";
        if (a.equals("up") || a.equals("down")) return new double[] {d, d, len};
        if (a.equals("forward") || a.equals("back")) return new double[] {len, d, d};
        return new double[] {d, len, d};
    }

    // "a + b": the words with sep between them.
    private static String joined(List<String> words, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String w : words) sb.append(sb.length() == 0 ? "" : sep).append(w);
        return sb.toString();
    }

    // 2 decimals at most, no trailing zeros: 3.50 -> "3.5", -0.00 -> "0".
    static String n(double v) {
        String s = String.format(Locale.US, "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }
}
