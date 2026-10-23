package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.examples.cookbook.LightRecipes;
import com.crossbotics.ftchelper.robot.LightColor;
import com.qualcomm.hardware.rev.RevBlinkinLedDriver;
import com.qualcomm.robotcore.hardware.Servo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The Lights module in the simulator: the light's color follows the rules, flashes, and reaches the page. */
public class SimLightsTest {
    static {
        CookbookSamples.fill();   // the cookbook writes MEASURE: sample numbers in their place
    }

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private Path robotFile() throws Exception {
        SimFromRobot.Result res = SimFromRobot.merge(null, new LightRecipes.Robot().simLines(),
                LightRecipes.Robot.class.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        assertTrue(res.text, res.text.contains("light.light = gobilda"));
        Path file = tmp.newFile("lights.properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test public void aScenarioCanCheckWhatALightShows() throws Exception {
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        Path scenario = tmp.newFile("light.sim").toPath();
        Files.write(scenario, java.util.Arrays.asList(
                "init \"Cookbook Lights\"",
                "press g1.b                          # RED",
                "expect within 2 light light = RED",
                "expect light light = GREEN          # fails: it is RED",
                "expect servo light target = 0.5    # a light is not a servo: fails with a hint"), StandardCharsets.UTF_8);
        try (SimRun s = new SimRun(robotFile().toString())) {
            s.sim.ds.register("Cookbook Lights", "TeleOp", LightRecipes.Controls.class);
            SimScenario sc = new SimScenario(s.sim);
            sc.run(scenario);
            String report = String.join("\n", sc.report());
            assertEquals(report, 2, sc.failures());
            assertTrue(report, report.contains("shows RED"));
            assertTrue(report, report.contains("is a light: expect light light = GREEN"));
        }
    }

    @Test public void theRecipeLightShowsTheAllianceFollowsTheIntakeAndFlashes() throws Exception {
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        try (SimRun s = new SimRun(robotFile().toString())) {
            s.sim.ds.register("Cookbook Lights", "TeleOp", LightRecipes.Controls.class);
            s.init("Cookbook Lights");
            SimLight light = s.robot().lights.get("light");
            // INIT, no alliance chosen yet: white blinking, once a second ("choose the alliance").
            assertTrue("INIT: white until chosen: " + light.words(), SimRun.waitFor(() -> light.words().equals("WHITE"), 2));
            assertTrue("and dark half the time", SimRun.waitFor(() -> light.words().equals("off"), 1.5));
            s.press(v -> s.g1().b = v);                                // RED
            assertTrue("INIT: solid RED once chosen: " + light.words(), SimRun.waitFor(() -> light.words().equals("RED"), 2));
            SimRun.pause(1.2);
            assertEquals("solid: no blinking", "RED", light.words());
            s.start();
            assertEquals("START: the alliance flashes 1 s", "RED", light.words());
            assertTrue("then the rules (none true: dark)", SimRun.waitFor(() -> light.words().equals("off"), 2));
            s.g2().right_trigger = 1;                                  // intake in (hold): PURPLE
            assertTrue(light.words(), SimRun.waitFor(() -> light.words().equals("PURPLE"), 2));
            assertEquals(LightColor.PURPLE.webColor(), light.webColor());
            s.press(v -> s.g2().a = v);                                // a 0.5 s yellow flash
            assertTrue(light.words(), SimRun.waitFor(() -> light.words().equals("YELLOW"), 1));
            assertTrue("then back to the rules", SimRun.waitFor(() -> light.words().equals("PURPLE"), 2));
            s.g2().right_trigger = 0;
            assertTrue(SimRun.waitFor(() -> light.words().equals("off"), 2));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Light : OFF (otherwise)"));
            SimServer server = new SimServer(s.sim, 0, false);
            String json = SimJson.state(s.sim, server);
            server.close();
            assertTrue(json, json.contains("\"lights\":[{\"name\":\"light\""));
            assertEquals("", s.error());
        }
    }

    @Test public void colorsFromTheSignal() throws Exception {
        SimLight g = new SimLight("light", "gobilda");
        assertEquals("off (no signal)", g.words());
        assertEquals("", g.webColor());
        ((Servo) g.device).setPosition(LightColor.GREEN.gobildaPosition());
        assertEquals("GREEN", g.words());
        ((Servo) g.device).setPosition(0.45);                         // 1410 us: between the named colors
        assertEquals("sage", g.words());
        assertEquals("#", g.webColor().substring(0, 1));
        ((Servo) g.device).setPosition(0.9);                          // 2220 us: white
        assertEquals("white", g.words());
        assertEquals("#ffffff", g.webColor());

        SimLight b = new SimLight("blinkin", "blinkin");
        assertTrue(b.words(), b.words().startsWith("no signal"));
        ((RevBlinkinLedDriver) b.device).setPattern(LightColor.YELLOW.blinkinPattern());
        assertEquals("YELLOW", b.words());
        assertEquals(LightColor.YELLOW.webColor(), b.webColor());
        ((RevBlinkinLedDriver) b.device).setPattern(RevBlinkinLedDriver.BlinkinPattern.RAINBOW_RAINBOW_PALETTE);
        assertEquals("RAINBOW_RAINBOW_PALETTE", b.words());
        ((RevBlinkinLedDriver) b.device).setPattern(RevBlinkinLedDriver.BlinkinPattern.BLACK);
        assertEquals("off", b.words());
        assertEquals("", b.webColor());
    }

    @Test(expected = IllegalArgumentException.class)
    public void anUnknownKindStops() {
        new SimLight("x", "neon");
    }
}
