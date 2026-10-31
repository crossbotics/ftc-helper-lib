package com.crossbotics.ftchelper.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.qualcomm.hardware.dfrobot.HuskyLens;
import com.qualcomm.robotcore.hardware.I2cDeviceSynch;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** HuskyLensCamera with a pretend camera, a pretend clock, and reads that run at once. */
public class HuskyLensCameraTest {
    private long now = 1_000_000_000L;

    /** The SDK's HuskyLens with the I2C replaced: it gives the boxes the test sets. */
    private static final class FakeHusky extends HuskyLens {
        boolean throwOnRead = false;
        final List<Algorithm> selected = new ArrayList<>();
        final int[] reads = {0};
        // Plugged in? An empty port on a REV hub reads as zeros, and the SDK's knock() still says true.
        final boolean[] plugged;
        Block[] boxes = new Block[0];
        Runnable whileSelecting = null;

        FakeHusky() {
            this(new boolean[] {true});
        }

        private FakeHusky(boolean[] plugged) {
            super(client(plugged));
            this.plugged = plugged;
        }

        private static I2cDeviceSynch client(boolean[] plugged) {
            return (I2cDeviceSynch) Proxy.newProxyInstance(I2cDeviceSynch.class.getClassLoader(),
                    new Class<?>[] {I2cDeviceSynch.class}, (proxy, m, a) -> {
                        if (m.getName().equals("read")) {
                            return plugged[0] ? new byte[] {0x55, (byte) 0xAA, 0x11, 0, 0x2E, 0x3E} : new byte[6];
                        }
                        Class<?> t = m.getReturnType();
                        if (t == boolean.class) return false;
                        if (t == int.class) return 0;
                        return null;
                    });
        }

        Block box(int x, int y, int w, int h, int id) {
            byte[] b = new byte[16];
            int[] numbers = {x, y, w, h, id};
            for (int i = 0; i < numbers.length; i++) {
                b[5 + i * 2] = (byte) numbers[i];
                b[6 + i * 2] = (byte) (numbers[i] >> 8);
            }
            return new Block(b);
        }

        @Override public boolean knock() { return true; }
        @Override public void selectAlgorithm(Algorithm a) {
            selected.add(a);
            if (whileSelecting != null) whileSelecting.run();
        }
        @Override public Block[] blocks() {
            reads[0]++;
            if (throwOnRead) throw new IllegalStateException("I2C trouble");
            return plugged[0] ? boxes : new Block[0];
        }
    }

    private HuskyLensCamera camera(FakeHusky h) {
        return new HuskyLensCamera("huskylens", h, () -> now, Runnable::run).hfovDeg(60);
    }

    @Test public void theBoxReadsAsTheSdkMeansIt() {
        HuskyLens.Block b = new FakeHusky().box(300, 200, 40, 30, 2);
        assertEquals(300, b.x);
        assertEquals(200, b.y);
        assertEquals(40, b.width);
        assertEquals(30, b.height);
        assertEquals(2, b.id);
    }

