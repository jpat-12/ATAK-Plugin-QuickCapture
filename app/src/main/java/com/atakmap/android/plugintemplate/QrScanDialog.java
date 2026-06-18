package com.atakmap.android.plugintemplate;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.quickcapture.plugin.R;
import com.atakmap.coremap.log.Log;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A full-screen {@link Dialog} that opens the device camera and decodes QR codes
 * using the Camera2 API and ZXing.
 *
 * <p>The camera preview is automatically corrected for the current device orientation
 * so the image is never inverted or rotated when the user is in landscape mode.
 *
 * <p>When a QR code is successfully decoded, the {@link Callback} is invoked on the
 * main thread and the dialog is automatically dismissed.
 *
 * <p><b>Usage:</b>
 * <pre>
 *     QrScanDialog dlg = new QrScanDialog(atakContext, pluginContext, url -> {
 *         // handle decoded URL
 *     });
 *     dlg.show();
 * </pre>
 */
public class QrScanDialog extends Dialog {

    private static final String TAG       = "FieldTak.QrScan";
    private static final int    PREVIEW_W = 1280;
    private static final int    PREVIEW_H = 720;

    public interface Callback {
        void onQrDecoded(String url);
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Context         pluginContext;
    private       Callback        callback;   // not final: referenced inside field-level lambda
    private final Handler         mainHandler  = new Handler(Looper.getMainLooper());
    private final Semaphore       cameraLock   = new Semaphore(1);
    private final MultiFormatReader zxing       = new MultiFormatReader();

    private TextureView           textureView;
    private CameraDevice          cameraDevice;
    private CameraCaptureSession  captureSession;
    private ImageReader           imageReader;
    private HandlerThread         bgThread;
    private Handler               bgHandler;
    private volatile boolean      decoded      = false;

    /** Back-camera sensor orientation in degrees (0, 90, 180, or 270). */
    private int sensorOrientation = 90;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param atakContext   The ATAK host Activity context (for the Dialog window).
     * @param pluginContext The plugin context (for resource inflation).
     * @param callback      Called once on the main thread when a QR code is decoded.
     */
    public QrScanDialog(/* @NonNull */ Context atakContext,
                        /* @NonNull */ Context pluginContext,
                        /* @NonNull */ Callback callback) {
        super(atakContext);
        this.pluginContext = pluginContext;
        this.callback      = callback;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View root = com.atak.plugins.impl.PluginLayoutInflater
                .inflate(pluginContext, R.layout.dialog_qr_scan, null);
        setContentView(root);

        textureView = root.findViewById(R.id.qr_texture_view);
        TextView hint = root.findViewById(R.id.qr_hint_text);
        if (hint != null) hint.setText("Point camera at QR code");

        root.findViewById(R.id.qr_cancel_btn).setOnClickListener(v -> dismiss());
    }

