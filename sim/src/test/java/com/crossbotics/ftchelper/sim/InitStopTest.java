package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.core.InitStop;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import org.junit.Test;

/**
 * A mistake found at INIT is shown as plain lines on the Driver Station, and the OpMode waits for STOP
 * (no red crash line). A mistake after START goes on as before. The simulator normally keeps the old way
 * (its own error panel): these tests turn that off.
 */
public class InitStopTest {

    /** Stops at INIT with the library's kind of message. */
    public static class StopsAtInit extends LinearOpMode {
        @Override public void runOpMode() {
            InitStop.run(this, () -> {
                throw new IllegalStateException("Tunables.Lift has no max: write max");
            });
        }
    }

    /** Stops after START. */
    public static class StopsAfterStart extends LinearOpMode {
        @Override public void runOpMode() {
            InitStop.run(this, () -> {
                waitForStart();
                throw new IllegalStateException("broke while driving");
            });
        }
    }

    /** Asks for a clean stop at INIT (not a mistake). */
    public static class StopsItselfAtInit extends LinearOpMode {
        @Override public void runOpMode() {
            InitStop.run(this, this::terminateOpModeNow);
        }
    }

    @Test public void aCleanStopAtInitIsNotShownAsAMistake() throws Exception {
        try (SimRun s = new SimRun()) {
            InitStop.throwInsteadOfShowing(false);
            s.sim.ds.register("Stops itself", "TeleOp", StopsItselfAtInit.class);
            s.init("Stops itself");
            assertTrue(SimRun.waitFor(() -> s.sim.ds.phase() == SimDriverStation.Phase.STOPPED, 2));
            assertFalse(s.screenHas("INIT STOPPED"));
            assertEquals("", s.error());
        } finally {
            InitStop.throwInsteadOfShowing(true);
        }
    }

    @Test public void anInitMistakeIsShownAsPlainLinesUntilStop() throws Exception {
        try (SimRun s = new SimRun()) {
            InitStop.throwInsteadOfShowing(false);
            s.sim.ds.register("Stops at INIT", "TeleOp", StopsAtInit.class);
            s.init("Stops at INIT");
            assertTrue(SimRun.waitFor(() -> s.screenHas("INIT STOPPED"), 2));
            assertTrue(s.screen().toString(), s.screenHas("Tunables.Lift has no max: write max"));
            assertTrue(s.screenHas("Fix it, then press INIT again"));
            assertFalse("no Java words", s.screenHas("IllegalStateException"));
            SimRun.pause(0.3);
            assertEquals("it waits: still in INIT", SimDriverStation.Phase.INIT, s.sim.ds.phase());
            assertEquals("not a crash", "", s.error());
            s.sim.ds.stop();
            assertTrue(SimRun.waitFor(() -> s.sim.ds.phase() == SimDriverStation.Phase.STOPPED, 2));
            assertEquals("", s.error());
        } finally {
            InitStop.throwInsteadOfShowing(true);
        }
    }

    @Test public void aMistakeAfterStartGoesOnAsBefore() throws Exception {
        try (SimRun s = new SimRun()) {
            InitStop.throwInsteadOfShowing(false);
            s.sim.ds.register("Stops after START", "TeleOp", StopsAfterStart.class);
            s.init("Stops after START");
            s.sim.ds.start();
            assertTrue(SimRun.waitFor(() -> !s.error().isEmpty(), 2));
            assertTrue(s.error(), s.error().contains("broke while driving"));
        } finally {
            InitStop.throwInsteadOfShowing(true);
        }
    }

    @Test public void theSimulatorKeepsItsOwnErrorPanel() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Stops at INIT", "TeleOp", StopsAtInit.class);
            s.init("Stops at INIT");
            assertTrue(SimRun.waitFor(() -> !s.error().isEmpty(), 2));
            assertTrue(s.error(), s.error().contains("Tunables.Lift has no max"));
        }
    }

    @Test public void aMistakeInTheTeamsCodeSaysWhere() {
        NullPointerException e = new NullPointerException();
        e.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("java.util.Objects", "requireNonNull", "Objects.java", 200),
            new StackTraceElement("org.firstinspires.ftc.teamcode.Robot", "define", "Robot.java", 42),
        });
        assertEquals("NullPointerException in Robot.java line 42", InitStop.words(e));
    }

    @Test public void theLibrarysOwnMessageIsShownAsItIs() {
        IllegalArgumentException e = new IllegalArgumentException("No DcMotorEx named \"arm\" in the active robot configuration.");
        e.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("com.crossbotics.ftchelper.hardware.Hw", "get", "Hw.java", 51),
            new StackTraceElement("org.firstinspires.ftc.teamcode.Robot", "define", "Robot.java", 42),
        });
        assertEquals("No DcMotorEx named \"arm\" in the active robot configuration.", InitStop.words(e));
    }
}
