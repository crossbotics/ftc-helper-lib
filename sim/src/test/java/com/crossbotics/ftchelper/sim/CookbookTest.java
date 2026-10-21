package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.examples.cookbook.ActionRecipes;
import com.crossbotics.ftchelper.examples.cookbook.AutonomousRecipes;
import com.crossbotics.ftchelper.examples.cookbook.ButtonRecipes;
import com.crossbotics.ftchelper.examples.cookbook.DrivingRecipes;
import com.crossbotics.ftchelper.examples.cookbook.MechanismRecipes;
import com.crossbotics.ftchelper.examples.cookbook.OdometryRecipes;
import com.crossbotics.ftchelper.examples.cookbook.PedroRecipes;
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
            PedroRecipes.class, TelemetryRecipes.class);

    // The whole files (one class per file, as in TeamCode): the robot, its TeleOp, an autonomous, the finders.
    private static final List<Class<?>> FILES = Arrays.asList(
            com.crossbotics.ftchelper.examples.cookbook.Robot.class,
            com.crossbotics.ftchelper.examples.cookbook.Controls.class,
            com.crossbotics.ftchelper.examples.cookbook.LeaveTheStart.class,
            com.crossbotics.ftchelper.examples.cookbook.ServoFinder.class,
            com.crossbotics.ftchelper.examples.cookbook.MotorFinder.class,
            com.crossbotics.ftchelper.examples.cookbook.DriveTuner.class,
            com.crossbotics.ftchelper.examples.cookbook.OdometryTuner.class,
            com.crossbotics.ftchelper.examples.cookbook.ShotTuner.class);

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
}
