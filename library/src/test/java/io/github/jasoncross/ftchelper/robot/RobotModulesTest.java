package io.github.jasoncross.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.jasoncross.ftchelper.FakeMotor;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.core.SettingsFile;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.tuning.Tunable;
import io.github.jasoncross.ftchelper.tuning.TuningReset;
import java.io.File;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RobotModulesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
    }

    @After public void back() {
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    /** The numbers a team would have in Tunables.java. */
    public static class Numbers {
        public static class Drive {
            public static double maxPower = 0.9, practiceMaxPower = 0.5, slowSpeed = 0.25;
            public static boolean calibrated = true;
        }
        public static class Lift {
            public static double min = 0, max = 3000;
            // kD = 0: the PID's D part divides by the real time between loops, which is tiny in a test.
            public static double kP = 0.004, kI = 0, kD = 0, kF = 0.05;
            public static double down = 0, low = 900, high = 2400;
        }
        public static class Claw {
            public static double open = 0.2, closed = 0.75;
        }
        public static class Intake {
            public static double inPower = 0.8;     // outPower missing on purpose
        }
    }

    /** A robot like the design's first sketch. */
    public static class LiftBot extends RobotDefinition {
        public Intake intake;
        public Lift lift;
        public Claw claw;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                    .motor("gobilda-5203-312").wheelMm(96));
            intake = add(Intake.motor("intake"));
            lift = add(Lift.slide("lift").bottomSwitch("lift_bottom"));
            claw = add(Claw.servo("claw"));
        }
    }

    // ------------------------------------------------------------------------------------------

    @Test public void tunablesSectionsBecomeLiveTunablesAndNotesSayWhatIsMissing() {
        LiftBot r = new LiftBot();
        RobotTestKit kit = new RobotTestKit();
        r.setup(kit);
        assertEquals(0.004, Tunable.number("Lift/kP", 99, 1).get(), 1e-12);
        assertEquals(2400, Tunable.number("Lift/high", 99, 1).get(), 1e-12);
        assertEquals(0.8, Tunable.number("Intake/inPower", 99, 1).get(), 1e-12);
        assertEquals(0.7, Tunable.number("Intake/outPower", 99, 1).get(), 1e-12);   // the library's default
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("Tunables.Intake: outPower missing"));
        assertEquals(java.util.Arrays.asList("down", "low", "high"), r.lift.positions());
    }

    @Test public void tunedValuesAreListedAsLinesToCopyBack() {
        LiftBot r = new LiftBot();
        r.setup(new RobotTestKit());
        assertTrue(RobotDefinition.changedTunables().isEmpty());
        Tunable.number("Lift/high", 0, 1).set(2450);
        List<String> lines = RobotDefinition.changedTunables();
        assertEquals(1, lines.size());
        assertTrue(lines.get(0), lines.get(0).startsWith("Tunables.Lift.high = 2450"));
        assertTrue(lines.get(0), lines.get(0).contains("(code 2400"));
    }

    @Test public void aTunedPositionStaysInsideTheSafeRange() {
        LiftBot r = new LiftBot();
        r.setup(new RobotTestKit());
        Tunable high = Tunable.number("Lift/high", 0, 1);
        high.set(9999);
        assertEquals(3000, high.get(), 1e-9);
    }

    @Test public void driveSettingsComeFromRobotJavaAndTunables() {
        LiftBot r = new LiftBot();
        RobotConfig c = r.robotConfig();
        DriveConfig d = c.drive();
        assertEquals(DriveConfig.DriveType.MECANUM, d.type);
        assertTrue(d.frontLeftReversed && d.backLeftReversed);
        assertFalse(d.frontRightReversed || d.backRightReversed);
        assertEquals(537.7, c.auto().ticksPerRev, 1e-9);
        assertEquals(96 / 25.4, c.auto().wheelDiameterIn, 1e-9);
        assertTrue(c.auto().calibrationConfirmed);
        assertEquals(0.25, r.slowSpeed(), 1e-9);
    }

    @Test public void reversedNamesReplaceTheLeftSideDefault() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("left_drive", "right_drive").reversed("right_drive"));
            }
        };
        DriveConfig d = r.robotConfig().drive();
        assertFalse(d.frontLeftReversed);
        assertTrue(d.frontRightReversed);
        assertFalse(d.hasBackMotors());
    }

    @Test public void aDeviceNameUsedTwiceStopsAtInit() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("left", "right"));
                add(Claw.servo("claw"));
                add(Positioner.servo("claw"));
            }
        };
        try {
            r.describe();
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("\"claw\" is used twice"));
        }
    }

    @Test public void liftGoesToNamedHeightsWhateverTheCapitals() {
        LiftBot r = new LiftBot();
        RobotTestKit kit = new RobotTestKit();
        r.setup(kit);
        kit.switches.get("lift_bottom")[0] = true;   // homed at the bottom
        r.all().update();
        kit.switches.get("lift_bottom")[0] = false;
        FakeMotor m = kit.motors.get("lift");
        r.lift.goTo("HIGH");
        for (int i = 0; i < 400; i++) {
            r.all().update();
            m.position += m.power * 40;
        }
        assertTrue(r.lift.isAt("high"));
        assertEquals(2400, r.lift.position(), 25);
    }

    @Test public void intakeRunsInStopsAndReversesWhileHeld() {
        LiftBot r = new LiftBot();
        RobotTestKit kit = new RobotTestKit();
        r.setup(kit);
        FakeMotor m = kit.motors.get("intake");
        r.intake.in();
        r.all().update();
        assertEquals(0.8, m.power, 1e-9);
        r.intake.reverse();
        r.all().update();
        assertEquals(-0.7, m.power, 1e-9);
        r.intake.endReverse();
        r.all().update();
        assertEquals(0.8, m.power, 1e-9);   // back to what it was doing
        r.intake.toggle();
        r.all().update();
        assertEquals(0, m.power, 1e-9);
    }

    @Test public void onAtStartWaitsForStart() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(Numbers.class);
                drive(Drive.tank("l", "r"));
                add(Intake.crServos("left_intake", "right_intake").reversed("right_intake").onAtStart());
            }
        };
        RobotTestKit kit = new RobotTestKit();
        r.setup(kit);
        r.all().update();
        assertEquals(0, kit.crServos.get("left_intake").power, 1e-9);   // nothing moves in INIT
        r.started();
        r.all().update();
        assertEquals(1.0, kit.crServos.get("left_intake").power, 1e-9);
        assertEquals(1.0, kit.crServos.get("right_intake").power, 1e-9);
    }

    @Test public void clawTogglesBetweenOpenAndClosed() {
        LiftBot r = new LiftBot();
        RobotTestKit kit = new RobotTestKit();
        r.setup(kit);
        assertTrue(Double.isNaN(kit.servos.get("claw").position));   // no command before START
        r.claw.toggle();
        r.all().update();
        assertEquals(0.2, kit.servos.get("claw").position, 1e-9);
        r.claw.toggle();
        r.all().update();
        assertEquals(0.75, kit.servos.get("claw").position, 1e-9);
        assertTrue(r.claw.isClosed());
    }

    @Test public void aClawNeedsOpenAndClosed() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(Numbers.class);
                drive(Drive.tank("l", "r"));
                add(Claw.servo("claw").tunables("Lift"));   // a section without open/closed
            }
        };
        try {
            r.setup(new RobotTestKit());
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("needs \"open\" and \"closed\""));
        }
    }

    @Test public void positionerReadsItsSectionFromTheDeviceName() {
        assertEquals("IntakeDeploy", Module.sectionFor("intake_deploy"));
        assertEquals("Webcam1", Module.sectionFor("Webcam 1"));
        assertEquals("Lift", Module.sectionFor("lift"));
    }

    @Test public void launcherStepsItsSpeedAndAnIndexerFeedsIt() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer")));
            }
        };
        RobotTestKit kit = new RobotTestKit();
        r.setup(kit);
        Launcher l = (Launcher) r.modules().get(0);
        l.spinUp();
        r.all().update();
        assertEquals(2600, l.targetRpm(), 1e-9);
        l.adjust(2);
        r.all().update();
        assertEquals(2800, l.targetRpm(), 1e-9);
        r.started();
        assertEquals(0.0, kit.servos.get("indexer").position, 1e-9);   // indexer at rest after START
    }

    @Test public void anIndexerInsideALauncherCannotBeAddedAgain() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                Indexer i = Indexer.servo("indexer");
                add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(i));
                add(i);
            }
        };
        try {
            r.describe();
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("already feeds a launcher"));
        }
    }

    @Test public void unknownMotorIdsStopWithTheList() {
        try {
            Drive.tank("l", "r").motor("gobilda-5203-313");
            fail("expected an error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("gobilda-5203-312"));
        }
    }

    @Test public void simulatorLinesComeFromTheCodeWithoutHardware() {
        List<String> lines = new LiftBot().simLines();
        String all = String.join("\n", lines);
        assertTrue(all, all.contains("drive.type = mecanum"));
        assertTrue(all, all.contains("drive.motors = front_left, front_right, back_left, back_right"));
        assertTrue(all, all.contains("motor.lift = lift, part=gobilda-5203-312, min=0, max=3000"));
        assertTrue(all, all.contains("touch.lift_bottom = lift, below=20"));
        assertTrue(all, all.contains("servo.claw = 0.20"));
        assertTrue(all, all.contains("grab.claw = shape=claw, servo=claw, closedAbove=0.48"));
    }

    @Test public void reversedServosGetTheirRealPositionInTheSimulatorFile() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(Numbers.class);
                drive(Drive.tank("l", "r"));
                add(Claw.servo("claw").reversed());
                add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer").reversed()));
            }
        };
        String all = String.join("\n", r.simLines());
        assertTrue(all, all.contains("servo.claw = 0.80"));                          // open 0.2, reversed
        assertTrue(all, all.contains("grab.claw = shape=claw, servo=claw, closedBelow=0.5"));
        assertTrue(all, all.contains("servo.indexer = 1.00"));                       // rest 0 (default), reversed
    }

    @Test public void anAutonomousActionThatFailsSaysSoInTheReport() {
        LiftBot r = new LiftBot();
        r.setup(new RobotTestKit());
        io.github.jasoncross.ftchelper.auto.AutoRoutine routine = new io.github.jasoncross.ftchelper.auto.AutoRoutine(
                () -> true, () -> { }, new io.github.jasoncross.ftchelper.Fakes.FakeTelemetry().telemetry).subsystems(r.all());
        Steps s = new Steps(routine, null, r);
        s.action(Action.named("Never").waitUntil("the impossible", () -> false, 0.05), 2);
        routine.runAll();
        assertEquals("[Never: timed out waiting for the impossible]", routine.getReport().toString());
    }

    @Test public void aCameraThatIsNotAddedStopsAtInit() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Turret.motor("turret", "gobilda-5203-117").follows(Camera.limelight("limelight")));
            }
        };
        try {
            r.setup(new RobotTestKit());
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("camera that is not added"));
        }
    }

    public static class FastNumbers {
        public static class Launcher {
            public static double speedRpm = 7000;
        }
    }

    @Test public void aLauncherSpeedAboveTheMotorsTopSpeedStops() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(FastNumbers.class);
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            }
        };
        try {
            r.setup(new RobotTestKit());
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("above the gobilda-5203-6000's top speed"));
        }
    }

    @Test public void usingAModuleBeforeSetupSaysSo() {
        LiftBot r = new LiftBot();
        r.describe();
        try {
            r.lift.goTo("high");
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not set up yet"));
        }
        assertNull(r.all());
    }
}
