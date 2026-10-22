package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.auto.AutoRoutine;
import com.crossbotics.ftchelper.auto.AutoStep;
import com.crossbotics.ftchelper.match.InitMenu;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/**
 * The steps of one autonomous, read top to bottom (see {@link Auto}). Each method adds a step and
 * hands the list back, so they chain:
 *
 * <pre>{@code
 * s.start("Back wall, 2nd tile from the left, facing the field", 0)
 *  .menuYesNo("Score", true)
 *  .run("Hold the piece", robot.claw::close)
 *  .move("Leave the wall", 4, 0, 0.4)
 *  .onlyIf("Score")
 *      .run("Lift up", () -> robot.lift.goTo("high"))       // rises during the next moves
 *      .move("Drive to the goal", 24, 0, 0.6)
 *      .turn("Face the goal", 45, 0.5)                      // mirrored for RED
 *      .waitUntil("Lift at the top", robot.lift::isAtTarget, 2.0)
 *      .run("Drop", robot.claw::open)
 *  .end()
 *  .parkWithSeconds(4)
 *  .move("Park", 20, 90, 0.7);
 * }</pre>
 *
 * <p>Moves and turns: directions in degrees, clockwise from forward for moves (0 forward, 90 right),
 * counterclockwise + for turns; written for BLUE and mirrored for RED. Distances in inches, powers
 * 0 to 1. Each move or turn waits until it is done; {@code run} starts something and goes on at once
 * (the mechanisms keep moving during the next steps).
 *
 * <p>The list is read in INIT (to learn the menu, the start position and the park time: no step runs
 * then), again in INIT each time the drivers change a menu line (so a {@code start(...)} or
 * {@code parkWithSeconds(...)} inside an {@code onlyIf} follows the choice), and once after START.
 * So write only steps here, no other code with side effects.
 *
 * Docs: docs/modules/robot.md (section "Autos")
 */
public final class Steps {
    private final AutoRoutine routine;   // null in INIT (only the menu and start are read)
    private final InitMenu menu;
    private final RobotDefinition robot;
    // True on the first read in INIT: the menu lines are added then (later reads only look at them).
    private final boolean addMenuLines;
    // The onlyIf blocks we are inside: true = add the steps, false = skip them.
    private final Deque<Boolean> blocks = new ArrayDeque<>();
    // The same blocks, but true only when the drivers' CURRENT choice matches (also in INIT, where
    // "blocks" says true for every block so that every menu line is learned). start(...) and
    // parkWithSeconds(...) follow these, so a start spot can be a menu choice.
    private final Deque<Boolean> chosen = new ArrayDeque<>();
    // The labels of the menu lines this list adds, in order (Auto reads them to notice a change).
    final java.util.List<String> menuLabels = new java.util.ArrayList<>();
    String where = null;
    double headingDeg = 0;
    double spotX = Double.NaN, spotY = Double.NaN;
    double parkSec = 0;

    Steps(AutoRoutine routine, InitMenu menu, RobotDefinition robot) {
        this(routine, menu, robot, routine == null);
    }

    // addMenuLines false: a later read in INIT, when the menu already has its lines.
    Steps(AutoRoutine routine, InitMenu menu, RobotDefinition robot, boolean addMenuLines) {
        this.addMenuLines = addMenuLines;
        this.routine = routine;
        this.menu = menu;
        this.robot = robot;
        // A path that holds its end point keeps driving after its step; any other driving step ends that
        // hold first, so the follower and the step never drive the wheels at once.
        if (routine != null && robot != null && robot.hasFollower()) {
            routine.beforeDriving(() -> {
                PathFollower f = robot.follower();
                if (f != null && f.isHolding()) f.cancel();
            });
        }
    }

    // True when steps are being added (after START, and not inside a skipped onlyIf).
    private boolean adding() {
        return routine != null && !blocks.contains(false);
    }

    /**
     * Where to place the robot (shown in INIT) and which way it faces, degrees from "straight away
     * from your driver", counterclockwise + (TeleOp's field-centric forward uses it).
     */
    public Steps start(String whereToPlace, double headingDeg) {
        // Inside an onlyIf the drivers did not choose: not the start (a menu choice of start spot).
        if (chosen.contains(false)) return this;
        where = whereToPlace;
        this.headingDeg = headingDeg;
        return this;
    }

