package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Every example robot INITs with no error: its Robot.java is set up (as at INIT) on the simulated robot
 * made from it. Most of all, INIT stops on a Tunables field that no part reads (a typo), so a leftover or
 * misspelled field in an example's Tunables.java fails here. INIT must also be clean: no misnamed-section
 * warning; the first-run notes (starting values from the parts catalog) are allowed.
 */
public class ExampleRobotsInitTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // One Robot.java per example robot (docs/templates.md lists them), and the cookbook's.
    private static final List<Class<? extends RobotDefinition>> ROBOTS = Arrays.asList(
            com.crossbotics.ftchelper.examples.gobildabot.Robot.class,
            com.crossbotics.ftchelper.examples.revbot.Robot.class,
            com.crossbotics.ftchelper.examples.andymarkbot.Robot.class,
            com.crossbotics.ftchelper.examples.studicabot.Robot.class,
            com.crossbotics.ftchelper.examples.lessonbot.Robot.class,
            com.crossbotics.ftchelper.examples.flywheelbot.Robot.class,
            com.crossbotics.ftchelper.examples.turretbot.Robot.class,
            com.crossbotics.ftchelper.examples.v2bot.Robot.class,
            com.crossbotics.ftchelper.examples.odometrybot.Robot.class,
            com.crossbotics.ftchelper.examples.pedrobot.Robot.class,
            com.crossbotics.ftchelper.examples.quickstart.Robot.class,
            com.crossbotics.ftchelper.examples.cookbook.Robot.class);

    @Test public void everyExampleRobotInitsWithNoError() throws Exception {
        StringBuilder failed = new StringBuilder();
        for (Class<? extends RobotDefinition> c : ROBOTS) {
            try {
                RobotDefinition r = c.getDeclaredConstructor().newInstance();
                SimFromRobot.Result res = SimFromRobot.merge(null, r.simLines(), c.getName(), "biobuzz-2026");
                assertTrue(c.getName() + ": " + res.problems, res.problems.isEmpty());
                Path file = tmp.newFile(c.getPackage().getName() + ".properties").toPath();
                Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
                SimRobot robot = SimRobot.load(file);
                // INIT, as the OpMode does: a new definition, set up on the simulated hardware.
                RobotDefinition fresh = c.getDeclaredConstructor().newInstance();
                fresh.setup(robot.hardwareMap());
                // INIT is clean: no warning about a misnamed Tunables section. Notes are fine, among them
                // the intended first-run ones ("starting values from the parts catalog", positions that
                // are library guesses): an example may leave numbers out to show the first run.
                // (Not the cookbook: its Tunables.java holds a section for every recipe, also parts its
                // Robot.java does not have.)
                if (c != com.crossbotics.ftchelper.examples.cookbook.Robot.class) {
                    assertTrue(c.getName() + ": " + fresh.sectionWarnings(), fresh.sectionWarnings().isEmpty());
                }
                fresh.all().stop();
            } catch (Throwable e) {
                failed.append("\n").append(c.getName()).append(": ").append(e);
            }
        }
        if (failed.length() > 0) fail("These example robots stop at INIT:" + failed);
    }
}
