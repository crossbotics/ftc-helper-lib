package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import com.qualcomm.robotcore.hardware.DistanceSensor;
import com.qualcomm.robotcore.hardware.NormalizedColorSensor;
import com.qualcomm.robotcore.hardware.NormalizedRGBA;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Pieces and PieceSensor: telling kinds apart, counting in and out, the next piece, the hooks. */
public class PiecesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
    }

    @After public void back() {
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    /** A pretend colour+distance sensor (like a REV Color Sensor V3): set rgb and mm. */
    static final class FakeColor {
        float r, g, b;
        double mm = 120;
        final Object device = Proxy.newProxyInstance(NormalizedColorSensor.class.getClassLoader(),
                new Class<?>[] {NormalizedColorSensor.class, DistanceSensor.class}, (proxy, m, a) -> {
                    switch (m.getName()) {
                        case "getNormalizedColors":
                            NormalizedRGBA c = new NormalizedRGBA();
                            c.red = r; c.green = g; c.blue = b; c.alpha = 1;
                            return c;
                        case "getDistance": return ((DistanceUnit) a[0]).fromMm(mm);
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        default: return defaultFor(m.getReturnType());
                    }
                });

        void show(float r, float g, float b, double mm) {
            this.r = r; this.g = g; this.b = b; this.mm = mm;
        }

        void yellow() { show(0.30f, 0.25f, 0.05f, 15); }     // hue 48
        void red() { show(0.30f, 0.05f, 0.04f, 15); }        // hue 2
        void blue() { show(0.04f, 0.10f, 0.30f, 15); }       // hue 225
        void empty() { show(0.02f, 0.02f, 0.02f, 120); }
    }

    static Object defaultFor(Class<?> r) {
        if (r == boolean.class) return false;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == double.class) return 0.0;
        if (r == float.class) return 0f;
        return null;
    }

    /** The test kit's motors and servos, plus pretend colour and distance sensors (no webcams). */
    static final class Kit implements Devices {
        final RobotTestKit kit = new RobotTestKit();
        final Map<String, FakeColor> colors = new HashMap<>();
        final Map<String, double[]> distances = new HashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Class<? extends T> type, String name) {
            if (type == NormalizedColorSensor.class) {
                if (!colors.containsKey(name)) colors.put(name, new FakeColor());
                return (T) colors.get(name).device;
            }
            if (type == DistanceSensor.class) {
                final double[] mm = distances.computeIfAbsent(name, k -> new double[] {150});
                return (T) Proxy.newProxyInstance(DistanceSensor.class.getClassLoader(), new Class<?>[] {DistanceSensor.class},
                        (proxy, m, a) -> m.getName().equals("getDistance") ? (Object) ((DistanceUnit) a[0]).fromMm(mm[0])
                                : defaultFor(m.getReturnType()));
            }
            return kit.get(type, name);
        }

        @Override
        public <T> T tryGet(Class<? extends T> type, String name) {
            if (type == WebcamName.class) return null;
            return get(type, name);
        }
    }

    /** Tunables a team would have after the Piece Tuner. */
    public static class Numbers {
        public static class IntakeColor {
            public static double POLLEN = 50, RED_NECTAR = 5, BLUE_NECTAR = 222;
            public static double nearMm = 40, steadyMs = 30;
        }
        public static class ReadyColor {
            public static double POLLEN = 50, RED_NECTAR = 5, BLUE_NECTAR = 222;
            public static double steadyMs = 0;
        }
        public static class SizeSensor {
            public static double POLLEN = 79, NECTAR = 59;   // 150 mm channel: 150 - 71, 150 - 91
            public static double nearMm = 120, steadyMs = 0;
        }
    }

    static final class Bot extends RobotDefinition {
        Pieces pieces;
        Intake intake;
        Indexer indexer;
        boolean withReady = true;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("l", "r"));
            intake = add(Intake.motor("intake").stopsWhen(() -> pieces.isFull()));
            indexer = add(Indexer.crServo("indexer"));
            Pieces p = Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
                    .comingIn(PieceSensor.color("intake_color")).max(4).startsWith(1, "POLLEN");
            if (withReady) p.readyToFire(PieceSensor.color("ready_color"));
            pieces = add(p);
        }
    }

    // A clock the test moves on by hand (nanoseconds).
    private final long[] now = {0};

    private Bot setup(Kit kit, boolean withReady) {
        Bot b = new Bot();
        b.withReady = withReady;
        b.setup(kit);
        b.pieces.intakeSensor().clock = () -> now[0];
        if (b.pieces.readySensor() != null) b.pieces.readySensor().clock = () -> now[0];
        b.indexer.clock = () -> now[0];
        return b;
    }

    // Runs the robot's loop for ms milliseconds, 10 ms a loop.
    private void run(Bot b, int ms) {
        for (int i = 0; i < ms / 10; i++) {
            now[0] += 10_000_000L;
            b.all().update();
        }
    }

    @Test public void theColourTellsTheKindsApartAndTheDistanceSaysIfOneIsThere() {
        Kit kit = new Kit();
        Bot b = setup(kit, false);
        FakeColor in = kit.colors.get("intake_color");
        PieceSensor s = b.pieces.intakeSensor();
        in.empty();
        run(b, 50);
        assertEquals(Pieces.NONE, s.kind());
        in.red();
        run(b, 50);
        assertEquals("hue 2: RED_NECTAR (5)", "RED_NECTAR", s.kind());
        in.show(0.30f, 0.04f, 0.06f, 15);   // hue 355: still red, the short way round the wheel
        run(b, 50);
        assertEquals("RED_NECTAR", s.kind());
        in.blue();
        run(b, 50);
        assertEquals("BLUE_NECTAR", s.kind());
        in.show(0.05f, 0.30f, 0.05f, 15);   // green: no kind is near
        run(b, 50);
        assertEquals(Pieces.UNKNOWN, s.kind());
        in.yellow();
        in.mm = 80;                          // a yellow thing far away: not in the spot
        run(b, 50);
        assertEquals(Pieces.NONE, s.kind());
    }

    @Test public void aFlickerShorterThanSteadyMsDoesNotCount() {
        Kit kit = new Kit();
        Bot b = setup(kit, false);
        FakeColor in = kit.colors.get("intake_color");
        in.empty();
        run(b, 50);
        in.yellow();
        run(b, 20);                          // 20 ms < steadyMs 30
        in.empty();
        run(b, 50);
        assertEquals("only the preload", 1, b.pieces.count());
    }

    @Test public void piecesComingInCountUpAndEachFeedCountsOneOut() {
        Kit kit = new Kit();
        Bot b = setup(kit, false);
        FakeColor in = kit.colors.get("intake_color");
        assertEquals("the preload", 1, b.pieces.count());
        assertEquals("POLLEN", b.pieces.next());
        in.red(); run(b, 60); in.empty(); run(b, 60);
        in.yellow(); run(b, 60);
        in.blue(); run(b, 60);               // a second piece right behind it, another kind: counts too
        in.empty(); run(b, 60);
        assertEquals(4, b.pieces.count());
        assertEquals("[POLLEN, RED_NECTAR, POLLEN, BLUE_NECTAR]", b.pieces.held().toString());
        assertTrue(b.pieces.isFull());
        assertTrue(b.pieces.whenFull().getAsBoolean());
        assertEquals(2, b.pieces.count("POLLEN"));

        b.indexer.feedOne();                 // one goes out
        b.indexer.feedOne();                 // ignored: still feeding (not a second piece)
        assertEquals(3, b.pieces.count());
        assertEquals("RED_NECTAR", b.pieces.next());
        assertTrue(b.pieces.whenNextIs("RED_NECTAR").getAsBoolean());
        assertEquals("RED_NECTAR", b.pieces.nextKind().get());
        assertFalse(b.pieces.isFull());
        run(b, 500);                         // the feed ends (feedSec 0.4)
        for (int i = 0; i < 3; i++) { b.indexer.feedOne(); run(b, 500); }
        assertTrue(b.pieces.isEmpty());
        assertEquals(Pieces.NONE, b.pieces.next());
        b.indexer.feedOne();                 // a feed with nothing in: still 0
        assertEquals(0, b.pieces.count());
    }

    @Test public void theIntakeIsHeldBackWhenFull() {
        Kit kit = new Kit();
        Bot b = setup(kit, false);
        b.pieces.set("POLLEN", "POLLEN", "POLLEN");
        b.intake.in();
        run(b, 20);
        assertEquals(1.0, kit.kit.motors.get("intake").power, 1e-9);
        b.pieces.added("POLLEN");
        run(b, 20);
        assertTrue(b.intake.isHeldBack());
        assertEquals("full: it stops pulling in", 0.0, kit.kit.motors.get("intake").power, 1e-9);
        b.intake.out();
        run(b, 20);
        assertTrue("out still works", kit.kit.motors.get("intake").power < 0);
        b.intake.in();
        b.pieces.removed();
        run(b, 20);
        assertEquals("room again: runs in by itself", 1.0, kit.kit.motors.get("intake").power, 1e-9);
    }

    @Test public void theReadySensorKnowsTheNextPieceBest() {
        Kit kit = new Kit();
        Bot b = setup(kit, true);
        FakeColor ready = kit.colors.get("ready_color");
        ready.empty();
        b.pieces.set("POLLEN", "BLUE_NECTAR");
        ready.red();                         // the count had it wrong: a red one is in the slot
        run(b, 20);
        assertEquals("RED_NECTAR", b.pieces.next());
        assertEquals("[RED_NECTAR, BLUE_NECTAR]", b.pieces.held().toString());
        b.indexer.feedOne();                 // it fires; the sensor still sees it for a moment
        run(b, 20);
        assertEquals("not counted again", 1, b.pieces.count());
        assertEquals("BLUE_NECTAR", b.pieces.next());
        ready.empty();
        run(b, 20);
        ready.blue();
        run(b, 20);
        assertEquals("BLUE_NECTAR", b.pieces.next());
        b.pieces.clear();
        ready.yellow();                      // loaded by hand, never counted
        run(b, 20);
        assertEquals(1, b.pieces.count());
        assertEquals("POLLEN", b.pieces.next());
    }

    @Test public void aDistanceSensorTellsSizes() {
        final Kit kit = new Kit();
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(Numbers.class);
                drive(Drive.tank("l", "r"));
                add(PieceSensor.distance("size_sensor").kinds("POLLEN", "NECTAR"));
            }
        };
        r.setup(kit);
        PieceSensor s = (PieceSensor) r.modules().get(0);
        double[] mm = kit.distances.get("size_sensor");
        String[] seen = new String[4];
        double[] readings = {150, 80, 57, 68};
        for (int i = 0; i < 4; i++) {
            mm[0] = readings[i];
            r.all().update();
            seen[i] = s.kind();
        }
        assertEquals("NONE POLLEN NECTAR UNKNOWN", String.join(" ", seen));
        assertTrue(s.measuresSize());
        mm[0] = DistanceSensor.distanceOutOfRange;
        r.all().update();
        assertEquals("out of range: nothing there", Pieces.NONE, s.kind());
    }

    @Test public void untunedKindsAreUnknownAndSaySoAtInit() {
        Kit kit = new Kit();
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(Numbers.class);
                drive(Drive.tank("l", "r"));
                add(PieceSensor.color("new_color").kinds("POLLEN"));
            }
        };
        r.setup(kit);
        kit.colors.get("new_color").yellow();
        PieceSensor s = (PieceSensor) r.modules().get(0);
        s.clock = () -> now[0];
        r.all().update();
        now[0] += 100_000_000L;
        r.all().update();
        assertEquals(Pieces.UNKNOWN, s.kind());
        assertTrue(r.notes().toString(), r.notes().toString().contains("Tunables.NewColor: POLLEN missing"));
    }

    @Test public void aMissingCameraSeesNothingAndSaysWhy() {
        Kit kit = new Kit();
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(PieceSensor.camera("Webcam 2").kinds("POLLEN").box(-0.2, 0.2, 0.2, -0.2));
            }
        };
        r.setup(kit);
        r.all().update();
        PieceSensor s = (PieceSensor) r.modules().get(0);
        assertEquals(Pieces.NONE, s.kind());
        assertTrue(s.problem(), s.problem().contains("no webcam called \"Webcam 2\""));
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        s.addTelemetry(t);
        t.update();
        assertTrue(String.join("\n", ft.lastScreen).contains("no webcam"));
        r.all().stop();
    }

    @Test public void kindNamesAreChecked() {
        for (String bad : new String[] {"red nectar", "NONE", "1POLLEN"}) {
            try {
                Pieces.kinds(bad);
                fail(bad);
            } catch (IllegalArgumentException expected) {
                // the message says why
            }
        }
        try {
            Pieces.kinds("POLLEN").startsWith(2, "Pollen");
            fail("capitals count");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("capitals"));
        }
    }

    @Test public void hueMath() {
        assertEquals(20, PieceSensor.hueDiff(350, 10), 1e-9);
        assertEquals(60, PieceSensor.hueSat(1, 1, 0)[0], 1e-9);
        assertEquals(240, PieceSensor.hueSat(0, 0, 1)[0], 1e-9);
        assertTrue(Double.isNaN(PieceSensor.hueSat(0.5, 0.5, 0.5)[0]));
    }

    @Test public void theSimulatorLinesSayWhichSpot() {
        Bot b = new Bot();
        String lines = String.join("\n", b.simLines());
        assertTrue(lines, lines.contains("color.intake_color = at=intake"));
        assertTrue(lines, lines.contains("color.ready_color = at=ready"));
    }
}
