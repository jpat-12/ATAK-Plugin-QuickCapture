package com.atakmap.android.plugintemplate;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.atakmap.android.plugintemplate.qc.PhotoAttachmentManager;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full-screen Dialog that opens the back camera for still photo capture via the Camera2 API.
 *
 * <p>Shows a live preview and a shutter button.  When the user taps the shutter,
 * one JPEG frame is captured, saved to a temp file under the app's external Pictures
 * directory, and returned to the caller as a {@code content://} URI via the
 * {@link Callback} interface.
 */
public class PhotoCaptureDialog extends Dialog {

    private static final String TAG        = "FieldTak.PhotoCapture";
    private static final int    PREVIEW_W  = 1280;
    private static final int    PREVIEW_H  = 720;
    private static final int    CAPTURE_W  = 1920;
    private static final int    CAPTURE_H  = 1080;

    public interface Callback {
        /** Called on the main thread with the content:// URI of the saved photo. */
        void onPhotoCaptured(Uri photoUri);
    }

    private final Context      pluginContext;
    private final Callback     callback;
    private final Handler      mainHandler = new Handler(Looper.getMainLooper());
    private final Semaphore    cameraLock  = new Semaphore(1);

    private TextureView           textureView;
    private Button                shutterBtn;
    private CameraDevice          cameraDevice;
    private CameraCaptureSession  captureSession;
    private ImageReader           previewReader;
    private ImageReader           stillReader;
    private HandlerThread         bgThread;
    private Handler               bgHandler;
    private final AtomicBoolean   capturing   = new AtomicBoolean(false);
    private final AtomicBoolean   dismissed   = new AtomicBoolean(false);

    private int sensorOrientation = 90;

    public PhotoCaptureDialog(Context atakContext, Context pluginContext, Callback callback) {
        super(atakContext);
        this.pluginContext = pluginContext;
        this.callback      = callback;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                              ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
    }

    @Override
    public void show() {
        super.show();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                getContext().checkSelfPermission(Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getContext(), "Camera permission required", Toast.LENGTH_LONG).show();
            dismiss();
            return;
        }
        startBackgroundThread();
        if (textureView.isAvailable()) {
            configureTransform(textureView.getWidth(), textureView.getHeight());
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceListener);
        }
    }

    @Override
    public void dismiss() {
        if (dismissed.compareAndSet(false, true)) {
            closeCamera();
            stopBackgroundThread();
        }
        super.dismiss();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private android.view.View buildLayout() {
        // Root: TextureView fills the screen; bottom bar overlaid at bottom
        FrameLayout root = new FrameLayout(getContext());
        root.setBackgroundColor(Color.BLACK);

        textureView = new TextureView(getContext());
        root.addView(textureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Bottom control bar
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.argb(180, 0, 0, 0));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(16), dp(12));
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        root.addView(bar, barLp);

        // Cancel button (left)
        Button cancelBtn = new Button(getContext());
        cancelBtn.setText("Cancel");
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setBackgroundColor(Color.TRANSPARENT);
        cancelBtn.setOnClickListener(v -> dismiss());
        bar.addView(cancelBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Spacer
        bar.addView(new android.view.View(getContext()), new LinearLayout.LayoutParams(
                0, 1, 1f));

        // Shutter button (center-right)
        shutterBtn = new Button(getContext());
        shutterBtn.setText("📷  Capture");
        shutterBtn.setTextColor(Color.WHITE);
        shutterBtn.setTextSize(15f);
        shutterBtn.setTypeface(null, Typeface.BOLD);
        shutterBtn.setBackgroundColor(Color.parseColor("#1B5E20"));
        shutterBtn.setPadding(dp(24), dp(10), dp(24), dp(10));
        shutterBtn.setOnClickListener(v -> captureStill());
        bar.addView(shutterBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Top hint label
        TextView hint = new TextView(getContext());
        hint.setText("Frame your photo, then tap Capture");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(13f);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(Color.argb(140, 0, 0, 0));
        hint.setPadding(dp(12), dp(8), dp(12), dp(8));
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.TOP;
        root.addView(hint, hintLp);

        return root;
    }

    // ── Background thread ─────────────────────────────────────────────────────

    private void startBackgroundThread() {
        bgThread  = new HandlerThread("FieldTak-Photo");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (bgThread != null) {
            bgThread.quitSafely();
            try { bgThread.join(1000); } catch (InterruptedException ignored) {}
            bgThread  = null;
            bgHandler = null;
        }
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void openCamera() {
        CameraManager mgr = (CameraManager)
                getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            String camId = findBackCamera(mgr);
            if (camId == null) { Log.e(TAG, "No back camera"); dismiss(); return; }

            previewReader = ImageReader.newInstance(PREVIEW_W, PREVIEW_H, ImageFormat.JPEG, 2);
            stillReader   = ImageReader.newInstance(CAPTURE_W, CAPTURE_H, ImageFormat.JPEG, 1);
            stillReader.setOnImageAvailableListener(stillListener, bgHandler);

            if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Camera lock timeout"); return;
            }
            //noinspection MissingPermission
            mgr.openCamera(camId, stateCallback, bgHandler);
        } catch (CameraAccessException | InterruptedException e) {
            Log.e(TAG, "openCamera failed", e);
            dismiss();
        }
    }

    private String findBackCamera(CameraManager mgr) throws CameraAccessException {
        for (String id : mgr.getCameraIdList()) {
            CameraCharacteristics chars = mgr.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                Integer so = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
                if (so != null) sensorOrientation = so;
                return id;
            }
        }
        return null;
    }

    private void closeCamera() {
        try {
            cameraLock.acquire();
            if (captureSession != null) { captureSession.close(); captureSession = null; }
            if (cameraDevice  != null)  { cameraDevice.close();   cameraDevice  = null; }
            if (previewReader != null)  { previewReader.close();  previewReader = null; }
            if (stillReader   != null)  { stillReader.close();    stillReader   = null; }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted closing camera", e);
        } finally {
            cameraLock.release();
        }
    }

    private void createCaptureSession() {
        try {
            SurfaceTexture st = textureView.getSurfaceTexture();
            if (st == null) return;
            st.setDefaultBufferSize(PREVIEW_W, PREVIEW_H);
            Surface previewSurface = new Surface(st);
            Surface stillSurface   = stillReader.getSurface();

            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, stillSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            captureSession = session;
                            try {
                                // Repeating preview to TextureView only
                                CaptureRequest.Builder preview =
                                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                preview.addTarget(previewSurface);
                                preview.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                captureSession.setRepeatingRequest(preview.build(), null, bgHandler);
                                mainHandler.post(() -> {
                                    if (shutterBtn != null) shutterBtn.setEnabled(true);
                                });
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Preview request failed", e);
                            }
                        }
                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Session configure failed");
                            mainHandler.post(() -> dismiss());
                        }
                    }, bgHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "createCaptureSession failed", e);
        }
    }

    // ── Still capture ─────────────────────────────────────────────────────────

    private void captureStill() {
        if (!capturing.compareAndSet(false, true)) return;
        if (shutterBtn != null) {
            shutterBtn.setEnabled(false);
            shutterBtn.setText("Capturing...");
        }
        bgHandler.post(() -> {
            try {
                if (captureSession == null || cameraDevice == null) return;
                CaptureRequest.Builder still =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                still.addTarget(stillReader.getSurface());
                still.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                still.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
                still.set(CaptureRequest.JPEG_QUALITY, (byte) 90);
                captureSession.capture(still.build(), null, bgHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Still capture failed", e);
                capturing.set(false);
                mainHandler.post(() -> {
                    if (shutterBtn != null) {
                        shutterBtn.setEnabled(true);
                        shutterBtn.setText("📷  Capture");
                    }
                });
            }
        });
    }

    private final ImageReader.OnImageAvailableListener stillListener = reader -> {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null || dismissed.get()) return;

            ByteBuffer buf   = image.getPlanes()[0].getBuffer();
            byte[]     bytes = new byte[buf.remaining()];
            buf.get(bytes);

            File outFile = PhotoAttachmentManager.createCaptureFile(getContext());
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(bytes);
            }
            Uri contentUri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getPackageName() + ".fileprovider",
                    outFile);

            mainHandler.post(() -> {
                dismiss();
                if (callback != null) callback.onPhotoCaptured(contentUri);
            });
        } catch (IOException e) {
            Log.e(TAG, "Failed to save captured photo", e);
            capturing.set(false);
            mainHandler.post(() -> {
                Toast.makeText(getContext(), "Failed to save photo", Toast.LENGTH_SHORT).show();
                if (shutterBtn != null) {
                    shutterBtn.setEnabled(true);
                    shutterBtn.setText("📷  Capture");
                }
            });
        } finally {
            if (image != null) image.close();
        }
    };

    // ── Orientation ───────────────────────────────────────────────────────────

    private int jpegOrientation() {
        Display display = getWindow().getWindowManager().getDefaultDisplay();
        int deviceRotation = display.getRotation();
        int degrees = 0;
        switch (deviceRotation) {
            case Surface.ROTATION_0:   degrees =   0; break;
            case Surface.ROTATION_90:  degrees =  90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        return (sensorOrientation + degrees + 360) % 360;
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null || getWindow() == null) return;
        if (viewWidth == 0 || viewHeight == 0) return;

        Display display = getWindow().getWindowManager().getDefaultDisplay();
        int rotation = display.getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        float cx = viewRect.centerX();
        float cy = viewRect.centerY();

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            RectF bufferRect = new RectF(0, 0, PREVIEW_H, PREVIEW_W);
            bufferRect.offset(cx - bufferRect.centerX(), cy - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) viewHeight / PREVIEW_H, (float) viewWidth / PREVIEW_W);
            matrix.postScale(scale, scale, cx, cy);
            matrix.postRotate(90 * (rotation - 2), cx, cy);
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180, cx, cy);
        }
        textureView.setTransform(matrix);
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private final TextureView.SurfaceTextureListener surfaceListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
                    configureTransform(w, h);
                    openCamera();
                }
                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {
                    configureTransform(w, h);
                }
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
                @Override public void    onSurfaceTextureUpdated(SurfaceTexture s)   {}
            };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraLock.release();
            cameraDevice = camera;
            createCaptureSession();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraLock.release();
            camera.close();
            cameraDevice = null;
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraLock.release();
            camera.close();
            cameraDevice = null;
            Log.e(TAG, "Camera error: " + error);
            mainHandler.post(() -> dismiss());
        }
    };

    private int dp(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }
}
