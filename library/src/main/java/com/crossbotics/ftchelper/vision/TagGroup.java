package com.crossbotics.ftchelper.vision;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A group of AprilTags in a row that mark one target (BIOBUZZ: the 4 tags under each CELL), so a camera
 * can report the GROUP as one target with one aiming point, even when it sees only some of the tags.
 *
 * <p>The webcam does not need this: the FTC SDK's own tag library already groups the tags ("clusters")
 * and {@link WebcamTags} reports each cluster by its name. {@link LimelightTags} uses these groups to do
 * the same, so {@code camera.target("RED SCORING")} works with either camera.
 *
 * <p>Each tag's place along the row is in inches, from the row's middle, positive = to the right as
 * the tags are printed (the same numbers as the SDK's cluster list).
 *
 * <p>A group can say which field element it is on ({@link #element}). When one element has two groups
 * (BIOBUZZ: each HIVE has two CELLs, 4 tags under each), {@link #goalTarget} aims at the group that reads
 * right side up now: the up CELL, the one you can score in. The down CELL's tags read upside down, and
 * a tipped HIVE swaps the two.
 *
 * <pre>{@code
 * TagGroup myGoal = new TagGroup("GOAL", new int[] {1, 2}, new double[] {-3, 3});
 * LimelightTags camera = LimelightTags.create(hardwareMap, "limelight").groups(Arrays.asList(myGoal));
 * }</pre>
 *
 * Docs: docs/modules/vision.md
 */
public final class TagGroup {
    /** The group's name, as the target's name (the SDK's cluster name for this season's field). */
    public final String name;
    /** The field element the group is on, e.g. "RED HIVE" (null: not given). */
    public final String element;
    private final int[] ids;
    private final double[] alongRowIn;

    /**
     * @param name        the target's name, e.g. "RED SCORING"
     * @param ids         the tag ids in the group
     * @param alongRowIn  each tag's place along the row, inches from the middle (+ = right as printed),
     *                    in the same order as {@code ids}
     */
    public TagGroup(String name, int[] ids, double[] alongRowIn) {
        this(name, ids, alongRowIn, null);
    }

    /**
     * A group on a field element that has more than one group (e.g. a HIVE's two CELLs).
     *
     * @param element the field element's name, the same for each of its groups, e.g. "RED HIVE"
     */
    public TagGroup(String name, int[] ids, double[] alongRowIn, String element) {
        if (ids.length != alongRowIn.length || ids.length < 2) {
            throw new IllegalArgumentException("TagGroup " + name + ": needs at least 2 tags, each with its place along the row");
        }
        for (int i = 0; i < ids.length; i++) {
            for (int j = i + 1; j < ids.length; j++) {
                if (ids[i] == ids[j]) throw new IllegalArgumentException("TagGroup " + name + ": tag " + ids[i] + " is listed twice");
                if (alongRowIn[i] == alongRowIn[j]) {
                    throw new IllegalArgumentException("TagGroup " + name + ": tags " + ids[i] + " and " + ids[j] + " are at the same place along the row");
                }
            }
        }
        this.name = name;
        this.element = element;
        this.ids = ids.clone();
        this.alongRowIn = alongRowIn.clone();
    }

    /** True if this tag is in the group. */
    public boolean contains(int id) {
        return indexOf(id) >= 0;
    }

    /** The tag's place along the row (inches from the middle, + = right as printed), NaN if not in the group. */
    public double alongRowIn(int id) {
        int i = indexOf(id);
        return i < 0 ? Double.NaN : alongRowIn[i];
    }

    /** The tag ids in the group. */
    public int[] ids() {
        return ids.clone();
    }

    private int indexOf(int id) {
        for (int i = 0; i < ids.length; i++) if (ids[i] == id) return i;
        return -1;
    }

    /**
     * BIOBUZZ (2026-27): the 4 tags under each CELL, the same groups and names as the FTC SDK 12.0's
     * tag library (AprilTagGameDatabase.getBioBuzzTagLibrary(), manual section 9.9). Each alliance's
     * HIVE has two CELLs: "RED SCORING" and "RED AUDIENCE" are the red HIVE's, the "BLUE" ones the blue's.
     */
    public static List<TagGroup> biobuzz() {
        double[] row = {-6.5, -2.75, 2.75, 6.5};
        return Collections.unmodifiableList(Arrays.asList(
                new TagGroup("RED SCORING", new int[] {30, 31, 32, 33}, row, "RED HIVE"),
                new TagGroup("RED AUDIENCE", new int[] {34, 35, 36, 37}, row, "RED HIVE"),
                new TagGroup("BLUE AUDIENCE", new int[] {38, 39, 40, 41}, row, "BLUE HIVE"),
                new TagGroup("BLUE SCORING", new int[] {42, 43, 44, 45}, row, "BLUE HIVE")));
    }

    /**
     * The target to aim at for a goal named {@code name}. If {@code name} is a group on a field element
     * with more than one group (BIOBUZZ: "RED SCORING" is on the red HIVE), it is that element's group
     * that reads RIGHT SIDE UP now ({@link TargetObservation#isUpright()}): the HIVE's up CELL, whichever
     * of its two CELLs that is. A group that reads upside down (a down CELL, e.g. seen from under the
     * HIVE) is never the goal: null then, "no goal in view". Any other name: the target with that name,
     * as {@code camera.target(name)}.
     *
     * <pre>{@code
     * TargetObservation goal = TagGroup.goalTarget(camera, "RED SCORING");   // the red HIVE's up CELL
     * }</pre>
     *
     * @param camera the camera
     * @param name   a group's name (this season's), or any target name
     * @return the target to aim at, or null if it is not in view now
     */
    public static TargetObservation goalTarget(TargetSource camera, String name) {
        List<TagGroup> cells = sameElement(name);
        if (cells.isEmpty()) return camera.target(name);
        for (TagGroup g : cells) {
            TargetObservation t = camera.target(g.name);
            if (t != null && t.isUpright()) return t;
        }
        return null;
    }

    /**
     * Why {@link #goalTarget} gives no goal while the camera sees a group of the same element: e.g.
     * "RED SCORING is upside down (a down CELL)". "" when there is nothing to say.
     */
    public static String whyNoGoal(TargetSource camera, String name) {
        for (TagGroup g : sameElement(name)) {
            TargetObservation t = camera.target(g.name);
            if (t == null) continue;
            if (Double.isNaN(t.rollDeg)) return g.name + ": the camera cannot tell if it is right side up";
            if (!t.isUpright()) return g.name + " is upside down (a down CELL)";
        }
        return "";
    }

    // This season's groups on the same element as the group called name (several), or empty.
    private static List<TagGroup> sameElement(String name) {
        String element = null;
        for (TagGroup g : currentSeason()) if (g.name.equals(name)) element = g.element;
        List<TagGroup> out = new java.util.ArrayList<>();
        if (element == null) return out;
        for (TagGroup g : currentSeason()) if (element.equals(g.element)) out.add(g);
        return out.size() > 1 ? out : new java.util.ArrayList<TagGroup>();
    }

    /** This season's groups (the default for {@link LimelightTags}). */
    public static List<TagGroup> currentSeason() {
        return biobuzz();
    }
}
