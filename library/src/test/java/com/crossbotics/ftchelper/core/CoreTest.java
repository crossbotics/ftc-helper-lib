package com.crossbotics.ftchelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Before public void useTempSettings() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        Profile.resetForTests();
    }

    @After public void back() {
        SettingsFile.useForTests(null);
        Profile.resetForTests();
    }

    @Test public void subsystemsUpdateInOrderAndStopEvenIfOneFails() {
        List<String> log = new ArrayList<>();
        Subsystem a = new Subsystem() {
            public void update() { log.add("a"); }
            public void stop() { throw new RuntimeException("broken"); }
        };
        Subsystem b = new Subsystem() {
            public void update() { log.add("b"); }
            public void stop() { log.add("b stopped"); }
        };
        Subsystems group = new Subsystems().add(a, null, b);
        group.update();
        assertEquals(Arrays.asList("a", "b"), log);
        try {
            group.stop();
            fail("the first error should come through");
        } catch (RuntimeException expected) {
            assertTrue(log.contains("b stopped"));   // b was still stopped
        }
        assertEquals(2, group.all().size());
    }

    @Test public void settingsSaveAndLoad() {
        File f = new File(tmp.getRoot(), "s/settings.properties");
        SettingsFile s = new SettingsFile(f);
        assertEquals("x", s.get("a", "x"));
        s.put("a", "hello");
        s.put("n", "1.5");
        assertTrue(s.save());
        SettingsFile again = new SettingsFile(f);
        assertEquals("hello", again.get("a", "x"));
        assertEquals(1.5, again.getDouble("n", 0), 1e-12);
        assertEquals(7, again.getDouble("missing", 7), 1e-12);
        again.put("n", "not a number");
        assertEquals(3, again.getDouble("n", 3), 1e-12);
    }

    @Test public void profileDefaultsToCompetitionAndIsSaved() {
        assertEquals(Profile.COMPETITION, Profile.current());
        assertEquals(2.0, Profile.pick(1.0, 2.0), 1e-12);
        Profile.set(Profile.PRACTICE);
        Profile.resetForTests();                       // as if the app restarted
        assertEquals(Profile.PRACTICE, Profile.current());
        assertEquals("p", Profile.pick("p", "c"));
        assertTrue(Profile.pick(true, false));
    }

    @Test public void telemetryLevelsAndWarningsFirst() {
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        t.section("Lift").line("Height", "HIGH").debug("Power", "%.2f", 0.5).warn("Battery low");
        t.update();
        assertEquals(Arrays.asList("!! Battery low", "--- Lift ---", "Height : HIGH"), ft.lastScreen);
        t.setDebug(true);
        t.line("A", 1).debug("B", 2).warn("x").warn("x");
        t.update();
        assertEquals(Arrays.asList("!! x", "A : 1", "B : 2"), ft.lastScreen);
    }

    @Test public void telemetryRateLimit() {
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        long[] now = {0};
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> now[0]).setMinIntervalMs(100);
        assertTrue(t.line("a", 1).update());
        now[0] += 50_000_000L;
        assertFalse(t.line("a", 2).update());
        now[0] += 60_000_000L;
        assertTrue(t.line("a", 3).update());
        assertEquals(2, ft.updates);
        assertEquals(Arrays.asList("a : 3"), ft.lastScreen);
    }

    @Test public void aWarningInASkippedLoopIsSentNextTime() {
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        long[] now = {0};
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> now[0]).setMinIntervalMs(100);
        assertTrue(t.line("a", 1).update());
        now[0] += 50_000_000L;
        assertFalse(t.warn("Jam").update());                     // skipped loop: the warning waits
        now[0] += 60_000_000L;
        assertTrue(t.line("a", 3).update());
        assertEquals(Arrays.asList("!! Jam", "a : 3"), ft.lastScreen);
    }

    @Test public void aSlowUpdateIsReported() {
        Subsystem slow = new Subsystem() {
            public void update() {
                long end = System.nanoTime() + 40_000_000L;   // 40 ms of busy work (like a hidden wait)
                while (System.nanoTime() < end) { }
            }
            public String name() { return "Slowpoke"; }
        };
        Subsystems group = new Subsystems().add(slow);
        group.update();
        assertTrue(group.getSlowUpdateWarning().startsWith("Slowpoke update took"));
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        group.addTelemetry(t);
        t.update();
        assertTrue(ft.lastScreen.get(0).startsWith("!! Slowpoke update took"));
    }

    @Test public void aSaveLeavesNoTempFileAndATempFileIsReadIfItIsAllThereIs() throws Exception {
        File f = new File(tmp.getRoot(), "safe/settings.properties");
        SettingsFile s = new SettingsFile(f);
        s.put("a", "1");
        assertTrue(s.save());
        s.put("a", "2");
        assertTrue(s.save());
        assertEquals("2", new SettingsFile(f).get("a", "x"));
        assertFalse("the temp file is swapped in", new File(f.getPath() + ".tmp").exists());
        // a save whose swap failed left only the new file as .tmp: it is read
        assertTrue(f.renameTo(new File(f.getPath() + ".tmp")));
        assertEquals("2", new SettingsFile(f).get("a", "x"));
    }

    @Test public void settingsSaveInTheBackground() throws Exception {
        File f = new File(tmp.getRoot(), "bg.properties");
        SettingsFile s = new SettingsFile(f);
        s.put("a", "1");
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        boolean[] ok = {false};
        s.saveInBackground(result -> { ok[0] = result; done.countDown(); });
        assertTrue(done.await(2, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(ok[0]);
        assertEquals("1", new SettingsFile(f).get("a", "x"));
    }
}
