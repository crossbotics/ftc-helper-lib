package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.examples.flywheelbot.Robot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The robot file made from Robot.java when the simulator starts (./gradlew :sim:run and :sim:scenario call
 * SimFromRobot.auto first): made when missing, the team's own lines kept, nothing done with -PnoFromRobot
 * (sim.fromRobot empty), and the file left as it is when it and the code disagree.
 */
public class SimFromRobotAutoTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final Path starter = java.nio.file.Paths.get("robots/starter.properties");

    @After public void clear() {
        System.clearProperty("sim.fromRobot");
        System.clearProperty("sim.robotClass");
        System.clearProperty("sim.teamCode");
    }

    private Path robotFile() throws Exception {
        Path file = tmp.newFolder("project", "simulator").toPath().resolve("robot.properties");
        System.setProperty("sim.fromRobot", file.toString());
        System.setProperty("sim.robotClass", Robot.class.getName());
        return file;
    }

    private static String read(Path f) throws Exception {
        return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
    }

    @Test public void theSimulatorMakesTheRobotFileWhenItStarts() throws Exception {
        Path file = robotFile();
        assertEquals("the made file is the one to use", file, SimFromRobot.auto(starter));
        String text = read(file);
        assertTrue(text, text.contains("drive.motors = left_drive, right_drive   " + SimFromRobot.MARK));
        assertTrue(text, text.startsWith("# The simulator's robot, made from " + Robot.class.getName()));
    }

    @Test public void noFromRobotLeavesEverythingAsItIs() throws Exception {
        Path file = robotFile();
        System.setProperty("sim.fromRobot", "");   // -PnoFromRobot (or -Probot=...)
        assertEquals(starter, SimFromRobot.auto(starter));
        assertFalse(Files.exists(file));
    }

    @Test public void theTeamsOwnLinesAreKeptEachStart() throws Exception {
        Path file = robotFile();
        SimFromRobot.auto(starter);
        String edited = read(file).replace("battery = 12.8", "battery = 13.1")
                + "shape.bumper = box, at=0,0,1, size=18x18x2   # measured\n";
        Files.write(file, edited.getBytes(StandardCharsets.UTF_8));
        assertEquals(file, SimFromRobot.auto(starter));
        String again = read(file);
        assertEquals("nothing else changes when the code is the same", edited, again);
        assertTrue(again, again.contains("battery = 13.1"));
        assertTrue(again, again.contains("shape.bumper = box, at=0,0,1, size=18x18x2   # measured"));
    }

    @Test public void aFileThatDisagreesWithTheCodeIsUsedAsItIs() throws Exception {
        Path file = robotFile();
        SimFromRobot.auto(starter);
        String clash = read(file) + "drive.motors = fl, fr, bl, br\n";   // the team's own line: other drive motors
        Files.write(file, clash.getBytes(StandardCharsets.UTF_8));
        assertEquals(file, SimFromRobot.auto(starter));
        assertEquals("nothing written", clash, read(file));
    }

    @Test public void aHandWrittenFileIsNotChanged() throws Exception {
        Path file = robotFile();
        String own = "battery = 12.8\ndrive.type = mecanum\n";   // written by hand: no "made from" line
        Files.write(file, own.getBytes(StandardCharsets.UTF_8));
        assertEquals(file, SimFromRobot.auto(starter));
        assertEquals("used as it is", own, read(file));
    }

    @Test public void aChangedMarkedLineIsReplacedAndTheOldFileKept() throws Exception {
        Path file = robotFile();
        SimFromRobot.auto(starter);
        String made = read(file);
        String edited = made.replace("drive.motors = left_drive, right_drive   " + SimFromRobot.MARK,
                "drive.motors = a, b   " + SimFromRobot.MARK);
        Files.write(file, edited.getBytes(StandardCharsets.UTF_8));
        SimFromRobot.auto(starter);
        assertEquals("the code's line is back", made, read(file));
        assertEquals("the old file is kept", edited, read(SimFromRobot.backup(file)));
    }

    @Test public void theRobotClassIsFoundInTeamCode() throws Exception {
        Path src = tmp.newFolder("TeamCode", "src", "main", "java", "a").toPath();
        Files.write(src.resolve("Other.java"), "package a;\nclass Other extends RobotDefinition {}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(src.resolve("Robot.java"), "package com.crossbotics.ftchelper.examples.gobildabot;\n\npublic class Robot extends RobotDefinition {}\n"
                .getBytes(StandardCharsets.UTF_8));
        Files.write(src.resolve("Notes.java"), "package a;\nclass Notes {}\n".getBytes(StandardCharsets.UTF_8));
        String teamCode = tmp.getRoot().toPath().resolve("TeamCode").toString();
        assertEquals("Robot.java first; only classes that extend RobotDefinition",
                Arrays.asList("com.crossbotics.ftchelper.examples.gobildabot.Robot", "a.Other"), SimFromRobot.robotSources(teamCode));
        // the class the robot file was made from comes first
        Path file = tmp.newFile("made.properties").toPath();
        Files.write(file, ("# The simulator's robot, made from " + Robot.class.getName() + " (again at each start of the simulator).\n")
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(Robot.class.getName(), SimFromRobot.robotClassFor(file, "", teamCode));
        assertEquals("-ProbotClass wins", "x.Given", SimFromRobot.robotClassFor(file, "x.Given", teamCode));
    }
}
