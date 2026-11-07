package jp.dharmainsight.shizukacamera;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.MediaRecorder;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.RejectedExecutionException;

final class CameraEngine {
    interface Listener {
        void onReady(Info info);
        void onRecordingStarted(Info info);
        void onVideo(Uri uri, String error);
        void onStatus(String text, boolean error);
        void onPhoto(Uri uri, Bitmap thumbnail, String error);
        void onQrUrl(String url);
    }

    static final class Info {
        final int width, height, sensorRotation;
        final boolean front, flash, switchable;
        final float maxZoom, minEv, maxEv;
        Info(Size size, int rotation, boolean front, boolean flash, boolean switchable,
             float maxZoom, float minEv, float maxEv) {
            width = size.getWidth(); height = size.getHeight(); sensorRotation = rotation;
            this.front = front; this.flash = flash; this.switchable = switchable;
            this.maxZoom = maxZoom; this.minEv = minEv; this.maxEv = maxEv;
        }
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("SantiCam");
    private final Handler worker;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private final AtomicBoolean saving = new AtomicBoolean();
    private final AtomicBoolean scanPending = new AtomicBoolean();
    private final CameraManager manager;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private Surface previewSurface;
    private SurfaceTexture texture;
    private CameraCharacteristics characteristics;
    private CaptureRequest.Builder request;
    private Rect activeArray, crop;
    private Range<Integer> exposureRange;
    private float exposureStep = 1f, zoom = 1f, maxZoom = 1f;
    private int generation, configuration, focusGeneration, pendingOrientation = -1;
    private int sensorRotation = 90, fallbackLevel;
    private boolean front, flashAvailable, switchable, frameSeen, torch;
    private Size frameSize;
    private volatile boolean destroyed;
    private MediaRecorder recorder;
    private ParcelFileDescriptor videoFile;
    private Uri videoUri;
    private boolean recording, qrEnabled;
    private long lastQrSample;

    CameraEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        thread.start(); worker = new Handler(thread.getLooper());
    }

