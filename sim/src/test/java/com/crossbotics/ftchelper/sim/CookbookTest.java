package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.examples.cookbook.ActionRecipes;
import com.crossbotics.ftchelper.examples.cookbook.AimRecipes;
import com.crossbotics.ftchelper.examples.cookbook.AutonomousRecipes;
import com.crossbotics.ftchelper.examples.cookbook.ButtonRecipes;
import com.crossbotics.ftchelper.examples.cookbook.DrivingRecipes;
import com.crossbotics.ftchelper.examples.cookbook.HoodRecipes;
import com.crossbotics.ftchelper.examples.cookbook.InterlockRecipes;
import com.crossbotics.ftchelper.examples.cookbook.LightRecipes;
import com.crossbotics.ftchelper.examples.cookbook.MechanismRecipes;
import com.crossbotics.ftchelper.examples.cookbook.OdometryRecipes;
import com.crossbotics.ftchelper.examples.cookbook.PedroRecipes;
import com.crossbotics.ftchelper.examples.cookbook.PieceRecipes;
import com.crossbotics.ftchelper.examples.cookbook.TelemetryRecipes;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The cookbook (docs/cookbook.md, examples/.../cookbook): every recipe robot makes a simulator robot
 * file with no problems, and every recipe OpMode INITs and runs for a moment without an error. So a
 * recipe that compiles but stops at INIT (two jobs on one button, a missing Tunables number) fails here.
 */
