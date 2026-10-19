package io.github.jasoncross.ftchelper.robot;

import io.github.jasoncross.ftchelper.auto.AutoRoutine;
import io.github.jasoncross.ftchelper.auto.EasyAuto;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.match.InitMenu;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
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
    protected final double startHeadingDeg() {
        return learned == null ? 0 : learned.headingDeg;
    }

    @Override
    protected final double parkReserveSec() {
        return learned == null ? 0 : learned.parkSec;
    }

    @Override
    protected final void addInitTelemetry(RobotTelemetry t) {
        if (robot != null) for (String n : robot.notes()) t.text(n);
        List<String> changed = RobotDefinition.changedTunables();
        if (!changed.isEmpty()) {
            t.section("Tuned on this robot (copy into Tunables.java)");
            for (String c : changed) t.text(c);
        }
    }

    @Override
    protected final void buildRoutine(AutoRoutine routine) {
        robot.setAlliance(alliance());
        routine.run("Start", robot::started);
        steps(new Steps(routine, menu, robot), robot);
    }
}
