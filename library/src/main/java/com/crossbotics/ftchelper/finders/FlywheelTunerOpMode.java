package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.drive.DriverControls;
import com.crossbotics.ftchelper.finders.FlywheelTuneResults.Battery;
import com.crossbotics.ftchelper.finders.FlywheelTuneResults.Test;
import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Output;
import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Stage;
import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Stop;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.launcher.Flywheel.SpeedControl;
import com.crossbotics.ftchelper.launcher.ShotTable;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.BackgroundWorker;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.util.ArrayList;
import java.util.List;

/**
 * The Flywheel Tuner: tests many speed-control settings on the launcher's flywheel, both PID and bang-bang,
 * and finds the best of each. It never feeds a ball: a "pretend shot" turns the power off until the wheel
 * has lost one ball's speed, then times how fast the setting gets it back.
 *
 * <p>One press of A runs a whole battery run by itself, after a 3 s countdown. ANY button stops it at once
 * (help and STOP too); every finished test is saved on the Control Hub, and the next A press goes on from the
 * test it was on. A run on a FULL battery (12.6 V or more) tests every setting; a run on a LOW battery
 * (12.0 to 12.4 V) checks the best ones again, then a short driving check of each (the sticks drive at half
 * power). The last screens show the exact lines for {@code Tunables.java}: the PID numbers ({@code kP},
 * {@code kI}, {@code kD}, {@code kF}) and all six bang-bang numbers. It works the same whichever
 * {@code speedControl} the robot uses: it changes nothing in {@code Tunables.java}.
 *
 * <p>In "Tune and check" it is in the menu for a robot with a launcher. On its own:
 * <pre>{@code
 * @TeleOp(name = "Flywheel Tuner", group = "Finders")
 * public class FlywheelTuner extends FlywheelTunerOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 *
 * KEEP HANDS AWAY from the wheel; wear safety glasses; take every ball out; nobody in front of the launcher.
 *
 * Docs: docs/tuning/06-flywheel-tuner.md
 */
public abstract class FlywheelTunerOpMode extends FinderOpMode {

    /** The robot in five files: {@code return new Robot();}. */
    protected abstract RobotDefinition robot();

    /** How often the flywheel motor's current is read (on a helper thread: it is a slow hub question), seconds. */
    static final double AMPS_READ_SEC = 0.1;

    private String problem = "";
    private RobotDefinition robotDef;
    private Launcher launcher;
    private Flywheel flywheel;
    private DcMotorEx motor;
    private FlywheelTuneDriver driver;
    private FlywheelTuneSession session;
    private DriverControls driving;
    private boolean drove = false;
    // The saved file was made with other settings: A starts a new session (the old one is kept).
    private String savedChanged = "";
    private java.util.function.DoubleSupplier battery;
    private BackgroundWorker ampsWorker;
    private volatile double amps = Double.NaN;
    private double nextAmpsAt = 0;
    private boolean[] was = null;
    private int page = 0;
    private int savedAt = 0;
    private boolean linesWritten = false;
    private double voltsNow = Double.NaN;

    @Override protected String finderName() { return "Flywheel Tuner"; }

    @Override
    protected void setup() {
        robotDef = robot();
        for (Module<?> m : robotDef.modules()) {
            if (m instanceof Launcher) { launcher = (Launcher) m; break; }
        }
        if (launcher == null) {
            problem = "No launcher in Robot.java.\nPress STOP. Use another tool.";
            return;
        }
        // The whole robot is set up, but not started: nothing moves, and its start actions do not run.
        try {
            robotDef.setup(hardwareMap);
        } catch (RuntimeException e) {
            problem = "Robot.java did not set up. Fix this first:\n" + e.getMessage();
            return;
        }
        if (!launcher.isPresent() || launcher.part() == null) {
            problem = launcher.name() + " is not connected.\nAdd it to the robot configuration.";
            return;
        }
        flywheel = launcher.part().getFlywheel();
        motor = flywheel.getMotor();
        driver = new FlywheelTuneDriver(flywheel);
        battery = com.crossbotics.ftchelper.hardware.Hw.batteryReader(hardwareMap);
        try {
            driving = DriverControls.create(hardwareMap, robotDef.robotConfig().drive(), null);
        } catch (RuntimeException noDrive) {
            driving = null;   // the driving check is then skipped (it says so)
        }
        FlywheelTuneSession.Config c = config(launcher);
        c.hasDrive = driving != null;
        FlywheelTuneResults saved = FlywheelTuneResults.load();
        if (!saved.tests().isEmpty() && !saved.setup().equals(c.setup())) {
            savedChanged = changed(saved.setup(), c.setup());
            saved = new FlywheelTuneResults(c.setup());
        } else if (saved.tests().isEmpty()) {
            saved = new FlywheelTuneResults(c.setup());
        }
        session = new FlywheelTuneSession(c, saved);
    }