    /**
     * Where to place the robot: the note for the drive team (shown in INIT), and the spot as seen from
     * your drivers' wall, written for BLUE: {@code xIn} inches to the right of the field's centre line,
     * {@code yIn} from the centre toward the far wall (negative = your half), heading 0 = facing away
     * from you, counterclockwise +. RED uses it like the routes: mirrored left-right, or (with
     * {@code mirrorForRed()} false, a field turned half a turn like BIOBUZZ) the same numbers from the
     * red wall. The simulator puts the robot there.
     *
     * <pre>{@code
     * s.start("Against our wall, 2nd tile from the left, facing the field", -36, -63, 0)
     * }</pre>
     */
    public Steps start(String whereToPlace, double xIn, double yIn, double headingDeg) {
        if (chosen.contains(false)) return this;
        spotX = xIn;
        spotY = yIn;
        return start(whereToPlace, headingDeg);
    }

    // ----------------------------- the INIT menu -----------------------------

    /** A yes/no choice in the INIT menu (D-pad), read with {@link #onlyIf} or {@link #isYes}. */
    public Steps menuYesNo(String label, boolean startYes) {
        if (addMenuLines) menu.yesNo(label, startYes);
        menuLabels.add(label);
        return this;
    }

    /**
     * A choice between options in the INIT menu, read with {@link #onlyIf(String, String)} or
     * {@link #choice}. The first option is chosen at the start. It can also choose the start spot: a
     * {@code start(...)} inside an {@code onlyIf} is used only for its option, and the note (and the
     * simulator's robot) changes as the drivers change the choice.
     *
     * <pre>{@code
     * s.menuChoice("Start", "Near goal", "Far")
     *  .onlyIf("Start", "Near goal").start("Back against our goal", -56, -61, 0).end()
     *  .onlyIf("Start", "Far").start("Against our wall, far corner tile", 56, -61, 0).end()
     * }</pre>
     */
    public Steps menuChoice(String label, String... options) {
        if (addMenuLines) menu.choice(label, options);
        menuLabels.add(label);
        return this;
    }

    /** True if the yes/no choice is "yes". */
    public boolean isYes(String label) {
        return menu.isYes(label);
    }

    /** The option chosen for this label. */
    public String choice(String label) {
        return menu.get(label);
    }

    /** The steps up to {@link #end()} run only if the yes/no choice is "yes". */
    public Steps onlyIf(String yesNoLabel) {
        return onlyIf(yesNoLabel, "YES");
    }

    /** The steps up to {@link #end()} run only if this option was chosen. */
    public Steps onlyIf(String label, String option) {
        boolean now = option.equals(chosenNow(label));
        blocks.push(routine == null || now);
        chosen.push(now);
        return this;
    }

