package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Module;
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
 * <p>The open time and the push time are not measured here: fire a few pieces and read the firing stats
 * (debug lines, "Last feed ended: shot seen" or "backup time").
 *
 * Docs: docs/guides/finders.md, "Feed Stop Finder"
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
        section = launcher.name();
        servo = hardwareMap.get(Servo.class, launcher.feedStopName());
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        if (!problem.isEmpty()) t.warn(problem);
        t.text("Feed stop finder: where it holds, where it lets a piece through.");
        t.text("Nothing moves until you press A. Press START.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> nudge(1))
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> nudge(-1))
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, step::bigger)
         .bindPress(Pad.G1, Btn.DPAD_LEFT, step::smaller)
         .bindPress(Pad.G1, Btn.A, this::pressA)
         .bindPress(Pad.G1, Btn.X, () -> { if (moved()) { hold = position; writeResults(); } })
         .bindPress(Pad.G1, Btn.B, () -> { if (moved()) { release = position; writeResults(); } });
    }

    private boolean moved() {
        return servo != null && !Double.isNaN(position);
    }

    // A: first, move to the start point. With both saved: go to hold, then release, then hold...
    private void pressA() {
        if (servo == null) return;
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
        if (servo == null) return;
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
    }

    @Override protected void step() { }

    @Override
    protected void show(RobotTelemetry t) {
        if (servo == null) {
            t.warn(problem);
            return;
        }
        if (!moved()) {
            t.line("Start point", FinderResults.num(startAt, 3) + "  (D-pad up/down; A = move the feed stop there)");
            t.line("Step", step.label() + "  (D-pad left = smaller, right = bigger)");
            t.text("Choose a start point near where it sits now. Nothing moves until A.");
            return;
        }
        t.line("Position", FinderResults.num(position, 3));
        t.line("Step", step.label() + "  (D-pad left = smaller, right = bigger)");
        t.line("Hold (X)", Double.isNaN(hold) ? "-" : FinderResults.num(hold, 3));
        t.line("Release (B)", Double.isNaN(release) ? "-" : FinderResults.num(release, 3));
        t.text("D-pad up/down = move, X = hold here, B = release here"
                + (!Double.isNaN(hold) && !Double.isNaN(release) ? ", A = try hold / release" : ""));
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds the feed stop's hold and release positions.",
            "1. Put a piece behind the feed stop. Wheel off.",
            "2. D-pad up/down = a START POINT near where it is",
            "   (nothing moves), A = move it there.",
            "3. D-pad up/down = move by the step; left/right =",
            "   smaller/bigger step (0.1 to 0.001, starts 0.05).",
            "4. Where it holds the piece firmly (not straining",
            "   on the frame): X = hold.",
            "5. Where the piece rolls through freely: B = release.",
            "6. A = try hold / release. Copy the line.",
            "STOP saves the line to the results file.",
        };
    }

    // A feed stop stays where it is: holding is safe.
    @Override protected void stopAll() { }
}