public class CookbookTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final List<Class<?>> GROUPS = Arrays.asList(DrivingRecipes.class, ButtonRecipes.class,
            MechanismRecipes.class, ActionRecipes.class, AutonomousRecipes.class, OdometryRecipes.class,
            PedroRecipes.class, TelemetryRecipes.class, PieceRecipes.class, AimRecipes.class, LightRecipes.class,
            HoodRecipes.class, InterlockRecipes.class);

    // The whole files (one class per file, as in TeamCode): the robot, its TeleOp, an autonomous, the finders.
    private static final List<Class<?>> FILES = Arrays.asList(
            com.crossbotics.ftchelper.examples.cookbook.Robot.class,
            com.crossbotics.ftchelper.examples.cookbook.Controls.class,
            com.crossbotics.ftchelper.examples.cookbook.autos.LeaveTheStart.class,
            com.crossbotics.ftchelper.examples.cookbook.autos.NearPreloadPark.class,
            com.crossbotics.ftchelper.examples.cookbook.autos.NearCycle.class,
            com.crossbotics.ftchelper.examples.cookbook.autos.ChooseInInit.class,
            com.crossbotics.ftchelper.examples.cookbook.ServoFinder.class,
            com.crossbotics.ftchelper.examples.cookbook.HoodFinder.class,
            com.crossbotics.ftchelper.examples.cookbook.MotorFinder.class,
            com.crossbotics.ftchelper.examples.cookbook.DriveTuner.class,
            com.crossbotics.ftchelper.examples.cookbook.OdometryTuner.class,
            com.crossbotics.ftchelper.examples.cookbook.ShotTuner.class,
            com.crossbotics.ftchelper.examples.cookbook.PieceTuner.class);

    // Every recipe class: the nested ones in the topic files, and the whole files.
    private static List<Class<?>> recipeClasses() {
        List<Class<?>> all = new ArrayList<>(FILES);
        for (Class<?> group : GROUPS) all.addAll(Arrays.asList(group.getDeclaredClasses()));
        return all;
    }

    private int files = 0;

    private Path robotFile(Class<?> robotClass) throws Exception {
        RobotDefinition r = (RobotDefinition) robotClass.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, r.simLines(), robotClass.getName(), "biobuzz-2026");
        assertTrue(robotClass.getName() + ": " + res.problems, res.problems.isEmpty());
        Path file = tmp.newFile(robotClass.getName() + "-" + (files++) + ".properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // The robot an OpMode runs: ControlMap<R> / Auto<R>, a finder's robot(), or (Servo and Motor
    // Finder, which need none) the mechanism robot.
    private static Class<?> robotOf(Class<?> opMode) throws Exception {
        Type t = opMode.getGenericSuperclass();
        if (t instanceof ParameterizedType) return (Class<?>) ((ParameterizedType) t).getActualTypeArguments()[0];
        for (Class<?> c = opMode; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("robot");
                m.setAccessible(true);
                Object made = m.invoke(opMode.getDeclaredConstructor().newInstance());
                if (made != null) return made.getClass();
            } catch (NoSuchMethodException e) {
                // look in the superclass
            } catch (java.lang.reflect.InvocationTargetException e) {
                break;   // the base class's robot() says "add a robot() method": none given
            }
        }
        return MechanismRecipes.Robot.class;
    }

    @Test public void everyRecipeRobotMakesARobotFile() throws Exception {
        int robots = 0;
        for (Class<?> c : recipeClasses()) {
            if (!RobotDefinition.class.isAssignableFrom(c)) continue;
            robotFile(c);
            robots++;
        }
        assertTrue("found the recipe robots: " + robots, robots >= 15);
    }

    @Test public void everyRecipeOpModeInitsAndRuns() throws Exception {
        List<String> failed = new ArrayList<>();
        int opModes = 0;
        for (Class<?> c : recipeClasses()) {
            if (!OpMode.class.isAssignableFrom(c) || Modifier.isAbstract(c.getModifiers())) continue;
            TeleOp tele = c.getAnnotation(TeleOp.class);
            Autonomous auto = c.getAnnotation(Autonomous.class);
            String name = tele != null ? tele.name() : auto.name();
            opModes++;
            @SuppressWarnings("unchecked")
            Class<? extends OpMode> type = (Class<? extends OpMode>) c;
            try (SimRun s = new SimRun(robotFile(robotOf(c)).toString())) {
                // The simulator lists top-level OpModes itself; nested ones (with a $ in the class name) are added here.
                if (c.getEnclosingClass() != null) s.sim.ds.register(name, tele != null ? "TeleOp" : "Autonomous", type);
                s.init(name);
                if (!s.error().isEmpty()) {
                    failed.add(name + " at INIT: " + s.error() + "\n" + String.join("\n", s.screen()));
                    continue;
                }
                s.start();
                SimRun.pause(1.0);
                if (!s.error().isEmpty()) failed.add(name + " running: " + s.error());
            }
        }
        assertTrue(String.join("\n\n", failed), failed.isEmpty());
        assertTrue("found the recipe OpModes: " + opModes, opModes >= 20);
    }

    // Two recipes that do something you can see: a toggle on a button, and an action on a button.
    @Test public void theToggleAndTheActionWork() throws Exception {
        try (SimRun s = new SimRun(robotFile(ButtonRecipes.Robot.class).toString())) {
            s.sim.ds.register("Cookbook Buttons", "TeleOp", ButtonRecipes.Controls.class);
            s.init("Cookbook Buttons");
            assertTrue(String.join("\n", s.screen()), s.screenHas("G1 X: intake on/off"));
            s.start();
            s.press(v -> s.g1().x = v);
            assertTrue("the intake runs", SimRun.waitFor(() -> s.robot().motors.get("intake").userPower() > 0.5, 1));
            s.press(v -> s.g1().x = v);
            assertTrue("and stops", SimRun.waitFor(() -> s.robot().motors.get("intake").userPower() == 0, 1));
            assertEquals("", s.error());
        }
        try (SimRun s = new SimRun(robotFile(ActionRecipes.Robot.class).toString())) {
            s.sim.ds.register("Cookbook Actions", "TeleOp", ActionRecipes.Controls.class);
            s.init("Cookbook Actions");
            s.start();
            s.press(v -> s.g2().y = v);                            // score high
            assertTrue("the lift rises\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.robot().motors.get("lift").shaft() > 1000, 5));
            assertEquals("", s.error());
        }
    }

    // The two-motor lift recipe: both motors turn one slide (the second mirrored), and pause stops both.
    @Test public void theTwoMotorLiftMovesOnBothMotorsAndPausesBoth() throws Exception {
        try (SimRun s = new SimRun(robotFile(MechanismRecipes.TwoMotorLift.class).toString())) {
            s.sim.ds.register("Cookbook Two Motor Lift", "TeleOp", MechanismRecipes.TwoMotorLiftControls.class);
            s.init("Cookbook Two Motor Lift");
            s.start();
            s.press(v -> s.g2().y = v);                            // the highest height
            assertTrue("the slide rises\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.robot().motors.get("lift_left").shaft() > 1000, 5));
            SimMotor left = s.robot().motors.get("lift_left"), right = s.robot().motors.get("lift_right");
            assertEquals("one slide: the second shaft turns with it, mirrored", -left.shaft(), right.shaft(), 1e-6);
            assertEquals("the same power", left.userPower(), right.userPower(), 1e-9);
            s.press(v -> s.g1().back = v);                         // pause
            assertTrue("pause stops both", SimRun.waitFor(() -> left.userPower() == 0 && right.userPower() == 0, 1));
            assertEquals("", s.error());
        }
    }

    // One autonomous with a menu: the "Start" line moves the note and the robot in INIT, and the "Plan"
    // line chooses the steps (Far + cycle: 12 steps, the 3rd crosses to the near side).
    @Test public void theInitMenuChoosesTheStartAndThePlan() throws Exception {
        try (SimRun s = new SimRun(robotFile(com.crossbotics.ftchelper.examples.cookbook.Robot.class).toString())) {
            s.init("Choose in INIT");
            s.press(v -> s.g1().x = v);                                   // BLUE
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Place robot : NEAR:"), 1));
            // BLUE's spot (-36, -61) seen from the blue wall: x +36 in the simulator's field frame.
            assertTrue("near spot, x " + s.robot().x, SimRun.waitFor(() -> Math.abs(s.robot().x - 36) < 0.5, 2));
            s.press(v -> s.g1().dpad_down = v);                           // Start delay
            s.press(v -> s.g1().dpad_down = v);                           // Start
            s.press(v -> s.g1().dpad_right = v);                          // Far
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Place robot : FAR:"), 1));
            assertTrue("far spot, x " + s.robot().x, SimRun.waitFor(() -> Math.abs(s.robot().x + 36) < 0.5, 2));
            s.press(v -> s.g1().dpad_down = v);                           // Plan
            s.press(v -> s.g1().dpad_right = v);                          // Preload + Cycle + Park
            assertTrue(String.join("\n", s.screen()), s.screenHas("Plan: Preload + Cycle + Park"));
            s.start();
            assertTrue(String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.screenHas("Step : 3/12 Across to the near side"), 3));
            assertEquals("", s.error());
        }
    }

    // A part written in Robot.java but not on the robot yet (optional, left out of the configuration):
    // TeleOp INITs with one note, its button is off, the action at START skips it and starts the
    // intake; an autonomous using it keeps going and its report says what it skipped.
    @Test public void aPartNotOnTheRobotYetKeepsTeleOpAndAutoRunning() throws Exception {
        Path file = robotFile(MechanismRecipes.NotOnTheRobotYet.class);
        Files.write(file, ("missing = intake_deploy\n").getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);
        try (SimRun s = new SimRun(file.toString())) {
            s.sim.ds.register("Cookbook Not On The Robot Yet", "TeleOp", MechanismRecipes.NotOnTheRobotYetControls.class);
            s.init("Cookbook Not On The Robot Yet");
            String init = String.join("\n", s.screen());
            assertEquals(init, "", s.error());
            // The simulator starts in COMPETITION (the safe default): the part that is off is a loud "!!" warning.
            assertTrue(init, s.screenHas("!! COMPETITION: \"intake_deploy\" not in the configuration: IntakeDeploy is OFF (optional part)"));
            assertTrue(init, s.screenHas("G2 X: deploy intake - !! OFF: IntakeDeploy not on the robot"));
            s.start();
            assertTrue("the action at START starts the intake\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.robot().motors.get("intake").userPower() > 0.5, 2));
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("IntakeDeploy : - (not on the robot)"), 1));
            s.press(v -> s.g2().x = v);   // off: nothing happens
            SimRun.pause(0.3);
            assertEquals("", s.error());
            assertTrue("the intake still runs", s.robot().motors.get("intake").userPower() > 0.5);
        }
        // The same robot file without the line, and the device taken out from the test instead (like a
        // scenario's "set device intake_deploy missing"): the autonomous.
        Path full = robotFile(MechanismRecipes.NotOnTheRobotYet.class);
        try (SimRun s = new SimRun(full.toString())) {
            s.robot().setMissing("intake_deploy", true);
            s.sim.ds.register("Cookbook Not On The Robot Yet Auto", "Autonomous", MechanismRecipes.NotOnTheRobotYetAuto.class);
            s.init("Cookbook Not On The Robot Yet Auto");
            assertEquals(String.join("\n", s.screen()), "", s.error());
            s.start();
            assertTrue("the report says what was skipped\n" + String.join("\n", s.screen()),
                    SimRun.waitFor(() -> s.screenHas("skipped IntakeDeploy (not on the robot)"), 8));
            assertTrue("and the intake runs", s.robot().motors.get("intake").userPower() > 0.5);
            assertEquals("", s.error());
        }
    }

    // The Shot Tuner with two tables and odometry: the distance comes from the odometry (no goal in
    // the camera's view), it starts on the launcher's start table, Y and the D-pad pick the other, and
    // each table gets its own line for Tunables.java.
    @Test public void theShotTunerTunesEachTableWithTheOdometrysDistance() throws Exception {
        try (SimRun s = new SimRun(robotFile(AimRecipes.Robot.class).toString())) {
            s.sim.ds.register("Cookbook Aim Shot Tuner", "TeleOp", AimRecipes.ShotTuner.class);
            s.init("Cookbook Aim Shot Tuner");
            assertTrue(String.join("\n", s.screen()), s.screenHas("Odometry : put the robot at x 0, y 0 in"));
            s.start();
            // From (0, 0) to the goal at (13, -13): 18.4 in (averaged over half a second of readings).
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("18.4 in (odometry, "), 2));
            // The speed starts from the POLLEN table at that distance (before its first point: 2200).
            assertTrue(String.join("\n", s.screen()), s.screenHas("Speed : 2200 RPM"));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Points : NECTAR 0, POLLEN 0"));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Table : POLLEN"));
            s.press(v -> s.g1().x = v);                                   // no shots marked: it only warns...
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Press X again to save the speed now anyway"), 1));
            assertTrue(!s.screenHas("speedByDistancePollen"));
            s.press(v -> s.g1().x = v);                                   // ...X again: saved (POLLEN)
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas(
                    "public static double[][] speedByDistancePollen = {{18.4, 2200}};   // in Tunables.Launcher"), 1));
            // Y until "> Table" (SPEED -> TABLE: no hood, no hand-set distance), then the other table.
            s.press(v -> s.g1().y = v);
            s.press(v -> s.g1().dpad_right = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Table : NECTAR"), 1));
            // The NECTAR table's own speed at that distance (2400), then save.
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Speed : 2400 RPM"), 1));
            s.press(v -> s.g1().x = v);
            s.press(v -> s.g1().x = v);                                   // twice: no shots marked here either
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas(
                    "public static double[][] speedByDistanceNectar = {{18.4, 2400}};   // in Tunables.Launcher"), 1));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Points : NECTAR 1, POLLEN 1"));
            assertTrue("the POLLEN line stays", s.screenHas("speedByDistancePollen"));
            assertEquals("", s.error());
        }
    }
}
