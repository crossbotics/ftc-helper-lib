package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.launcher.Launcher.ShotMode;
import com.crossbotics.ftchelper.match.MatchTimer;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.tuning.TuningReset;
import org.junit.After;
import org.junit.Test;

/**
 * The fire button as one job: every press fires at once, a hold keeps firing, a tap then a hold sprays
 * (only with sprayEnabled), fireOn/spinOn move it, and a launcher with a separate feeder spins at START.
 */
public class FireButtonTest {

    @After public void back() {
        SettingsFile.useForTests(null);
        TuningReset.clear();
        MatchTimer.forget();
    }

    public static class SprayOn {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; public static boolean sprayEnabled = true; }
    }

    public static class SprayOnNoTapHold {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher {
            public static double speedRpm = 3000;
            public static boolean sprayEnabled = true, sprayOnTapHold = false;
        }
    }

    public static class SprayOff {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
    }

    public static class NoSpinAtStart {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; public static boolean spinAtStart = false; }
    }

    // A launcher with a feed stop (a separate feeder), the numbers from `numbers`, and a pretend clock.
    private static Launcher launcher(Class<?> numbers, Fakes.Clock c) {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(numbers);
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000").feedStop("stop"));
            }
        };
        r.setup(new PretendDevices());
        r.startForTests();
        Launcher l = (Launcher) r.modules().get(0);
        l.clock = c::get;
        return l;
    }

    // A quick tap: press, 0.1 s, let go.
    private static void tap(Launcher l, Fakes.Clock c) {
        l.fireWhileHeld();
        c.addSec(0.1);
        l.ceaseFire();
    }

    @Test public void tapThenHoldSpraysWhileHeld() {
        Fakes.Clock c = new Fakes.Clock();
        Launcher l = launcher(SprayOn.class, c);
        assertTrue(l.sprayOnTapHold());
        tap(l, c);
        c.addSec(0.1);
        l.fireWhileHeld();                                  // the second press fires at once, like a tap
        assertEquals(2, l.part().getShotsLeft());
        assertFalse("not yet: it could still be a second tap", l.isSprayingOnHold());
        c.addSec(0.35);                                     // held past holdSec (0.3 s)
        l.fireWhileHeld();
        assertTrue(l.isSprayingOnHold());
        l.update();
        assertEquals(ShotMode.SPRAY, l.shotModeNow());
        l.ceaseFire();
        assertFalse(l.isSprayingOnHold());
        l.update();
        assertTrue("let go: back to the launcher's own choice", l.shotModeNow() != ShotMode.SPRAY);
    }

    @Test public void twoQuickTapsAreTwoShotsNotASpray() {
        Fakes.Clock c = new Fakes.Clock();
        Launcher l = launcher(SprayOn.class, c);
        tap(l, c);
        c.addSec(0.1);
        tap(l, c);
        assertFalse(l.isSprayingOnHold());
        assertEquals(2, l.part().getShotsLeft());
    }

    @Test public void aLongHoldThenAHoldIsNotASpray() {
        Fakes.Clock c = new Fakes.Clock();
        Launcher l = launcher(SprayOn.class, c);
        l.fireWhileHeld();
        c.addSec(1.0);                                      // a hold, not a tap
        l.fireWhileHeld();
        l.ceaseFire();
        c.addSec(0.1);
        l.fireWhileHeld();
        c.addSec(0.5);
        l.fireWhileHeld();
        assertFalse("only a TAP then a hold sprays", l.isSprayingOnHold());
    }

    @Test public void tooSlowASecondPressIsAPlainHold() {
        Fakes.Clock c = new Fakes.Clock();
        Launcher l = launcher(SprayOn.class, c);
        tap(l, c);
        c.addSec(0.5);                                      // more than tapGapSec (0.35 s)
        l.fireWhileHeld();
        c.addSec(0.5);
        l.fireWhileHeld();
        assertFalse(l.isSprayingOnHold());
    }

    @Test public void noSprayWithoutSprayEnabledOrWithSprayOnTapHoldOff() {
        for (Class<?> numbers : new Class<?>[] {SprayOff.class, SprayOnNoTapHold.class}) {
            Fakes.Clock c = new Fakes.Clock();
            Launcher l = launcher(numbers, c);
            assertFalse(numbers.getSimpleName(), l.sprayOnTapHold());
            tap(l, c);
            c.addSec(0.1);
            l.fireWhileHeld();
            c.addSec(0.5);
            l.fireWhileHeld();
            assertFalse(numbers.getSimpleName() + ": keeps firing instead", l.isSprayingOnHold());
            l.update();
            assertEquals(numbers.getSimpleName(), ShotMode.QUICK, l.shotModeNow());
        }
    }

    // The button map's help line for this input ("" if it has none).
    private static String line(ButtonPlan plan, String input) {
        for (String s : plan.help()) if (s.startsWith(input + ":")) return s;
        return "";
    }

    @Test public void fireOnAndSpinOnMoveTheButtonsWithEverything() {
        Fakes.Clock c = new Fakes.Clock();
        Launcher l = launcher(SprayOn.class, c);
        RobotDefinition r = l.robot;
        ButtonPlan plan = ControlMap.makePlan(r, false, true, (g1, g2) -> {
            l.fireOn(g2.lb);
            l.spinOn(g2.x);
        });
        assertTrue(line(plan, "G2 LB"), line(plan, "G2 LB").contains("fire one; hold: keep firing; tap, then hold: spray"));
        assertTrue(line(plan, "G2 X"), line(plan, "G2 X").contains("spin up / down"));
        assertFalse("RB is free now: " + line(plan, "G2 RB"), line(plan, "G2 RB").contains("fire"));
        assertTrue("moved, so no INIT note: " + plan.notes(), plan.notes().isEmpty());
    }

    @Test public void fireOnNullLeavesNoFireButtonAndNoNote() {
        Fakes.Clock c = new Fakes.Clock();
        Launcher l = launcher(SprayOff.class, c);
        ButtonPlan plan = ControlMap.makePlan(l.robot, false, true, (g1, g2) -> l.fireOn(null));
        for (String s : plan.help()) assertFalse(s, s.contains("fire one"));
        assertTrue(plan.notes().toString(), plan.notes().isEmpty());
        assertFalse("RB is free: " + line(plan, "G2 RB"), line(plan, "G2 RB").contains("fire"));
    }

    @Test public void aSeparateFeederSpinsAtStartAndTunablesCanTurnItOff() {
        Fakes.Clock c = new Fakes.Clock();
        Launcher l = launcher(SprayOff.class, c);
        assertTrue("a feed stop: spins at START", l.spinsAtStart());
        assertTrue(l.isSpinning());
        Launcher off = launcher(NoSpinAtStart.class, c);
        assertFalse("Tunables spinAtStart = false wins", off.spinsAtStart());
        assertFalse(off.isSpinning());
    }

    @Test public void noSeparateFeederOrTheCodeSayingNoWaitsForLB() {
        final Intake[] in = new Intake[1];
        RobotDefinition pushedOnly = new RobotDefinition() {
            @Override protected void define() {
                tunables(SprayOff.class);
                drive(Drive.tank("l", "r"));
                in[0] = add(Intake.motor("intake").part("gobilda-5203-1150"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000").pushedBy(in[0]));
            }
        };
        pushedOnly.setup(new PretendDevices());
        pushedOnly.startForTests();
        Launcher a = (Launcher) pushedOnly.modules().get(1);
        assertFalse("pushed in by the intake only: the intake would push pieces into a spinning wheel", a.isSpinning());

        RobotDefinition saysNo = new RobotDefinition() {
            @Override protected void define() {
                tunables(SprayOff.class);
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000").feedStop("stop").spinAtStart(false));
            }
        };
        saysNo.setup(new PretendDevices());
        saysNo.startForTests();
        assertFalse("spinAtStart(false) in Robot.java", ((Launcher) saysNo.modules().get(0)).isSpinning());
    }
}
