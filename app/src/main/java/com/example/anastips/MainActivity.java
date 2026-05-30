package com.example.anastips;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_CAMERA = 2001;

    private TextureView textureView;
    private TextView statusText;
    private TextView modeText;
    private Button captureButton;
    private Button monsterButton;
    private Switch aiSwitch;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private ExecutorService processorExecutor;

    private String cameraId;
    private Size previewSize;
    private Size captureSize;
    private int sensorOrientation = 0;
    private boolean aiEnabled = true;
    private boolean monsterMode = true;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        processorExecutor = Executors.newSingleThreadExecutor();
        buildUi();
        if (hasCameraPermission()) {
            startCameraWhenReady();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        textureView = new TextureView(this);
        root.addView(textureView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setPadding(dp(16), dp(18), dp(16), dp(12));
        topPanel.setBackgroundColor(Color.argb(150, 0, 0, 0));

        TextView title = new TextView(this);
        title.setText("SmartCam AI Monster");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        topPanel.addView(title);

        modeText = new TextView(this);
        modeText.setTextColor(Color.rgb(191, 219, 254));
        modeText.setTextSize(14);
        modeText.setGravity(Gravity.CENTER);
        modeText.setPadding(0, dp(4), 0, 0);
        topPanel.addView(modeText);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        root.addView(topPanel, topParams);

        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(dp(16), dp(12), dp(16), dp(18));
        bottomPanel.setBackgroundColor(Color.argb(175, 0, 0, 0));

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setText("جاهز للتصوير");
        statusText.setPadding(0, 0, 0, dp(10));
        bottomPanel.addView(statusText);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);

        aiSwitch = new Switch(this);
        aiSwitch.setText("AI Enhance");
        aiSwitch.setTextColor(Color.WHITE);
        aiSwitch.setTextSize(14);
        aiSwitch.setChecked(true);
        aiSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                aiEnabled = isChecked;
                updateModeText();
            }
        });
        row.addView(aiSwitch);

        monsterButton = new Button(this);
        monsterButton.setText("MONSTER ON");
        monsterButton.setAllCaps(false);
        monsterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                monsterMode = !monsterMode;
                updateModeText();
            }
        });
        LinearLayout.LayoutParams monsterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        monsterParams.setMargins(dp(12), 0, 0, 0);
        row.addView(monsterButton, monsterParams);

        captureButton = new Button(this);
        captureButton.setText("التقاط وتحسين");
        captureButton.setAllCaps(false);
        captureButton.setTextSize(18);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureStillImage();
            }
        });

        bottomPanel.addView(row);
        LinearLayout.LayoutParams captureParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        );
        captureParams.setMargins(0, dp(10), 0, 0);
        bottomPanel.addView(captureButton, captureParams);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        root.addView(bottomPanel, bottomParams);
        setContentView(root);
        updateModeText();
    }

    private void updateModeText() {
        String mode;
        if (!aiEnabled) {
            mode = "AI OFF — سيتم حفظ الصورة الأصلية فقط";
            monsterButton.setText("MONSTER OFF");
        } else if (monsterMode) {
            mode = "AI ON — Monster/Extreme local enhancement";
            monsterButton.setText("MONSTER ON");
        } else {
            mode = "AI ON — Fast local enhancement";
            monsterButton.setText("FAST AI");
        }
        modeText.setText(mode);
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraWhenReady();
        } else {
            toast("لازم إذن الكاميرا حتى يعمل التطبيق");
            status("Camera permission denied");
        }
    }

    private void startCameraWhenReady() {
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                startBackgroundThread();
                openCamera();
            }

            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });

        if (textureView.isAvailable()) {
            startBackgroundThread();
            openCamera();
        }
    }

    private void startBackgroundThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("SmartCamCameraThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try {
                cameraThread.join();
            } catch (InterruptedException ignored) {
            }
            cameraThread = null;
            cameraHandler = null;
        }
    }

    private void openCamera() {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            cameraId = chooseBackCamera(manager);
            if (cameraId == null) {
                status("لم أجد كاميرا خلفية");
                return;
            }

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                status("Camera configuration not available");
                return;
            }

            captureSize = chooseLargest(map.getOutputSizes(ImageFormat.JPEG));
            previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
            imageReader = ImageReader.newInstance(captureSize.getWidth(), captureSize.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image == null) return;
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    image.close();
                    processAndSave(bytes);
                }
            }, cameraHandler);

            if (!hasCameraPermission()) return;
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    createPreviewSession();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    status("Camera error: " + error);
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            status("Camera access error: " + e.getMessage());
        }
    }

    private String chooseBackCamera(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        String[] ids = manager.getCameraIdList();
        return ids.length > 0 ? ids[0] : null;
    }

    private Size chooseLargest(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(1920, 1080);
        return Collections.max(Arrays.asList(sizes), new Comparator<Size>() {
            @Override
            public int compare(Size a, Size b) {
                return Long.compare((long) a.getWidth() * a.getHeight(), (long) b.getWidth() * b.getHeight());
            }
        });
    }

    private Size choosePreviewSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(1280, 720);
        Size best = sizes[0];
        long target = 1920L * 1080L;
        long bestDiff = Math.abs((long) best.getWidth() * best.getHeight() - target);
        for (Size s : sizes) {
            long area = (long) s.getWidth() * s.getHeight();
            long diff = Math.abs(area - target);
            if (diff < bestDiff) {
                best = s;
                bestDiff = diff;
            }
        }
        return best;
    }

    private void createPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null || cameraDevice == null) return;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            List<Surface> outputs = new ArrayList<>();
            outputs.add(previewSurface);
            outputs.add(imageReader.getSurface());

            cameraDevice.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler);
                        status("جاهز — أعلى دقة التقاط: " + captureSize.getWidth() + "x" + captureSize.getHeight());
                    } catch (CameraAccessException e) {
                        status("Preview error: " + e.getMessage());
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    status("Preview configuration failed");
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            status("Preview setup error: " + e.getMessage());
        }
    }

    private void captureStillImage() {
        if (busy) {
            toast("المعالجة شغالة، استنى تخلص الصورة الحالية");
            return;
        }
        if (cameraDevice == null || captureSession == null || imageReader == null) {
            status("الكاميرا غير جاهزة بعد");
            return;
        }
        try {
            busy = true;
            captureButton.setEnabled(false);
            status("جاري التقاط الصورة...");

            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation());

            captureSession.stopRepeating();
            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    try {
                        session.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler);
                    } catch (CameraAccessException ignored) {
                    }
                }
            }, cameraHandler);
        } catch (Exception e) {
            busy = false;
            captureButton.setEnabled(true);
            status("Capture error: " + e.getMessage());
        }
    }

    private int getJpegOrientation() {
        int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees;
        switch (deviceRotation) {
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
            case Surface.ROTATION_0:
            default: degrees = 0; break;
        }
        return (sensorOrientation + degrees + 360) % 360;
    }

    private void processAndSave(final byte[] jpegBytes) {
        final boolean useAi = aiEnabled;
        final boolean useMonster = monsterMode;
        processorExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!useAi) {
                        status("AI OFF — حفظ الصورة الأصلية...");
                        saveBytes(jpegBytes, "ORIGINAL");
                        return;
                    }

                    status(useMonster ? "Monster AI بدأ المعالجة..." : "Fast AI بدأ المعالجة...");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    if (!useMonster) {
                        options.inSampleSize = 2;
                    }

                    Bitmap bitmap;
                    try {
                        bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length, options);
                    } catch (OutOfMemoryError oom) {
                        status("الذاكرة امتلأت، سأكمل بنسخة أصغر بدل ما ينهار التطبيق...");
                        BitmapFactory.Options fallback = new BitmapFactory.Options();
                        fallback.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        fallback.inSampleSize = useMonster ? 2 : 4;
                        bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length, fallback);
                    }

                    if (bitmap == null) {
                        status("فشل قراءة الصورة، سأحفظ الأصلية.");
                        saveBytes(jpegBytes, "ORIGINAL_FALLBACK");
                        return;
                    }

                    AiImageEnhancer.Mode mode = useMonster ? AiImageEnhancer.Mode.MONSTER_AI : AiImageEnhancer.Mode.FAST_AI;
                    Bitmap enhanced = AiImageEnhancer.enhance(bitmap, mode, new AiImageEnhancer.ProgressListener() {
                        @Override
                        public void onProgress(String message) {
                            status(message);
                        }
                    });
                    saveBitmap(enhanced, useMonster ? "MONSTER_AI" : "FAST_AI");
                    if (enhanced != bitmap) enhanced.recycle();
                    bitmap.recycle();
                } catch (Throwable t) {
                    status("Processing error: " + t.getMessage());
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            captureButton.setEnabled(true);
                        }
                    });
                }
            }
        });
    }

    private void saveBytes(byte[] bytes, String suffix) throws Exception {
        String name = makeFileName(suffix);
        Uri uri = createImageUri(name);
        OutputStream out = getContentResolver().openOutputStream(uri);
        if (out == null) throw new Exception("Cannot open output stream");
        out.write(bytes);
        out.flush();
        out.close();
        status("تم الحفظ: " + name);
        toast("تم حفظ الصورة");
    }

    private void saveBitmap(Bitmap bitmap, String suffix) throws Exception {
        String name = makeFileName(suffix);
        Uri uri = createImageUri(name);
        OutputStream out = getContentResolver().openOutputStream(uri);
        if (out == null) throw new Exception("Cannot open output stream");
        bitmap.compress(Bitmap.CompressFormat.JPEG, 98, out);
        out.flush();
        out.close();
        status("تم الحفظ: " + name);
        toast("تم حفظ الصورة المحسّنة");
    }

    private String makeFileName(String suffix) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "SmartCam_" + suffix + "_" + ts + ".jpg";
    }

    private Uri createImageUri(String name) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SmartCamAI");
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
        }
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("Cannot create MediaStore item");
        return uri;
    }

    private void status(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusText != null) statusText.setText(message);
            }
        });
    }

    private void toast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission() && textureView != null && textureView.isAvailable()) {
            startBackgroundThread();
            openCamera();
        }
    }

    @Override
    protected void onDestroy() {
        closeCamera();
        if (processorExecutor != null) processorExecutor.shutdownNow();
        super.onDestroy();
    }

    private void closeCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception ignored) {
        }
    }
}
