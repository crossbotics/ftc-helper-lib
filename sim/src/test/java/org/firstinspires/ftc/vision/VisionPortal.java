package org.firstinspires.ftc.vision;

import android.util.Size;
import java.util.ArrayList;
import java.util.List;
import org.firstinspires.ftc.robotcore.external.hardware.camera.BuiltinCameraDirection;
import org.firstinspires.ftc.robotcore.external.hardware.camera.CameraName;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.hardware.camera.controls.CameraControl;
import org.firstinspires.ftc.robotcore.external.stream.CameraStreamSource;

/**
 * SIMULATOR ONLY: the FTC SDK 12.0 VisionPortal with the same public parts, for the simulator's
 * classpath. The SDK's own class needs the Android app when it loads (to find the live-view screen), so
 * the simulator uses this copy. Its Builder makes the pretend VisionPortalImpl (no live view; the
 * resolution comes from the robot file's camera line).
 */
public abstract class VisionPortal implements CameraStreamSource {
    public static final int DEFAULT_VIEW_CONTAINER_ID = 0;

    public static int[] makeMultiPortalView(int numPortals, MultiPortalLayout mpl) {
        return new int[numPortals];
    }

    public static VisionPortal easyCreateWithDefaults(BuiltinCameraDirection cameraDirection, VisionProcessor... processors) {
        return new Builder().setCamera(cameraDirection).addProcessors(processors).build();
    }

    public static VisionPortal easyCreateWithDefaults(CameraName cameraName, VisionProcessor... processors) {
        return new Builder().setCamera(cameraName).addProcessors(processors).build();
    }

    public enum StreamFormat { YUY2, MJPEG }

    public enum MultiPortalLayout { VERTICAL, HORIZONTAL }

    public enum CameraState {
        OPENING_CAMERA_DEVICE, CAMERA_DEVICE_READY, STARTING_STREAM, STREAMING, STOPPING_STREAM,
        CLOSING_CAMERA_DEVICE, CAMERA_DEVICE_CLOSED, ERROR
    }

    public static class Builder {
        private static final ArrayList<VisionProcessor> attachedProcessors = new ArrayList<>();
        private CameraName camera;
        private int liveViewContainerId = DEFAULT_VIEW_CONTAINER_ID;
        private boolean autoStopLiveView = true;
        private boolean autoStartStreamOnBuild = true;
        private boolean showStatsOverlay = true;
        private Size cameraResolution = null;
        private StreamFormat streamFormat = null;
        private final List<VisionProcessor> processors = new ArrayList<>();

        public Builder setCamera(CameraName camera) {
            this.camera = camera;
            return this;
        }

        public Builder setCamera(BuiltinCameraDirection cameraDirection) {
            throw new RuntimeException("Simulator: phone (built-in) cameras are not simulated. Use a webcam (camera.NAME in the robot file).");
        }

        public Builder setStreamFormat(StreamFormat streamFormat) { this.streamFormat = streamFormat; return this; }
        public Builder enableLiveView(boolean enableLiveView) { liveViewContainerId = enableLiveView ? DEFAULT_VIEW_CONTAINER_ID : 0; return this; }
        public Builder setAutoStopLiveView(boolean autoPause) { autoStopLiveView = autoPause; return this; }
        public Builder setLiveViewContainerId(int liveViewContainerId) { this.liveViewContainerId = liveViewContainerId; return this; }
        public Builder setCameraResolution(Size cameraResolution) { this.cameraResolution = cameraResolution; return this; }

        public Builder addProcessor(VisionProcessor processor) {
            synchronized (attachedProcessors) {
                if (attachedProcessors.contains(processor)) {
                    throw new RuntimeException("This VisionProcessor has already been attached to a VisionPortal, "
                            + "either a different one or perhaps even this same portal.");
                }
                attachedProcessors.add(processor);
            }
            processors.add(processor);
            return this;
        }

        public Builder addProcessors(VisionProcessor... processors) {
            for (VisionProcessor p : processors) addProcessor(p);
            return this;
        }

        public Builder setAutoStartStreamOnBuild(boolean autoStartStreamOnBuild) { this.autoStartStreamOnBuild = autoStartStreamOnBuild; return this; }
        public Builder setShowStatsOverlay(boolean showStatsOverlay) { this.showStatsOverlay = showStatsOverlay; return this; }

        public VisionPortal build() {
            if (camera == null) throw new RuntimeException("You can't build a vision portal without setting a camera!");
            if (streamFormat != null && (!camera.isWebcam() || camera.isSwitchable())) {
                throw new IllegalStateException("setStreamFormat() may only be used with a webcam");
            }
            VisionPortal portal = new VisionPortalImpl(camera, liveViewContainerId, autoStopLiveView, cameraResolution,
                    streamFormat, autoStartStreamOnBuild, showStatsOverlay, processors.toArray(new VisionProcessor[0]));
            processors.clear();
            return portal;
        }
    }

    public abstract void setProcessorEnabled(VisionProcessor processor, boolean enabled);
    public abstract boolean getProcessorEnabled(VisionProcessor processor);
    public abstract CameraState getCameraState();
    public abstract void saveNextFrameRaw(String filename);
    public abstract void stopStreaming();
    public abstract void resumeStreaming();
    public abstract void stopLiveView();
    public abstract void resumeLiveView();
    public abstract float getFps();
    public abstract <T extends CameraControl> T getCameraControl(Class<T> controlType);
    public abstract void setActiveCamera(WebcamName webcamName);
    public abstract WebcamName getActiveCamera();
    public abstract void close();
}
