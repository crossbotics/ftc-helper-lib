package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.qualcomm.hardware.dfrobot.HuskyLens;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.junit.Test;

/** The five-files Camera module: a camera problem never stops the OpMode. */
public class CameraModuleTest {
    // The test kit's motors, but no cameras at all (as if the camera is not in the configuration).
    private static Devices noCameras() {
        final RobotTestKit kit = new RobotTestKit();
        return new Devices() {
            @Override public <T> T get(Class<? extends T> type, String name) {
                if (type == Limelight3A.class || type == WebcamName.class || type == HuskyLens.class) throw new IllegalArgumentException("no " + name);
                return kit.get(type, name);
            }

            @Override public <T> T tryGet(Class<? extends T> type, String name) {
                if (type == Limelight3A.class || type == WebcamName.class || type == HuskyLens.class) return null;
                return kit.tryGet(type, name);
            }
        };
    }

    static final class CameraBot extends RobotDefinition {
        Camera limelight, webcam, husky;

        @Override protected void define() {
            drive(Drive.tank("l", "r"));
            limelight = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
            webcam = add(Camera.webcam("Webcam 1").autoExposure());
            husky = add(Camera.huskyLens("huskylens").tag(1, 30));
        }
    }

    @Test public void aMissingCameraWarnsAndSeesNothingButSetupGoesOn() {
        CameraBot r = new CameraBot();
        r.setup(noCameras());                       // must not stop
        assertNull(r.limelight.goal());
        assertNull(r.webcam.target(31));
        assertNull("no lighting test without a camera", r.limelight.lighting());
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.initLoop(t);
        r.all().update();                           // the match loop too
        t.update();
        String screen = String.join("\n", ft.lastScreen);
        assertTrue(screen, screen.contains("not found: no Limelight called \"limelight\" in the robot configuration"));
        assertTrue(screen, screen.contains("not found: no webcam called \"Webcam 1\""));
        assertTrue(screen, screen.contains("not found: no HuskyLens called \"huskylens\""));
        assertTrue(r.husky.isHuskyLens());
        assertNull(r.husky.target(30));
        r.all().stop();
    }

    // A HuskyLens that sees taught tags 1 and 2, side by side and right side up, 20 pixels across.
    private static final class TwoTags extends HuskyLens {
        final java.util.List<Algorithm> selected = new java.util.concurrent.CopyOnWriteArrayList<>();

        TwoTags() {
            super((com.qualcomm.robotcore.hardware.I2cDeviceSynch) java.lang.reflect.Proxy.newProxyInstance(
                    HuskyLens.class.getClassLoader(), new Class<?>[] {com.qualcomm.robotcore.hardware.I2cDeviceSynch.class},
                    (proxy, m, a) -> m.getName().equals("read") ? new byte[] {0x55, (byte) 0xAA, 0x11, 0, 0x2E, 0x3E}
                            : m.getReturnType() == boolean.class ? (Object) false : m.getReturnType() == int.class ? (Object) 0 : null));
        }

        private Block box(int x, int id) {
            byte[] b = new byte[16];
            int[] numbers = {x, 120, 20, 20, id};
            for (int i = 0; i < numbers.length; i++) b[5 + i * 2] = (byte) numbers[i];
            return new Block(b);
        }

        @Override public boolean knock() { return true; }
        @Override public void selectAlgorithm(Algorithm a) { selected.add(a); }
        @Override public Block[] blocks() { return new Block[] {box(100, 1), box(220, 2)}; }
    }

    private static Devices withHusky(final HuskyLens husky) {
        final RobotTestKit kit = new RobotTestKit();
        return new Devices() {
            @Override public <T> T get(Class<? extends T> type, String name) {
                return kit.get(type, name);
            }

            @SuppressWarnings("unchecked")
            @Override public <T> T tryGet(Class<? extends T> type, String name) {
                if (type == HuskyLens.class) return (T) husky;
                return kit.tryGet(type, name);
            }
        };
    }