    /**
     * The robot's numbers the tests use: today's settings, the table's speeds, the ready band, one ball. The
     * Group Shot Tuner builds the same one, to check that the saved results are for the robot as it is now.
     */
    static FlywheelTuneSession.Config config(Launcher launcher) {
        Flywheel flywheel = launcher.part().getFlywheel();
        FlywheelTuneSession.Config c = new FlywheelTuneSession.Config();
        c.today = FlywheelSetting.of(flywheel);
        ShotTable table = launcher.table(launcher.tableName());
        if (table != null && table.points().size() >= 2) {
            List<ShotTable.Shot> p = table.points();
            ShotTable.Shot first = p.get(0), last = p.get(p.size() - 1);
            c.lowRpm = first.rpm;
            c.highRpm = last.rpm;
            c.middleRpm = table.lookup((first.distance + last.distance) / 2).rpm;
        } else {
            // No table: the speed in Tunables, and 10% either side for the speed changes.
            double speed = launcher.speedRpm();
            c.middleRpm = speed;
            c.lowRpm = Math.round(speed * 0.9 / 10) * 10.0;
            c.highRpm = Math.round(speed * 1.1 / 10) * 10.0;
        }
        // Never near the motor's top speed: a low battery could not reach it.
        double most = 0.8 * flywheel.getMaxRpm();
        c.highRpm = Math.min(c.highRpm, most);
        c.middleRpm = Math.min(c.middleRpm, most);
        c.readyBandRpm = flywheel.getTolerance();
        c.settleSec = flywheel.getSettleTime();
        c.dropRpm = pretendDrop(launcher);
        c.paceSec = launcher.part().getShotInterval();
        c.readyTimeoutSec = launcher.part().getReadyTimeout();
        c.countsPerRev = flywheel.getCountsPerRev();
        return c;
    }

    /** The pretend shot's drop: ballDropRpm when it is written, else shotDropRpm, RPM. */
    static double pretendDrop(Launcher launcher) {
        double ball = launcher.ballDropRpm();
        return Double.isNaN(ball) ? launcher.part().getFlywheel().getShotDrop() : ball;
    }

