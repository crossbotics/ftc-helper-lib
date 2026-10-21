package org.firstinspires.ftc.vision;

import android.graphics.Bitmap;
import android.util.Size;
import com.crossbotics.ftchelper.sim.SimVision;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.robotcore.external.function.Consumer;
import org.firstinspires.ftc.robotcore.external.function.Continuation;
import org.firstinspires.ftc.robotcore.external.hardware.camera.CameraName;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.hardware.camera.controls.CameraControl;
import org.firstinspires.ftc.robotcore.external.hardware.camera.controls.ExposureControl;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessorImpl;

/**
 * SIMULATOR ONLY: stands in for the FTC SDK's VisionPortalImpl (same name and constructor, so the SDK's
 * own VisionPortal.Builder makes this one in the simulator; the simulator's classes come first on its
 * classpath). There is no real camera: about 1 s after it opens, it "streams" at 30 pictures a second,
 * and each picture asks the pretend camera (camera.NAME in the robot file) which AprilTags it would see.
 * Only the SDK's AprilTag finder gets pictures; other VisionProcessors get init() and nothing more.
 */
public class VisionPortalImpl extends VisionPortal implements AutoCloseable {
    private static final long OPEN_NANOS = 1_000_000_000L;   // a webcam takes about 1 s to start
    private static final long FRAME_MS = 33;                  // 30 pictures a second

    private final Object cam;
    private final CameraName cameraName;
    private final VisionProcessor[] processors;
    private final boolean[] enabled;
    private final long openedAt = System.nanoTime();
    private volatile boolean streamWanted;
    private volatile boolean closed = false;
    private volatile CameraState state = CameraState.OPENING_CAMERA_DEVICE;
    private volatile float fps = 0;
    private final Thread pictures;

    public VisionPortalImpl(CameraName camera, int cameraMonitorViewId, boolean autoPauseCameraMonitor, Size cameraResolution,
                            StreamFormat webcamStreamFormat, boolean autoStartStream, boolean showStatsOverlay,
                            VisionProcessor[] processors) {
        this.cam = SimVision.camera(camera);
        if (cam == null) {
            throw new RuntimeException("Simulator: this camera is not a simulator camera. Use a webcam from the hardware map "
                    + "and add it to the robot file (camera.\"Webcam 1\" = forward=6, up=10, pitch=10).");
        }
        this.cameraName = camera;
        this.processors = processors.clone();
        this.enabled = new boolean[processors.length];
        java.util.Arrays.fill(enabled, true);
        this.streamWanted = autoStartStream;
        for (VisionProcessor p : this.processors) p.init(SimVision.width(cam), SimVision.height(cam), null);
        SimVision.opened(cam, this);
        pictures = new Thread(this::run, "Pretend camera " + SimVision.name(cam));
        pictures.setDaemon(true);
        pictures.start();
    }

    private void run() {
        long frames = 0, windowStart = System.nanoTime();
        while (!closed) {
            long now = System.nanoTime();
            if (now - openedAt < OPEN_NANOS) {
                state = CameraState.OPENING_CAMERA_DEVICE;
            } else if (!streamWanted) {
                state = CameraState.CAMERA_DEVICE_READY;
                fps = 0;
            } else {
                state = CameraState.STREAMING;
                for (int i = 0; i < processors.length; i++) {
                    if (enabled[i] && processors[i] instanceof AprilTagProcessorImpl) {
                        ((AprilTagProcessorImpl) processors[i]).simFrame(cam, now);
                    }
                }
                frames++;
                if (now - windowStart > 1_000_000_000L) {
                    fps = frames * 1e9f / (now - windowStart);
                    frames = 0;
                    windowStart = now;
                }
            }
            try {
                // the next picture 33 ms after this one started (30 a second, like a webcam)
                Thread.sleep(Math.max(1, FRAME_MS - (System.nanoTime() - now) / 1_000_000));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    @Override public void setProcessorEnabled(VisionProcessor processor, boolean on) {
        for (int i = 0; i < processors.length; i++) if (processors[i] == processor) { enabled[i] = on; return; }
        throw new IllegalArgumentException("Processor not attached to this portal");
    }

    @Override public boolean getProcessorEnabled(VisionProcessor processor) {
        for (int i = 0; i < processors.length; i++) if (processors[i] == processor) return enabled[i];
        throw new IllegalArgumentException("Processor not attached to this portal");
    }

    @Override public CameraState getCameraState() { return state; }
    @Override public void saveNextFrameRaw(String filename) { }
    @Override public void stopStreaming() { streamWanted = false; }
    @Override public void resumeStreaming() { streamWanted = true; }
    @Override public void stopLiveView() { }
    @Override public void resumeLiveView() { }
    @Override public float getFps() { return fps; }

    // Exposure, gain, focus...: a pretend control that accepts every setting.
    @Override
    @SuppressWarnings("unchecked")
    public <T extends CameraControl> T getCameraControl(Class<T> type) {
        if (closed) throw new RuntimeException("Cannot get camera control: the portal is closed");
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, m, a) -> {
            Class<?> r = m.getReturnType();
            // The exposure is remembered (the pretend venue lighting uses it): manual ms, or 0 = automatic.
            if (m.getName().equals("setExposure")) {
                SimVision.exposure(cam, ((java.util.concurrent.TimeUnit) a[1]).toMillis((Long) a[0]));
                return true;
            }
            if (m.getName().equals("setMode") && a[0] != ExposureControl.Mode.Manual) SimVision.exposure(cam, 0);
            if (m.getName().equals("getMinExposure")) return ((java.util.concurrent.TimeUnit) a[0]).convert(1, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (m.getName().equals("getMaxExposure")) return ((java.util.concurrent.TimeUnit) a[0]).convert(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (m.getName().equals("getMaxGain")) return 255;
            if (r == boolean.class) return true;
            if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (m.getName().equals("equals")) return proxy == a[0];
            if (r == long.class) return 0L;
            if (r == int.class) return 0;
            if (r == double.class) return 0.0;
            if (r == float.class) return 0f;
            return null;
        });
    }

    @Override public void setActiveCamera(WebcamName webcamName) {
        throw new IllegalStateException("setActiveCamera() is only for switchable cameras");
    }

    @Override public WebcamName getActiveCamera() {
        return cameraName instanceof WebcamName ? (WebcamName) cameraName : null;
    }

    @Override public void getFrameBitmap(Continuation<? extends Consumer<Bitmap>> continuation) { }

    @Override public void close() {
        if (closed) return;
        closed = true;
        state = CameraState.CAMERA_DEVICE_CLOSED;
        pictures.interrupt();
        SimVision.closed(cam, this);
    }
}
