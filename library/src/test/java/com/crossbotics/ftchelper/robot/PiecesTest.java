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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
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
        // Names that are not in the robot configuration.
        final Set<String> missing = new HashSet<>();

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
            if (type == WebcamName.class || missing.contains(name)) return null;
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

    /** The same, with a ready sensor that waits 30 ms (steadyMs) like a real one. */
    public static class SlowReady {
        public static class IntakeColor {
            public static double POLLEN = 50, RED_NECTAR = 5, BLUE_NECTAR = 222;
            public static double nearMm = 40, steadyMs = 30;
        }
        public static class ReadyColor {
            public static double POLLEN = 50, RED_NECTAR = 5, BLUE_NECTAR = 222;
            public static double nearMm = 40, steadyMs = 30;
        }
    }

    static final class Bot extends RobotDefinition {
        Pieces pieces;
        Intake intake;
        Indexer indexer;
        boolean withReady = true, servoIndexer = false;
        Class<?> numbers = Numbers.class;

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.tank("l", "r"));
            intake = add(Intake.motor("intake").stopsWhen(() -> pieces.isFull()));
            indexer = add(servoIndexer ? Indexer.servo("indexer") : Indexer.crServo("indexer"));
            Pieces p = Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
                    .comingIn(PieceSensor.color("intake_color")).max(4).startsWith(1, "POLLEN");
            if (withReady) p.readyToFire(PieceSensor.color("ready_color"));
            pieces = add(p);
        }
    }

    // A clock the test moves on by hand (nanoseconds).
    private final long[] now = {0};

    private Bot setup(Kit kit, boolean withReady) {
        return setup(kit, withReady, Numbers.class);
    }

    private Bot setup(Kit kit, boolean withReady, Class<?> numbers) {
        Bot b = new Bot();
        b.withReady = withReady;
        b.numbers = numbers;
        b.setup(kit);
        clocks(b);
        return b;
    }

    private void clocks(Bot b) {
        b.pieces.comingInSensor().clock = () -> now[0];
        if (b.pieces.readyToFireSensor() != null) b.pieces.readyToFireSensor().clock = () -> now[0];
        b.indexer.clock = () -> now[0];
        b.intake.clock = () -> now[0];
        b.pieces.clock = () -> now[0];
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
        PieceSensor s = b.pieces.comingInSensor();
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

        b.indexer.feedOne();                 // one is going out
        b.indexer.feedOne();                 // ignored: still feeding (not a second piece)
        assertEquals("counted out only once it has gone", 4, b.pieces.count());
        assertEquals("the next one: the piece after the one going out", "RED_NECTAR", b.pieces.next());
        assertTrue(b.pieces.whenNextIs("RED_NECTAR").getAsBoolean());
        assertEquals("RED_NECTAR", b.pieces.nextKind().get());
        run(b, 500);                         // the feed ends (feedSec 0.4): no ready sensor, so now it counts
        assertEquals(3, b.pieces.count());
        assertEquals("RED_NECTAR", b.pieces.next());
        assertFalse(b.pieces.isFull());
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
        b.pieces.pieceIn("POLLEN");
        run(b, 20);
        assertTrue(b.intake.isHeldBack());
        assertEquals("full: it stops pulling in", 0.0, kit.kit.motors.get("intake").power, 1e-9);
        b.intake.out();
        run(b, 20);
        assertTrue("out still works", kit.kit.motors.get("intake").power < 0);
        b.intake.in();
        b.pieces.pieceOut();
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
        assertEquals("still there: not counted out yet", 2, b.pieces.count());
        assertEquals("BLUE_NECTAR", b.pieces.next());
        ready.empty();                       // it left the slot: counted out now, before the feed ends
        run(b, 20);
        assertEquals(1, b.pieces.count());
        ready.blue();
        run(b, 20);
        assertEquals("BLUE_NECTAR", b.pieces.next());
        run(b, 500);                         // the feed ends: not counted a second time
        assertEquals(1, b.pieces.count());
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
        s.background = Runnable::run;        // the helper thread's reads, at once (a test)
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
    // ----------------------------- review fixes -----------------------------

    private static RobotDefinition sizeRobot() {
        return new RobotDefinition() {
            @Override protected void define() {
                tunables(Numbers.class);
                drive(Drive.tank("l", "r"));
                add(PieceSensor.distance("size_sensor").kinds("POLLEN", "NECTAR"));
            }
        };
    }

    @Test public void theDistanceSensorIsReadOffTheLoopAndOldReadingsAreThrownAway() {
        Kit kit = new Kit();
        RobotDefinition r = sizeRobot();
        r.setup(kit);
        PieceSensor s = (PieceSensor) r.modules().get(0);
        s.clock = () -> now[0];
        List<Runnable> helper = new ArrayList<>();    // the helper thread's queue: runs only when the test says
        s.background = helper::add;
        double[] mm = kit.distances.get("size_sensor");
        mm[0] = 80;
        s.update();
        assertEquals("the loop only asks: one read waiting", 1, helper.size());
        assertFalse("no reading yet", s.isWorking());
        assertEquals(Pieces.NONE, s.kind());
        now[0] += 10_000_000L;
        s.update();
        assertEquals("one read at a time", 1, helper.size());
        helper.remove(0).run();                       // the helper thread reads (it may take a while)
        now[0] += 10_000_000L;
        s.update();
        assertTrue(s.isWorking());
        assertEquals("POLLEN", s.kind());
        int readings = s.readings();
        // A read that takes 0.3 s: by the time it arrives the piece has moved on, so it is thrown away.
        mm[0] = 57;
        helper.remove(0).run();
        now[0] += 300_000_000L;
        s.update();
        assertEquals("too old: not used", readings, s.readings());
        assertEquals("POLLEN", s.kind());
        // No new reading for more than 0.5 s: it sees nothing and says why.
        now[0] += 300_000_000L;
        s.update();
        assertFalse(s.isWorking());
        assertEquals(Pieces.NONE, s.kind());
        assertTrue(s.problem(), s.problem().contains("no new reading"));
        // Readings again: back to work.
        helper.remove(0).run();
        now[0] += 10_000_000L;
        s.update();
        assertTrue(s.isWorking());
        assertEquals("NECTAR", s.kind());
        assertEquals("", s.problem());
    }

    @Test public void theDistanceSensorUsesTheLibraryHelperThreadByDefault() throws Exception {
        Kit kit = new Kit();
        RobotDefinition r = sizeRobot();
        r.setup(kit);
        PieceSensor s = (PieceSensor) r.modules().get(0);
        kit.distances.get("size_sensor")[0] = 57;
        for (int i = 0; i < 200 && !s.isWorking(); i++) {
            s.update();
            Thread.sleep(5);
        }
        assertEquals("NECTAR", s.kind());
        r.all().stop();
    }

    @Test public void missingSensorsWarnAtInitAndSeeNothing() {
        Kit kit = new Kit();
        kit.missing.add("intake_color");
        kit.missing.add("size_sensor");
        Bot b = setup(kit, false);                    // no error: INIT goes on
        RobotDefinition r = sizeRobot();
        r.setup(kit);
        String notes = b.notes() + " " + r.notes();
        assertTrue(notes, notes.contains("intake_color: not found: no colour sensor called \"intake_color\""));
        assertTrue(notes, notes.contains("size_sensor: not found: no distance sensor called \"size_sensor\""));
        run(b, 100);
        assertEquals(Pieces.NONE, b.pieces.atIntake());
        assertFalse(b.pieces.comingInSensor().isWorking());
        assertEquals("the preload, nothing more", 1, b.pieces.count());
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        b.pieces.addTelemetry(t);
        t.update();
        assertTrue(String.join("\n", ft.lastScreen).contains("not found"));
    }

    @Test public void aSensorWithNoNewReadingsStopsCounting() {
        // Like a camera whose pictures stopped: the same answer again is no new reading.
        Kit kit = new Kit();
        Bot b = setup(kit, false);
        final boolean[] frozen = {false};
        final double[][] reading = {{48, 0.8, 15}};
        b.pieces.comingInSensor().useReader(() -> frozen[0] ? null : reading[0].clone());
        run(b, 60);
        assertEquals("one came in", 2, b.pieces.count());
        frozen[0] = true;                             // the camera stops (or is unplugged)
        run(b, 600);
        assertFalse(b.pieces.comingInSensor().isWorking());
        assertEquals("no stale answer", Pieces.NONE, b.pieces.atIntake());
        assertTrue(b.pieces.comingInSensor().problem().contains("no new reading"));
        assertEquals(2, b.pieces.count());
        reading[0] = new double[] {48, 0.8, 15};      // back: the same piece still there, not counted twice
        frozen[0] = false;
        run(b, 60);
        assertEquals(2, b.pieces.count());
    }

    @Test public void aStoppedOrJammedFeedDoesNotCountOut() {
        Kit kit = new Kit();
        Bot b = setup(kit, false);                    // a CR servo indexer, no ready sensor
        b.indexer.feedOne();
        run(b, 100);
        b.indexer.stop();                             // stopped part way: the piece is still in
        assertEquals(1, b.pieces.count());
        assertFalse(b.indexer.isFeeding());
        b.pieces.feedStarted();                       // what a jammed feed tells it
        b.pieces.feedEnded(false);
        assertEquals("a jam: still in", 1, b.pieces.count());
        b.indexer.feedOne();
        run(b, 500);                                  // a feed that ends normally
        assertEquals(0, b.pieces.count());
    }

    @Test public void aServoStoppedAfterItsPushDidFire() {
        Kit kit = new Kit();
        Bot b = new Bot();
        b.withReady = false;
        b.servoIndexer = true;
        b.setup(kit);
        clocks(b);
        b.indexer.feedOne();
        run(b, 100);                                  // pushSec 0.25: still pushing
        b.indexer.stop();
        assertEquals("stopped mid-push: not fired", 1, b.pieces.count());
        b.indexer.feedOne();
        run(b, 300);                                  // the push is done, on its way back
        b.indexer.stop();
        assertEquals(0, b.pieces.count());
    }

    @Test public void aFeedWithTheReadySlotEmptyFiresNothing() {
        Kit kit = new Kit();
        Bot b = setup(kit, true);
        kit.colors.get("ready_color").empty();
        run(b, 20);
        b.indexer.feedOne();
        run(b, 500);
        assertEquals("the ready sensor saw nothing go: still 1", 1, b.pieces.count());
    }

    @Test public void theReadySensorIsTrustedAgainAfterAShortGap() {
        // The next piece arrives sooner than steadyMs after the last one left: the slot never reads
        // empty for long enough, but the new piece's reading is trusted once it is steady.
        Kit kit = new Kit();
        Bot b = setup(kit, true, SlowReady.class);
        FakeColor ready = kit.colors.get("ready_color");
        b.pieces.set("POLLEN", "BLUE_NECTAR", "BLUE_NECTAR");   // the count is wrong about the 2nd one
        ready.yellow();
        run(b, 60);
        b.indexer.feedOne();
        ready.empty();
        run(b, 20);                                   // 20 ms empty (< steadyMs 30): it left
        assertEquals(2, b.pieces.count());
        ready.yellow();                               // a yellow one right behind
        run(b, 60);
        assertEquals("the ready sensor knows best again (the feed is still going)", "POLLEN", b.pieces.next());
        assertEquals("[POLLEN, BLUE_NECTAR]", b.pieces.held().toString());
    }

    // Codex: a feed that ends (counted out) while the ready sensor's steady answer still shows the piece
    // that just went must not put that piece back in the count.
    @Test public void aFinishedFeedDoesNotBringBackThePieceItSentOut() {
        Kit kit = new Kit();
        Bot b = setup(kit, true, SlowReady.class);    // the ready sensor waits 30 ms (steadyMs)
        FakeColor ready = kit.colors.get("ready_color");
        ready.yellow();
        run(b, 60);
        assertEquals("[POLLEN]", b.pieces.held().toString());
        b.pieces.feedStarted();                       // the indexer feeds it...
        b.pieces.feedEnded(true);                     // ...and its feed ends before the sensor saw it go
        assertEquals(0, b.pieces.count());
        run(b, 20);                                   // the sensor still shows the old reading
        assertEquals("not counted in again", 0, b.pieces.count());
        assertEquals(Pieces.NONE, b.pieces.next());
        ready.empty();
        run(b, 60);                                   // the slot reads empty: the sensor is trusted again
        assertEquals(0, b.pieces.count());
        ready.yellow();                               // a new piece, loaded by hand
        run(b, 60);
        assertEquals("a NEW piece in the slot counts", 1, b.pieces.count());
        // A jam (nothing fired): the piece is still in, and the sensor is trusted at once.
        b.pieces.feedStarted();
        b.pieces.feedEnded(false);
        run(b, 20);
        assertEquals(1, b.pieces.count());
        assertEquals("POLLEN", b.pieces.next());
    }

    @Test public void piecesPushedOutThroughTheIntakeCountOutAndAreNotCountedInAgain() {
        Kit kit = new Kit();
        Bot b = setup(kit, false);
        FakeColor in = kit.colors.get("intake_color");
        b.intake.in();
        in.red(); run(b, 60); in.empty(); run(b, 60);
        in.blue(); run(b, 60);                        // a jam: a 3rd piece stuck at the sensor
        assertEquals(3, b.pieces.count());
        b.intake.out();                               // back it out
        run(b, 20);
        in.empty(); run(b, 60);                       // it left through the intake
        assertEquals(2, b.pieces.count());
        assertEquals("[POLLEN, RED_NECTAR]", b.pieces.held().toString());
        in.red(); run(b, 60);                         // a held piece rolls past the sensor on its way out:
        assertEquals("not counted in", 2, b.pieces.count());
        in.empty(); run(b, 60);
        assertEquals("counted out", 1, b.pieces.count());
        b.intake.in();
        run(b, 400);
        in.red(); run(b, 60); in.empty(); run(b, 60); // taken in again: counts again
        assertEquals(2, b.pieces.count());
    }

    @Test public void twoTouchingPiecesOfTheSameKindCountTwiceWithADistanceDip() {
        Kit kit = new Kit();
        Bot b = setup(kit, false);
        FakeColor in = kit.colors.get("intake_color");
        in.yellow();                                  // 15 mm
        run(b, 60);
        assertEquals(2, b.pieces.count());
        in.mm = 19; run(b, 30); in.mm = 14; run(b, 30); // a small wobble (under gapMm 8): the same piece
        assertEquals(2, b.pieces.count());
        in.mm = 27; run(b, 30);                       // the join between two balls: farther
        in.mm = 15; run(b, 30);                       // the second ball: close again
        assertEquals(3, b.pieces.count());
        in.empty(); run(b, 60);
        assertEquals("[POLLEN, POLLEN, POLLEN]", b.pieces.held().toString());
        in.red(); run(b, 60);                         // a red one, then a blue one touching it:
        in.mm = 27; run(b, 30);
        in.mm = 15; run(b, 30);                       // the dip counts it (it still reads red)
        in.blue(); run(b, 60);                        // its colour: the same piece, kind written down
        assertEquals("[POLLEN, POLLEN, POLLEN, RED_NECTAR, BLUE_NECTAR]", b.pieces.held().toString());
    }

    @Test public void theDriversCanOverrideAFullCountAndAreWarned() {
        LongSupplier padClock = Pad.clock;
        try {
            Pad.clock = () -> now[0];
            Kit kit = new Kit();
            Bot b = setup(kit, false);
            b.pieces.set("POLLEN", "POLLEN", "POLLEN", "POLLEN");
            ButtonPlan plan = ControlMap.makePlan(b, false, true, (p1, p2) -> { });
            String help = String.join("\n", plan.help());
            assertTrue(help, help.contains("G2 RT: in (hold; 1 s: in even when full)"));
            assertTrue(help, help.contains("piece count = 0"));
            com.crossbotics.ftchelper.gamepad.ControllerBindings driver = new com.crossbotics.ftchelper.gamepad.ControllerBindings();
            com.crossbotics.ftchelper.gamepad.ControllerBindings operator = new com.crossbotics.ftchelper.gamepad.ControllerBindings();
            com.qualcomm.robotcore.hardware.Gamepad g1 = new com.qualcomm.robotcore.hardware.Gamepad();
            com.qualcomm.robotcore.hardware.Gamepad g2 = new com.qualcomm.robotcore.hardware.Gamepad();
            plan.compile(b, driver, operator, new ArrayList<>(), () -> g1, () -> g2);
            Runnable loop = () -> {
                now[0] += 20_000_000L;
                driver.update(g1, g2);
                operator.update(g1, g2);
                plan.loop(false, false);
                b.all().update();
            };
            loop.run();
            g2.right_trigger = 1;
            for (int i = 0; i < 25; i++) loop.run();      // 0.5 s
            assertEquals("full: held back", 0.0, kit.kit.motors.get("intake").power, 1e-9);
            for (int i = 0; i < 30; i++) loop.run();      // past 1 s
            assertEquals("held 1 s: in anyway", 1.0, kit.kit.motors.get("intake").power, 1e-9);
            g2.right_trigger = 0;
            loop.run();
            assertEquals(0.0, kit.kit.motors.get("intake").power, 1e-9);
            // Held back for more than 2 s: the Driver Station says so.
            b.intake.in();
            for (int i = 0; i < 110; i++) loop.run();
            Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
            RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
            b.intake.addTelemetry(t);
            t.update();
            assertTrue(String.join("\n", ft.lastScreen), String.join("\n", ft.lastScreen).contains("held back by stopsWhen"));
            // Hold the left stick button 1 s: the count is 0, and the intake runs in again.
            g2.left_stick_button = true;
            for (int i = 0; i < 55; i++) loop.run();
            assertEquals(0, b.pieces.count());
            assertEquals(1.0, kit.kit.motors.get("intake").power, 1e-9);
        } finally {
            Pad.clock = padClock;
        }
    }
}
