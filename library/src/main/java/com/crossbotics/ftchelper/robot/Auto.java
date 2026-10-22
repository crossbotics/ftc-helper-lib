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

    /**
     * The steps. Read once in INIT (for the menu and start; nothing runs) and once after START.
     *
     * @param s     the step list
     * @param robot the robot (set up: its modules can be named in steps)
     */
    protected abstract void steps(Steps s, R robot);

    /** The robot this autonomous runs (set up in INIT). */
    protected final R robot() {
        return robot;
    }

    @SuppressWarnings("unchecked")
    private R newRobot() {
        return (R) RobotDefinition.newFromTypeOf(getClass(), Auto.class);
    }

    // ----------------------------- EasyAuto's hooks -----------------------------

    @Override
    protected final RobotConfig robotConfig() {
        if (config == null) config = newRobot();
        return config.robotConfig();
    }

    @Override
    protected final Subsystems createSubsystems() {
        // A fresh robot each time: EasyAuto makes the mechanisms again if the profile changes in INIT.
        robot = newRobot();
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
    }

    @Override
    protected final String startPosition() {
        return learned == null ? null : learned.where;
    }

    @Override
    protected final double[] startSpotIn() {
        return learned == null || Double.isNaN(learned.spotX) ? null : new double[] {learned.spotX, learned.spotY};
    }

    @Override
    protected final double startHeadingDeg() {
        return learned == null ? 0 : learned.headingDeg;
    }

    @Override
    protected final double parkReserveSec() {
        return learned == null ? 0 : learned.parkSec;
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
        }
        if (robot != null) {
            // The modules' INIT work (a camera's lighting test), with their lines.
            robot.initLoop(t);
            for (String n : robot.notes()) t.text(n);
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
