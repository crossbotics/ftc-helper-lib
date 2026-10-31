package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Parts marked optional() in Robot.java (docs/modules/robot.md, "Parts not on the robot yet"): a part
 * whose device is not in the configuration is off instead of stopping INIT, and everything that uses
 * it copes (actions, buttons, interlock rules, the Tunables, the aim).
 */
public class OptionalPartsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final Fakes.Clock clock = new Fakes.Clock();

    @Before public void setUp() {
        com.crossbotics.ftchelper.safety.SafeMotor.freshReadingsForTests(true);
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        // PRACTICE: a part that is off is a quiet note (COMPETITION makes it loud: its own tests).
        com.crossbotics.ftchelper.core.Profile.set(com.crossbotics.ftchelper.core.Profile.PRACTICE);
    }

    @After public void back() {
        com.crossbotics.ftchelper.safety.SafeMotor.freshReadingsForTests(false);
        com.crossbotics.ftchelper.core.Profile.resetForTests();
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    public static class Numbers {
        public static class IntakeDeploy {
            public static double stowed = 0.1, deployed = 0.8, travelSec = 0.3;
        }
        public static class Lift {
            public static double min = 0, max = 3000, kP = 0.004, kI = 0, kD = 0, kF = 0.05;
            public static double down = 0, high = 2400;
        }
        public static class Claw {
            public static double open = 0.2, closed = 0.75;
        }
    }

    // No Lift section at all: a lift that is there stops at INIT (its safe range is required).
    public static class NoLiftNumbers {
        public static class Claw {
            public static double open = 0.2, closed = 0.75;
        }
    }

    // A wrong safe range (max below min).
    public static class WrongLiftNumbers {
        public static class Lift {
            public static double min = 100, max = 50;
        }
    }

    /** A robot built in steps: the deploy servo and the lift may not be on it yet. */
    static class StepBot extends RobotDefinition {
        final Class<?> numbers;
        Positioner intakeDeploy;
        Intake intake;
        Lift lift;
        Claw claw;

        StepBot(Class<?> numbers) {
            this.numbers = numbers;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            intakeDeploy = add(Positioner.servo("intake_deploy").optional());
            intake = add(Intake.motor("intake"));
            lift = add(Lift.slide("lift").optional());
            claw = add(Claw.servo("claw"));
        }
    }

    private StepBot bot(RobotTestKit kit, String... absent) {
        return bot(Numbers.class, kit, absent);
    }

    private StepBot bot(Class<?> numbers, RobotTestKit kit, String... absent) {
        kit.absent.addAll(Arrays.asList(absent));
        StepBot r = new StepBot(numbers);
        r.setup(kit);
        r.runner.useClock(clock::get);
        return r;
    }

    private void loop(RobotDefinition r) {
        clock.addSec(0.02);
        r.all().update();
    }

    private static List<String> screen(RobotDefinition r) {
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.all().addTelemetry(t);
        t.update();
        return ft.lastScreen;
    }

    // ----------------------------- set up -----------------------------

    @Test public void aMissingOptionalPartIsOffInsteadOfStoppingInit() {
        RobotTestKit kit = new RobotTestKit();
        StepBot r = bot(kit, "intake_deploy");
        assertTrue(r.intakeDeploy.isOptional());
        assertFalse(r.intakeDeploy.isPresent());
        assertTrue(r.intake.isPresent());
        assertTrue(r.notes().toString(), r.notes().contains("\"intake_deploy\" not in the configuration: IntakeDeploy is off (optional part)"));
        // Never asked for: nothing is sent to a device that is not there.
        assertFalse(kit.servos.containsKey("intake_deploy"));
        // Commands are ignored, questions never hold anything up.
        r.intakeDeploy.goTo("deployed");
        r.intakeDeploy.next();
        assertTrue(r.intakeDeploy.isDone());
        assertTrue(r.intakeDeploy.isAt("stowed"));   // last sent there (next() after deployed)
        loop(r);
        assertTrue(screen(r).toString(), screen(r).contains("IntakeDeploy : " + MissingPart.TELEMETRY));
    }

    @Test public void anOptionalPartThatIsThereWorksAsUsual() {
        RobotTestKit kit = new RobotTestKit();
        StepBot r = bot(kit);
        assertTrue(r.intakeDeploy.isPresent());
        r.started();
        r.intakeDeploy.goTo("deployed");
        loop(r);
        assertEquals(0.8, kit.servos.get("intake_deploy").position, 1e-9);
        for (String n : r.notes()) assertFalse(n, n.contains("not in the configuration"));
    }

    @Test public void aMissingPartThatIsNotOptionalStopsAndSaysHowToMarkIt() {
        try {
            bot(new RobotTestKit(), "claw");
            fail("expected a stop at INIT");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("No Servo named \"claw\""));
            assertTrue(e.getMessage(), e.getMessage().contains("Add .optional() to Claw's line in Robot.java"));
        }
    }

    // Only the first device decides: an optional lift that is there needs its switch too.
    @Test public void halfAPartStillStopsInit() {
        RobotTestKit kit = new RobotTestKit();
        kit.absent.add("lift_bottom");
        try {
            new RobotDefinition() {
                @Override protected void define() {
                    tunables(Numbers.class);
                    drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
                    add(Lift.slide("lift").bottomSwitch("lift_bottom").optional());
                }
            }.setup(kit);
            fail("expected a stop at INIT");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("lift_bottom"));
        }
        // Without its first device the whole lift is off, switch and all.
        RobotTestKit kit2 = new RobotTestKit();
        kit2.absent.add("lift");
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(Numbers.class);
                drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
                add(Lift.slide("lift").bottomSwitch("lift_bottom").optional());
            }
        }.setup(kit2);
        assertFalse(r.modules().get(0).isPresent());
        assertFalse(kit2.switches.containsKey("lift_bottom"));
    }

    @Test public void aMissingPartNeedsNoTunablesSection() {
        StepBot r = bot(NoLiftNumbers.class, new RobotTestKit(), "lift");
        assertFalse(r.lift.isPresent());
        for (String n : r.notes()) assertFalse(n, n.contains("Tunables.Lift"));
        r.lift.goTo("high");
        r.lift.manual(0.5);
        assertTrue(r.lift.isAtTarget());
        // The same robot WITH the lift: its safe range is required, as always.
        try {
            bot(NoLiftNumbers.class, new RobotTestKit());
            fail("expected a stop: no Tunables.Lift");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Lift"));
        }
    }

    @Test public void wrongNumbersOfAMissingPartAreANoteNotAStop() {
        StepBot r = bot(WrongLiftNumbers.class, new RobotTestKit(), "lift");
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("Lift is off (optional part). Fix its Tunables before adding it: Tunables.Lift: max"));
    }

    @Test public void aMultiDeviceIntakeIsMissingWhenItsFirstDeviceIs() {
        RobotTestKit kit = new RobotTestKit();
        kit.absent.add("left_intake");
        kit.absent.add("right_intake");
        Intake[] in = new Intake[1];
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
                in[0] = add(Intake.crServos("left_intake", "right_intake").optional());
            }
        }.setup(kit);
        assertFalse(in[0].isPresent());
        in[0].in();
        assertFalse(in[0].isRunning());
        r.all().update();
        // The first one there, the second not: a wiring mistake, INIT stops.
        RobotTestKit kit2 = new RobotTestKit();
        kit2.absent.add("right_intake");
        try {
            new RobotDefinition() {
                @Override protected void define() {
                    drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
                    add(Intake.crServos("left_intake", "right_intake").optional());
                }
            }.setup(kit2);
            fail("expected a stop at INIT");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("right_intake"));
        }
    }

    // A launcher that is not there takes its feeder and hood with it; a feeder can be optional alone.
    @Test public void aMissingLauncherTurnsOffItsFeederAndHood() {
        RobotTestKit kit = new RobotTestKit();
        kit.absent.add("launcher");
        Launcher[] l = new Launcher[1];
        Indexer feeder = Indexer.servo("indexer");
        Hood hood = Hood.servo("hood");
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
                l[0] = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(feeder).hood(hood).optional());
            }
        }.setup(kit);
        assertFalse(l[0].isPresent());
        assertFalse(feeder.isPresent());
        assertFalse(hood.isPresent());
        assertFalse("its feeder and hood are never asked for", kit.servos.containsKey("indexer") || kit.servos.containsKey("hood"));
        r.started();
        l[0].spinUp();
        l[0].fire();
        for (int i = 0; i < 5; i++) r.all().update();
        assertFalse(l[0].isReady());
        assertFalse(l[0].isSpinning());
        assertFalse(l[0].isShooting());
        int offNotes = 0;
        for (String n : r.notes()) if (n.contains("not in the configuration")) offNotes++;
        assertEquals(r.notes().toString(), 1, offNotes);   // one note for the launcher, none for its feeder and hood

        RobotTestKit kit2 = new RobotTestKit();
        kit2.absent.add("indexer");
        Indexer feeder2 = Indexer.servo("indexer").optional();
        RobotDefinition r2 = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
                l[0] = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(feeder2));
            }
        }.setup(kit2);
        assertTrue(l[0].isPresent());
        assertFalse(feeder2.isPresent());
        assertTrue(r2.notes().toString(), r2.notes().toString().contains("\"indexer\" not in the configuration: Indexer is off"));
        r2.started();
        l[0].spinUp();
        l[0].fire();
        for (int i = 0; i < 5; i++) r2.all().update();
        assertFalse(feeder2.isBusy());
    }

    // ----------------------------- actions -----------------------------

    // Team 8938's deploy action, with no deploy servo yet: the servo step and its wait are skipped,
    // the intake still starts, and the action says what it skipped.
    @Test public void anActionSkipsAMissingPartAndTheWaitsAfterIt() {
        RobotTestKit kit = new RobotTestKit();
        StepBot r = bot(kit, "intake_deploy");
        Action deploy = Action.named("Deploy intake")
                .then(() -> r.intakeDeploy.goTo("deployed"))
                .waitUntil("the deploy servo", r.intakeDeploy::isDone, 2.0)
                .waitSeconds(1.5)
                .then(r.intake::in, () -> r.intakeDeploy.goTo("stowed"));
        r.started();
        r.run(deploy);
        loop(r);
        assertFalse("done at once: nothing to wait for", deploy.isRunning());
        assertEquals("done", deploy.result());
        assertTrue(r.intake.isRunning());
        assertTrue(kit.motors.get("intake").power > 0.5);
        assertEquals(Collections.singletonList("IntakeDeploy"), deploy.skipped());
        assertEquals("Deploy intake: done; skipped IntakeDeploy (not on the robot)", deploy.status());
        // With the servo there, the same action waits for it.
        StepBot full = bot(new RobotTestKit());
        Action deploy2 = Action.named("Deploy intake")
                .then(() -> full.intakeDeploy.goTo("deployed"))
                .waitUntil("the deploy servo", full.intakeDeploy::isDone, 2.0)
                .then(full.intake::in);
        full.started();
        full.run(deploy2);
        loop(full);
        assertTrue(deploy2.isRunning());
        assertTrue(deploy2.skipped().isEmpty());
    }

    // A step that uses a missing part AND one that is there keeps its wait; the missing lift counts as
    // at its target, so the action still goes on.
    @Test public void aMixedStepKeepsItsWaitAndAMissingLiftIsAtItsTarget() {
        RobotTestKit kit = new RobotTestKit();
        StepBot r = bot(kit, "lift");
        Action score = Action.named("Score")
                .then(() -> r.lift.goTo("high"), r.claw::close)
                .waitSeconds(0.1)
                .waitUntil("lift at the top", r.lift::isAtTarget, 1.0)
                .then(r.claw::open);
        r.started();
        r.run(score);
        loop(r);
        assertTrue("the 0.1 s wait is kept (the claw is there)", score.isRunning());
        for (int i = 0; i < 10; i++) loop(r);
        assertFalse(score.isRunning());
        assertEquals("done", score.result());
        assertEquals(0.2, kit.servos.get("claw").position, 1e-9);
        assertEquals(Collections.singletonList("Lift"), score.skipped());
    }

    // The driver's own commands to a missing part: ignored, and they do not stop an action.
    @Test public void driverCommandsToAMissingPartDoNotStopAnAction() {
        StepBot r = bot(new RobotTestKit(), "lift");
        Action a = Action.named("Close and wait").then(r.claw::close).waitSeconds(1);
        r.run(a);
        r.lift.manual(0.8);
        loop(r);
        assertTrue(a.isRunning());
    }

    // ----------------------------- buttons -----------------------------

    @Test public void buttonsOfAMissingPartAreOffOnTheMapAndDoNothing() {
        RobotTestKit kit = new RobotTestKit();
        StepBot r = bot(kit, "intake_deploy", "lift");
        final Action deploy = Action.named("Deploy intake").then(() -> r.intakeDeploy.goTo("deployed"));
        ButtonPlan plan = ControlMap.makePlan(r, false, true, (g1, g2) -> {
            g1.x.press(deploy).says("deploy intake").needs(r.intakeDeploy);
            g2.y.toggle(r.intake).says("intake on/off");
            g2.leftStickX.use(r.lift::manual).says("lift by hand").needs(r.lift);
        });
        List<String> help = plan.help();
        assertTrue(help.toString(), help.contains("G1 X: deploy intake - off: IntakeDeploy not on the robot"));
        assertTrue(help.toString(), help.contains("G2 Y: intake on/off"));
        assertTrue(help.toString(), help.contains("G2 left stick X: lift by hand - off: Lift not on the robot"));
        // The lift's own suggested buttons are off too.
        boolean liftOff = false;
        for (String h : help) if (h.contains("[Lift]")) liftOff |= h.endsWith(" - off: Lift not on the robot");
        assertTrue(help.toString(), liftOff);
        for (String h : help) if (h.contains("[Lift]")) assertTrue(h, h.endsWith(" - off: Lift not on the robot"));

        // Pressing X does nothing; Y still works.
        ControllerBindings driver = new ControllerBindings(), operator = new ControllerBindings();
        final Gamepad gp1 = new Gamepad(), gp2 = new Gamepad();
        Supplier<Gamepad> p1 = () -> gp1, p2 = () -> gp2;
        plan.compile(r, driver, operator, new ArrayList<>(), p1, p2);
        gp1.x = true;
        gp2.y = true;
        driver.update(gp1, gp2);
        operator.update(gp1, gp2);
        assertFalse(deploy.isRunning());
        assertTrue(r.intake.isRunning());
    }

    @Test public void toggleOfAMissingPartIsOff() {
        StepBot r = bot(new RobotTestKit(), "lift");
        Intake[] in = new Intake[1];
        RobotTestKit kit = new RobotTestKit();
        kit.absent.add("intake");
        RobotDefinition r2 = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
                in[0] = add(Intake.motor("intake").optional());
            }
        }.setup(kit);
        ButtonPlan plan = ControlMap.makePlan(r2, false, false, (g1, g2) -> g2.a.toggle(in[0]).says("intake"));
        assertTrue(plan.help().toString(), plan.help().contains("G2 A: intake - off: Intake not on the robot"));
        assertTrue(r.claw.isPresent());
    }

    // ----------------------------- interlock rules and the aim -----------------------------

    public static class TurretNumbers {
        public static class Lift {
            public static double min = 0, max = 3000, down = 0, high = 2400;
        }
        // No TurretLift section: the rule needs none while the lift is not on the robot.
    }

    @Test public void anInterlockRuleWithAMissingPartIsOff() {
        RobotTestKit kit = new RobotTestKit();
        kit.absent.add("lift");
        final Interlock[] rule = new Interlock[1];
        final Turret[] turret = new Turret[1];
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(TurretNumbers.class);
                drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
                turret[0] = add(Turret.motor("turret", "gobilda-5203-117"));
                Lift lift = add(Lift.slide("lift").optional());
                rule[0] = keepApart("TurretLift", turret[0].zone(), lift.zoneAbove());
            }
        }.setup(kit);
        assertTrue(rule[0].isOff());
        assertTrue(r.notes().toString(), r.notes().contains("Interlock TurretLift: off (Lift is not on the robot)"));
        r.started();
        turret[0].aimAt(170);
        for (int i = 0; i < 5; i++) r.all().update();
        assertFalse(rule[0].isHolding());
        assertFalse(turret[0].isHeld());
    }

    // A following turret that is not on the robot yet: whole-robot aim may be used instead.
    @Test public void aMissingTurretLeavesTheAimToTheWholeRobot() {
        RobotTestKit kit = new RobotTestKit();
        kit.absent.add("turret");
        kit.missing.add("limelight");
        final Turret[] turret = new Turret[1];
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
                Camera camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
                turret[0] = add(Turret.motor("turret", "gobilda-5203-117").follows(camera).optional());
            }
        }.setup(kit);
        assertFalse(turret[0].isPresent());
        assertTrue(turret[0].isAimed());
        assertFalse(r.aimMode().isOn());   // no "separate modes" stop
    }

    // ----------------------------- COMPETITION: loud; the tuning menu: hidden -----------------------------

    // At an event a part that is off is most likely a mistake (a misspelled configuration name): in
    // COMPETITION it is a "!!" warning at INIT and "!! OFF" on the button map; PRACTICE keeps the note.
    @Test public void inCompetitionAMissingPartIsALoudWarning() {
        StepBot r = bot(new RobotTestKit(), "intake_deploy");
        String note = "\"intake_deploy\" not in the configuration: IntakeDeploy is off (optional part)";
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.showNotes(t);
        t.update();
        assertTrue(ft.lastScreen.toString(), ft.lastScreen.contains(note));
        for (String line : ft.lastScreen) assertFalse(line, line.startsWith("!!"));
        ButtonPlan practice = ControlMap.makePlan(r, false, false, (g1, g2) -> g2.x.press(r.intakeDeploy::next).says("deploy").needs(r.intakeDeploy));
        assertTrue(practice.help().toString(), practice.help().contains("G2 X: deploy - off: IntakeDeploy not on the robot"));

        com.crossbotics.ftchelper.core.Profile.set(com.crossbotics.ftchelper.core.Profile.COMPETITION);
        ft = new Fakes.FakeTelemetry();
        t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.showNotes(t);
        t.update();
        assertEquals("!! COMPETITION: \"intake_deploy\" not in the configuration: IntakeDeploy is OFF (optional part). "
                + "On the robot? Check its name in the configuration, then INIT again", ft.lastScreen.get(0));
        assertFalse("not twice: the warning replaces the note", ft.lastScreen.contains(note));
        ButtonPlan competition = ControlMap.makePlan(r, false, false, (g1, g2) -> g2.x.press(r.intakeDeploy::next).says("deploy").needs(r.intakeDeploy));
        assertTrue(competition.help().toString(), competition.help().contains("G2 X: deploy - !! OFF: IntakeDeploy not on the robot"));
    }

    // Its numbers are left out of the tuning menu (changing them would do nothing); its saved value is
    // kept, and the next INIT with the part there shows them again.
    @Test public void theTuningMenuHidesTheNumbersOfAMissingPart() {
        StepBot r = bot(new RobotTestKit(), "intake_deploy");
        com.crossbotics.ftchelper.tuning.Tunable deployed = com.crossbotics.ftchelper.tuning.Tunable.number("IntakeDeploy/deployed", 0, 1);
        assertTrue(deployed.isHidden());
        assertFalse(com.crossbotics.ftchelper.tuning.Tunable.shown().contains(deployed));
        assertTrue(com.crossbotics.ftchelper.tuning.Tunable.all().contains(deployed));
        assertEquals(Collections.singletonList("IntakeDeploy"), com.crossbotics.ftchelper.tuning.Tunable.hiddenGroups());
        for (com.crossbotics.ftchelper.tuning.Tunable t : com.crossbotics.ftchelper.tuning.Tunable.shown()) {
            assertFalse(t.name(), t.name().startsWith("IntakeDeploy/"));
        }
        assertTrue(r.claw.isPresent());
        bot(new RobotTestKit());   // INIT again, the servo there
        assertFalse(deployed.isHidden());
        assertTrue(com.crossbotics.ftchelper.tuning.Tunable.shown().contains(deployed));
    }

    // ----------------------------- the Hardware Check's list -----------------------------

    @Test public void checkConfigurationSaysWhatEachMissingDeviceMeans() {
        RobotTestKit kit = new RobotTestKit();
        kit.absent.addAll(Arrays.asList("intake_deploy", "claw", "back_right"));
        StepBot r = new StepBot(Numbers.class);
        List<String> lines = r.checkConfiguration(kit);
        assertTrue(lines.toString(), lines.contains("\"back_right\" (Drive): NOT in the configuration: INIT will stop. Add it, a robot always needs its drive"));
        assertTrue(lines.toString(), lines.contains("\"intake_deploy\" (IntakeDeploy): not in the configuration: optional, so the part is off (fine)"));
        assertTrue(lines.toString(), lines.contains("\"claw\" (Claw): NOT in the configuration: INIT will stop. Add it, or add .optional() to Claw in Robot.java"));
        assertEquals(3, lines.size());
        assertTrue(new StepBot(Numbers.class).checkConfiguration(new RobotTestKit()).isEmpty());
    }

    // ----------------------------- every kind of part -----------------------------

    /** One of every kind of part, each optional, each one's device missing. */
    static class EveryPart extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            add(Intake.motor("intake").optional());
            add(Intake.crServos("left_intake", "right_intake").optional());
            add(Lift.slide("lift").bottomSwitch("lift_bottom").optional());
            add(Arm.motor("arm", "gobilda-5203-117").optional());
            add(Claw.servo("claw").optional());
            add(Wrist.servo("wrist").optional());
            add(Positioner.servo("intake_deploy").optional());
            add(Turret.motor("turret", "gobilda-5203-117").homeSwitch("turret_home").optional());
            add(Turret.servo("servo_turret").optional());
            add(Turret.crServo("cr_turret").analogEncoder("cr_turret_encoder").optional());
            add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.motor("feeder"))
                    .hood(Hood.motor("hood", "gobilda-5203-117")).optional());
            add(Indexer.crServo("windmill").optional());
            add(Lights.gobilda("light").optional());
            add(Lights.blinkin("blinkin").optional());
            add(Camera.webcam("Webcam 1").optional());
            add(Camera.huskyLens("huskylens").optional());
            add(PieceSensor.color("intake_color").kinds("POLLEN", "NECTAR").optional());
        }
    }

    // Every public command and question of every missing part runs without an error (on the stand-in
    // devices), and so do the loops, pause, resume and stop.
    @Test public void everyKindOfPartCanBeMissingWithoutACrash() throws Exception {
        RobotTestKit kit = new RobotTestKit();
        EveryPart r = new EveryPart();
        for (Module<?> m : r.modules()) {
            kit.absent.addAll(m.deviceNames());
            for (Module<?> c : m.children()) kit.absent.addAll(c.deviceNames());
        }
        r.setup(kit);
        r.runner.useClock(clock::get);
        List<Module<?>> parts = new ArrayList<>();
        for (Module<?> m : r.modules()) {
            parts.addAll(m.children());
            parts.add(m);
        }
        for (Module<?> m : parts) assertFalse(m.name(), m.isPresent());
        int offNotes = 0;
        for (String n : r.notes()) if (n.contains("not in the configuration")) offNotes++;
        assertEquals(r.notes().toString(), 17, offNotes);
        r.started();
        List<String> problems = new ArrayList<>();
        int calls = 0;
        for (int round = 0; round < 2; round++) {
            for (Module<?> m : parts) {
                for (Method method : m.getClass().getMethods()) {
                    if (Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass() == Object.class) continue;
                    // Builders (they hand back a part) are for define(), not for after setup.
                    if (Module.class.isAssignableFrom(method.getReturnType()) || method.getReturnType() == Zone.class) continue;
                    Object[] args = argsFor(method, m);
                    if (args == null) continue;
                    calls++;
                    try {
                        method.invoke(m, args);
                    } catch (InvocationTargetException e) {
                        Throwable c = e.getCause();
                        // A wrong input (our made-up one) may be refused; anything else is a crash.
                        if (c instanceof IllegalArgumentException || c instanceof UnsupportedOperationException) continue;
                        problems.add(m.name() + "." + method.getName() + ": " + c);
                    }
                }
                Action a = Action.named("Use " + m.name()).then(m::touch).waitSeconds(10);
                r.run(a);
            }
            for (int i = 0; i < 3; i++) loop(r);
            screen(r);
            r.pauseAll();
            r.resume();
        }
        r.all().stop();
        assertTrue(String.join("\n", problems), problems.isEmpty());
        assertTrue("calls: " + calls, calls > 150);
        for (String line : screen(r)) {
            if (line.contains(":") && !line.startsWith("Action")) assertTrue(line, line.endsWith(MissingPart.TELEMETRY));
        }
    }

    // Made-up inputs for a method: a position name, 0.5, 1, true; null if it takes something else.
    private static Object[] argsFor(Method method, Module<?> m) {
        Class<?>[] types = method.getParameterTypes();
        Object[] out = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == String.class) out[i] = "high";
            else if (t == double.class) out[i] = 0.5;
            else if (t == int.class) out[i] = 1;
            else if (t == long.class) out[i] = 1L;
            else if (t == boolean.class) out[i] = true;
            else return null;
        }
        return out;
    }

    // The stand-in devices answer like plain objects and remember what they were set to.
    @Test public void standInDevicesRememberWhatTheyWereSet() {
        com.qualcomm.robotcore.hardware.DcMotorEx motor = MissingPart.standIn(com.qualcomm.robotcore.hardware.DcMotorEx.class, "lift");
        motor.setPower(0.4);
        assertEquals(0.4, motor.getPower(), 1e-12);
        assertEquals(0, motor.getCurrentPosition());
        assertEquals(com.qualcomm.robotcore.hardware.DcMotor.RunMode.values()[0], motor.getMode());
        assertEquals("lift (not in the configuration)", motor.getDeviceName());
        assertTrue(motor.equals(motor));
        assertEquals(0, MissingPart.standIn(com.qualcomm.robotcore.hardware.AnalogInput.class, "enc").getVoltage(), 1e-12);
        assertTrue(MissingPart.standIn(com.qualcomm.hardware.rev.RevBlinkinLedDriver.class, "b") != null);
    }
}
