package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The first run of a launcher with no numbers in Tunables.java: the starting gains and speed from the
 * parts catalog (not the hub's built-in gains, which have no F) get the simulated wheel to its speed,
 * and "ready", within a couple of seconds.
 */
public class SimFirstRunLauncherTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A Tunables.java with nothing for the launcher. */
    public static class NoLauncherNumbers {
        public static class Auto {
            public static double parkInches = 24;
        }
    }

    public static class Bot extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            tunables(NoLauncherNumbers.class);
            drive(Drive.tank("left", "right"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
        }
    }

    @Test public void aLauncherWithNoNumbersReachesItsStartingSpeed() throws Exception {
        SimFromRobot.Result res = SimFromRobot.merge(null, new Bot().simLines(), Bot.class.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile("bot.properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        SimRobot robot = SimRobot.load(file);
        Bot bot = new Bot();
        bot.setup(robot.hardwareMap());
        assertTrue(bot.notes().toString(), bot.notes().toString().contains(
                "Launcher: starting values from the parts catalog (kF 11.7, kP 1.17, kI 0.117, speed 3600 rpm)"));
        bot.launcher.spinUp();
        // Simulated time (10 ms loops). "Ready" itself waits real time (the flywheel's settle time), so
        // the test watches the speed: within the 60 RPM ready band.
        double inBandAt = Double.NaN;
        for (int i = 0; i < 400; i++) {                  // 4 s
            bot.all().update();
            robot.step(0.01);
            double rpm = bot.launcher.part().getFlywheel().getRpm();
            if (Double.isNaN(inBandAt) && Math.abs(rpm - 3600) < 60) inBandAt = i * 0.01;
        }
        assertEquals(3600, bot.launcher.targetRpm(), 1e-9);
        assertTrue("near its speed within 3 s: " + inBandAt, inBandAt < 3);
        assertEquals("and stays there", 3600, bot.launcher.part().getFlywheel().getRpm(), 60);
        bot.all().stop();
    }
}
