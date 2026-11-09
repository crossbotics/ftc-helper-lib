package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * examples/.../allbuttons/Controls.java writes out every pre-made button (the library's and the parts')
 * and frees the rest. It must make the same button map as a Controls.java with no buttons of its own:
 * when a part's or the library's defaults change, this test fails, so the example never drifts.
 */
public class AllButtonsTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // Each example robot's Controls.java writes out its parts' buttons (useStandardButtons() false). Its twin is
    // the same file with the parts' own buttons switched back on: a part button the example forgot shows up in
    // the twin's map only. (A written-out line replaces the part's own, so the words were copied from the parts'
    // own map; allbuttons/Controls.java, checked above against the plain defaults, shows they match.)
    public static class Twin_gobildabot extends com.crossbotics.ftchelper.examples.gobildabot.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }
    public static class Twin_gobildastraferbot extends com.crossbotics.ftchelper.examples.gobildastraferbot.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }
    public static class Twin_revbot extends com.crossbotics.ftchelper.examples.revbot.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }
    public static class Twin_andymarkbot extends com.crossbotics.ftchelper.examples.andymarkbot.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }
    public static class Twin_studicabot extends com.crossbotics.ftchelper.examples.studicabot.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }
    public static class Twin_lessonbot extends com.crossbotics.ftchelper.examples.lessonbot.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }
    public static class Twin_flywheelbot extends com.crossbotics.ftchelper.examples.flywheelbot.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }
    public static class Twin_turretbot extends com.crossbotics.ftchelper.examples.turretbot.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }
    public static class Twin_v2bot extends com.crossbotics.ftchelper.examples.v2bot.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }
    public static class Twin_odometrybot extends com.crossbotics.ftchelper.examples.odometrybot.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }
    public static class Twin_quickstart extends com.crossbotics.ftchelper.examples.quickstart.Controls {
        @Override protected boolean useStandardButtons() { return true; }
    }

    private static final Object[][] EXAMPLES = {
            new Object[] {"gobildabot", com.crossbotics.ftchelper.examples.gobildabot.Robot.class, com.crossbotics.ftchelper.examples.gobildabot.Controls.class, Twin_gobildabot.class},
            new Object[] {"gobildastraferbot", com.crossbotics.ftchelper.examples.gobildastraferbot.Robot.class, com.crossbotics.ftchelper.examples.gobildastraferbot.Controls.class, Twin_gobildastraferbot.class},
            new Object[] {"revbot", com.crossbotics.ftchelper.examples.revbot.Robot.class, com.crossbotics.ftchelper.examples.revbot.Controls.class, Twin_revbot.class},
            new Object[] {"andymarkbot", com.crossbotics.ftchelper.examples.andymarkbot.Robot.class, com.crossbotics.ftchelper.examples.andymarkbot.Controls.class, Twin_andymarkbot.class},
            new Object[] {"studicabot", com.crossbotics.ftchelper.examples.studicabot.Robot.class, com.crossbotics.ftchelper.examples.studicabot.Controls.class, Twin_studicabot.class},
            new Object[] {"lessonbot", com.crossbotics.ftchelper.examples.lessonbot.Robot.class, com.crossbotics.ftchelper.examples.lessonbot.Controls.class, Twin_lessonbot.class},
            new Object[] {"flywheelbot", com.crossbotics.ftchelper.examples.flywheelbot.Robot.class, com.crossbotics.ftchelper.examples.flywheelbot.Controls.class, Twin_flywheelbot.class},
            new Object[] {"turretbot", com.crossbotics.ftchelper.examples.turretbot.Robot.class, com.crossbotics.ftchelper.examples.turretbot.Controls.class, Twin_turretbot.class},
            new Object[] {"v2bot", com.crossbotics.ftchelper.examples.v2bot.Robot.class, com.crossbotics.ftchelper.examples.v2bot.Controls.class, Twin_v2bot.class},
            new Object[] {"odometrybot", com.crossbotics.ftchelper.examples.odometrybot.Robot.class, com.crossbotics.ftchelper.examples.odometrybot.Controls.class, Twin_odometrybot.class},
            new Object[] {"quickstart", com.crossbotics.ftchelper.examples.quickstart.Robot.class, com.crossbotics.ftchelper.examples.quickstart.Controls.class, Twin_quickstart.class}
    };

    /** The twin: the same robot, no buttons of its own (the library and the parts add theirs). */
    @TeleOp(name = "All Buttons Default TeleOp", group = "Test")
    public static class DefaultButtons extends ControlMap<com.crossbotics.ftchelper.examples.allbuttons.Robot> {
        public static boolean oneDriver = false;

        @Override protected boolean useStandardButtons() { return true; }   // the parts add their usual buttons

        @Override
        protected void buttons(com.crossbotics.ftchelper.examples.allbuttons.Robot robot, Pad g1, Pad g2) { }
    }

    // The button map's lines on the INIT screen: "G1 ...", "G2 ...".
    private static List<String> buttonMap(String teleOp) throws Exception {
        return buttonMap("robots/starter.properties", teleOp, "All Buttons Default TeleOp", DefaultButtons.class);
    }

    private static List<String> buttonMap(String robotFile, String teleOp, String extraName, Class<? extends ControlMap<?>> extra)
            throws Exception {
        try (SimRun s = new SimRun(robotFile)) {
            s.sim.ds.register(extraName, "TeleOp", extra);
            s.init(teleOp);
            assertEquals(teleOp + "\n" + String.join("\n", s.screen()), "", s.error());
            List<String> map = new ArrayList<>();
            for (String line : s.screen()) {
                String t = line.trim();
                // A part's own button ends with its name, "[Intake]"; the same button written out has none.
                if (t.startsWith("G1 ") || t.startsWith("G2 ")) map.add(t.replaceAll(" \\[(?!library\\])[A-Za-z0-9]+\\]$", ""));
            }
            return map;
        }
    }

    @Test public void everyPreMadeButtonWrittenOutMakesTheSameButtonMap() throws Exception {
        List<String> written = buttonMap("All Buttons TeleOp");
        List<String> defaults = buttonMap("All Buttons Default TeleOp");
        assertFalse("the default map has lines", defaults.isEmpty());
        assertTrue("it has the parts' buttons: " + defaults, String.join("\n", defaults).contains("RB"));
        assertEquals("allbuttons/Controls.java must match the defaults line for line", defaults, written);
    }

    private Path robotFile(Class<?> robotClass) throws Exception {
        com.crossbotics.ftchelper.robot.RobotDefinition r =
                (com.crossbotics.ftchelper.robot.RobotDefinition) robotClass.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, SimFromRobot.holdsFromPieces(r.simLines(), r), robotClass.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile(robotClass.getPackage().getName() + ".properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** The first robot with the library's buttons left at their defaults (no libraryButtons method). */
    public static class FirstRobotDefaults extends com.crossbotics.ftchelper.examples.firstrobot.Controls {
        @Override
        protected void libraryButtons(com.crossbotics.ftchelper.teleop.LibraryButtons lib, Pad g1, Pad g2) { }
    }

    // firstrobot/Controls.java writes out every library button "as the defaults": the map must be the same.
    @Test public void theFirstRobotsLibraryButtonsAreTheDefaults() throws Exception {
        List<String> written = buttonMap("robots/starter.properties", "First Robot TeleOp", "First Robot Defaults", FirstRobotDefaults.class);
        List<String> defaults = buttonMap("robots/starter.properties", "First Robot Defaults", "First Robot Defaults", FirstRobotDefaults.class);
        assertTrue("it has the library's buttons: " + defaults, String.join("\n", defaults).contains("BACK"));
        assertEquals(defaults, written);
    }

    @SuppressWarnings("unchecked")
    @Test public void everyExampleWritesOutItsPartsButtons() throws Exception {
        StringBuilder failed = new StringBuilder();
        for (Object[] e : EXAMPLES) {
            String file = robotFile((Class<?>) e[1]).toString();
            String name = ((Class<?>) e[2]).getAnnotation(TeleOp.class).name();
            List<String> written = buttonMap(file, name, "Twin " + e[0], (Class<? extends ControlMap<?>>) e[3]);
            List<String> twin = buttonMap(file, "Twin " + e[0], "Twin " + e[0], (Class<? extends ControlMap<?>>) e[3]);
            String print = System.getenv("PRINT_MAPS");                // a file: every example's map, to compare by eye
            if (print != null) Files.write(java.nio.file.Paths.get(print), ("=== " + e[0] + "\n" + String.join("\n", twin) + "\n")
                    .getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            if (!written.equals(twin)) failed.append(e[0]).append(":\n  written: ").append(written).append("\n  twin:    ").append(twin).append('\n');
        }
        assertEquals("every part's button is written out in each example's Controls.java", "", failed.toString());
    }
}
