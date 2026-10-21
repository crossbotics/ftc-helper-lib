package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The Characterizer, run whole in the simulator on a robot whose numbers are known: the fit tool must
 * find those numbers again from the logs (a round trip through the same files a real robot writes).
 */
public class SimCharacterizerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private void runTest(SimRun s, int downPresses, double seconds) throws Exception {
        for (int i = 0; i < downPresses; i++) s.press(v -> s.g1().dpad_down = v);
        s.press(v -> s.g1().a = v);
        assertTrue("the test finishes: " + s.sim.ds.screen.screen(),
                SimRun.waitFor(() -> s.screenHas("done") || s.screenHas("Stopped") || s.screenHas("Could not"), seconds));
        assertTrue(String.join("\n", s.sim.ds.screen.screen()), s.screenHas("done"));
        SimRun.pause(0.3);    // the log is saved in the background
    }

    @Test public void fitFindsTheSimulatedRobotsNumbers() throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        String robotText = base
                .replace("motor.lift = lift, part=gobilda-5203-312, min=0, max=2750, gravity=0.06",
                        "motor.lift = lift, part=gobilda-5203-312, min=0, max=2750, gravity=0.06, kS=0.04, accelSec=0.1")
                .replace("motor.flywheel = flywheel, part=gobilda-5203-6000, feeder=feeder, shotDrop=0.15",
                        "motor.flywheel = flywheel, part=gobilda-5203-6000, feeder=feeder, shotDrop=0.15, kS=0.02, accelSec=0.5, coastSec=1.8")
                + "\ndrive.kS = 0.05\ndrive.accelSec = 0.2\ndrive.turnDegPerCount = 0.08\n";
        assertTrue(robotText.contains("kS=0.04") && robotText.contains("coastSec=1.8"));
        File robotFile = tmp.newFile("robot.properties");
        Files.write(robotFile.toPath(), robotText.getBytes("UTF-8"));
        File logs = new File(System.getProperty("ftchelper.dataDir"), "characterizer");
        if (logs.isDirectory()) for (File f : logs.listFiles()) f.delete();
        try (SimRun s = new SimRun(robotFile.getPath())) {
            s.init("Characterizer").start();
            s.robot().setPose(40, -40, 0);          // open space: 5 ft ahead and behind, room to turn
            runTest(s, 0, 20);     // Drive
            s.robot().setPose(40, -40, 0);
            s.robot().setBattery(12.0);   // a weaker battery: the fit scales the powers by the logged volts
            runTest(s, 1, 20);     // Turn
            s.robot().setBattery(13.2);
            runTest(s, 1, 40);     // Lift/arm lift
            runTest(s, 2, 25);     // Flywheel flywheel (skips the arm)
        }
        List<Path> files = new ArrayList<>();
        for (File f : logs.listFiles()) files.add(f.toPath());
        List<String> report = new ArrayList<>();
        Map<String, Map<String, String>> fit = SimFit.fit(files, report);
        System.out.println(String.join("\n", report));
        Map<String, String> drive = fit.get("");
        assertEquals("drive kS", 0.05, Double.parseDouble(drive.get("drive.kS")), 0.01);
        assertEquals("drive accelSec", 0.2, Double.parseDouble(drive.get("drive.accelSec")), 0.03);
        assertEquals("drive top speed per power", 2796, Double.parseDouble(drive.get("drive.maxTicksPerSec")), 100);
        assertEquals("turning", 0.08, Double.parseDouble(drive.get("drive.turnDegPerCount")), 0.005);
        Map<String, String> lift = fit.get("motor.lift");
        assertEquals("lift kS", 0.04, Double.parseDouble(lift.get("kS")), 0.015);
        assertEquals("lift gravity", 0.06, Double.parseDouble(lift.get("gravity")), 0.015);
        assertEquals("lift accelSec", 0.1, Double.parseDouble(lift.get("accelSec")), 0.03);
        assertEquals(2796, Double.parseDouble(lift.get("maxTicksPerSec")), 150);
        Map<String, String> fly = fit.get("motor.flywheel");
        assertEquals("flywheel kS", 0.02, Double.parseDouble(fly.get("kS")), 0.01);
        assertEquals("flywheel accelSec", 0.5, Double.parseDouble(fly.get("accelSec")), 0.08);
        assertEquals("flywheel coastSec", 1.8, Double.parseDouble(fly.get("coastSec")), 0.3);

        // --write puts them into the robot file, keeping the other settings on the motor's line.
        String written = SimFit.apply(robotText, fit);
        assertTrue(written, written.contains("motor.lift = lift, part=gobilda-5203-312, min=0, max=2750, gravity=" + lift.get("gravity")));
        assertTrue(written.contains("drive.turnDegPerCount = " + drive.get("drive.turnDegPerCount")));
    }

    @Test public void fitToolKeepsWindowsLineEndingsAndNeverAddsADuplicateMotor() {
        java.util.Map<String, java.util.Map<String, String>> fit = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> lift = new java.util.LinkedHashMap<>();
        lift.put("kS", "0.04");
        fit.put("motor.lift", lift);
        java.util.Map<String, String> ghost = new java.util.LinkedHashMap<>();
        ghost.put("kS", "0.1");
        fit.put("motor.ghost", ghost);
        String text = "battery = 12.8\r\nmotor.lift = lift, min=0, max=2750   # the lift\r\n";
        List<String> notes = new ArrayList<>();
        String out = SimFit.apply(text, fit, notes);
        assertEquals("battery = 12.8\r\nmotor.lift = lift, min=0, max=2750, kS=0.04   # the lift\r\n", out);
        assertTrue(notes.toString(), notes.size() == 1 && notes.get(0).contains("motor.ghost"));
    }

    @Test public void holdingStartStopsARunningTest() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Characterizer").start();
            s.robot().setPose(40, -40, 0);
            s.press(v -> s.g1().a = v);                              // Drive test
            assertTrue(SimRun.waitFor(() -> s.robot().wheels.get(0).userPower() > 0.05, 5));
            s.g1().start = true;                                       // START: stops at once
            assertTrue("the wheels stop", SimRun.waitFor(() -> s.robot().wheels.get(0).userPower() == 0, 1));
            SimRun.pause(1.3);                                         // held 1 s: the help page opens
            assertTrue(String.join("\n", s.sim.ds.screen.screen()), s.screenHas("help"));
            s.g1().start = false;
        }
    }
}