    @Test public void theMiddleIsStraightAheadAndLeftIsPositive() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 20, 20, 1), h.box(0, 120, 20, 20, 2), h.box(320, 120, 20, 20, 3)};
        HuskyLensCamera c = camera(h);
        c.update();
        assertEquals(0, c.target(1).bearingDeg, 0.01);
        assertEquals("the left edge is half the view angle to the left", 30, c.target(2).bearingDeg, 0.01);
        assertEquals(-30, c.target(3).bearingDeg, 0.01);
    }

    @Test public void itSendsTheModeOnceAndAgainWhenItChanges() {
        FakeHusky h = new FakeHusky();
        HuskyLensCamera c = camera(h);
        c.update();
        c.update();
        assertEquals(Arrays.asList(HuskyLens.Algorithm.TAG_RECOGNITION), h.selected);
        c.mode(HuskyLensCamera.Mode.COLORS);
        c.update();
        assertEquals(Arrays.asList(HuskyLens.Algorithm.TAG_RECOGNITION, HuskyLens.Algorithm.COLOR_RECOGNITION), h.selected);
    }

    @Test public void changingTheModeDropsTheOldTargetsAtOnce() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 20, 20, 1)};
        HuskyLensCamera c = camera(h);
        c.update();
        assertEquals(1, c.targets().size());
        c.mode(HuskyLensCamera.Mode.OBJECTS);
        assertTrue(c.targets().isEmpty());
    }

    @Test public void tagNumbersAndNames() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(100, 120, 20, 20, 1), h.box(150, 120, 20, 20, 2), h.box(200, 120, 20, 20, 3), h.box(250, 120, 20, 20, 0)};
        HuskyLensCamera c = camera(h).tag(1, 31).label(2, "POLLEN");
        c.update();
        assertEquals("Tag 31", c.target(31).name);
        assertNull("the taught number is now the real number", c.target(1));
        assertEquals(2, c.target("POLLEN").id);
        assertEquals("Taught 3", c.target(3).name);
        assertEquals("Untaught tag", c.target(0).name);
    }

    @Test public void tagNumbersCountOnlyForTags() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(100, 120, 20, 20, 1)};
        HuskyLensCamera c = camera(h).tag(1, 31).mode(HuskyLensCamera.Mode.COLORS);
        c.update();
        assertNotNull("a taught colour keeps its number", c.target(1));
        assertNull(c.target(31));
    }

    @Test public void noSizeNoDistanceAndWithASizeTwiceAsFarIsHalfAsBig() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 40, 40, 1), h.box(160, 120, 20, 20, 2)};
        HuskyLensCamera c = camera(h);
        c.update();
        assertFalse(c.target(1).hasDistance());
        c.sizeIn(4);
        now += 1_000_000;
        c.update();
        double focal = 160 / Math.tan(Math.toRadians(30));
        assertEquals(4 * focal / 40, c.target(1).distanceIn, 0.01);
        assertEquals(2 * c.target(1).distanceIn, c.target(2).distanceIn, 0.01);
    }

    @Test public void aGroupNeedsTwoTagsAndTheirOrderSaysRightSideUp() {
        FakeHusky h = new FakeHusky();
        HuskyLensCamera c = camera(h).tag(1, 30).tag(2, 31).tag(3, 32).tag(4, 33).sizeIn(4);
        h.boxes = new HuskyLens.Block[] {h.box(120, 120, 20, 20, 1)};
        c.update();
        assertNull("one tag is not a group", c.target("RED SCORING"));
        assertFalse("one tag has no roll", c.target(30).isUpright());
        // Tags 30 and 33 (13 in apart on the row): their numbers rise to the right.
        h.boxes = new HuskyLens.Block[] {h.box(100, 120, 20, 20, 1), h.box(220, 120, 20, 20, 4)};
        now += 1_000_000;
        c.update();
        TargetObservation up = c.target("RED SCORING");
        assertNotNull(up);
        assertEquals(TargetObservation.CLUSTER, up.id);
        assertTrue("rising to the right: " + up.rollDeg, up.isUpright());
        assertEquals("the row's middle is the picture's middle", 0, up.bearingDeg, 0.5);
        assertEquals(4 * (160 / Math.tan(Math.toRadians(30))) / 20, up.distanceIn, 0.5);
        // The same tags swapped in the picture: upside down.
        h.boxes = new HuskyLens.Block[] {h.box(220, 120, 20, 20, 1), h.box(100, 120, 20, 20, 4)};
        now += 1_000_000;
        c.update();
        assertFalse(c.target("RED SCORING").isUpright());
        assertEquals("BIOBUZZ: an upside-down CELL is never the goal", null, TagGroup.goalTarget(c, "RED SCORING"));
    }

    @Test public void aCameraThatIsNotPluggedInSaysWhyAndSeesNothing() {
        FakeHusky h = new FakeHusky();
        h.plugged[0] = false;
        HuskyLensCamera c = camera(h);
        c.update();
        assertFalse(c.isWorking());
        assertTrue(c.status(), c.status().contains("not answering"));
        assertTrue(h.selected.isEmpty());
        h.plugged[0] = true;
        c.update();
        assertTrue("back as soon as it answers: " + c.status(), c.isWorking());
    }

    @Test public void pullingTheCableIsNoticedWithinAboutASecond() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 20, 20, 1)};
        HuskyLensCamera c = camera(h);
        c.update();
        assertTrue(c.isWorking());
        h.plugged[0] = false;
        now += 100_000_000L;
        c.update();
        assertTrue("no boxes yet looks like seeing nothing", c.targets().isEmpty() || c.isWorking());
        now += 1_000_000_000L;
        c.update();
        assertFalse(c.isWorking());
        assertTrue(c.status(), c.status().contains("not answering"));
        h.plugged[0] = true;
        c.update();
        assertTrue(c.status(), c.isWorking());
        assertEquals("sent again: it may have restarted", 2, h.selected.size());
    }

    @Test public void itDoesNotKnockWhileItSeesBoxes() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 20, 20, 1)};
        HuskyLensCamera c = camera(h);
        c.update();
        now += 5_000_000_000L;
        c.update();
        assertTrue(c.status(), c.isWorking());
        assertEquals("the mode was sent once: no second knock needed", 1, h.selected.size());
    }

    @Test public void aModeChangeDuringTheSendIsNotLost() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 20, 20, 1)};
        HuskyLensCamera c = camera(h);
        // The loop changes the mode while the helper thread is sending TAGS.
        h.whileSelecting = () -> {
            h.whileSelecting = null;
            c.mode(HuskyLensCamera.Mode.COLORS);
        };
        c.update();
        assertTrue("a TAGS reading is not used as colours", c.targets().isEmpty());
        c.update();
        assertEquals(Arrays.asList(HuskyLens.Algorithm.TAG_RECOGNITION, HuskyLens.Algorithm.COLOR_RECOGNITION), h.selected);
        assertEquals(1, c.targets().size());
    }

    @Test public void aReadFromBeforeCloseIsThrownAway() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 20, 20, 1)};
        final List<Runnable> jobs = new ArrayList<>();
        HuskyLensCamera c = new HuskyLensCamera("huskylens", h, () -> now, jobs::add);
        c.update();
        Runnable old = jobs.remove(0);
        c.close();
        c.start();
        c.update();
        assertEquals("the new session asks again", 1, jobs.size());
        old.run();
        c.update();
        assertTrue("the old answer is not used", c.targets().isEmpty());
        assertEquals("and the new read is still the only one asked", 1, jobs.size());
        jobs.remove(0).run();
        c.update();
        assertEquals(1, c.targets().size());
    }

    @Test public void aReadingThatIsTooOldWhenItArrivesIsNotUsed() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 20, 20, 1)};
        final List<Runnable> jobs = new ArrayList<>();
        final long[] extra = {0};
        HuskyLensCamera c = new HuskyLensCamera("huskylens", h, () -> now + extra[0], jobs::add).keepTargetsMs(500);
        c.update();
        jobs.remove(0).run();
        // The reading was taken, then 600 ms passed before the loop picked it up.
        extra[0] = 600_000_000L;
        c.update();
        assertTrue(c.targets().isEmpty());
    }

    @Test public void theLongerSideOfTheBoxGivesTheDistance() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 20, 40, 1)};
        HuskyLensCamera c = camera(h).sizeIn(4);
        c.update();
        assertEquals(4 * (160 / Math.tan(Math.toRadians(30))) / 40, c.target(1).distanceIn, 0.01);
    }

    @Test public void aFailedReadSaysWhyAndNeverStopsTheLoop() {
        FakeHusky h = new FakeHusky();
        h.throwOnRead = true;
        HuskyLensCamera c = camera(h);
        c.update();
        assertFalse(c.isWorking());
        assertTrue(c.status(), c.status().contains("read failed"));
    }

    @Test public void oldTargetsAreDroppedAndAStoppedCameraIsNotWorking() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 20, 20, 1)};
        final List<Runnable> jobs = new ArrayList<>();
        HuskyLensCamera c = new HuskyLensCamera("huskylens", h, () -> now, jobs::add).keepTargetsMs(500);
        c.update();
        jobs.remove(0).run();
        c.update();
        assertEquals("the reading came", 1, c.targets().size());
        // The helper thread is stuck: readings stop coming, but the loop goes on.
        now += 600_000_000L;
        c.update();
        assertTrue("too old to aim with", c.targets().isEmpty());
        assertTrue(c.isWorking());
        now += 600_000_000L;
        c.update();
        assertFalse(c.isWorking());
        assertTrue(c.status(), c.status().contains("no new reading"));
    }

    @Test public void updateNeverAsksTwiceWhileAReadIsRunning() {
        FakeHusky h = new FakeHusky();
        final List<Runnable> jobs = new ArrayList<>();
        HuskyLensCamera c = new HuskyLensCamera("huskylens", h, () -> now, jobs::add);
        c.update();
        c.update();
        c.update();
        assertEquals(1, jobs.size());
    }

    @Test public void closeForgetsEverything() {
        FakeHusky h = new FakeHusky();
        h.boxes = new HuskyLens.Block[] {h.box(160, 120, 20, 20, 1)};
        HuskyLensCamera c = camera(h);
        c.update();
        c.close();
        assertTrue(c.targets().isEmpty());
        assertFalse(c.isWorking());
        assertEquals("not started", c.status());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aWrongViewAngleStopsAtOnce() {
        new HuskyLensCamera("h", new FakeHusky()).hfovDeg(0);
    }
}