    public static class HuskyNumbers {
        public static class Huskylens {
            public static double hfovDeg = 60;
            public static double sizeIn = 3.25;
        }
    }

    static final class HuskyGoalBot extends RobotDefinition {
        Camera camera;

        @Override protected void define() {
            tunables(HuskyNumbers.class);
            drive(Drive.tank("l", "r"));
            camera = add(Camera.huskyLens("huskylens").tag(1, 30).tag(2, 33).goal("BLUE SCORING", "RED SCORING"));
        }
    }

    @Test public void aHuskyLensGoalWorksThroughTheRobotFile() throws Exception {
        HuskyGoalBot r = new HuskyGoalBot();
        r.setup(withHusky(new TwoTags()));
        assertFalse(r.notes().toString(), r.notes().toString().contains("hfovDeg"));
        com.crossbotics.ftchelper.vision.TargetObservation goal = null;
        // BLUE by default, so this looks at the group by name: goal() is for the alliance's own.
        for (int i = 0; i < 200 && goal == null; i++) {
            r.all().update();
            goal = r.camera.target("RED SCORING");
            Thread.sleep(5);
        }
        assertTrue("seen: " + r.camera.source().targets(), goal != null);
        assertTrue(goal.isUpright());
        assertEquals("the row's middle is the picture's middle", 0, goal.bearingDeg, 0.5);
        assertTrue("sizeIn gives a distance", goal.hasDistance());
        r.all().stop();
    }

    static final class HuskyColorsBot extends RobotDefinition {
        Camera camera;

        @Override protected void define() {
            drive(Drive.tank("l", "r"));
            camera = add(Camera.huskyLens("huskylens").colors().label(1, "POLLEN").goal("BLUE SCORING", "RED SCORING"));
        }
    }

    @Test public void iNitSaysWhatAHuskyLensStillNeedsAndColorsAreSent() throws Exception {
        HuskyColorsBot r = new HuskyColorsBot();
        TwoTags husky = new TwoTags();
        r.setup(withHusky(husky));
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("hfovDeg missing"));
        assertTrue(notes, notes.contains("sizeIn missing"));
        assertTrue(notes, notes.contains("needs tag(taughtNumber, tagNumber)"));
        com.crossbotics.ftchelper.vision.TargetObservation pollen = null;
        for (int i = 0; i < 200 && pollen == null; i++) {
            r.all().update();
            pollen = r.camera.target("POLLEN");
            Thread.sleep(5);
        }
        assertTrue(pollen != null);
        assertEquals(HuskyLens.Algorithm.COLOR_RECOGNITION, husky.selected.get(0));
        r.all().stop();
    }

    @Test public void theRobotFileLineListsWhatWasTaughtInOrder() {
        java.util.List<String> out = new java.util.ArrayList<>();
        Camera.huskyLens("huskylens").tag(2, 35).tag(1, 34).tag(4, 37).simLines(out, null);
        assertTrue(out.toString(), out.get(0).startsWith("huskylens.huskylens = name=huskylens, "));
        assertTrue(out.toString(), out.get(0).contains("taught=34|35|-|37"));
        assertFalse("no measured view angle: none in the file", out.get(0).contains("hfov="));
        out.clear();
        Camera.huskyLens("huskylens").simLines(out, TunableSection.of(HuskyNumbers.class, "Huskylens"));
        assertTrue(out.toString(), out.get(0).contains("hfov=60.0"));
        out.clear();
        Camera.huskyLens("h").colors().label(1, "POLLEN").simLines(out, null);
        assertTrue(out.toString(), out.get(0).contains("taught=POLLEN"));
    }

    @Test(expected = IllegalStateException.class)
    public void tagLinesAreForAHuskyLens() {
        Camera.webcam("Webcam 1").tag(1, 30);
    }

    @Test(expected = IllegalStateException.class)
    public void aHuskyLensHasNoLightingTest() {
        Camera.huskyLens("huskylens").autoExposure();
    }
}
