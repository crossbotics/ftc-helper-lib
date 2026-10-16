package io.github.jasoncross.ftchelper.vision;

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
    private final int[] ids;
    private final double[] alongRowIn;

    /**
     * @param name        the target's name, e.g. "RED SCORING"
     * @param ids         the tag ids in the group
     * @param alongRowIn  each tag's place along the row, inches from the middle (+ = right as printed),
     *                    in the same order as {@code ids}
     */
    public TagGroup(String name, int[] ids, double[] alongRowIn) {
        if (ids.length != alongRowIn.length || ids.length < 2) {
            throw new IllegalArgumentException("TagGroup " + name + ": needs at least 2 tags, each with its place along the row");
        }
        this.name = name;
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
     * tag library (AprilTagGameDatabase.getBioBuzzTagLibrary(), manual section 9.9).
     */
    public static List<TagGroup> biobuzz() {
        double[] row = {-6.5, -2.75, 2.75, 6.5};
        return Collections.unmodifiableList(Arrays.asList(
                new TagGroup("RED SCORING", new int[] {30, 31, 32, 33}, row),
                new TagGroup("RED AUDIENCE", new int[] {34, 35, 36, 37}, row),
                new TagGroup("BLUE AUDIENCE", new int[] {38, 39, 40, 41}, row),
                new TagGroup("BLUE SCORING", new int[] {42, 43, 44, 45}, row)));
    }

    /** This season's groups (the default for {@link LimelightTags}). */
    public static List<TagGroup> currentSeason() {
        return biobuzz();
    }
}
