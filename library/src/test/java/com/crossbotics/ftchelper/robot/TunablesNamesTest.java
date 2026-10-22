package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * INIT stops on a Tunables field that no part reads (a typo like inPowr), in every part's section, with
 * a "did you mean" when it looks like a real one. Names the team picks (positions, piece kinds, shot
 * tables, an interlock rule's part fields) still work.
 */
public class TunablesNamesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        SafeMotor.freshReadingsForTests(true);
    }

    @After public void back() {
        SafeMotor.freshReadingsForTests(false);
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    /** Every part a robot may have, each reading the section it is given. */
    static class Bot extends RobotDefinition {
        final Class<?> numbers;

        Bot(Class<?> numbers) {
            this.numbers = numbers;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            add(Intake.motor("intake"));
            Wrist wrist = add(Wrist.servo("wrist"));
            Claw claw = add(Claw.servo("claw"));
            add(Lift.slide("lift"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer")));
            add(PieceSensor.color("ready").kinds("POLLEN", "NECTAR"));
            keepApart("WristClaw", wrist.zone(), claw.zone());
        }
    }

    // Everything right: positions, kinds, two shot tables, the rule's fields and a margin.
    public static class Good {
        public static class Drive { public static double maxPower = 1.0; }
        public static class Aim { public static double goalX = -58, goalY = 55; }
        public static class Intake { public static double inPower = 1.0, outPower = 0.7; }
        public static class Wrist { public static double folded = 0.1, out = 0.6, travelSec = 0.3; }
        public static class Claw { public static double open = 0.6, closed = 0.4; }
        public static class Lift { public static double min = 0, max = 2000, down = 0, high = 1800; }
        public static class Launcher {
            public static double speedRpm = 2600, nominalVolts = 12.5;
            public static double[][] speedByDistancePollen = {{24, 2200}, {48, 2500}};
            public static double[][] speedByDistanceNectar = {{24, 2300}, {48, 2600}};
        }
        public static class Indexer { public static double rest = 0.1, push = 0.5; }
        public static class Ready { public static double POLLEN = 60, NECTAR = 200, gapMm = 8; }
        public static class WristClaw {
            public static double wristFrom = 0, wristTo = 0.3, clawFrom = 0, clawTo = 0.45, clawMargin = 0.03;
        }
    }

    public static class IntakeTypo {
        public static class Intake { public static double inPowr = 0.5; }
        public static class WristClaw { public static double wristFrom = 0, wristTo = 0.3, clawFrom = 0, clawTo = 0.45; }
        public static class Lift { public static double min = 0, max = 2000; }
    }

    public static class AimTypo {
        public static class Aim { public static double goalx = 10, goalY = 20; }
        public static class WristClaw { public static double wristFrom = 0, wristTo = 0.3, clawFrom = 0, clawTo = 0.45; }
        public static class Lift { public static double min = 0, max = 2000; }
    }

    public static class LauncherTypo {
        public static class Launcher { public static double nominalVolt = 12.5; }
        public static class WristClaw { public static double wristFrom = 0, wristTo = 0.3, clawFrom = 0, clawTo = 0.45; }
        public static class Lift { public static double min = 0, max = 2000; }
    }

    public static class DriveTypo {
        public static class Drive { public static double maxPowr = 0.8; }
        public static class WristClaw { public static double wristFrom = 0, wristTo = 0.3, clawFrom = 0, clawTo = 0.45; }
        public static class Lift { public static double min = 0, max = 2000; }
    }

    public static class RuleTypo {
        public static class WristClaw { public static double wristFrom = 0, wrsitTo = 0.3, clawFrom = 0, clawTo = 0.45; }
        public static class Lift { public static double min = 0, max = 2000; }
    }

    public static class Leftover {
        public static class Indexer { public static double rest = 0.1, push = 0.5, spinPower = 1; }   // not an Indexer field
        public static class WristClaw { public static double wristFrom = 0, wristTo = 0.3, clawFrom = 0, clawTo = 0.45; }
        public static class Lift { public static double min = 0, max = 2000; }
    }

    // A position part: a true/false that is not one of its settings is a typo too (positions are numbers).
    public static class FlagOnAClaw {
        public static class Claw { public static double open = 0.6, closed = 0.4; public static boolean reversd = true; }
        public static class WristClaw { public static double wristFrom = 0, wristTo = 0.3, clawFrom = 0, clawTo = 0.45; }
        public static class Lift { public static double min = 0, max = 2000; }
    }

    // A position that looks like a setting still stops (as before): "MaxPower" for maxPower.
    public static class LiftLookalike {
        public static class Lift { public static double min = 0, max = 2000, MaxPower = 0.5, high = 1800; }
        public static class WristClaw { public static double wristFrom = 0, wristTo = 0.3, clawFrom = 0, clawTo = 0.45; }
    }

    @Test public void aRobotWithEveryKindOfNameInitsCleanly() {
        new Bot(Good.class).setup(new PiecesTest.Kit());
    }

    @Test public void aMisspelledFieldStopsInitWithDidYouMean() {
        expect(IntakeTypo.class, "Tunables.Intake.inPowr is not a setting of Intake (did you mean inPower?)");
        expect(AimTypo.class, "Tunables.Aim.goalx is not a setting of Aim (did you mean goalX?)");
        expect(LauncherTypo.class, "Tunables.Launcher.nominalVolt is not a setting of Launcher (did you mean nominalVolts?)");
        expect(DriveTypo.class, "Tunables.Drive.maxPowr is not a setting of Drive (did you mean maxPower?)");
        expect(RuleTypo.class, "Tunables.WristClaw.wrsitTo is not a setting of the interlock rule \"WristClaw\" (did you mean wristTo?)");
    }

    @Test public void aFieldNoPartReadsStopsInitWithTheKnownNames() {
        expect(Leftover.class, "Tunables.Indexer.spinPower is not a setting of Indexer: the robot would quietly ignore it");
        expect(Leftover.class, "Known: feedSec, power, push, pushSec, rest, returnSec");
        expect(FlagOnAClaw.class, "Tunables.Claw.reversd is not a setting of Claw");
    }

    @Test public void aPositionThatLooksLikeASettingStillStops() {
        expect(LiftLookalike.class, "Tunables.Lift.MaxPower looks like \"maxPower\"");
    }

    private static void expect(Class<?> numbers, String words) {
        try {
            new Bot(numbers).setup(new PiecesTest.Kit());
            fail("INIT should stop: " + words);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(words));
        }
    }
}