    // The option chosen now, or null when there is no such menu line (yet). After START a missing line
    // stops with the menu's message, as before; in INIT it counts as "not chosen".
    private String chosenNow(String label) {
        if (routine != null) return menu.get(label);
        try {
            return menu.get(label);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Ends the latest {@link #onlyIf}. */
    public Steps end() {
        if (blocks.isEmpty()) throw new IllegalStateException("Steps: end() without an onlyIf(...)");
        blocks.pop();
        chosen.pop();
        return this;
    }

    // ----------------------------- steps -----------------------------

    /** Drives a distance in a direction (clockwise from forward: 0 forward, 90 right, 180 back). Waits until done. */
    public Steps move(String label, double inches, double directionDeg, double maxPower) {
        if (adding()) routine.move(label, inches, directionDeg, maxPower);
        return this;
    }

    /**
     * Drives to a point on the field, using the odometry, and turns to a heading at the same time
     * (mecanum). Seen from your drivers' wall, written for BLUE like the start spot: {@code xIn} inches
     * right of the centre line, {@code yIn} inches from the centre toward the far wall, heading 0 =
     * facing away, counterclockwise +. Waits until it is there (within 1 inch).
     *
     * <p>Needs an {@code odometry(...)} line in Robot.java, and {@code Tunables.Odometry.checked = true}
     * on a real robot (the Odometry Tuner); until then the step is skipped and the report says why.
     */
    public Steps driveTo(String label, double xIn, double yIn, double headingDeg, double maxPower) {
        return driveTo(label, xIn, yIn, headingDeg, maxPower, 1.0);
    }

    /**
     * {@link #driveTo(String, double, double, double, double)} that also starts things on the way:
     * {@code PathAt.inchesLeft(12, ...)}, {@code PathAt.fraction(0.5, ...)}, {@code PathAt.near(...)}.
     * The "path" is the straight line from where the robot is when the step starts to the point.
     * ({@code PathAt.part(n)} needs a path with parts: only {@code part(1)} works here.)
     */
    public Steps driveTo(String label, double xIn, double yIn, double headingDeg, double maxPower, PathAt... along) {
        return driveTo(label, xIn, yIn, headingDeg, maxPower, 1.0, along);
    }

    /** {@link #driveTo(String, double, double, double, double)} with your own "close enough", inches. */
    public Steps driveTo(String label, double xIn, double yIn, double headingDeg, double maxPower, double toleranceIn) {
        return driveTo(label, xIn, yIn, headingDeg, maxPower, toleranceIn, new PathAt[0]);
    }

    /** {@link #driveTo(String, double, double, double, double, PathAt...)} with your own "close enough", inches. */
    public Steps driveTo(String label, double xIn, double yIn, double headingDeg, double maxPower, double toleranceIn,
                         PathAt... along) {
        if (robot.odometrySpec() == null) {
            throw new IllegalStateException("Steps: driveTo(\"" + label + "\") needs odometry: add an odometry(...) line "
                    + "to Robot.java (docs/modules/odometry.md)");
        }
        if (!robot.drive().isMecanum()) {
            throw new IllegalStateException("Steps: driveTo(\"" + label + "\") needs a mecanum drive (it drives sideways). "
                    + "On a tank drive, use turnTo(...) and then move(...).");
        }
        checkAlong("driveTo", label, along, 1);
        if (!adding()) return this;
        boolean simulator = "true".equals(System.getProperty(com.crossbotics.ftchelper.auto.EasyAuto.SIMULATOR_PROPERTY));
        if (!robot.odometryChecked() && !simulator) {
            routine.step(label, ctx -> ctx.note("skipped: Tunables.Odometry.checked is false (run the Odometry Tuner first)"));
            return this;
        }
        routine.driveTo(label, robot::pose, () -> robot.odometry() != null && robot.odometry().isReady(),
                xIn, yIn, headingDeg, maxPower, toleranceIn, events(along));
        return this;
    }

    // At INIT: no missing PathAt, and no part(n) past the path's last part (parts: 0 = unknown).
    private void checkAlong(String step, String label, PathAt[] along, int parts) {
        if (along == null) return;
        for (PathAt a : along) {
            if (a == null) throw new IllegalStateException("Steps: " + step + "(\"" + label + "\"): a PathAt is null");
            if (parts > 0 && a.partNumber() > parts) {
                throw new IllegalStateException("Steps: " + step + "(\"" + label + "\"): PathAt.part(" + a.partNumber()
                        + "), but the path has " + parts + (parts == 1 ? " part" : " parts")
                        + (step.equals("driveTo") ? " (driveTo is one straight line)" : ""));
            }
        }
    }

    // The PathAts as the routine's plain events (an action runs on the robot's action runner).
    private java.util.List<com.crossbotics.ftchelper.auto.PathEvent> events(PathAt[] along) {
        java.util.List<com.crossbotics.ftchelper.auto.PathEvent> out = new java.util.ArrayList<>();
        if (along != null) for (PathAt a : along) out.add(a.event(robot, routine.mirrorsX()));
        return out;
    }

    /**
     * Follows a path with the robot's path follower (Robot.java's {@code follower(...)} line, e.g. Pedro
     * Pathing through the "pedro" module), and waits until it is at the end. The path is written for
     * BLUE like everything else: on RED with mirroring on, the follower mirrors it. At most 10 s.
     * Like driveTo, it is skipped until {@code Tunables.Odometry.checked = true} (not in the simulator).
     *
     * @param path the path (for Pedro: {@code PedroPath.line(...)} or a Pedro Path)
     */
    public Steps follow(String label, Object path) {
        return follow(label, path, 10);
    }

    /**
     * {@link #follow(String, Object)} that also starts things ALONG the path, while the robot keeps
     * driving. Each happens once:
     *
     * <pre>{@code
     * s.follow("To the goal", TO_GOAL,
     *          PathAt.fraction(0.5, "Intake on", robot.intake::in),     // halfway along the whole path
     *          PathAt.inchesLeft(12, "Spin up", robot.launcher::spinUp));   // 12 in before the end
     * }</pre>
     *
     * If the path ends early, the report says which did not happen. See {@link PathAt}.
     */
    public Steps follow(String label, Object path, PathAt... along) {
        return follow(label, path, 10, along);
    }

    /** {@link #follow(String, Object)} with your own time limit, seconds. */
    public Steps follow(String label, final Object path, final double timeoutSec) {
        return follow(label, path, timeoutSec, new PathAt[0]);
    }

    /** {@link #follow(String, Object, PathAt...)} with your own time limit, seconds. */
    public Steps follow(String label, final Object path, final double timeoutSec, PathAt... along) {
        if (!robot.hasFollower()) {
            throw new IllegalStateException("Steps: follow(\"" + label + "\") needs a path follower: add a "
                    + "follower(...) line to Robot.java (docs/modules/pedro.md)");
        }
        if (path == null) throw new IllegalStateException("Steps: follow(\"" + label + "\"): the path is null");
        if (robot.follower() != null) {
            try {
                robot.follower().check(path);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Steps: follow(\"" + label + "\"): " + e.getMessage(), e);
            }
            checkAlong("follow", label, along, robot.follower().parts(path));
        } else {
            checkAlong("follow", label, along, 0);
        }
        if (!adding()) return this;
        // Like driveTo: an odometry that was never checked could send the robot anywhere.
        boolean simulator = "true".equals(System.getProperty(com.crossbotics.ftchelper.auto.EasyAuto.SIMULATOR_PROPERTY));
        if (!robot.odometryChecked() && !simulator) {
            routine.step(label, ctx -> ctx.note("skipped: Tunables.Odometry.checked is false (run the Odometry Tuner first)"));
            return this;
        }
        final boolean mirror = routine.mirrorsX();
        routine.followPath(label, new com.crossbotics.ftchelper.auto.PathJob() {
            @Override public void start() { robot.follower().follow(path, mirror); }
            @Override public boolean isBusy() { return robot.follower().isBusy(); }
            @Override public boolean isReady() { return robot.follower().isReady(); }
            @Override public double remainingIn() { return robot.follower().remainingIn(); }
            @Override public void cancel() { robot.follower().cancel(); }
            @Override public double totalIn() { return robot.follower().totalIn(); }
            @Override public int partNow() { return robot.follower().partNow(); }
        }, timeoutSec, events(along));
        return this;
    }

    /** Turns by an angle (counterclockwise +). Waits until done. */
    public Steps turn(String label, double degrees, double maxPower) {
        if (adding()) routine.turn(label, degrees, maxPower);
        return this;
    }

    /** Turns to a heading (0 = the way it faced at the start, counterclockwise +). Waits until done. */
    public Steps turnTo(String label, double headingDeg, double maxPower) {
        if (adding()) routine.turnTo(label, headingDeg, maxPower);
        return this;
    }

    /** Runs a command now and goes on at once (e.g. {@code () -> robot.lift.goTo("high")}). */
    public Steps run(String label, Runnable command) {
        if (adding()) routine.run(label, command);
        return this;
    }

    /** Waits this long (the mechanisms keep working). */
    public Steps waitSeconds(String label, double seconds) {
        if (adding()) routine.waitFor(label, seconds);
        return this;
    }

    /** Waits until something is true, at most {@code timeoutSec} (the report says if it timed out). */
    public Steps waitUntil(String label, BooleanSupplier condition, double timeoutSec) {
        if (adding()) routine.waitUntil(label, condition, timeoutSec);
        return this;
    }

    /** Runs an action from Actions.java and waits until it is done (at most {@code timeoutSec}). */
    public Steps action(final Action action, double timeoutSec) {
        if (adding()) {
            routine.step(action.name(), ctx -> {
                robot.run(action);
                ctx.waitUntil(() -> !action.isRunning(), timeoutSec);
                if (action.isRunning()) {
                    // Out of time (or autonomous is over): stop it, and say so in the report.
                    robot.cancel(action);
                    if (ctx.isActive()) ctx.note("timed out");
                } else if (!"done".equals(action.result())) {
                    ctx.note(action.result());   // e.g. "timed out waiting for lift at the top"
                }
                // A part that is not on the robot yet (optional in Robot.java): the action went on without it.
                if (!action.skippedWords().isEmpty()) ctx.note(action.skippedWords());
            });
        }
        return this;
    }

    /** {@link #action(Action, double)} with up to 10 seconds. */
    public Steps action(Action action) {
        return action(action, 10);
    }

    /** Starts an action and goes on at once: it runs during the next steps. */
    public Steps alongside(final Action action) {
        if (adding()) routine.run(action.name() + " (alongside)", () -> robot.run(action));
        return this;
    }

    /** Any other step (a mechanism's step, or your own: see {@code AutoStep}). */
    public Steps step(String label, AutoStep step) {
        if (adding()) routine.step(label, step);
        return this;
    }

    /**
     * The steps after this are PARK steps, and they get the last {@code seconds} of autonomous: when
     * only that many seconds are left, the step running before it stops at once and the rest before
     * it are skipped; the park steps always run. The report says which were cut short or skipped.
     * Docs: docs/modules/robot.md, "Keep time to park".
     *
     * @param seconds how long the park steps take, plus a little (from a timed run)
     */
    public Steps parkWithSeconds(double seconds) {
        // Only the park time of the branch the drivers chose (a park time can differ per route).
        if (!chosen.contains(false)) parkSec = seconds;
        if (adding()) routine.thenPark();
        return this;
    }
}
