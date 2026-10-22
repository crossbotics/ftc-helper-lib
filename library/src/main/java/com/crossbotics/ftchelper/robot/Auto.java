package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.auto.AutoRoutine;
import com.crossbotics.ftchelper.auto.EasyAuto;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.match.InitMenu;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.List;

/**
 * One autonomous, in one short file: the list of {@link Steps}. Everything else (INIT menu with
 * alliance and start delay, red/blue mirroring, the 30 s timer, the park reserve, stopping
 * everything at the end, the report) is {@link EasyAuto}'s.
 *
 * <pre>{@code
 * @Autonomous(name = "Score and Park", preselectTeleOp = "TeleOp")
 * public class ScoreAndPark extends Auto<Robot> {
 *     @Override protected void steps(Steps s, Robot robot) {
 *         s.start("Back wall, 2nd tile from the left, facing the field", 0)
 *          .run("Hold the piece", robot.claw::close)
 *          .move("Drive to the goal", 24, 0, 0.6)
 *          .action(Actions.scoreHigh(robot))
 *          .parkWithSeconds(4)
 *          .move("Park", 20, 90, 0.7);
 *     }
 * }
 * }</pre>
 *
 * <p>A new autonomous: copy the file, change its name and its steps.
 *
 * <p>Checked before the match: autonomous refuses to run until {@code Tunables.Drive.calibrated} is
 * true (after the Drive Tuner), and a step that names a part not on the robot stops in INIT.
 *
 * Docs: docs/modules/robot.md (section "Autos")
 */
public abstract class Auto<R extends RobotDefinition> extends EasyAuto {
    private R robot;
    private R config;      // made once, for the drive settings (no hardware)
    private InitMenu menu;
    private Steps learned;
    // The drivers' choices when "learned" was read (alliance and every menu line), to notice a change.
    private String learnedFor;

    /**
     * The steps. Read in INIT (for the menu, the start and the park time; nothing runs), again in INIT
     * whenever the drivers change a menu choice, and once after START.
     *
     * @param s     the step list
     * @param robot the robot (set up: its modules can be named in steps)
     */
    protected abstract void steps(Steps s, R robot);

    /** The robot this autonomous runs (set up in INIT). */
    protected final R robot() {
        return robot;
    }

    /**
     * Makes a new robot: the class named in {@code extends Auto<Robot>}. Teams never need to change
     * this. The simulator's "Built Auto" (it runs a plan from the auto builder with whichever Robot
     * class the team has) gives its own. Called once for the settings and again at each INIT read.
     *
     * @return a new robot, not set up yet (no hardware)
     */
    @SuppressWarnings("unchecked")
    protected R newRobot() {
        return (R) RobotDefinition.newFromTypeOf(getClass(), Auto.class);
    }

    // ----------------------------- EasyAuto's hooks -----------------------------

    @Override
    protected final RobotConfig robotConfig() {
        if (config == null) config = newRobot();
        return config.robotConfig();
    }

    /**
     * True: the steps are written for BLUE and mirrored left-right for RED; false: RED uses the same
     * numbers from the red wall (a field turned half a turn, like BIOBUZZ). Default: the robot's one
     * setting ({@link RobotDefinition#mirrorForRed()}: {@code Tunables.Aim.mirrorForRed}, else
     * {@code Tunables.Odometry.mirrorForRed}, else true). Overriding it here still works: the robot's aim
     * and reset spot then use your answer too (and the TeleOp after this autonomous).
     */
    @Override
    protected boolean mirrorForRed() {
        R r = robot != null ? robot : config;
        if (r == null) {
            config = newRobot();
            r = config;
        }
        return r.mirrorForRed();
    }