    // Which numbers differ between the saved setup line and today's, in words.
    static String changed(String saved, String now) {
        String[] a = saved.split(" "), b = now.split(" ");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i].equals(b[i]) || i == 0) continue;
            int eq = b[i].indexOf('=');
            out.add(eq > 0 ? b[i].substring(0, eq) : b[i]);
        }
        if (!a[0].equals(b[0])) out.add(0, "speedControl");
        if (out.isEmpty()) out.add("the settings");
        return FinderResults.commas(out);
    }

    @Override protected void bind(ControllerBindings c) {
        // Every button is read in step(): while the flywheel may move, ANY button stops it.
    }

    // ---------------------------------------------------------------- every loop

    @Override
    protected void step() {
        if (session == null) return;
        buttons();
        double now = System.nanoTime() / 1e9;
        // The current is a slow hub question: asked on a helper thread at most every AMPS_READ_SEC.
        if (now >= nextAmpsAt) {
            nextAmpsAt = now + AMPS_READ_SEC;
            if (ampsWorker == null) ampsWorker = new BackgroundWorker("flywheel-amps");
            ampsWorker.submitLatest("amps", () -> {
                try {
                    amps = motor.getCurrent(org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit.AMPS);
                } catch (RuntimeException notThere) {
                    amps = Double.NaN;
                }
            });
        }
        voltsNow = battery.getAsDouble();
        double cpr = flywheel.getCountsPerRev();
        FlywheelTuneSession.Sample s = new FlywheelTuneSession.Sample(now, motor.getVelocity() / cpr * 60,
                motor.getCurrentPosition(), voltsNow, amps, driver.appliedPower(), flywheel.isFaulted() ? flywheel.getFault() : "");
        Output out = session.update(s);
        driver.apply(out);
        drive(out.drive);
        // Each finished test is saved at once, on a helper thread (the loop never waits for the storage).
        if (session.saves() != savedAt) {
            savedAt = session.saves();
            session.results().saveInBackground();
            writeResults();
        }
    }

    /**
     * "2279 RPM, target 2300: at speed" / "1800 RPM, target 2300: NOT at speed (500 slow)" / "900 RPM (power
     * off)": the number, the target, and a verdict in words.
     */
    static String speedWords(double rpm, double target, double bandRpm) {
        String now = FinderResults.num(rpm, 0) + " RPM";
        if (!(target > 0)) return now + " (no target)";
        double off = rpm - target;
        String verdict = Math.abs(off) <= bandRpm ? "at speed"
                : "NOT at speed (" + FinderResults.num(Math.abs(off), 0) + (off < 0 ? " slow)" : " fast)");
        return now + ", target " + FinderResults.num(target, 0) + ": " + verdict;
    }

    // The driving check: the sticks drive at half power. Never at any other time.
    private void drive(boolean allowed) {
        if (driving == null) return;
        if (allowed && inputsReady()) {
            driving.update(gamepad1, FlywheelTuneSession.DRIVE_POWER);
            drove = true;
        } else if (drove) {
            driving.stop();
            drove = false;
        }
    }

    // ANY new button press stops a moving test. Otherwise A starts or goes on, B skips, the D-pad turns pages.
    private void buttons() {
        boolean[] now = pressedNow(gamepad1, gamepad2);
        boolean[] before = was;
        was = now;
        // Held through START, or through help closing: nothing counts until a fresh press.
        if (before == null || !inputsReady()) return;
        boolean any = false;
        for (int i = 0; i < now.length; i++) if (now[i] && !before[i]) any = true;
        if (!any) return;
        if (session.moving()) {
            session.stop(Stop.BUTTON, "");
            driver.off();
            drive(false);
            return;
        }
        boolean x = fresh(now, before, X);
        if (session.stage() == Stage.FINISHED && x) {
            // X two times: a new session (the old file is kept). Once is not enough: the results stay.
            if (newSessionAsked) newSession();
            else newSessionAsked = true;
            return;
        }
        newSessionAsked = false;
        if (fresh(now, before, A)) pressA();
        else if (fresh(now, before, B)) session.pressB();
        else if (fresh(now, before, RIGHT)) page = Math.min(page + 1, PAGES - 1);
        else if (fresh(now, before, LEFT)) page = Math.max(page - 1, 0);
    }

    private void pressA() {
        if (!savedChanged.isEmpty()) {
            // A new session: the old file is kept under another name (a button press, not the loop).
            FlywheelTuneResults.keepOld();
            savedChanged = "";
            return;
        }
        // A fresh start after a bad speed reading: the person checked the wires (bang-bang's latch clears).
        if (session.stage() == Stage.STOPPED && session.stopWhy() == Stop.ENCODER) flywheel.clearFault();
        session.pressA();
    }

    private static final int A = 0, B = 1, X = 2, LEFT = 8, RIGHT = 9;
    private boolean newSessionAsked = false;

    // A new, empty session for the robot as it is now; the finished one is kept as flywheel-tuner-old.txt.
    private void newSession() {
        newSessionAsked = false;
        FlywheelTuneResults.keepOld();
        FlywheelTuneSession.Config c = session.config();
        session = new FlywheelTuneSession(c, new FlywheelTuneResults(c.setup()));
        results.removeStartingWith("tuner:");
        results.notDone("run 1 (FULL battery) is not finished.");
        linesWritten = false;
        page = 0;
    }
    private static final int PAGES = 4;

    private static boolean fresh(boolean[] now, boolean[] before, int i) {
        return now[i] && !before[i];
    }

    // Every button on both gamepads, and the triggers pulled more than half.
    static boolean[] pressedNow(Gamepad g1, Gamepad g2) {
        boolean[] out = new boolean[34];
        fill(out, 0, g1);
        fill(out, 17, g2);
        return out;
    }

    private static void fill(boolean[] out, int at, Gamepad g) {
        if (g == null) return;
        boolean[] b = {g.a, g.b, g.x, g.y, g.left_bumper, g.right_bumper, g.dpad_up, g.dpad_down, g.dpad_left,
                g.dpad_right, g.back, g.start, g.guide, g.left_stick_button, g.right_stick_button,
                g.left_trigger > 0.5, g.right_trigger > 0.5};
        System.arraycopy(b, 0, out, at, b.length);
    }

    // ---------------------------------------------------------------- the lines to copy

    /** The exact Tunables.java lines for a setting: kP, kI, kD, kF for PID; the six bang numbers for bang-bang. */
    static List<String> tunablesLines(FlywheelSetting s) {
        List<String> out = new ArrayList<>();
        for (String[] f : fields(s)) out.addAll(FinderResults.javaLines(f[0], f[1]));
        return out;
    }

    private static String[][] fields(FlywheelSetting s) {
        if (s.control == SpeedControl.PID) {
            return new String[][] {
                {"kP", FinderResults.exact(s.kP, 0)}, {"kI", FinderResults.exact(s.kI, 0)},
                {"kD", FinderResults.exact(s.kD, 0)}, {"kF", FinderResults.exact(s.kF, 2)}};
        }
        return new String[][] {
            {"bangKs", FinderResults.num(s.ks, 3)}, {"bangKv", FinderResults.exact(s.kv, 6)},
            {"bangFullBelowRpm", FinderResults.num(s.fullBelowRpm, 0)}, {"bangCoastAboveRpm", FinderResults.num(s.coastAboveRpm, 0)},
            {"bangHysteresisRpm", FinderResults.num(s.hysteresisRpm, 0)}, {"bangMaxPower", FinderResults.exact(s.maxPower, 0)}};
    }

    // The results file's lines: DONE with both winners, or NOT DONE and what is left.
    private void writeResults() {
        FlywheelTuneResults r = session.results();
        results.removeStartingWith("tuner:");
        String section = launcher.name();
        for (SpeedControl c : SpeedControl.values()) {
            FlywheelSetting w = r.winner(c);
            if (w == null) continue;
            for (String[] f : fields(w)) results.setTunable("tuner:" + c + ":" + f[0], section, f[0], f[1]);
        }
        if (session.stage() == Stage.FINISHED) {
            if (r.winner(SpeedControl.PID) == null && r.winner(SpeedControl.BANG_BANG) == null) results.notDone(noWinnerWhy());
            else results.done();
        } else {
            results.notDone(session.battery() == Battery.FULL ? "run 1 (FULL battery) is not finished."
                    : "run 2 (LOW battery) is not finished.");
        }
        linesWritten = true;
    }

    private String noWinnerWhy() {
        for (Test t : session.results().tests()) {
            if (t.id.startsWith("drive:") && t.why.equals(FlywheelTuneSession.Reason.SKIPPED.words)) {
                return "a driving check was skipped.";
            }
        }
        return "no setting passed every test.";
    }

    // ---------------------------------------------------------------- the screens

    @Override
    protected void showInit(RobotTelemetry t) {
        warnLines(t, problem);
        t.warn("The flywheel runs at up to FULL power.");
        t.warn("Safety glasses. Hands clear. Nobody in front.");
        t.warn("Take every ball out of the robot.");
        t.text("Flywheel Tuner: tests speed-control settings");
        t.text("and finds the best. It never feeds a ball.");
        if (session == null) return;
        if (!savedChanged.isEmpty()) {
            t.text("Saved tests used other settings: a new start.");
        } else if (session.stage() == Stage.FINISHED) {
            t.text("Saved: both runs are done. {PLAY} = results.");
        } else {
            Battery b = session.battery();
            t.text("Run " + (b == Battery.FULL ? "1 of 2: FULL" : "2 of 2: LOW") + " battery, " + FlywheelTuneSession.batteryRange(b) + ".");
            showVolts(t);
        }
        t.text("Nothing moves until you press {A} after {PLAY}.");
        t.text("ANY button stops the flywheel.");
    }

    private void showVolts(RobotTelemetry t) {
        double v = battery == null ? Double.NaN : battery.getAsDouble();
        if (Double.isNaN(v)) return;
        String wrong = session.startProblem(v);
        // Not "NOT OK" alone: say which run needs which battery (12.8 V is fine for run 1, too high for run 2).
        Battery b = session.battery();
        t.line("Battery", FinderResults.num(v, 1) + " V" + (wrong.isEmpty() ? ": OK"
                : ": run " + (b == Battery.FULL ? 1 : 2) + " needs " + FlywheelTuneSession.batteryRange(b)));
    }

    @Override
    protected boolean showsResults() {
        return false;
    }

    @Override
    protected String stoppedWords() {
        return "Flywheel power is OFF. It may still spin.";
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (session == null) {
            warnLines(t, problem);
            return;
        }
        if (!session.results().tests().isEmpty() && !FlywheelTuneResults.lastSaveWorked()) {
            t.warn("Could NOT save on the Control Hub.");
            t.warn("Take a photo of each result screen.");
        }
        if (!savedChanged.isEmpty()) {
            t.section("Flywheel Tuner: settings changed");
            t.text("The saved tests used other settings.");
            t.text("Changed: " + savedChanged + ".");
            t.text("Those tests cannot be used now.");
            t.text("Next: {A} = start a new session.");
            t.text("(The old file is kept: flywheel-tuner-old.txt)");
            return;
        }
        switch (session.stage()) {
            case READY: showReady(t); break;
            case COUNTDOWN: showCountdown(t); break;
            case TESTING: showTesting(t); break;
            case ASK_DRIVE: showAskDrive(t); break;
            case STOPPED: showStopped(t); break;
            case BETWEEN: showBetween(t); break;
            default: showResults(t);
        }
    }

    private void where(RobotTelemetry t, String what) {
        Battery b = session.battery();
        t.section("Flywheel Tuner: " + what + " (run " + (b == Battery.FULL ? "1, FULL" : "2, LOW") + ")");
    }

    private void showReady(RobotTelemetry t) {
        t.warn("{A} starts the flywheel, after 3 s.");
        t.warn("ANY button stops it. Hands clear.");
        Battery b = session.battery();
        t.section("Flywheel Tuner: run " + (b == Battery.FULL ? "1 of 2, FULL" : "2 of 2, LOW") + " battery");
        boolean going = session.testNumber() > 1;
        t.text(going ? "Next: press {A} to go on from test " + session.testNumber() + "."
                : "Next: press {A}. It counts down, then starts.");
        showVolts(t);
        t.text("This run needs " + FlywheelTuneSession.batteryRange(b) + ", motor off.");
        textLines(t, session.refusal());
        t.text("Up to " + session.testTotal() + " tests. Stay until it says DONE.");
    }

    private void showCountdown(RobotTelemetry t) {
        t.warn("Starting in " + session.countdownLeft() + " ...   ANY button = cancel");
        t.warn("Hands clear. Nobody in front.");
        if (session.countdownForDrive()) {
            int[] d = session.driveNumber();
            where(t, "driving check " + d[0] + " of " + d[1]);
            t.text("Do NOT drive until it says DRIVE NOW.");
        } else {
            where(t, "test " + session.testNumber() + " of up to " + session.testTotal());
            t.text("It runs by itself until it says DONE.");
        }
    }

    private void showTesting(RobotTelemetry t) {
        boolean driveNow = !Double.isNaN(session.driveLeft());
        if (driveNow) {
            t.warn("DRIVE NOW: the sticks drive (half power).");
            t.warn("ANY button stops everything.");
        } else {
            t.warn("Test running. ANY button stops it.");
            t.warn("The flywheel starts and stops by itself.");
        }
        if (session.driveWaiting() || driveNow) {
            int[] d = session.driveNumber();
            where(t, "driving check " + d[0] + " of " + d[1]);
        } else {
            where(t, "test " + session.testNumber() + " of up to " + session.testTotal());
        }
        if (session.driveWaiting()) {
            t.text("Next: wait. Do NOT drive yet.");
        } else if (driveNow) {
            t.text("Drive forward and back about 4 ft,");
            t.text("turn a little: " + FinderResults.num(Math.ceil(session.driveLeft()), 0) + " s left.");
        } else {
            t.text("Next: wait. Stay with the robot.");
        }
        t.line("Testing", session.testing());
        t.line("Doing", session.doing());
        if (session.doing().startsWith("pretend shot")) t.text("  (no ball: the tuner slows the wheel itself)");
        double rpm = motor.getVelocity() / flywheel.getCountsPerRev() * 60;
        double target = session.targetNow();
        t.line("Speed", speedWords(rpm, target, session.config().readyBandRpm));
        if (!Double.isNaN(voltsNow)) t.line("Battery", FinderResults.num(voltsNow, 1) + " V");
        int[] gb = session.goodAndBad();
        t.line("Settings so far", gb[0] + " good, " + gb[1] + " not good");
        textLines(t, session.lastFailed());
        textLines(t, session.refusal());
        if (session.recoveryImpossible()) {
            t.warn("Found: full power is too weak to be back");
            t.warn("at speed between balls. Help page: why.");
        }
    }

    private void showAskDrive(RobotTelemetry t) {
        Test f = session.askedDrive();
        int[] d = session.driveNumber();
        t.warn("Next step needs YOU to drive.");
        t.warn("The STICKS move the robot (half power).");
        t.warn("Clear floor 5 ft around. Flywheel runs.");
        t.section("Flywheel Tuner: driving check " + d[0] + " of " + d[1]);
        if (driving == null) {
            t.warn("No drive found: this check cannot run.");
            t.text("Setting: " + (f == null ? "" : f.setting.label()));
            t.text("{A} or {B} = skip it. Then there is no winner.");
            t.text("Fix the drive in Robot.java, then again.");
            textLines(t, session.refusal());
            return;
        }
        t.text("Take the robot off its block first.");
        t.text("Setting: " + (f == null ? "" : f.setting.label()));
        t.text("Drive forward and back about 4 ft,");
        t.text("turn a little, for " + FinderResults.num(FlywheelTuneSession.DRIVE_SEC, 0) + " seconds.");
        t.text("{A} = start the driving check (3 s countdown).");
        t.text("{B} = skip it (then there is no winner).");
        textLines(t, session.refusal());
    }

    private void showStopped(RobotTelemetry t) {
        t.warn("Flywheel power is OFF. It may still spin.");
        t.section("Flywheel Tuner: STOPPED");
        switch (session.stopWhy()) {
            case HELP:
                t.text("Help was opened, so the test stopped.");
                break;
            case ENCODER:
                t.warn("The flywheel had power, but its speed");
                t.warn("read 0 or backward.");
                t.text("(" + session.stopDetail() + ")");
                t.text("Next: press STOP. Open Tune and check, run");
                t.text("\"Encoder Check\". Fix the cable. Start again.");
                return;
            case LOW_BATTERY:
                t.warn("Battery " + session.stopDetail() + " V is too low (under 10 V).");
                t.text("Finished tests are saved. The test that");
                t.text("was running did not count.");
                t.text("Next: press STOP. Put in this run's battery,");
                t.text("charged to " + FlywheelTuneSession.batteryRange(session.battery()) + ".");
                t.text("Then {A} goes on from test " + session.testNumber() + ".");
                textLines(t, session.refusal());
                return;
            case CURRENT:
                t.warn("The flywheel motor took over "
                        + FinderResults.num(FlywheelTuneSession.AMPS_LIMIT, 0) + " A for 1 s.");
                t.warn("Something drags or jams the wheel.");
                t.text("Next: press STOP. Turn the wheel by hand:");
                t.text("it must spin freely. Fix what rubs.");
                t.text("Then {A} goes on from test " + session.testNumber() + ".");
                textLines(t, session.refusal());
                return;
            case NO_REST:
                t.text("The flywheel did not stop in "
                        + FinderResults.num(FlywheelTuneSession.REST_LIMIT, 0) + " s.");
                t.text("Check that the wheel spins freely by hand.");
                break;
            default:
                t.text("You pressed a button. The flywheel is");
                t.text("rolling to a stop. Wait until it is still.");
        }
        t.text("Finished tests are saved.");
        t.text("Next: {A} = go on from test " + session.testNumber() + ".");
        textLines(t, session.refusal());
    }

    private void showBetween(RobotTelemetry t) {
        t.section("Flywheel Tuner: run 1 of 2 done");
        t.text("Saved on the Control Hub.");
        t.text("Next: press STOP (■). Turn the robot off.");
        t.text("Put in a LOW battery (" + FlywheelTuneSession.batteryRange(Battery.LOW) + ").");
        t.text("Turn it on. Open the Flywheel Tuner again.");
        t.text("Press {PLAY}, then {A} to start run 2.");
        showVolts(t);
        textLines(t, session.refusal());
    }

    private void showResults(RobotTelemetry t) {
        FlywheelTuneResults r = session.results();
        if (!linesWritten) writeResults();
        String[] titles = {"result", "PID numbers", "bang-bang numbers", "what it cannot measure"};
        t.section("Flywheel Tuner: " + titles[page] + " (page " + (page + 1) + " of " + PAGES + ")");
        if (page == 0) {
            boolean any = r.winner(SpeedControl.PID) != null || r.winner(SpeedControl.BANG_BANG) != null;
            t.text(any ? "DONE" : "NOT DONE: " + noWinnerWhy());
            for (SpeedControl c : SpeedControl.values()) {
                Test w = r.winnerTest(c);
                String name = c == SpeedControl.PID ? "Best PID" : "Best bang-bang";
                if (w == null) t.text(name + ": none. Keep today's numbers.");
                else t.text(name + ": back at speed in " + FinderResults.num(w.number("recovery"), 2) + " s.");
            }
            if (!any) {
                t.text("Why each one failed: flywheel-tuner.txt,");
                t.text("and the docs page. Fix that, then again.");
            }
            if (!r.recoveryRanks()) showNotRanked(t);
            showStartTime(t, r);
            if (session.recoveryImpossible()) showTooWeak(t);
        } else if (page == 1 || page == 2) {
            SpeedControl c = page == 1 ? SpeedControl.PID : SpeedControl.BANG_BANG;
            FlywheelSetting w = r.winner(c);
            if (w == null) {
                t.text("No " + (c == SpeedControl.PID ? "PID" : "bang-bang") + " setting was good enough.");
                t.text("Keep today's numbers: nothing to copy.");
            } else {
                t.text("Open Tunables.java, find class " + launcher.name() + ".");
                t.text("Change these lines:");
                for (String l : tunablesLines(w)) t.text("  " + l);
                t.text("Keep speedControl as it is: the Group Shot");
                t.text("Tuner chooses PID or bang-bang.");
            }
        } else {
            t.text("Motor heat: feel the motor after a run.");
            t.text("How hard the hub's PID really brakes.");
            t.text("Real balls: pretend shots only slow the wheel.");
            t.text("Next: the Group Shot Tuner, with real balls.");
            t.text("Saved on the Control Hub: flywheel-tuner.txt");
        }
        t.text("D-pad right/left = next/back page.");
        t.text(newSessionAsked ? "Press {X} again: a NEW session (old one kept)."
                : "{X} two times = start a new session.");
    }

    // From a stop to steady speed, against how long Continuous fire waits.
    private void showStartTime(RobotTelemetry t, FlywheelTuneResults r) {
        double worst = Double.NaN;
        for (SpeedControl c : SpeedControl.values()) {
            Test w = r.winnerTest(c);
            if (w == null) continue;
            Test low = r.find(Battery.LOW, w.id);
            for (Test x : new Test[] {w, low}) {
                if (x != null && !Double.isNaN(x.number("ready"))) worst = Double.isNaN(worst) ? x.number("ready") : Math.max(worst, x.number("ready"));
            }
        }
        if (Double.isNaN(worst)) return;
        double limit = session.config().readyTimeoutSec;
        if (worst <= limit) {
            t.text("From a stop to steady speed: " + FinderResults.num(worst, 1) + " s.");
            t.text("OK: holding fire waits up to " + FinderResults.num(limit, 1) + " s.");
        } else {
            t.warn("From a stop it needs " + FinderResults.num(worst, 1) + " s to steady speed.");
            t.warn("Holding fire gives up after " + FinderResults.num(limit, 1) + " s.");
            t.warn("Spin the flywheel up before you hold fire.");
        }
    }

    // The pretend shot is too small to time: the winner is the steadiest, not the quickest back.
    private void showNotRanked(RobotTelemetry t) {
        FlywheelTuneSession.Config c = session.config();
        t.warn("NOT ranked by time back at speed.");
        t.text("Ball drop setting: " + FinderResults.num(c.dropRpm, 0) + " RPM. Ready band: "
                + FinderResults.num(c.readyBandRpm, 0) + " RPM.");
        t.text("The gap is too small to time (need 2 speed");
        t.text("steps). So the best = the steadiest.");
        t.text("Fix: set ballDropRpm to the speed one real");
        t.text("ball takes off (Flywheel Finder: Shot drop).");
    }

    private void showTooWeak(RobotTelemetry t) {
        double[] f = session.fullPower();
        FlywheelTuneSession.Config c = session.config();
        t.warn("Ball drop setting: " + FinderResults.num(c.dropRpm, 0) + " RPM.");
        t.warn("Full power gets back only " + FinderResults.num(f[0] * c.paceSec, 0) + " RPM in "
                + FinderResults.num(c.paceSec, 2) + " s.");
        t.warn("No setting fixes that: it needs more time");
        t.warn("between balls, or a stronger launcher.");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Tests PID and bang-bang settings. Finds the best.",
            "Safety glasses. No balls in the robot.",
            "{A} = start (3 s countdown). Then it runs alone.",
            "ANY button stops it. {A} again goes on.",
            "Pretend shot: power off until the wheel loses",
            "the ball drop setting (ballDropRpm, else",
            "shotDropRpm), then it times the way back.",
            "Run 1: FULL battery (12.6 V or more).",
            "Run 2: LOW battery (12.0 to 12.4 V), then a",
            "driving check: you drive 10 s, half power.",
            "Too weak: full power cannot win back one ball",
            "before the next (shotIntervalSec). Feed slower.",
            "End: D-pad right = the lines to copy.",
        };
    }

    // Help opened: everything stops, exactly like a button.
    @Override
    protected void pause() {
        if (session != null) session.stop(Stop.HELP, "");
        halt();
    }

    // The flywheel and the drive stop, each even if the other fails.
    private void halt() {
        RuntimeException first = null;
        try { if (driver != null) driver.off(); } catch (RuntimeException e) { first = e; }
        try { if (driving != null) driving.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        drove = false;
        if (first != null) throw first;
    }

    @Override
    protected void stopAll() {
        if (session != null) session.stop(Stop.BUTTON, "");
        try {
            halt();
        } finally {
            if (ampsWorker != null) ampsWorker.shutdown();
            ampsWorker = null;
            // The last finished tests, saved now (the end of the OpMode: a short wait is fine here).
            if (session != null && !session.results().tests().isEmpty()) session.results().saveNow();
        }
    }
}