    @Override
    public void show() {
        super.show();

        // Check camera permission (dangerous permission requires grant on API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getContext().checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(),
                        "Camera permission is required to scan QR codes",
                        Toast.LENGTH_LONG).show();
                dismiss();
                return;
            }
        }

        if (textureView == null) {
            Log.e(TAG, "TextureView not found in layout — cannot start camera");
            dismiss();
            return;
        }

        startBackgroundThread();

        if (textureView.isAvailable()) {
            // TextureView already has a surface — configure transform and open camera
            configureTransform(textureView.getWidth(), textureView.getHeight());
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceListener);
        }
    }

    @Override
    public void dismiss() {
        decoded = true; // prevent further callbacks
        closeCamera();
        stopBackgroundThread();
        super.dismiss();
    }

    // ── Background thread ─────────────────────────────────────────────────────

    private void startBackgroundThread() {
        bgThread  = new HandlerThread("FieldTak-QR");
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

    // ── Camera setup ──────────────────────────────────────────────────────────

    private void openCamera() {
        CameraManager mgr = (CameraManager)
                getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            String camId = findBackCamera(mgr);
            if (camId == null) {
                Log.e(TAG, "No back camera found");
                dismiss();
                return;
            }

            // ImageReader: JPEG frames for ZXing decoding
            imageReader = ImageReader.newInstance(PREVIEW_W, PREVIEW_H,
                    ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(imageListener, bgHandler);

            if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Camera lock timeout");
                return;
            }

            //noinspection MissingPermission
            mgr.openCamera(camId, stateCallback, bgHandler);

        } catch (CameraAccessException | InterruptedException e) {
            Log.e(TAG, "openCamera failed", e);
            dismiss();
        }
    }

    /**
     * Finds the back-facing camera ID and stores its sensor orientation.
     * Sensor orientation is needed to compute the correct TextureView transform.
     */
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
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while closing camera", e);
        } finally {
            cameraLock.release();
        }
    }

    // ── Orientation / transform ───────────────────────────────────────────────

    /**
     * Computes and applies an affine transform to the TextureView so that the
     * camera preview is correctly oriented regardless of the current display rotation.
     *
     * <p>This is the standard Camera2 sample pattern:
     * <ul>
     *   <li>Portrait (ROTATION_0) — sensor is naturally 90°; no extra rotation needed.</li>
     *   <li>Landscape (ROTATION_90 / ROTATION_270) — swap width/height and rotate to match.</li>
     *   <li>Reverse portrait (ROTATION_180) — simply rotate 180°.</li>
     * </ul>
     *
     * @param viewWidth  Width of the TextureView in pixels.
     * @param viewHeight Height of the TextureView in pixels.
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null || getWindow() == null) return;
        if (viewWidth == 0 || viewHeight == 0) return; // layout not yet complete

        Display display = getWindow().getWindowManager().getDefaultDisplay();
        int displayRotation = display.getRotation();  // Surface.ROTATION_0/90/180/270

        Matrix matrix = new Matrix();
        RectF viewRect   = new RectF(0, 0, viewWidth, viewHeight);
        float centerX    = viewRect.centerX();
        float centerY    = viewRect.centerY();

        if (displayRotation == Surface.ROTATION_90
                || displayRotation == Surface.ROTATION_270) {
            // Landscape: swap aspect ratio so the buffer fills the view correctly
            RectF bufferRect = new RectF(0, 0, PREVIEW_H, PREVIEW_W);
            bufferRect.offset(centerX - bufferRect.centerX(),
                              centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / PREVIEW_H,
                    (float) viewWidth  / PREVIEW_W);
            matrix.postScale(scale, scale, centerX, centerY);
            // ROTATION_90 → -90°,  ROTATION_270 → +90°
            matrix.postRotate(90 * (displayRotation - 2), centerX, centerY);
        } else if (displayRotation == Surface.ROTATION_180) {
            matrix.postRotate(180, centerX, centerY);
        }
        // ROTATION_0 (portrait) → identity matrix (no transform needed)

        textureView.setTransform(matrix);
    }

    // ── Camera callbacks ──────────────────────────────────────────────────────

    private final TextureView.SurfaceTextureListener surfaceListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
                    // Apply orientation transform before the camera feeds the surface
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
        public void onOpened(/* @NonNull */ CameraDevice camera) {
            cameraLock.release();
            cameraDevice = camera;
            createCaptureSession();
        }
        @Override
        public void onDisconnected(/* @NonNull */ CameraDevice camera) {
            cameraLock.release();
            camera.close();
            cameraDevice = null;
        }
        @Override
        public void onError(/* @NonNull */ CameraDevice camera, int error) {
            cameraLock.release();
            camera.close();
            cameraDevice = null;
            Log.e(TAG, "Camera error: " + error);
            mainHandler.post(() -> dismiss());
        }
    };

    private void createCaptureSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(PREVIEW_W, PREVIEW_H);

            Surface previewSurface = new Surface(texture);
            Surface readerSurface  = imageReader.getSurface();

            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, readerSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(/* @NonNull */ CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            captureSession = session;
                            try {
                                CaptureRequest.Builder builder =
                                        cameraDevice.createCaptureRequest(
                                                CameraDevice.TEMPLATE_PREVIEW);
                                builder.addTarget(previewSurface);
                                builder.addTarget(readerSurface);
                                builder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                captureSession.setRepeatingRequest(
                                        builder.build(), null, bgHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Capture request failed", e);
                            }
                        }
                        @Override
                        public void onConfigureFailed(/* @NonNull */ CameraCaptureSession session) {
                            Log.e(TAG, "Capture session configuration failed");
                            mainHandler.post(() -> dismiss());
                        }
                    },
                    bgHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "createCaptureSession failed", e);
        }
    }

    // ── ZXing decoding ────────────────────────────────────────────────────────

    private final ImageReader.OnImageAvailableListener imageListener = reader -> {
        if (decoded) {
            Image img = reader.acquireLatestImage();
            if (img != null) img.close();
            return;
        }

        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;

            ByteBuffer buf   = image.getPlanes()[0].getBuffer();
            byte[]     bytes = new byte[buf.remaining()];
            buf.get(bytes);

            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) return;

            int   w      = bmp.getWidth();
            int   h      = bmp.getHeight();
            int[] pixels = new int[w * h];
            bmp.getPixels(pixels, 0, w, 0, 0, w, h);
            bmp.recycle();

            RGBLuminanceSource src    = new RGBLuminanceSource(w, h, pixels);
            BinaryBitmap       binary = new BinaryBitmap(new HybridBinarizer(src));

            com.google.zxing.Result result = zxing.decodeWithState(binary);
            // QR code found!
            decoded = true;
            String url = result.getText();
            Log.d(TAG, "QR decoded: " + url);
            mainHandler.post(() -> {
                dismiss();
                callback.onQrDecoded(url);
            });

        } catch (NotFoundException ignored) {
            // No QR in this frame — normal, keep scanning

        } catch (Exception e) {
            Log.e(TAG, "Decode error", e);

        } finally {
            zxing.reset();
            if (image != null) image.close();
        }
    };
}