    void start(SurfaceTexture output, boolean useFront, boolean scanQr) {
        if (destroyed) return;
        worker.post(() -> {
            ++generation; closeCamera();
            texture = output; front = useFront; zoom = 1; torch = false;
            qrEnabled = scanQr; lastQrSample = 0;
            int token = generation;
            emitStatus("カメラを準備しています", false);
            try {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    emitStatus("カメラの使用を許可してください", true); return;
                }
                String id = selectCamera(useFront);
                if (id == null) { emitStatus("利用できるカメラがありません", true); return; }
                characteristics = manager.getCameraCharacteristics(id);
                front = Integer.valueOf(CameraCharacteristics.LENS_FACING_FRONT).equals(characteristics.get(CameraCharacteristics.LENS_FACING));
                Integer rotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                sensorRotation = rotation == null ? 90 : rotation;
                activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if (activeArray == null) throw new IllegalStateException("Missing sensor area");
                crop = new Rect(activeArray);
                Float maximum = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                maxZoom = Math.min(8f, maximum == null ? 1f : maximum);
                flashAvailable = Boolean.TRUE.equals(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
                exposureRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                exposureStep = step == null || step.floatValue() <= 0 ? 1f : step.floatValue();
                Integer hardware = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                fallbackLevel = hardware != null && (hardware == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL || hardware == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3) ? 0 : 2;
                manager.openCamera(id, new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice camera) {
                        if (token != generation || destroyed) { camera.close(); return; }
                        device = camera; configure(token);
                    }
                    @Override public void onDisconnected(CameraDevice camera) {
                        camera.close();
                        if (token == generation) { closeCamera(); emitStatus("カメラが切断されました。再接続を押してください", true); }
                    }
                    @Override public void onError(CameraDevice camera, int error) {
                        camera.close();
                        if (token == generation) {
                            closeCamera();
                            emitStatus(error == ERROR_CAMERA_IN_USE || error == ERROR_MAX_CAMERAS_IN_USE
                                ? "別のアプリでカメラを使用中です" : "カメラを開けません。端末のカメラ設定も確認してください", true);
                        }
                    }
                }, worker);
            } catch (CameraAccessException | RuntimeException error) {
                closeCamera(); emitStatus("カメラを開けません。権限と端末のカメラ設定を確認してください", true);
            }
        });
    }

    void setQrEnabled(boolean enabled) {
        if (!destroyed) worker.post(() -> qrEnabled = enabled);
    }

    private String selectCamera(boolean useFront) throws CameraAccessException {
        String selected = null, any = null; boolean hasFront = false, hasBack = false;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            if (!has(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) continue;
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (any == null) any = id;
            if (Integer.valueOf(CameraCharacteristics.LENS_FACING_FRONT).equals(facing)) hasFront = true;
            if (Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(facing)) hasBack = true;
            if (selected == null && Integer.valueOf(useFront ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK).equals(facing)) selected = id;
        }
        switchable = hasFront && hasBack;
        return selected == null ? any : selected;
    }

    private Size selectFrameSize(StreamConfigurationMap map) {
        Size[] available = map.getOutputSizes(ImageFormat.YUV_420_888);
        if (available == null || available.length == 0) throw new IllegalStateException("No YUV stream");
        long cap = fallbackLevel == 0 ? 12600000L : fallbackLevel == 1 ? 3200000L : 2073600L;
        List<Size> choices = new ArrayList<>();
        for (Size size : available) {
            long area = (long) size.getWidth() * size.getHeight();
            if ((size.getWidth() & 1) != 0 || (size.getHeight() & 1) != 0 || area > cap) continue;
            if (fallbackLevel == 2 && (size.getWidth() > 1920 || size.getHeight() > 1080)) continue;
            choices.add(size);
        }
        if (choices.isEmpty()) return Arrays.stream(available).min(Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight())).get();
        choices.sort((a, b) -> {
            double ratioA = Math.abs((double) a.getWidth() / a.getHeight() - 4.0 / 3.0);
            double ratioB = Math.abs((double) b.getWidth() / b.getHeight() - 4.0 / 3.0);
            boolean goodA = ratioA < 0.025, goodB = ratioB < 0.025;
            if (goodA != goodB) return goodA ? -1 : 1;
            return Long.compare((long) b.getWidth() * b.getHeight(), (long) a.getWidth() * a.getHeight());
        });
        return choices.get(0);
    }

    private void configure(int token) {
        if (device == null || token != generation) return;
        closeSession();
        int configToken = ++configuration;
        try {
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) throw new IllegalStateException("No camera stream map");
            frameSize = selectFrameSize(map);
            Size[] previews = map.getOutputSizes(SurfaceTexture.class);
            if (previews == null || previews.length == 0) throw new IllegalStateException("No preview stream");
            double ratio = (double) frameSize.getWidth() / frameSize.getHeight();
            Size previewSize = Arrays.stream(previews).filter(s -> s.getWidth() <= 1920 && s.getHeight() <= 1080)
                .min(Comparator.comparingDouble(s -> Math.abs((double) s.getWidth() / s.getHeight() - ratio) * 100000000 - (long) s.getWidth() * s.getHeight()))
                .orElse(previews[0]);
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewSurface = new Surface(texture);
            reader = ImageReader.newInstance(frameSize.getWidth(), frameSize.getHeight(), ImageFormat.YUV_420_888, 3);
            reader.setOnImageAvailableListener(source -> receiveFrame(source, token, configToken), worker);
            request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(previewSurface); request.addTarget(reader.getSurface());
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            request.set(CaptureRequest.FLASH_MODE, torch ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            request.set(CaptureRequest.SCALER_CROP_REGION, crop);
            setContinuousFocus();
            Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (ranges != null) {
                Range<Integer> best = null;
                for (Range<Integer> r : ranges) if (r.getUpper() <= 30 && r.getUpper() >= 15 && (best == null || r.getUpper() > best.getUpper() || (r.getUpper().equals(best.getUpper()) && r.getLower() < best.getLower()))) best = r;
                if (best != null) request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, best);
            }
            device.createCaptureSession(Arrays.asList(previewSurface, reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    if (token != generation || configToken != configuration || device == null) { configured.close(); return; }
                    session = configured;
                    try { repeat(); } catch (CameraAccessException | RuntimeException e) { fallback(token, configToken); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) {
                    failed.close(); fallback(token, configToken);
                }
            }, worker);
            worker.postDelayed(() -> {
                if (token == generation && configToken == configuration && !frameSeen) fallback(token, configToken);
            }, 7000);
        } catch (CameraAccessException | RuntimeException e) { fallback(token, configToken); }
    }

    private void fallback(int token, int configToken) {
        if (token != generation || configToken != configuration) return;
        if (fallbackLevel < 2) {
            fallbackLevel++; emitStatus("カメラに合う画質へ調整しています", false); configure(token);
        } else { closeCamera(); emitStatus("映像を取得できません。再接続を押してください", true); }
    }

    private void receiveFrame(ImageReader source, int token, int configToken) {
        if (token != generation || configToken != configuration) return;
        try (Image image = source.acquireLatestImage()) {
            if (image == null) return;
            if (!frameSeen) {
                frameSeen = true;
                float min = exposureRange == null ? 0 : Math.max(-2, exposureRange.getLower() * exposureStep);
                float max = exposureRange == null ? 0 : Math.min(2, exposureRange.getUpper() * exposureStep);
                Info info = new Info(frameSize, sensorRotation, front, flashAvailable, switchable, maxZoom, min, max);
                main.post(() -> { if (!destroyed) listener.onReady(info); });
            }
            if (qrEnabled && !saving.get()) maybeScanQr(image, token, configToken);
            if (pendingOrientation < 0) return;
            int rotation = CameraMath.jpegRotation(sensorRotation, pendingOrientation, front);
            pendingOrientation = -1;
            Rect r = image.getCropRect();
            Image.Plane[] p = image.getPlanes();
            byte[] bytes = YuvPacking.nv21(r.width(), r.height(), r.left, r.top,
                p[0].getBuffer(), p[0].getRowStride(), p[0].getPixelStride(),
                p[1].getBuffer(), p[1].getRowStride(), p[1].getPixelStride(),
                p[2].getBuffer(), p[2].getRowStride(), p[2].getPixelStride());
            int width = r.width(), height = r.height();
            writer.execute(() -> save(bytes, width, height, rotation));
        } catch (RuntimeException error) {
            if (saving.get()) { pendingOrientation = -1; saving.set(false); photoResult(null, null, "写真を読み取れませんでした。もう一度撮影してください"); }
        }
    }

    // Read a compact luminance copy every ~450 ms, never decoding on the camera thread.
    private void maybeScanQr(Image image, int token, int configToken) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastQrSample < 450 || !scanPending.compareAndSet(false, true)) return;
        lastQrSample = now;
        final QrFrameSampler.Frame frame;
        try {
            Rect area = image.getCropRect();
            Image.Plane y = image.getPlanes()[0];
            frame = QrFrameSampler.sample(y.getBuffer(), y.getRowStride(), y.getPixelStride(),
                area.left, area.top, area.width(), area.height());
        } catch (RuntimeException failure) {
            scanPending.set(false); return;
        }
        try {
            scanner.execute(() -> {
                try {
                    String link = QrLinkPolicy.accept(QrScanner.decode(frame));
                    if (link != null && !destroyed) {
                        worker.post(() -> {
                            if (token == generation && configToken == configuration && qrEnabled &&
                                !destroyed && recorder == null) {
                                main.post(() -> { if (!destroyed) listener.onQrUrl(link); });
                            }
                        });
                    }
                } catch (RuntimeException ignored) {
                    // QR decode failure must not affect photography.
                } finally {
                    scanPending.set(false);
                }
            });
        } catch (RejectedExecutionException shutdown) {
            scanPending.set(false);
        }
    }

    void shoot(int orientation) {
        if (destroyed) return;
        worker.post(() -> {
            if (!frameSeen || session == null) { photoResult(null, null, "カメラの準備を待ってください"); return; }
            if (!saving.compareAndSet(false, true)) return;
            pendingOrientation = orientation;
            int token = generation;
            worker.postDelayed(() -> {
                if (token == generation && pendingOrientation >= 0) {
                    pendingOrientation = -1; saving.set(false);
                    photoResult(null, null, "映像が止まっています。再接続して撮影してください");
                }
            }, 4000);
        });
    }

    // All recorder/session state is confined to the camera worker.
    void startVideo(int orientation, boolean audio) {
        if (destroyed) return;
        worker.post(() -> {
            if (device == null || !frameSeen || recorder != null || saving.get()) {
                videoResult(null, "カメラの準備を待ってください"); return;
            }
            int token = generation;
            closeSession();
            int configToken = configuration;
            try {
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = map.getOutputSizes(MediaRecorder.class);
                Size[] previews = map.getOutputSizes(SurfaceTexture.class);
                if (sizes == null || previews == null) throw new IOException("No video stream");
                Size size = Arrays.stream(sizes)
                    .filter(v -> v.getWidth() <= 1920 && v.getHeight() <= 1080 && Arrays.asList(previews).contains(v))
                    .max(Comparator.comparingLong(v -> (long) v.getWidth() * v.getHeight()))
                    .orElseThrow(() -> new IllegalStateException("No compatible video size"));
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, "SANTICAM_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + ".mp4");
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/SantiCam");
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
                videoUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (videoUri == null) throw new IOException("No video output");
                videoFile = context.getContentResolver().openFileDescriptor(videoUri, "rw");
                if (videoFile == null) throw new IOException("No video descriptor");
                recorder = new MediaRecorder(context);
                if (audio) recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                if (audio) { recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); recorder.setAudioEncodingBitRate(128000); recorder.setAudioSamplingRate(44100); }
                recorder.setVideoSize(size.getWidth(), size.getHeight());
                recorder.setVideoFrameRate(30); recorder.setVideoEncodingBitRate(10000000);
                recorder.setOrientationHint(CameraMath.jpegRotation(sensorRotation, orientation, front));
                recorder.setOutputFile(videoFile.getFileDescriptor());
                recorder.setOnErrorListener((r, what, extra) -> { if (r == recorder) finishVideo(true, "動画の録画を継続できませんでした"); });
                recorder.prepare();
                texture.setDefaultBufferSize(size.getWidth(), size.getHeight());
                previewSurface = new Surface(texture);
                Surface videoSurface = recorder.getSurface();
                request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                request.addTarget(previewSurface); request.addTarget(videoSurface);
                request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                request.set(CaptureRequest.SCALER_CROP_REGION, crop);
                request.set(CaptureRequest.FLASH_MODE, torch ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
                int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                if (has(modes, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                frameSize = size;
                device.createCaptureSession(Arrays.asList(previewSurface, videoSurface), new CameraCaptureSession.StateCallback() {
                    @Override public void onConfigured(CameraCaptureSession configured) {
                        if (token != generation || configToken != configuration || recorder == null) { configured.close(); return; }
                        session = configured;
                        try {
                            repeat(); recorder.start(); recording = true;
                            Info info = new Info(size, sensorRotation, front, flashAvailable, switchable, maxZoom, 0, 0);
                            main.post(() -> { if (!destroyed) listener.onRecordingStarted(info); });
                        } catch (CameraAccessException | RuntimeException e) { finishVideo(true, "動画を開始できませんでした"); }
                    }
                    @Override public void onConfigureFailed(CameraCaptureSession failed) {
                        failed.close();
                        if (token == generation && configToken == configuration) finishVideo(true, "動画の設定に対応していません");
                    }
                }, worker);
                worker.postDelayed(() -> {
                    if (token == generation && configToken == configuration && !recording && recorder != null)
                        finishVideo(true, "動画の準備がタイムアウトしました");
                }, 7000);
            } catch (IOException | CameraAccessException | RuntimeException e) { finishVideo(true, "動画を開始できませんでした。空き容量と権限を確認してください"); }
        });
    }

    void stopVideo() { if (!destroyed) worker.post(() -> { if (recorder != null || videoUri != null) finishVideo(true, null); }); }

    private void finishVideo(boolean restart, String error) {
        Uri result = videoUri;
        boolean valid = recording;
        if (session != null) try { session.stopRepeating(); session.abortCaptures(); } catch (CameraAccessException | RuntimeException ignored) {}
        if (recorder != null) {
            if (recording) try { recorder.stop(); } catch (RuntimeException e) { valid = false; error = "録画が短すぎるか、動画を保存できませんでした"; }
            try { recorder.release(); } catch (RuntimeException ignored) {}
        }
        recorder = null; recording = false; videoUri = null;
        if (videoFile != null) try { videoFile.close(); } catch (IOException ignored) {}
        videoFile = null;
        if (result != null) {
            try {
                if (valid && error == null) {
                    ContentValues publish = new ContentValues(); publish.put(MediaStore.Video.Media.IS_PENDING, 0);
                    if (context.getContentResolver().update(result, publish, null, null) != 1) throw new IOException("Publish failed");
                } else { context.getContentResolver().delete(result, null, null); result = null; }
            } catch (IOException | RuntimeException e) {
                try { context.getContentResolver().delete(result, null, null); } catch (RuntimeException ignored) {}
                result = null; error = "動画を保存できませんでした";
            }
        }
        closeSession();
        videoResult(result, error == null && result == null ? "録画開始前に中止しました" : error);
        if (restart && device != null && !destroyed) configure(generation);
    }

    private void videoResult(Uri uri, String error) {
        main.post(() -> { if (!destroyed) listener.onVideo(uri, error); });
    }

    void setZoom(float value) {
        if (destroyed) return;
        worker.post(() -> {
            if (request == null || activeArray == null) return;
            zoom = CameraMath.clamp(value, 1, maxZoom);
            int width = Math.max(2, ((int) (activeArray.width() / zoom)) & ~1);
            int height = Math.max(2, ((int) (activeArray.height() / zoom)) & ~1);
            int left = activeArray.left + (activeArray.width() - width) / 2;
            int top = activeArray.top + (activeArray.height() - height) / 2;
            crop = new Rect(left, top, left + width, top + height);
            request.set(CaptureRequest.SCALER_CROP_REGION, crop); update();
        });
    }

    void setExposure(float ev) {
        if (destroyed) return;
        worker.post(() -> {
            if (request == null || exposureRange == null) return;
            request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureRange.clamp(Math.round(ev / exposureStep))); update();
        });
    }

    void setTorch(boolean enabled) {
        if (destroyed) return;
        worker.post(() -> {
            if (request == null || !flashAvailable) return;
            torch = enabled; request.set(CaptureRequest.FLASH_MODE, torch ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF); update();
        });
    }

    void focus(float x, float y) {
        if (destroyed) return;
        worker.post(() -> {
            if (request == null || session == null || crop == null) return;
            int token = generation, focusToken = ++focusGeneration;
            float[] point = CameraMath.sensorPoint(x, y, sensorRotation, front);
            Rect target = new Rect(crop);
            double aspect = (double) frameSize.getWidth() / frameSize.getHeight();
            if ((double) target.width() / target.height() > aspect) {
                int dx = (target.width() - (int) (target.height() * aspect)) / 2; target.inset(dx, 0);
            } else {
                int dy = (target.height() - (int) (target.width() / aspect)) / 2; target.inset(0, dy);
            }
            int side = Math.max(2, Math.min(target.width(), target.height()) / 8);
            int left = Math.max(target.left, Math.min(target.right - side, target.left + (int) (target.width() * point[0]) - side / 2));
            int top = Math.max(target.top, Math.min(target.bottom - side, target.top + (int) (target.height() * point[1]) - side / 2));
            MeteringRectangle[] region = {new MeteringRectangle(left, top, side, side, MeteringRectangle.METERING_WEIGHT_MAX)};
            Integer afRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            Integer aeRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
            try {
                if (aeRegions != null && aeRegions > 0) request.set(CaptureRequest.CONTROL_AE_REGIONS, region);
                int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                if (has(modes, CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                    request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                    session.capture(request.build(), null, worker);
                    request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                    if (afRegions != null && afRegions > 0) request.set(CaptureRequest.CONTROL_AF_REGIONS, region);
                    request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                    session.capture(request.build(), null, worker);
                    request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                }
                repeat();
                worker.postDelayed(() -> {
                    if (token == generation && focusToken == focusGeneration && request != null) { setContinuousFocus(); update(); }
                }, 3000);
            } catch (CameraAccessException | RuntimeException ignored) { /* Continuous AF remains available. */ }
        });
    }

    private void setContinuousFocus() {
        int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (has(modes, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        else if (has(modes, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        else request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
    }

    private void repeat() throws CameraAccessException { if (session != null && request != null) session.setRepeatingRequest(request.build(), null, worker); }
    private void update() {
        try { repeat(); } catch (CameraAccessException | RuntimeException e) { emitStatus("設定を変更できません。再接続を押してください", true); }
    }
    private static boolean has(int[] values, int value) { if (values != null) for (int v : values) if (v == value) return true; return false; }

    private void save(byte[] bytes, int width, int height, int rotation) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = null; Bitmap thumbnail = null; String error = null;
        try {
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "SANTICAM_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date(now)) + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/SantiCam");
            values.put(MediaStore.Images.Media.DATE_TAKEN, now);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Could not create photo");
            try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null || !new YuvImage(bytes, ImageFormat.NV21, width, height, null).compressToJpeg(new Rect(0, 0, width, height), 95, output)) throw new IOException("Could not encode photo");
            }
            try (ParcelFileDescriptor file = resolver.openFileDescriptor(uri, "rw")) {
                if (file == null) throw new IOException("Could not set orientation");
                ExifInterface exif = new ExifInterface(file.getFileDescriptor());
                int tag = rotation == 90 ? ExifInterface.ORIENTATION_ROTATE_90 : rotation == 180 ? ExifInterface.ORIENTATION_ROTATE_180 : rotation == 270 ? ExifInterface.ORIENTATION_ROTATE_270 : ExifInterface.ORIENTATION_NORMAL;
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(tag));
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(new Date(now)));
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "SantiCam 1.4.0");
                exif.saveAttributes();
            }
            ContentValues publish = new ContentValues(); publish.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(uri, publish, null, null) != 1) throw new IOException("Could not publish photo");
            context.getSharedPreferences("camera", Context.MODE_PRIVATE).edit().putString("lastPhoto", uri.toString()).apply();
            try {
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(width, height) / 320));
                try (InputStream input = resolver.openInputStream(uri)) { thumbnail = BitmapFactory.decodeStream(input, null, options); }
                if (thumbnail != null && rotation != 0) {
                    Matrix matrix = new Matrix(); matrix.postRotate(rotation);
                    Bitmap rotated = Bitmap.createBitmap(thumbnail, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), matrix, true);
                    if (rotated != thumbnail) thumbnail.recycle(); thumbnail = rotated;
                }
            } catch (Exception ignored) { /* Thumbnail failure must not discard a saved photo. */ }
        } catch (Exception e) {
            if (uri != null) try { resolver.delete(uri, null, null); } catch (RuntimeException ignored) {}
            uri = null; error = "保存できませんでした。空き容量を確認して再撮影してください";
        } finally { saving.set(false); photoResult(uri, thumbnail, error); }
    }

    private void photoResult(Uri uri, Bitmap bitmap, String error) {
        main.post(() -> { if (!destroyed) listener.onPhoto(uri, bitmap, error); else if (bitmap != null) bitmap.recycle(); });
    }
    private void emitStatus(String text, boolean error) { main.post(() -> { if (!destroyed) listener.onStatus(text, error); }); }

    void stop() { if (!destroyed) worker.post(() -> { ++generation; closeCamera(); }); }
    void destroy() {
        destroyed = true;
        worker.post(() -> { ++generation; closeCamera(); thread.quitSafely(); writer.shutdown(); scanner.shutdown(); });
    }
    private void closeSession() {
        ++configuration; ++focusGeneration; frameSeen = false; lastQrSample = 0;
        if (pendingOrientation >= 0) { pendingOrientation = -1; saving.set(false); photoResult(null, null, "撮影を中止しました"); }
        if (session != null) { session.close(); session = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (previewSurface != null) { previewSurface.release(); previewSurface = null; }
        request = null;
    }
    private void closeCamera() { if (recorder != null || videoUri != null) finishVideo(false, null); closeSession(); if (device != null) { device.close(); device = null; } }
}
