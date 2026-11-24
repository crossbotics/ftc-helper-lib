package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.robot.FeedTest;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.Parts;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds a launcher's feed stop positions ({@code Launcher.feedStop("feed_stop")} in Robot.java): where it
 * holds the pieces back, and where it lets one through. It shows the lines to copy into Tunables.java,
 * class Launcher: {@code public static double feedStopHold = 0.150;} and {@code feedStopRelease = 0.550;}.
 *
 * <p>In "Tune and check" it is in the menu for a robot with a feed stop. On its own:
 * <pre>{@code
 * @TeleOp(name = "Feed Stop Finder", group = "Finders")
 * public class FeedStopFinder extends FeedStopFinderOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 *
 * <p>With both positions saved, it also finds the two times:
 * <ul>
 *   <li>{@code feedStopMoveSec}, how long the stop takes to open: worked out from the servo's catalog speed
 *       when Robot.java names the part ({@code .feedStop("feed_stop", "gobilda-2000-speed")}), with 25% extra
 *       for the load.</li>
 *   <li>{@code feedStopOpenSec}, the backup time: LB spins the wheel up, RB fires a test shot (the stop opens,
 *       the indexer and pushing parts run like a real shot), and it times the piece until the wheel sees
 *       the shot. The longest test shot + 0.2 s. Its line shows after {@link #ENOUGH_SHOTS} test shots. It
 *       warns when two pieces went through one opening.</li>
 * </ul>
 *
 * Docs: docs/tuning/README.md, "Feed Stop Finder"
 */
public abstract class FeedStopFinderOpMode extends FinderOpMode {

    /** The robot in five files: {@code return new Robot();}. */
    protected abstract RobotDefinition robot();

    private Servo servo;
    private String section = "Launcher";
    private String problem = "";
    private double position = Double.NaN;   // NaN = not moved yet
    private double startAt = 0.5;
    private double hold = Double.NaN, release = Double.NaN;
    private final NudgeStep step = new NudgeStep();   // D-pad left/right changes it
    private boolean atRelease = false;      // A after both are saved: which one it went to last

    /** The test shot's backup time, seconds: long, so a slow piece is still timed. */
    static final double TEST_BACKUP_SEC = 3.0;
    /** Extra on the catalog's move time: the seller's speed is with no load. */
    static final double MOVE_EXTRA = 1.25;
    /** This many test shots before the backup time is shown as a line to copy. */
    static final int ENOUGH_SHOTS = 5;
    /** Extra on the longest test shot, seconds: a low battery or a worn piece is slower. */
    static final double OPEN_EXTRA_SEC = 0.2;

    private Launcher launcher;
    private String testProblem = "";        // why the test shot cannot run ("" = it can)
    private FeedTest test;                  // made when the wheel goes on (LB); null = wheel off
    private final List<Double> times = new ArrayList<>();   // the test shots' times to the shot, seconds
    private String testNote = "";           // a warning about the last test shot
    private boolean wasBusy = false;

    @Override protected String finderName() { return "Feed stop finder"; }

    @Override protected String stoppedWords() { return "Motor power is off. A wheel may still turn."; }

    @Override
    protected void setup() {
        RobotDefinition r = robot();
        Launcher launcher = null;
        for (Module<?> m : r.modules()) {
            if (m instanceof Launcher && ((Launcher) m).feedStopName() != null) { launcher = (Launcher) m; break; }
        }
        if (launcher == null) {
            problem = "Robot.java has no launcher with .feedStop(...).\nPress STOP. Add it, or use another tool.";
            return;
        }
        this.launcher = launcher;
        section = launcher.name();
        servo = hardwareMap.get(Servo.class, launcher.feedStopName());
        // The whole robot is set up (nothing moves: motors at 0), so the test shot can use the launcher's own
        // feed. A problem here only turns the test shot off: the positions still work.
        try {
            r.setup(hardwareMap);
        } catch (RuntimeException e) {
            testProblem = "No test shots: Robot.java did not set up.\n" + e.getMessage();
        }
    }

    /**
     * About how long the feed stop takes to open, seconds, rounded up to 0.05, with {@link #MOVE_EXTRA}:
     * from the servo's catalog speed. NaN when the part or a position is not known.
     */
    static double moveSecFor(Parts.ServoPart part, double hold, double release) {
        if (part == null || Double.isNaN(hold) || Double.isNaN(release)) return Double.NaN;
        double sec = part.moveSec(hold, release, false);
        if (Double.isNaN(sec)) return Double.NaN;
        return Math.max(0.05, roundUp(sec * MOVE_EXTRA));
    }

    /** The backup time for these test shots: the longest + {@link #OPEN_EXTRA_SEC}, rounded up to 0.05. NaN for none. */
    static double openSecFor(List<Double> shotSec) {
        double longest = Double.NaN;
        for (double s : shotSec) if (Double.isNaN(longest) || s > longest) longest = s;
        return Double.isNaN(longest) ? Double.NaN : roundUp(longest + OPEN_EXTRA_SEC);
    }

    // Up to the next 0.05 (a tiny tolerance, so 0.25 stays 0.25).
    private static double roundUp(double sec) {
        return Math.ceil(sec / 0.05 - 1e-6) * 0.05;
    }

    private boolean bothSaved() {
        return !Double.isNaN(hold) && !Double.isNaN(release);
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        warnLines(t, problem);
        t.warn("Hands clear of the feed stop: {A} moves it.");
        if (launcher != null && testProblem.isEmpty()) {
            t.warn("Test shots spin and FIRE: {LB}, then {RB}.");
            t.warn("Safety glasses. Nobody in front.");
        }
        t.text("Feed stop finder: the feed stop is the part");
        t.text("that holds pieces back. Finds its HOLD and");
        t.text("LET GO positions, and its times.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> nudge(1))
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> nudge(-1))
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, step::bigger)
         .bindPress(Pad.G1, Btn.DPAD_LEFT, step::smaller)
         .bindPress(Pad.G1, Btn.A, this::pressA)
         .bindPress(Pad.G1, Btn.X, () -> { if (moved() && test == null) { hold = position; positionsChanged(); } })
         .bindPress(Pad.G1, Btn.B, () -> { if (moved() && test == null) { release = position; positionsChanged(); } })
         .bindPress(Pad.G1, Btn.LB, this::toggleWheel)
         .bindPress(Pad.G1, Btn.RB, () -> { if (test != null) test.fire(); });
    }

    // New positions: the old test shots no longer count.
    private void positionsChanged() {
        times.clear();
        testNote = "";
        writeResults();
    }

    // LB: the wheel on (a fresh test feed with the positions found here; the stop goes to hold) or off.
    private void toggleWheel() {
        if (servo == null || launcher == null || !testProblem.isEmpty() || !bothSaved()) return;
        if (test != null) {
            test.spin(false);
            test = null;
            wasBusy = false;   // a test shot stopped part way is not a result
            position = hold;   // the test feed left the stop at hold
            atRelease = false;
            return;
        }
        double move = moveSecFor(launcher.feedStopPart(), hold, release);
        test = launcher.feedTest(hold, release, Double.isNaN(move) ? launcher.feedStopMoveSec() : move, TEST_BACKUP_SEC);
        test.spin(true);
    }

    private boolean moved() {
        return servo != null && !Double.isNaN(position);
    }

    // A: first, move to the start point. With both saved: go to hold, then release, then hold...
    private void pressA() {
        if (servo == null || test != null) return;
        if (!moved()) {
            position = startAt;
        } else if (!Double.isNaN(hold) && !Double.isNaN(release)) {
            atRelease = !atRelease;
            position = atRelease ? release : hold;
        } else {
            return;
        }
        servo.setPosition(position);
    }

    // One step up (+1) or down (-1). Before the first move only the start point changes.
    private void nudge(int direction) {
        if (servo == null || test != null) return;
        if (!moved()) {
            startAt = step.move(startAt, direction);
            return;
        }
        position = step.move(position, direction);
        servo.setPosition(position);
    }

    private void writeResults() {
        if (!Double.isNaN(hold)) results.setTunable("feedstop:hold", section, "feedStopHold", FinderResults.num(hold, 3));
        if (!Double.isNaN(release)) results.setTunable("feedstop:release", section, "feedStopRelease", FinderResults.num(release, 3));
        results.removeStartingWith("feedstoptimes:");
        double move = launcher == null ? Double.NaN : moveSecFor(launcher.feedStopPart(), hold, release);
        if (!Double.isNaN(move)) results.setTunable("feedstoptimes:move", section, "feedStopMoveSec", FinderResults.num(move, 2));
        // The line only after enough test shots: one shot is not the slowest one.
        double open = openSecFor(times);
        if (times.size() >= ENOUGH_SHOTS) {
            results.setTunable("feedstoptimes:open", section, "feedStopOpenSec", FinderResults.num(open, 2));
        }
        if (!bothSaved()) results.notDone("save both positions ({X} and {B}).");
        else if (testProblem.isEmpty() && times.size() < ENOUGH_SHOTS) {
            results.notDone(times.size() + " of " + ENOUGH_SHOTS + " test shots done.");
        } else {
            results.done();
        }
    }

    @Override
    protected void step() {
        if (test == null) return;
        test.update();
        // A test shot just ended: keep its time, or say what went wrong.
        if (wasBusy && !test.isBusy()) {
            if (Double.isNaN(test.lastSec())) {
                // It watches the wheel's speed: a piece may have gone without a drop that it could see.
                testNote = "No shot seen in " + FinderResults.num(TEST_BACKUP_SEC, 0) + " s. {LB} = flywheel off."
                        + "\nNo piece came out? Check LET GO and the path."
                        + "\nOne came out? The speed drop was too small.";
            } else {
                times.add(test.lastSec());
                testNote = test.lastPieces() >= 2 ? test.lastPieces() + " speed drops in one opening. 2 pieces out?"
                        + "\nCheck HOLD, and the gap between pieces." : "";
            }
            writeResults();
        }
        wasBusy = test.isBusy();
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (servo == null) {
            warnLines(t, problem);
            return;
        }
        if (!moved()) {
            t.warn("{A} = the feed stop JUMPS to this number.");
            t.warn("Hands clear. Nothing moves until {A}.");
            t.section("Feed stop finder: first move");
            t.text("Next: set a number near where it sits now.");
            t.text("0 and 1 are its two ends. 0.5 is the middle.");
            t.line("Start point", FinderResults.num(startAt, 3));
            t.line("Step", step.label() + "  (D-pad left/right)");
            t.text("Not sure? Take the servo's arm off. Use 0.500.");
            return;
        }
        if (test == null) {
            t.warn("Every step moves it. Hands clear.");
            if (Double.isNaN(hold)) {
                t.section("Feed stop finder: step 1 of 3, HOLD");
                t.text("Next: move to where it holds a piece firmly,");
                t.text("without pushing the frame. {X} = save HOLD.");
            } else if (Double.isNaN(release)) {
                t.section("Feed stop finder: step 2 of 3, LET GO");
                t.text("Next: move to where a piece rolls through.");
                t.text("{B} = save LET GO.");
            } else if (!testProblem.isEmpty()) {
                t.section("Feed stop finder: positions found");
                t.text("Next: {A} = try both positions.");
                warnLines(t, testProblem);
            } else {
                t.section("Feed stop finder: step 3 of 3, test shots");
                t.text("Next: load a piece. {LB} = flywheel on.");
                t.warn("{LB} = flywheel on, and the stop goes to HOLD.");
                t.warn("Safety glasses. Nobody in front.");
                t.text("{A} = try both positions first.");
            }
            t.line("Position", FinderResults.num(position, 3) + "  (0 to 1)");
            if (!bothSaved()) t.line("Step", step.label() + "  (D-pad left/right)");
            t.line("HOLD ({X})", Double.isNaN(hold) ? "not saved" : FinderResults.num(hold, 3));
            t.line("LET GO ({B})", Double.isNaN(release) ? "not saved" : FinderResults.num(release, 3));
            if (bothSaved()) showTimes(t);
            return;
        }
        t.warn("It shoots. Nobody in front. {LB} = stop.");
        t.section("Feed stop finder: test shot " + (times.size() + 1));
        if (test.isBusy()) t.text("Test shot on its way. Wait.");
        else if (test.isReady()) t.text("Next: {RB} = test shot. Hands away.");
        else t.text("Next: wait for the flywheel's speed.");
        t.line("Flywheel", FinderResults.num(test.rpmNow(), 0) + " of " + FinderResults.num(test.targetRpm(), 0)
                + " RPM" + (test.isReady() ? ", ready" : ""));
        showTimes(t);
    }

    // The two times: the move time from the catalog, and the test shots.
    private void showTimes(RobotTelemetry t) {
        double move = moveSecFor(launcher == null ? null : launcher.feedStopPart(), hold, release);
        if (!Double.isNaN(move)) {
            t.line("Opens in", "about " + FinderResults.num(move, 2) + " s (from the servo's speed)");
        } else {
            t.text("Opening time not known: in Robot.java, add");
            t.text("  the servo's part to .feedStop(...).");
        }
        if (!times.isEmpty()) {
            StringBuilder b = new StringBuilder();
            int from = Math.max(0, times.size() - 5);
            for (int i = from; i < times.size(); i++) b.append(i > from ? ", " : "").append(FinderResults.num(times.get(i), 2));
            t.line("Test shots", b + " s  (" + times.size() + (times.size() < ENOUGH_SHOTS ? " of " + ENOUGH_SHOTS : "") + ")");
        }
        warnLines(t, testNote);
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds the feed stop's HOLD and LET GO.",
            "1. Set a number near where it sits now.",
            "   {A} = it jumps there. Not sure? Arm off first.",
            "2. D-pad up/down = step. Left/right = step size.",
            "3. It holds a piece: {X}. Piece goes through: {B}.",
            "   {A} = try one, then the other.",
            "4. Safety glasses. Nobody in front.",
            "   Load a piece. {LB} = flywheel on.",
            "   {RB} = test shot. Do it 5 times or more.",
            "   {LB} = flywheel off, and it ends a test.",
            "New positions clear the test shots.",
        };
    }

    // The wheel stops. A feed stop stays where it is: holding is safe.
    @Override
    protected void stopAll() {
        if (test != null) {
            test.spin(false);
            test = null;
            wasBusy = false;
            if (bothSaved()) position = hold;
        }
    }
}