    // True when the team's class (or one between it and Auto) writes its own mirrorForRed().
    private boolean ownMirror() {
        for (Class<?> c = getClass(); c != null && c != Auto.class; c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod("mirrorForRed");
                return true;
            } catch (NoSuchMethodException e) {
                // not here: look at the class it extends
            }
        }
        return false;
    }

    @Override
    protected final Subsystems createSubsystems() {
        // A fresh robot each time: EasyAuto makes the mechanisms again if the profile changes in INIT.
        robot = newRobot();
        // The autonomous's own mirrorForRed(): the whole robot uses it (aim, reset spot), and so does the
        // TeleOp after it, unless Tunables says it (Tunables wins; INIT warns when the two differ).
        if (ownMirror()) {
            boolean own = mirrorForRed();
            robot.autoMirror = own;
            RobotDefinition.LAST_AUTO_MIRROR.put(robot.getClass().getName(), own);
        }
        // Autonomous: the robot was placed by hand, so parts start from their start positions (a turret's
        // angle from the last program is not used).
        robot.autonomous = true;
        robot.setup(hardwareMap);
        return robot.all();
    }

    @Override
    protected final void addChoices(InitMenu initMenu) {
        menu = initMenu;
        learned = new Steps(null, menu, robot);
        steps(learned, robot);
        learnedFor = choicesNow();
    }

    // The steps as the drivers have chosen so far. When a menu choice changed since the last read, the
    // list is read again (no step runs), so a start spot or park time inside an onlyIf follows the menu.
    private Steps learned() {
        if (learned == null || menu == null) return learned;
        String now = choicesNow();
        if (!now.equals(learnedFor)) {
            Steps again = new Steps(null, menu, robot, false);
            steps(again, robot);
            learned = again;
            learnedFor = now;
        }
        return learned;
    }

    // Every choice in one line of text, e.g. "BLUE|Near|YES".
    private String choicesNow() {
        StringBuilder b = new StringBuilder(menu.getAlliance().name());
        for (String label : learned.menuLabels) b.append('|').append(menu.get(label));
        return b.toString();
    }

    @Override
    protected final String startPosition() {
        Steps l = learned();
        return l == null ? null : l.where;
    }

    @Override
    protected final double[] startSpotIn() {
        Steps l = learned();
        return l == null || Double.isNaN(l.spotX) ? null : new double[] {l.spotX, l.spotY};
    }

    @Override
    protected final double startHeadingDeg() {
        Steps l = learned();
        return l == null ? 0 : l.headingDeg;
    }

    @Override
    protected final double parkReserveSec() {
        Steps l = learned();
        return l == null ? 0 : l.parkSec;
    }

    @Override
    protected final String notTunedHelp() {
        return "Tune it with the Drive Tuner (tutorials, main track lesson 6), then set Tunables.Drive.calibrated = true.";
    }

    @Override
    protected final void addInitTelemetry(RobotTelemetry t) {
        // The odometry in INIT: read it, and show its status, so a missing pod is seen before START.
        if (robot.odometry() != null) {
            robot.odometry().update();
            if (!robot.odometry().isReady()) t.warn("Odometry (" + robot.odometry().kind() + "): " + robot.odometry().status());
            else t.line("Odometry", robot.odometry().kind() + " ready");
            // Not checked with the Odometry Tuner yet: driveTo and path steps do not run on a real robot.
            if (!robot.odometryChecked()) {
                boolean sim = "true".equals(System.getProperty(SIMULATOR_PROPERTY));
                t.warn("Odometry not checked (Tunables.Odometry.checked = false): driveTo and path steps "
                        + (sim ? "run in the simulator only" : "are skipped (Odometry Tuner, then checked = true)"));
            }
        }
        if (robot != null) {
            // The modules' INIT work (a camera's lighting test), with their lines.
            robot.initLoop(t);
            robot.showNotes(t);   // a part that is off: a "!!" warning in COMPETITION
        }
        List<String> changed = RobotDefinition.changedTunables();
        if (!changed.isEmpty()) {
            t.section("Tuned on this robot (copy into Tunables.java)");
            for (String c : changed) t.text(c);
        }
    }

    // With odometry, TeleOp gets the whole end pose (where, and which way it faces).
    @Override
    protected final com.crossbotics.ftchelper.util.FieldPose endPose() {
        return robot.pose();
    }

    @Override
    protected final void buildRoutine(AutoRoutine routine) {
        robot.setAlliance(alliance());
        routine.run("Start", () -> {
            // The odometry starts at the start spot (written for BLUE; mirrored for RED like the routes).
            if (robot.odometry() != null) {
                double[] spot = startSpotIn();
                double x = spot == null ? 0 : spot[0], y = spot == null ? 0 : spot[1], h = startHeadingDeg();
                if (alliance() == com.crossbotics.ftchelper.match.Alliance.RED && mirrorForRed()) {
                    x = -x;
                    h = -h;
                }
                robot.odometry().setPose(new com.crossbotics.ftchelper.util.FieldPose(x, y, h));
            }
            robot.started();
        });
        steps(new Steps(routine, menu, robot), robot);
    }
}
