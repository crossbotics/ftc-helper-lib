package io.github.jasoncross.ftchelper.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.jasoncross.ftchelper.FakeMotor;
import io.github.jasoncross.ftchelper.Fakes;
import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.drive.AutoDrive;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.match.Alliance;
import io.github.jasoncross.ftchelper.match.MatchTimer;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** AutoRoutine on a pretend mecanum robot (10 ms per loop). */
public class AutoRoutineTest {

    static final class Sim {
        final FakeMotor fl = new FakeMotor(), fr = new FakeMotor(), bl = new FakeMotor(), br = new FakeMotor();
        final Fakes.Clock clock = new Fakes.Clock();
        final Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        double heading = 0;
        boolean active = true;
        int updates = 0;

        void step() {
            clock.addSec(0.01);
            for (FakeMotor m : new FakeMotor[]{fl, fr, bl, br}) m.position += m.power * 30;
            heading += (-fl.power - bl.power + fr.power + br.power) / 4 * 3;
        }

        AutoDrive drive() {
            AutoDriveConfig c = new AutoDriveConfig();
            c.calibrationConfirmed = true;
            c.wheelDiameterIn = 4;
            c.strafeCorrection = 1;
            return new AutoDrive(c, false, 1, fl.motor, fr.motor, bl.motor, br.motor,
                    () -> heading, () -> heading = 0, () -> active, this::step, s -> { }, clock::get);
        }

        AutoRoutine routine(MatchTimer timer) {
            Subsystem counter = () -> updates++;
            return new AutoRoutine(() -> active, this::step, ft.telemetry)
                    .withClock(clock::get)
                    .drive(drive())
                    .subsystems(counter)
                    .telemetry(new RobotTelemetry(ft.telemetry, clock::get))
                    .timer(timer);
        }
    }

    @Test public void runsStepsInOrderAndKeepsMechanismsUpdated() {
        Sim s = new Sim();
        MatchTimer timer = new MatchTimer(30, s.clock::get);
        timer.start();
        List<String> order = new ArrayList<>();
        AutoRoutine r = s.routine(timer)
                .run("A", () -> order.add("A"))
                .move("Drive", 12, 0, 0.6)
                .waitFor("Pause", 0.5)
                .step("B", ctx -> order.add("B"));
        r.runAll();
        assertEquals(2, order.size());
        assertEquals("A", order.get(0));
        assertTrue("mechanisms updated during the move and the wait", s.updates > 40);
        assertEquals("Drive: done", r.getReport().get(1));
        assertTrue(s.ft.lastScreen.toString().contains("Step : 3/4 Pause"));   // step 4 is instant: no loop runs during it
    }

    @Test public void redMirrorsTurns() {
        Sim s = new Sim();
        MatchTimer timer = new MatchTimer(30, s.clock::get);
        timer.start();
        s.routine(timer).mirrorFor(Alliance.RED).turnTo("Face left (blue)", 90, 0.5).runAll();
        assertEquals(-90, s.heading, 1.5);
    }

    @Test public void mainStepsStopForParkTime() {
        Sim s = new Sim();
        MatchTimer timer = new MatchTimer(3, s.clock::get);   // a short "match" for the test
        timer.start();
        boolean[] parked = {false};
        AutoRoutine r = s.routine(timer).reserveForPark(1.0)
                .waitFor("Long wait", 10)                     // would run past the reserve
                .run("Never", () -> { throw new AssertionError("should be skipped"); })
                .thenPark()
                .run("Park", () -> parked[0] = true);
        r.runAll();
        assertTrue(parked[0]);
        assertTrue(s.clock.nanos / 1e9 < 2.1);                // main part ended at 2 s (3 - 1)
        assertEquals("Never: skipped (time for park)", r.getReport().get(1));
    }

    @Test public void waitUntilReportsATimeout() {
        Sim s = new Sim();
        MatchTimer timer = new MatchTimer(30, s.clock::get);
        timer.start();
        AutoRoutine r = s.routine(timer).waitUntil("Never true", () -> false, 0.3);
        r.runAll();
        assertEquals("Never true: timed out", r.getReport().get(0));
    }

    @Test public void stoppingTheOpModeEndsEverything() {
        Sim s = new Sim();
        MatchTimer timer = new MatchTimer(30, s.clock::get);
        timer.start();
        int[] ran = {0};
        AutoRoutine r = s.routine(timer)
                .step("Stop now", ctx -> s.active = false)
                .run("After", () -> ran[0]++);
        r.runAll();
        assertEquals(0, ran[0]);
    }

    @Test public void parkTimeEndsAPoweredDriveAndParks() {
        Sim s = new Sim();
        MatchTimer timer = new MatchTimer(3, s.clock::get);
        timer.start();
        boolean[] parked = {false};
        AutoRoutine r = s.routine(timer).reserveForPark(1.0)
                .move("Very long drive", 500, 0, 0.3)          // would take far longer than 2 s
                .thenPark()
                .run("Park", () -> parked[0] = true);
        r.runAll();
        assertTrue(parked[0]);
        assertEquals("Very long drive: STOPPED", r.getReport().get(0));
        assertTrue(s.clock.nanos / 1e9 < 2.2);
    }

    @Test public void aStepThatThrowsLeavesTheWheelsStopped() {
        Sim s = new Sim();
        MatchTimer timer = new MatchTimer(30, s.clock::get);
        timer.start();
        AutoRoutine r = s.routine(timer).step("Drive then crash", ctx -> {
            s.fl.motor.setPower(0.5);
            s.fr.motor.setPower(0.5);
            throw new RuntimeException("bug in team code");
        });
        try {
            r.runAll();
            org.junit.Assert.fail("the error should come through");
        } catch (RuntimeException expected) {
            assertEquals(0, s.fl.power, 1e-12);
            assertEquals(0, s.fr.power, 1e-12);
        }
    }
}
