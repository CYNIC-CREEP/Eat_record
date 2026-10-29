package com.eatrecord.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.util.SizeF;
import android.util.Range;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A lifecycle-owned Camera2 preview and single-JPEG capture, without CameraX or frame analysis.
 * The Activity owns runtime permission and calls start/stop from its visible lifecycle.
 * Listener callbacks always run on the main thread; stale runs never deliver an image.
 */
public final class MealCameraController {
    public interface Listener {
        void onReady();
        void onJpeg(byte[] jpeg);
        void onError(String message);
    }

    private static final long MAX_JPEG_PIXELS = 8_000_000L;
    private static final int MAX_PREVIEW_EDGE = 1920;
    private static final long MAX_PREVIEW_PIXELS = 1920L * 1080L;
    private static final int MAX_JPEG_BYTES = 24 * 1024 * 1024;
    private static final long OPEN_TIMEOUT_MS = 10_000L;
    private static final long FOCUS_TIMEOUT_MS = 1_400L;
    private static final long SHOT_TIMEOUT_MS = 15_000L;

    private final Activity activity;
    private final TextureView textureView;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CameraManager cameraManager;
    private final DisplayManager displayManager;
    private final OrientationEventListener orientationListener;
    private volatile Run current;
    private Run retiring;
    private boolean wanted;
    private boolean displayRegistered;
    private long generation;
    private volatile boolean ready;
    private volatile int displayDegrees;
    private volatile int physicalDegrees = OrientationEventListener.ORIENTATION_UNKNOWN;

    // All resources below, except the explicitly volatile fields, belong to this run's worker.
    private static final class Run {
        final long generation;
        final SurfaceTexture texture;
        final int viewWidth;
        final int viewHeight;
        final int initialDisplayDegrees;
        final HandlerThread thread;
        final Handler handler;
        final AtomicBoolean capturing = new AtomicBoolean();
        volatile boolean active = true;
        volatile Size previewSize;
        volatile int sensorOrientation;
        volatile boolean front;
        boolean external;
        CameraCharacteristics characteristics;
        CameraDevice camera;
        CameraCaptureSession session;
        CaptureRequest previewRequest;
        ImageReader reader;
        Surface previewSurface;
        boolean opening;
        boolean closed;
        boolean failed;
        boolean previewReady;
        boolean awaitingFocus;
        boolean awaitingJpeg;
        int afMode = CaptureRequest.CONTROL_AF_MODE_OFF;
        boolean aeOn;
        boolean zoomRatioSupported;
        float minimumZoom = 1f;
        String cameraId;
        long shot;
        int jpegRotation;
        Runnable openTimeout;
        Runnable focusTimeout;
        Runnable shotTimeout;

        Run(long generation, SurfaceTexture texture, int width, int height, int rotation) {
            this.generation = generation;
            this.texture = texture;
            viewWidth = width;
            viewHeight = height;
            initialDisplayDegrees = rotation;
            thread = new HandlerThread("MealCamera-" + generation);
            thread.start();
            handler = new Handler(thread.getLooper());
        }
    }

