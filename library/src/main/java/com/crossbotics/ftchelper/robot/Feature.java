package com.crossbotics.ftchelper.robot;

/**
 * One ready-made thing the library can do for this robot (see {@link RobotDefinition#features()}): an
 * autonomous step or action ("Fire 2", "Aim at the goal"), a TeleOp toggle ("aim mode"), or something
 * it does by itself ("the Goal line on the Driver Station"). Ready when the robot has the parts it
 * needs; otherwise {@link #missing} says what to add. {@link #code} is the line to use it.
 *
 * Docs: docs/modules/robot.md, "What your robot can do"
 */
public final class Feature {
    /** Where it is used. */
    public enum Kind {
        /** A step in an autonomous ({@code Steps}), e.g. {@code .aimAtGoal("Aim", 3)}. */
        AUTO_STEP,
        /** An action, for an autonomous ({@code .action(...)}) or a TeleOp button ({@code g1.y.press(...)}). */
        ACTION,
        /** A TeleOp on/off switch for a button, in Controls.java. */
        TOGGLE,
        /** On by itself: nothing to write. */
        AUTOMATIC;

        /** "auto step", "action", "TeleOp toggle", "automatic". */
        public String words() {
            return this == AUTO_STEP ? "auto step" : this == ACTION ? "action" : this == TOGGLE ? "TeleOp toggle" : "automatic";
        }
    }

    public final Kind kind;
    /** What it is called: "Fire 2", "Aim at the goal". */
    public final String name;
    /** The line to use it (Java), "" for AUTOMATIC. */
    public final String code;
    /** What it needs, in words: "a launcher", "a camera with goal(...), or odometry + Tunables.Aim goalX, goalY". */
    public final String needs;
    /** What is missing now ("" = ready). */
    public final String missing;
    /** A short note ("" = none): "with a tank drive: forward and back only". */
    public final String note;

    Feature(Kind kind, String name, String code, String needs, String missing, String note) {
        this.kind = kind;
        this.name = name;
        this.code = code;
        this.needs = needs;
        this.missing = missing;
        this.note = note;
    }

    /** True if the robot has what it needs. */
    public boolean ready() {
        return missing.isEmpty();
    }

    @Override public String toString() {
        return (ready() ? "ready  " : "NOT ready  ") + kind.words() + ": " + name + (code.isEmpty() ? "" : "  " + code)
                + (ready() ? "" : "  (missing: " + missing + ")") + (note.isEmpty() ? "" : "  - " + note);
    }
}
