package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.robot.FeedTimeTest;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds how long a launcher's feeder must run per shot ({@code feedSec}), for a feeder that runs for a
 * time: a CR servo or a motor ({@code Launcher.feeder(Indexer.crServo("feeder"))} in Robot.java), with no
 * feed stop. It fires test shots and times each piece from the start of the feed to the shot. Fire several,
 * from every place a piece can sit: the longest time sets the number. After {@link #ENOUGH_SHOTS} it shows
 * the line to copy for class Feeder: {@code public static double feedSec = 0.45;}.
 *
 * <p>In "Tune and check" it is in the menu for a robot with such a launcher. On its own:
 * <pre>{@code
 * @TeleOp(name = "Feed Time Finder", group = "Finders")
 * public class FeedTimeFinder extends FeedTimeFinderOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 *
 * <pre>
 * LB = wheel on/off.  RB = a test shot (when the wheel is at speed).  X = clear the times.
 * </pre>
 *
 * KEEP HANDS AWAY from the wheel and the feeder; wear eye protection; nobody in front of the launcher.
 *
 * Docs: docs/tuning/07-feed-time-finder.md
 */
public abstract class FeedTimeFinderOpMode extends FinderOpMode {

    /** The robot in five files: {@code return new Robot();}. */
    protected abstract RobotDefinition robot();

    /** The longest one test feed runs, seconds: long, so a slow piece is still timed. */
    static final double LIMIT_SEC = 3.0;
    /** Extra on the longest test shot, seconds: a low battery or a worn wheel is slower. */
    static final double EXTRA_SEC = 0.1;
    /** Fire at least this many test shots before you trust the number. */
    static final int ENOUGH_SHOTS = 5;
    /** Times further apart than this, without stopOnShot: a feedSec for the slowest piece may feed two. */
    static final double SPREAD_SEC = 0.15;

    private String problem = "";
    private Launcher launcher;
    private FeedTimeTest test;              // made at INIT; null = no launcher to test
    private final List<Double> times = new ArrayList<>();   // the test shots' times to the shot, seconds
    private String testNote = "";           // a warning about the last test shot
    private boolean wasBusy = false;

    @Override protected String finderName() { return "Feed time finder"; }

    @Override
    protected void setup() {
        RobotDefinition r = robot();
        for (Module<?> m : r.modules()) {
            if (m instanceof Launcher && ((Launcher) m).hasTimedFeeder()) { launcher = (Launcher) m; break; }
        }
        if (launcher == null) {
            problem = "No launcher with a timed feeder in Robot.java.\n(A CR servo or motor, no feed stop.)\nPress STOP. Use another tool.";
            return;
        }
        // The whole robot is set up (nothing moves: motors at 0), so the test shot uses the launcher's own wheel and feeder.
        try {
            r.setup(hardwareMap);
        } catch (RuntimeException e) {
            problem = "Robot.java did not set up. Fix this first:\n" + e.getMessage();
            return;
        }
        test = launcher.feedTimeTest(LIMIT_SEC);
        if (!test.works()) {
            problem = (!launcher.isPresent() ? launcher.name() : launcher.name() + "'s feeder")
                    + " is not connected.\nAdd it to the robot configuration.";
            test = null;
        }
    }

    /** The most feedSec can be (its range in Tunables), seconds. */
    static final double MAX_FEED_SEC = 3.0;

    /**
     * The feedSec for these test shots: the longest + {@link #EXTRA_SEC}, rounded up to 0.05, at most
     * {@link #MAX_FEED_SEC}. NaN for none.
     */
    static double feedSecFor(List<Double> shotSec) {
        double longest = longest(shotSec);
        return Double.isNaN(longest) ? Double.NaN : Math.min(MAX_FEED_SEC, roundUp(longest + EXTRA_SEC));
    }

    static double longest(List<Double> shotSec) {
        double l = Double.NaN;
        for (double s : shotSec) if (Double.isNaN(l) || s > l) l = s;
        return l;
    }

    static double shortest(List<Double> shotSec) {
        double l = Double.NaN;
        for (double s : shotSec) if (Double.isNaN(l) || s < l) l = s;
        return l;
    }

    static double average(List<Double> shotSec) {
        if (shotSec.isEmpty()) return Double.NaN;
        double sum = 0;
        for (double s : shotSec) sum += s;
        return sum / shotSec.size();
    }

    // Up to the next 0.05 (a tiny tolerance, so 0.25 stays 0.25).
    private static double roundUp(double sec) {
        return Math.ceil(sec / 0.05 - 1e-6) * 0.05;
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        warnLines(t, problem);
        t.warn("{LB} spins the flywheel. {RB} FIRES a piece.");
        t.warn("Safety glasses. Nobody in front. Hands clear.");
        t.text("Feed time finder: how long must the feeder");
        t.text("run for one shot? (That is feedSec.)");
        t.text("Nothing moves until you press {LB}.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.LB, this::toggleWheel)
         .bindPress(Pad.G1, Btn.RB, () -> { if (test != null) test.fire(); })
         .bindPress(Pad.G1, Btn.X, () -> { times.clear(); testNote = ""; writeResults(); });
    }

    // LB: the wheel on or off.
    private void toggleWheel() {
        if (test == null) return;
        test.spin(!test.isSpinning());
    }

    private void writeResults() {
        double sec = feedSecFor(times);
        results.remove("feedtime");
        if (test == null) return;
        // No line before enough shots: the first one is not the slowest one.
        if (times.size() < ENOUGH_SHOTS) {
            results.notDone(times.size() + " of " + ENOUGH_SHOTS + " test shots done.");
            return;
        }
        results.done();
        results.setTunable("feedtime", test.feederSection(), "feedSec", FinderResults.num(sec, 2));
    }

    @Override
    protected void step() {
        if (test == null) return;
        test.update();
        // A test shot just ended: keep its time, or say what went wrong.
        if (wasBusy && !test.isBusy()) {
            if ("stopped".equals(test.lastEnd())) {
                testNote = "Test shot stopped part way: not counted.\nNext: load a piece and test again.";
            } else if (Double.isNaN(test.lastSec())) {
                // It watches the wheel's speed: a piece may have gone without a drop that it could see.
                testNote = "No shot seen in " + FinderResults.num(LIMIT_SEC, 0) + " s. Not counted."
                        + "\nNo piece came out? Check load and feeder."
                        + "\nOne came out? The speed drop was too small.";
            } else {
                times.add(test.lastSec());
                testNote = "";
            }
            writeResults();
        }
        wasBusy = test.isBusy();
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (test == null) {
            warnLines(t, problem);
            return;
        }
        t.warn(test.isSpinning() ? "It shoots. Nobody in front. {LB} = stop." : "{LB} spins the flywheel. Hands clear.");
        t.section("Feed time finder: " + times.size() + (times.size() < ENOUGH_SHOTS ? " of " + ENOUGH_SHOTS : "") + " test shots");
        if (test.isBusy()) {
            t.text("Test shot on its way. Wait.");
        } else if (times.size() >= ENOUGH_SHOTS) {
            t.text("Tested every place, the farthest back too?");
            t.text("Yes: copy the line. No: fire more.");
        } else if (!test.isSpinning()) {
            t.text("Next: load ONE piece, where it sits in a match.");
            t.text("Then press {LB}: the flywheel turns on.");
        } else if (!test.isReady()) {
            t.text("Next: wait for the flywheel's speed.");
        } else {
            t.text("Next: one piece loaded? {RB} = test shot.");
            t.text("Use every place, the farthest back too.");
        }
        t.line("Flywheel", test.isSpinning()
                ? FinderResults.num(test.rpmNow(), 0) + " of " + FinderResults.num(test.targetRpm(), 0) + " RPM"
                  + (test.isReady() ? ", ready" : "")
                : "off");
        warnLines(t, testNote);
        if (times.isEmpty()) return;
        StringBuilder b = new StringBuilder();
        int from = Math.max(0, times.size() - 5);
        for (int i = from; i < times.size(); i++) b.append(i > from ? ", " : "").append(FinderResults.num(times.get(i), 2));
        t.line("Test shots", b + " s  (" + times.size() + (times.size() < ENOUGH_SHOTS ? " of " + ENOUGH_SHOTS : "") + ")");
        if (times.size() >= ENOUGH_SHOTS) {
            t.line("Longest", FinderResults.num(longest(times), 2) + " s. feedSec = that + " + FinderResults.num(EXTRA_SEC, 1) + " s");
            double now = test.feedSecNow();
            if (!Double.isNaN(now)) t.line("feedSec in use now", FinderResults.num(now, 2) + " s");
        }
        if (longest(times) + EXTRA_SEC > MAX_FEED_SEC) {
            t.warn("Almost " + FinderResults.num(MAX_FEED_SEC, 0) + " s: feedSec cannot be longer.");
            t.warn("Check feeder power, and what slows the piece.");
        }
        double spread = longest(times) - shortest(times);
        if (!test.stopsOnShot() && spread > SPREAD_SEC) {
            t.warn("Times differ by " + FinderResults.num(spread, 2) + " s: 2 may feed at once.");
            t.warn("Set stopOnShot = true, class " + test.feederSection() + ".");
        }
        t.text("{X} = clear the times.");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds feedSec: how long the feeder runs a shot.",
            "(The feeder pushes a piece into the flywheel.)",
            "Safety glasses. Nobody in front of the launcher.",
            "1. Load ONE piece where it sits in a match.",
            "2. {LB} = flywheel on, at your speedRpm.",
            "3. When it says ready: {RB} = test shot.",
            "   It times it until the speed drops.",
            "4. Do it " + ENOUGH_SHOTS + " times or more, from every place.",
            "   The farthest back matters most.",
            "5. feedSec = the longest + " + FinderResults.num(EXTRA_SEC, 1) + " s.",
            "   Copy it when the screen says DONE.",
            "{LB} = stop. {X} = clear the times.",
        };
    }

    // The wheel and the feeder stop.
    @Override
    protected void stopAll() {
        if (test != null) test.spin(false);
    }
}