    public MealCameraController(Activity activity, TextureView textureView, Listener listener) {
        if (activity == null || textureView == null || listener == null) {
            throw new IllegalArgumentException("Activity, TextureView and listener are required");
        }
        this.activity = activity;
        this.textureView = textureView;
        this.listener = listener;
        cameraManager = activity.getSystemService(CameraManager.class);
        displayManager = activity.getSystemService(DisplayManager.class);
        orientationListener = new OrientationEventListener(activity, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override public void onOrientationChanged(int orientation) {
                if (orientation != ORIENTATION_UNKNOWN) physicalDegrees = orientation;
            }
        };
        onMain(() -> {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    ensureRun();
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    if (current == null) ensureRun();
                    else transform(current);
                }
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    retireCurrent();
                    return true; // TextureView owns and releases its SurfaceTexture.
                }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                    // Preview is GPU-backed; never read or analyze frames here.
                }
            });
            textureView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                        if (current == null) ensureRun();
                        else transform(current);
                    }
                }
            });
        });
    }

    public void start() {
        onMain(() -> {
            wanted = true;
            updateDisplayRotation();
            if (!displayRegistered && displayManager != null) {
                displayManager.registerDisplayListener(displayListener, main);
                displayRegistered = true;
            }
            if (orientationListener.canDetectOrientation()) orientationListener.enable();
            ensureRun();
        });
    }

    /** Invalidates callbacks immediately; camera close and thread exit never block the UI. */
    public void stop() {
        onMain(() -> {
            wanted = false;
            stopOrientation();
            retireCurrent();
        });
    }

    public boolean isReady() {
        Run run = current;
        return ready && run != null && run.active && !run.capturing.get();
    }

    public void capture() {
        onMain(() -> {
            final Run run = current;
            if (!isReady() || run == null || !run.capturing.compareAndSet(false, true)) return;
            ready = false;
            final int rotation = jpegOrientation(run.sensorOrientation, run.front, run.external,
                    physicalDegrees, displayDegrees);
            run.handler.post(() -> beginCapture(run, rotation));
        });
    }

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId) { }
        @Override public void onDisplayRemoved(int displayId) { }
        @Override public void onDisplayChanged(int displayId) {
            Display display = textureView.getDisplay();
            if (display != null && display.getDisplayId() == displayId) {
                updateDisplayRotation();
                if (current != null) transform(current);
            }
        }
    };

    private void updateDisplayRotation() {
        Display display = textureView.getDisplay();
        if (display == null) display = activity.getWindowManager().getDefaultDisplay();
        displayDegrees = rotationDegrees(display.getRotation());
    }

    private void stopOrientation() {
        orientationListener.disable();
        physicalDegrees = OrientationEventListener.ORIENTATION_UNKNOWN;
        if (displayRegistered && displayManager != null) {
            displayManager.unregisterDisplayListener(displayListener);
            displayRegistered = false;
        }
    }

    // Main thread: never overlap a new open with an old, possibly still-pending openCamera.
    private void ensureRun() {
        if (!wanted || activity.isFinishing() || activity.isDestroyed() || current != null
                || retiring != null || !textureView.isAvailable()
                || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) return;
        SurfaceTexture surface = textureView.getSurfaceTexture();
        if (surface == null) return;
        Run run = new Run(++generation, surface, textureView.getWidth(), textureView.getHeight(), displayDegrees);
        current = run;
        ready = false;
        run.handler.post(() -> open(run));
    }

    private boolean live(Run run) {
        return run != null && run.active && current == run;
    }

    private void retireCurrent() {
        Run run = current;
        current = null;
        ready = false;
        if (run == null) return;
        run.active = false;
        retiring = run;
        run.handler.post(() -> close(run));
    }

    private void open(final Run run) {
        if (!live(run)) { close(run); return; }
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail(run, "需要相机权限才能拍照，仍可从相册选择图片");
            return;
        }
        if (cameraManager == null) { fail(run, "此设备暂不支持应用内相机，可从相册选择图片"); return; }
        try {
            String id = selectCamera(run);
            if (id == null) { fail(run, "未找到可用相机，可从相册选择图片"); return; }
            StreamConfigurationMap map = run.characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size jpeg = chooseJpegSize(map.getOutputSizes(ImageFormat.JPEG));
            run.previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class), jpeg,
                    run.viewWidth, run.viewHeight, run.sensorOrientation, run.front, run.initialDisplayDegrees);
            if (jpeg == null || run.previewSize == null) {
                fail(run, "相机没有可用的拍照尺寸，可从相册选择图片");
                return;
            }
            run.texture.setDefaultBufferSize(run.previewSize.getWidth(), run.previewSize.getHeight());
            run.previewSurface = new Surface(run.texture);
            run.reader = ImageReader.newInstance(jpeg.getWidth(), jpeg.getHeight(), ImageFormat.JPEG, 2);
            run.reader.setOnImageAvailableListener(reader -> imageAvailable(run, reader), run.handler);
            main.post(() -> { if (live(run)) transform(run); });
            run.opening = true;
            run.openTimeout = () -> fail(run, "相机启动超时，请重试或从相册选择图片");
            run.handler.postDelayed(run.openTimeout, OPEN_TIMEOUT_MS);
            cameraManager.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    run.opening = false;
                    cancelOpenTimeout(run);
                    if (!live(run)) { camera.close(); close(run); return; }
                    run.camera = camera;
                    configure(run);
                }
                @Override public void onDisconnected(CameraDevice camera) {
                    run.opening = false;
                    cancelOpenTimeout(run);
                    camera.close();
                    if (live(run)) fail(run, "相机连接已中断，请重试或从相册选择图片");
                    else close(run);
                }
                @Override public void onError(CameraDevice camera, int error) {
                    run.opening = false;
                    cancelOpenTimeout(run);
                    camera.close();
                    if (live(run)) fail(run, cameraError(error));
                    else close(run);
                }
            }, run.handler);
        } catch (SecurityException e) {
            run.opening = false;
            fail(run, "相机权限或系统相机隐私开关已关闭，仍可使用相册");
        } catch (CameraAccessException e) {
            run.opening = false;
            fail(run, "相机暂时不可用，请重试或从相册选择图片");
        } catch (RuntimeException e) {
            run.opening = false;
            fail(run, "无法启动相机，请重试或从相册选择图片");
        }
    }

    private String selectCamera(Run run) throws CameraAccessException {
        String[] ids = cameraManager.getCameraIdList();
        for (int pass = 0; pass < 2; pass++) {
            String bestId = null;
            CameraCharacteristics best = null;
            double bestView = -1;
            for (String id : ids) {
                CameraCharacteristics c;
                try { c = cameraManager.getCameraCharacteristics(id); }
                catch (CameraAccessException | RuntimeException ignored) { continue; }
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                boolean back = facing != null && facing == CameraCharacteristics.LENS_FACING_BACK;
                if ((pass == 0) != back) continue;
                if (Build.VERSION.SDK_INT >= 28 && contains(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME)) continue;
                StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size jpeg = map == null ? null : chooseJpegSize(map.getOutputSizes(ImageFormat.JPEG));
                if (jpeg == null) continue;
                Size[] preview = map.getOutputSizes(SurfaceTexture.class);
                Integer sensor = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
                if (choosePreviewSize(preview, jpeg, run.viewWidth, run.viewHeight, sensor == null ? 0 : sensor,
                        facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT,
                        run.initialDisplayDegrees) == null) continue;
                double view = widestView(c);
                // Only enumerated IDs are opened. Hidden physical IDs are metadata, not open targets.
                if (best == null || view > bestView) { bestId = id; best = c; bestView = view; }
            }
            if (best != null) {
                Integer facing = best.get(CameraCharacteristics.LENS_FACING);
                Integer sensor = best.get(CameraCharacteristics.SENSOR_ORIENTATION);
                run.sensorOrientation = sensor == null ? 0 : sensor;
                run.front = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
                run.external = facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL;
                run.characteristics = best;
                run.cameraId = bestId;
                run.zoomRatioSupported = supportsZoomRatio(best);
                run.minimumZoom = run.zoomRatioSupported
                        ? minimumZoomRatio(best.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)) : 1f;
                int[] af = best.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                if (contains(af, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                    run.afMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
                } else if (contains(af, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
                    run.afMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
                } else if (contains(af, CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                    run.afMode = CaptureRequest.CONTROL_AF_MODE_AUTO;
                } else {
                    run.afMode = CaptureRequest.CONTROL_AF_MODE_OFF;
                }
                run.aeOn = contains(best.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
                        CaptureRequest.CONTROL_AE_MODE_ON);
                return bestId;
            }
        }
        return null;
    }

    private double widestView(CameraCharacteristics c) {
        double base = fieldOfViewScore(c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE),
                c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS));
        if (!supportsZoomRatio(c)) return base;
        float zoom = minimumZoomRatio(c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE));
        if (zoom >= 1f) return base;
        double physicalView = -1;
        // For a logical zoom-out camera use physical sensor/focal pairs, avoiding double-counting
        // its shortest physical focal length and the logical 0.5x multiplier.
        for (String physicalId : c.getPhysicalCameraIds()) {
            try {
                CameraCharacteristics physical = cameraManager.getCameraCharacteristics(physicalId);
                Integer facing = physical.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
                int[] capabilities = physical.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                if (contains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME)
                        || (contains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)
                            && !contains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE))) continue;
                physicalView = Math.max(physicalView, fieldOfViewScore(
                        physical.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE),
                        physical.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)));
            } catch (CameraAccessException | RuntimeException ignored) { }
        }
        return physicalView > 0 ? physicalView : base / zoom;
    }

    private static boolean supportsZoomRatio(CameraCharacteristics c) {
        if (Build.VERSION.SDK_INT < 30 || !supports(c, CaptureRequest.CONTROL_ZOOM_RATIO)) return false;
        Range<Float> range = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        return range != null && validPositive(range.getLower()) && validPositive(range.getUpper());
    }

    static float minimumZoomRatio(Range<Float> range) {
        return range != null && validPositive(range.getLower()) && validPositive(range.getUpper())
                ? range.getLower() : 1f;
    }

    /** Proportional to tan(diagonal FoV / 2); sensor size prevents confusing tiny macro with wide. */
    static double fieldOfViewScore(SizeF sensor, float[] focals) {
        if (sensor == null || !validPositive(sensor.getWidth()) || !validPositive(sensor.getHeight())) return -1;
        float shortest = Float.POSITIVE_INFINITY;
        if (focals != null) for (float focal : focals) if (validPositive(focal)) shortest = Math.min(shortest, focal);
        return Float.isInfinite(shortest) ? -1 : Math.hypot(sensor.getWidth(), sensor.getHeight()) / shortest;
    }

    private static boolean validPositive(float value) { return value > 0 && !Float.isInfinite(value) && !Float.isNaN(value); }

    private static boolean supports(CameraCharacteristics c, CaptureRequest.Key<?> key) {
        List<CaptureRequest.Key<?>> keys = c.getAvailableCaptureRequestKeys();
        return keys != null && keys.contains(key);
    }

    private void configure(final Run run) {
        if (!live(run)) return;
        try {
            final CaptureRequest.Builder preview = run.camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            preview.addTarget(run.previewSurface);
            autoControls(run, preview);
            run.previewRequest = preview.build();
            run.camera.createCaptureSession(Arrays.asList(run.previewSurface, run.reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (!live(run)) { session.close(); return; }
                    run.session = session;
                    try { session.setRepeatingRequest(run.previewRequest, previewCallback(run), run.handler); }
                    catch (CameraAccessException | RuntimeException e) {
                        fail(run, "相机预览启动失败，请重试或从相册选择图片");
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    session.close();
                    if (live(run)) fail(run, "无法配置相机预览，请重试或从相册选择图片");
                }
            }, run.handler);
        } catch (CameraAccessException | RuntimeException e) {
            fail(run, "相机预览启动失败，请重试或从相册选择图片");
        }
    }

    private CameraCaptureSession.CaptureCallback previewCallback(final Run run) {
        return new CameraCaptureSession.CaptureCallback() {
            @Override public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                    TotalCaptureResult result) {
                if (!live(run)) return;
                if (!run.previewReady) {
                    run.previewReady = true;
                    notifyReady(run);
                }
                checkFocus(run, result);
            }
            @Override public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                    CaptureResult result) {
                if (live(run)) checkFocus(run, result);
            }
        };
    }

    private void autoControls(Run run, CaptureRequest.Builder request) {
        if (contains(run.characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_MODES),
                CaptureRequest.CONTROL_MODE_AUTO)) {
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        }
        if (contains(run.characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES), run.afMode)) {
            request.set(CaptureRequest.CONTROL_AF_MODE, run.afMode);
        }
        if (run.aeOn) request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        if (contains(run.characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
                CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
            request.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
        }
        applyWideView(run, request);
        // No auto-flash, location metadata, per-frame bitmap decoding, or image upload.
    }

    private void applyWideView(Run run, CaptureRequest.Builder request) {
        CameraCharacteristics c = run.characteristics;
        // Apply to EVERY template: focus, still capture and unlock must not jump back to 1x.
        if (Build.VERSION.SDK_INT >= 30 && run.zoomRatioSupported) {
            request.set(CaptureRequest.CONTROL_ZOOM_RATIO, run.minimumZoom);
        }
        boolean logical = Build.VERSION.SDK_INT >= 28 && contains(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
        if (!logical && supports(c, CaptureRequest.LENS_FOCAL_LENGTH)) {
            float[] focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            float shortest = Float.POSITIVE_INFINITY;
            if (focals != null) for (float focal : focals) if (validPositive(focal)) shortest = Math.min(shortest, focal);
            if (!Float.isInfinite(shortest)) request.set(CaptureRequest.LENS_FOCAL_LENGTH, shortest);
        }
        Rect active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (active != null && active.width() > 0 && active.height() > 0 && supports(c, CaptureRequest.SCALER_CROP_REGION)) {
            request.set(CaptureRequest.SCALER_CROP_REGION, active);
        }
        if (contains(c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                && supports(c, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE)) {
            request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        }
        if (Build.VERSION.SDK_INT >= 31 && supports(c, CaptureRequest.SCALER_ROTATE_AND_CROP)
                && contains(c.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES),
                        CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)) {
            request.set(CaptureRequest.SCALER_ROTATE_AND_CROP, CaptureRequest.SCALER_ROTATE_AND_CROP_NONE);
        }
    }

    private void beginCapture(final Run run, int orientation) {
        if (!live(run)) return;
        if (run.camera == null || run.session == null || run.reader == null || !run.previewReady) {
            fail(run, "相机尚未准备好，请重试");
            return;
        }
        run.jpegRotation = orientation;
        final long shot = ++run.shot;
        run.awaitingFocus = true;
        run.shotTimeout = () -> {
            if (live(run) && run.capturing.get() && run.shot == shot) {
                fail(run, "拍照超时，请重试或从相册选择图片");
            }
        };
        run.handler.postDelayed(run.shotTimeout, SHOT_TIMEOUT_MS);
        run.focusTimeout = () -> {
            if (live(run) && run.awaitingFocus && run.shot == shot) takeJpeg(run);
        };
        run.handler.postDelayed(run.focusTimeout, FOCUS_TIMEOUT_MS);
        try {
            CaptureRequest.Builder focus = run.camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            focus.addTarget(run.previewSurface);
            autoControls(run, focus);
            if (run.afMode != CaptureRequest.CONTROL_AF_MODE_OFF) {
                focus.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            }
            if (run.aeOn) {
                focus.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            }
            run.session.capture(focus.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                        TotalCaptureResult result) {
                    if (live(run) && run.shot == shot) checkFocus(run, result);
                }
            }, run.handler);
        } catch (CameraAccessException | RuntimeException e) {
            // Some legacy devices reject triggers despite advertising auto modes. A normal still
            // request retains continuous AF/AE; the user does not lose the shutter action.
            takeJpeg(run);
        }
    }

    private void checkFocus(Run run, CaptureResult result) {
        if (!run.awaitingFocus || !run.capturing.get()) return;
        Integer af = result.get(CaptureResult.CONTROL_AF_STATE);
        Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
        boolean focusReady = run.afMode == CaptureRequest.CONTROL_AF_MODE_OFF || af == null
                || af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                || af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                || af == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED
                || af == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED;
        boolean exposureReady = !run.aeOn || ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED
                || ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED || ae == CaptureResult.CONTROL_AE_STATE_LOCKED;
        if (focusReady && exposureReady) takeJpeg(run);
    }

    private void takeJpeg(final Run run) {
        if (!live(run) || !run.awaitingFocus || !run.capturing.get()) return;
        run.awaitingFocus = false;
        if (run.focusTimeout != null) run.handler.removeCallbacks(run.focusTimeout);
        try {
            // Discard only stale, unowned JPEG buffers before issuing this single still request.
            Image old;
            while ((old = run.reader.acquireNextImage()) != null) old.close();
            CaptureRequest.Builder shot = run.camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            shot.addTarget(run.reader.getSurface());
            autoControls(run, shot);
            shot.set(CaptureRequest.JPEG_ORIENTATION, run.jpegRotation);
            shot.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
            run.awaitingJpeg = true;
            run.session.stopRepeating();
            run.session.capture(shot.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                        TotalCaptureResult result) {
                    if (live(run)) resumePreview(run);
                }
                @Override public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                        CaptureFailure failure) {
                    if (live(run)) fail(run, "拍照失败，请重试或从相册选择图片");
                }
                @Override public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
                    if (live(run) && run.awaitingJpeg) fail(run, "拍照已中断，请重试");
                }
            }, run.handler);
        } catch (CameraAccessException | RuntimeException e) {
            fail(run, "无法完成拍照，请重试或从相册选择图片");
        }
    }

    private void resumePreview(Run run) {
        if (!live(run) || run.camera == null || run.session == null) return;
        try {
            if (run.afMode != CaptureRequest.CONTROL_AF_MODE_OFF) {
                CaptureRequest.Builder unlock = run.camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                unlock.addTarget(run.previewSurface);
                autoControls(run, unlock);
                unlock.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                run.session.capture(unlock.build(), null, run.handler);
            }
            run.session.setRepeatingRequest(run.previewRequest, previewCallback(run), run.handler);
        } catch (CameraAccessException | RuntimeException e) {
            fail(run, "相机预览已中断，请重试");
        }
    }

    private void imageAvailable(Run run, ImageReader reader) {
        Image image = null;
        byte[] jpeg = null;
        try {
            image = reader.acquireNextImage();
            if (image == null || !live(run) || !run.awaitingJpeg || !run.capturing.get()) return;
            ByteBuffer bytes = image.getPlanes()[0].getBuffer();
            int count = bytes.remaining();
            if (count <= 0 || count > MAX_JPEG_BYTES) {
                fail(run, "相机照片过大或无效，请重试");
                return;
            }
            jpeg = new byte[count];
            bytes.get(jpeg);
        } catch (RuntimeException | OutOfMemoryError e) {
            if (live(run)) fail(run, "无法读取相机照片，请重试");
        } finally {
            if (image != null) image.close();
        }
        if (jpeg == null || !live(run)) return;
        run.awaitingJpeg = false;
        run.capturing.set(false);
        if (run.shotTimeout != null) run.handler.removeCallbacks(run.shotTimeout);
        final byte[] result = jpeg;
        main.post(() -> {
            if (!live(run) || activity.isFinishing() || activity.isDestroyed()) return;
            ready = true;
            listener.onJpeg(result);
        });
    }

    private void notifyReady(Run run) {
        main.post(() -> {
            if (!live(run) || run.capturing.get() || activity.isFinishing() || activity.isDestroyed()) return;
            ready = true;
            listener.onReady();
        });
    }

    private void fail(Run run, String message) {
        if (!live(run) || run.failed) return;
        run.failed = true;
        main.post(() -> {
            if (current != run || !run.active) return;
            // A manual start() can retry after the error. Avoid an infinite auto-open/error loop.
            wanted = false;
            stopOrientation();
            retireCurrent();
            if (!activity.isFinishing() && !activity.isDestroyed()) listener.onError(message);
        });
    }

    private void cancelOpenTimeout(Run run) {
        if (run.openTimeout != null) run.handler.removeCallbacks(run.openTimeout);
    }

    // Camera handler only. An outstanding open must deliver its callback before this handler exits:
    // otherwise a late onOpened could be dropped, leaving an uncloseable camera in the process.
    private void close(Run run) {
        if (run.closed) return;
        cancelOpenTimeout(run);
        if (run.focusTimeout != null) run.handler.removeCallbacks(run.focusTimeout);
        if (run.shotTimeout != null) run.handler.removeCallbacks(run.shotTimeout);
        run.awaitingFocus = false;
        run.awaitingJpeg = false;
        run.capturing.set(false);
        if (run.session != null) {
            try { run.session.close(); } catch (RuntimeException ignored) { }
            run.session = null;
        }
        if (run.camera != null) {
            try { run.camera.close(); } catch (RuntimeException ignored) { }
            run.camera = null;
        }
        if (run.reader != null) {
            try {
                run.reader.setOnImageAvailableListener(null, null);
                run.reader.close();
            } catch (RuntimeException ignored) { }
            run.reader = null;
        }
        if (run.previewSurface != null) {
            run.previewSurface.release();
            run.previewSurface = null;
        }
        if (run.opening) return;
        run.closed = true;
        run.thread.quitSafely();
        main.post(() -> {
            if (retiring == run) {
                retiring = null;
                ensureRun();
            }
        });
    }

    private void transform(Run run) {
        if (!live(run) || run.previewSize == null || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) return;
        textureView.setTransform(previewTransform(textureView.getWidth(), textureView.getHeight(),
                run.previewSize, run.sensorOrientation, displayDegrees));
    }

    /** TextureView already applies sensor orientation; undo its stretch, then display rotation. */
    static Matrix previewTransform(int width, int height, Size buffer, int sensorOrientation, int displayRotation) {
        Matrix matrix = new Matrix();
        if (width <= 0 || height <= 0 || buffer == null) return matrix;
        boolean sensorSwapped = sensorOrientation % 180 != 0;
        float naturalWidth = sensorSwapped ? buffer.getHeight() : buffer.getWidth();
        float naturalHeight = sensorSwapped ? buffer.getWidth() : buffer.getHeight();
        boolean displaySwapped = displayRotation % 180 != 0;
        float displayedWidth = displaySwapped ? naturalHeight : naturalWidth;
        float displayedHeight = displaySwapped ? naturalWidth : naturalHeight;
        // Fit the entire camera buffer. Unusual sensor ratios get bars, never extra digital zoom.
        float scale = Math.min(width / displayedWidth, height / displayedHeight);
        float cx = width / 2f, cy = height / 2f;
        matrix.setScale(scale * naturalWidth / width, scale * naturalHeight / height, cx, cy);
        matrix.postRotate(-displayRotation, cx, cy);
        return matrix;
    }

    static int jpegOrientation(int sensorOrientation, boolean front, boolean external,
            int physicalOrientation, int displayRotation) {
        if (external) return 0;
        // OrientationEventListener is clockwise; Display.getRotation is counterclockwise.
        int clockwise = physicalOrientation == OrientationEventListener.ORIENTATION_UNKNOWN
                ? -displayRotation : ((physicalOrientation + 45) / 90 * 90) % 360;
        return (sensorOrientation + (front ? -clockwise : clockwise) + 720) % 360;
    }

    static Size chooseJpegSize(Size[] sizes) {
        if (sizes == null) return null;
        Size best = null, smallest = null;
        for (Size size : sizes) {
            if (size == null || size.getWidth() <= 0 || size.getHeight() <= 0) continue;
            long area = pixels(size);
            if (smallest == null || area < pixels(smallest)) smallest = size;
            if (area <= MAX_JPEG_PIXELS && (best == null || area > pixels(best))) best = size;
        }
        if (best == null) return smallest;
        long minimumPixels = Math.min(1_000_000L, pixels(best));
        Size matched = null;
        double bestRatioError = Double.POSITIVE_INFINITY;
        for (Size size : sizes) {
            if (size == null || size.getWidth() <= 0 || size.getHeight() <= 0
                    || pixels(size) > MAX_JPEG_PIXELS || pixels(size) < minimumPixels) continue;
            double ratio = (double) Math.max(size.getWidth(), size.getHeight()) / Math.min(size.getWidth(), size.getHeight());
            double error = Math.abs(Math.log(ratio / (4.0 / 3.0)));
            if (matched == null || error < bestRatioError - .005
                    || (Math.abs(error - bestRatioError) <= .005 && pixels(size) > pixels(matched))) {
                matched = size;
                bestRatioError = error;
            }
        }
        return matched == null ? best : matched;
    }

    static Size choosePreviewSize(Size[] sizes, Size jpeg, int width, int height,
            int sensorOrientation, boolean front, int displayRotation) {
        if (sizes == null || jpeg == null) return null;
        boolean swapped = ((sensorOrientation + (front ? -displayRotation : displayRotation) + 360) % 180) != 0;
        int targetWidth = swapped ? height : width;
        int targetHeight = swapped ? width : height;
        double wantedRatio = (double) jpeg.getWidth() / jpeg.getHeight();
        Size best = null;
        double score = Double.POSITIVE_INFINITY;
        for (Size size : sizes) {
            if (size == null || size.getWidth() <= 0 || size.getHeight() <= 0
                    || Math.max(size.getWidth(), size.getHeight()) > MAX_PREVIEW_EDGE
                    || pixels(size) > MAX_PREVIEW_PIXELS) continue;
            // Keep the JPEG field of view when possible; then prefer enough (not excessive) pixels.
            double ratioError = Math.abs(Math.log(((double) size.getWidth() / size.getHeight()) / wantedRatio));
            double requiredScale = Math.max((double) targetWidth / size.getWidth(),
                    (double) targetHeight / size.getHeight());
            double resolutionCost = requiredScale > 1.0 ? 1.0 + Math.log(requiredScale) : -Math.log(requiredScale) * 0.15;
            double candidate = ratioError * 20.0 + resolutionCost;
            if (best == null || candidate < score) { best = size; score = candidate; }
        }
        return best;
    }

    private static long pixels(Size size) { return (long) size.getWidth() * size.getHeight(); }
    private static boolean contains(int[] modes, int mode) {
        if (modes != null) for (int value : modes) if (value == mode) return true;
        return false;
    }
    private static int rotationDegrees(int rotation) {
        if (rotation == Surface.ROTATION_90) return 90;
        if (rotation == Surface.ROTATION_180) return 180;
        if (rotation == Surface.ROTATION_270) return 270;
        return 0;
    }
    private static String cameraError(int error) {
        if (error == CameraDevice.StateCallback.ERROR_CAMERA_IN_USE
                || error == CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE) {
            return "相机正被其他应用使用，请关闭后重试，或从相册选择图片";
        }
        if (error == CameraDevice.StateCallback.ERROR_CAMERA_DISABLED) {
            return "相机被系统停用，请检查相机权限及隐私开关，或使用相册";
        }
        return "相机暂时不可用，请重试或从相册选择图片";
    }
    private void onMain(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else main.post(task);
    }
}
