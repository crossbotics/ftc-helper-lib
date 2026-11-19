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
 * holds the pieces back, and where it lets one through. It shows the line to copy:
 * {@code Tunables.Launcher: feedStopHold = 0.150, feedStopRelease = 0.550}.
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
 *       the shot. The longest test shot + 0.2 s. It warns when two pieces went through one opening.</li>
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
    /** Extra on the longest test shot, seconds: a low battery or a worn piece is slower. */
    static final double OPEN_EXTRA_SEC = 0.2;

    private Launcher launcher;
    private String testProblem = "";        // why the test shot cannot run ("" = it can)
    private FeedTest test;                  // made when the wheel goes on (LB); null = wheel off
    private final List<Double> times = new ArrayList<>();   // the test shots' times to the shot, seconds
    private String testNote = "";           // a warning about the last test shot
    private boolean wasBusy = false;

    @Override protected String finderName() { return "Feed stop finder"; }

    @Override
    protected void setup() {
        RobotDefinition r = robot();
        Launcher launcher = null;
        for (Module<?> m : r.modules()) {
            if (m instanceof Launcher && ((Launcher) m).feedStopName() != null) { launcher = (Launcher) m; break; }
        }
        if (launcher == null) {
            problem = "Robot.java has no launcher with .feedStop(\"name\")";
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
            testProblem = "test shot off: " + e.getMessage();
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
        if (!problem.isEmpty()) t.warn(problem);
        t.text("Feed stop finder: where it holds, where it lets a piece through.");
        t.text("Nothing moves until you press {A}. Press {PLAY}.");
        if (launcher != null && testProblem.isEmpty()) {
            t.text("The test shot ({LB}, {RB}) spins the wheel: eye protection.");
        }
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
        List<String> fields = new ArrayList<>();
        if (!Double.isNaN(hold)) fields.add("feedStopHold = " + FinderResults.num(hold, 3));
        if (!Double.isNaN(release)) fields.add("feedStopRelease = " + FinderResults.num(release, 3));
        results.set("feedstop", "Tunables." + section + ": " + FinderResults.commas(fields));
        List<String> timeFields = new ArrayList<>();
        double move = launcher == null ? Double.NaN : moveSecFor(launcher.feedStopPart(), hold, release);
        if (!Double.isNaN(move)) timeFields.add("feedStopMoveSec = " + FinderResults.num(move, 2));
        double open = openSecFor(times);
        if (!Double.isNaN(open)) timeFields.add("feedStopOpenSec = " + FinderResults.num(open, 2));
        if (timeFields.isEmpty()) results.remove("feedstoptimes");
        else results.set("feedstoptimes", "Tunables." + section + ": " + FinderResults.commas(timeFields));
    }

    @Override
    protected void step() {
        if (test == null) return;
        test.update();
        // A test shot just ended: keep its time, or say what went wrong.
        if (wasBusy && !test.isBusy()) {
            if (Double.isNaN(test.lastSec())) {
                testNote = "No shot seen in " + FinderResults.num(TEST_BACKUP_SEC, 0) + " s: the piece did not reach the wheel."
                        + " Check the release position ({B}).";
            } else {
                times.add(test.lastSec());
                testNote = test.lastPieces() >= 2 ? test.lastPieces() + " pieces in one opening: the next piece got past"
                        + " before the stop closed. Check the hold position, and the gap between pieces." : "";
            }
            writeResults();
        }
        wasBusy = test.isBusy();
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (servo == null) {
            t.warn(problem);
            return;
        }
        if (!moved()) {
            t.line("Start point", FinderResults.num(startAt, 3) + "  (D-pad up/down; {A} = move the feed stop there)");
            t.line("Step", step.label() + "  (D-pad left = smaller, right = bigger)");
            t.text("Choose a start point near where it sits now. Nothing moves until {A}.");
            return;
        }
        t.line("Position", FinderResults.num(position, 3));
        t.line("Step", step.label() + "  (D-pad left = smaller, right = bigger)");
        t.line("Hold ({X})", Double.isNaN(hold) ? "-" : FinderResults.num(hold, 3));
        t.line("Release ({B})", Double.isNaN(release) ? "-" : FinderResults.num(release, 3));
        if (test == null) {
            t.text("D-pad up/down = move, {X} = hold here, {B} = release here"
                    + (bothSaved() ? ", {A} = try hold / release" : ""));
        }
        if (!bothSaved()) return;
        showTimes(t);
    }

    // The two times: the move time from the catalog, and the test shots.
    private void showTimes(RobotTelemetry t) {
        t.section("Firing times");
        double move = moveSecFor(launcher == null ? null : launcher.feedStopPart(), hold, release);
        if (!Double.isNaN(move)) {
            t.line("Opens in", "about " + FinderResults.num(move, 2) + " s (" + launcher.feedStopPart().id + ", +25%)");
        } else {
            t.line("Opens in", "not known: add the servo's part to .feedStop(...) in Robot.java");
        }
        if (!testProblem.isEmpty()) {
            t.warn(testProblem);
            return;
        }
        if (test == null) {
            t.text("{LB} = wheel on for test shots (eye protection, nobody in front)");
        } else {
            t.line("Wheel ({LB} = off)", FinderResults.num(test.rpmNow(), 0) + " / " + FinderResults.num(test.targetRpm(), 0)
                    + " RPM" + (test.isReady() ? ", ready: {RB} = test shot" : ", spinning up"));
        }
        if (!times.isEmpty()) {
            StringBuilder b = new StringBuilder();
            int from = Math.max(0, times.size() - 5);
            for (int i = from; i < times.size(); i++) b.append(i > from ? ", " : "").append(FinderResults.num(times.get(i), 2));
            t.line("Test shots", b + " s  (" + times.size() + (times.size() < 5 ? " of 5" : "") + ")");
        }
        if (test != null && test.isBusy()) t.text("Test shot on its way...");
        if (!testNote.isEmpty()) t.warn(testNote);
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds the feed stop's hold and release positions.",
            "1. Put a piece behind the feed stop. Wheel off.",
            "2. D-pad up/down = a START POINT near where it is",
            "   (nothing moves), {A} = move it there.",
            "3. D-pad up/down = move by the step; left/right =",
            "   smaller/bigger step (0.1 to 0.001, starts 0.05).",
            "4. Where it holds the piece firmly (not straining",
            "   on the frame): {X} = hold.",
            "5. Where the piece rolls through freely: {B} = release.",
            "6. {A} = try hold / release. Copy the line.",
            "7. Eye protection. {LB} = wheel on, {RB} = test shot",
            "   (5 times). Copy the times line too.",
            "STOP saves the lines to the results file.",
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
