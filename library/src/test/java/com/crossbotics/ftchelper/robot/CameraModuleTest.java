package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
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
                if (type == Limelight3A.class || type == WebcamName.class) throw new IllegalArgumentException("no " + name);
                return kit.get(type, name);
            }

            @Override public <T> T tryGet(Class<? extends T> type, String name) {
                if (type == Limelight3A.class || type == WebcamName.class) return null;
                return kit.tryGet(type, name);
            }
        };
    }

    static final class CameraBot extends RobotDefinition {
        Camera limelight, webcam;

        @Override protected void define() {
            drive(Drive.tank("l", "r"));
            limelight = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
            webcam = add(Camera.webcam("Webcam 1").autoExposure());
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
        r.all().stop();
    }
}
