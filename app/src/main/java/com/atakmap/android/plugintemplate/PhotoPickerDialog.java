package com.atakmap.android.plugintemplate;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.plugintemplate.qc.PhotoAttachmentManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pre-submission interstitial dialog shown when the target feature layer supports attachments.
 *
 * <p>Displays a horizontal thumbnail strip, a camera button (opens {@link PhotoCaptureDialog}),
 * a gallery button (opens {@link GalleryPickerDialog}), and Submit / Skip buttons.
 *
 * <p>The user may add up to {@link PhotoAttachmentManager#MAX_PHOTOS} photos or tap Skip
 * to submit without any attachments.
 */
public class PhotoPickerDialog extends Dialog {

    public interface Callback {
        /** @param photos Selected/captured photo URIs. May be empty if user skipped. */
        void onReady(List<Uri> photos);
    }

    private static final int THUMB_SIZE = 80; // dp

    private final Context         pluginContext;
    private final String          templateLabel;
    private final Callback        callback;
    private final List<Uri>       photos = new ArrayList<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler         main   = new Handler(Looper.getMainLooper());

    private LinearLayout   thumbStrip;
    private Button         cameraBtn;
    private Button         galleryBtn;
    private Button         submitBtn;
    private TextView       countLabel;

    public PhotoPickerDialog(Context atakContext, Context pluginContext,
                             String templateLabel, Callback callback) {
        super(atakContext);
        this.pluginContext  = pluginContext;
        this.templateLabel  = templateLabel;
        this.callback       = callback;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                              ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1A1A1A"));
        setContentView(root);

        // ── Header ──────────────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.parseColor("#0D0D0D"));
        header.setPadding(dp(12), dp(10), dp(6), dp(10));
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header);

        TextView title = new TextView(getContext());
        title.setText("Add Photos — " + templateLabel);
        title.setTextColor(Color.parseColor("#DFB228"));
        title.setTextSize(14f);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        // ── Section label + count ────────────────────────────────────────────────
        LinearLayout labelRow = new LinearLayout(getContext());
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.setPadding(dp(14), dp(10), dp(14), dp(4));
        root.addView(labelRow);

        TextView sectionLabel = new TextView(getContext());
        sectionLabel.setText("PHOTOS (OPTIONAL)");
        sectionLabel.setTextColor(Color.parseColor("#DFB228"));
        sectionLabel.setTextSize(11f);
        sectionLabel.setTypeface(null, Typeface.BOLD);
        sectionLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(sectionLabel);

        countLabel = new TextView(getContext());
        countLabel.setText("0 / " + PhotoAttachmentManager.MAX_PHOTOS);
        countLabel.setTextColor(Color.parseColor("#888888"));
        countLabel.setTextSize(11f);
        labelRow.addView(countLabel);

        // ── Thumbnail strip ──────────────────────────────────────────────────────
        HorizontalScrollView hScroll = new HorizontalScrollView(getContext());
        hScroll.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(THUMB_SIZE + 16));
        hLp.setMargins(0, dp(4), 0, dp(4));
        hScroll.setLayoutParams(hLp);
        root.addView(hScroll);

        thumbStrip = new LinearLayout(getContext());
        thumbStrip.setOrientation(LinearLayout.HORIZONTAL);
        thumbStrip.setGravity(Gravity.CENTER_VERTICAL);
        hScroll.addView(thumbStrip);

        // Placeholder text when no photos yet
        TextView emptyHint = new TextView(getContext());
        emptyHint.setTag("empty_hint");
        emptyHint.setText("No photos added yet.\nUse Camera or Gallery below.");
        emptyHint.setTextColor(Color.parseColor("#555555"));
        emptyHint.setTextSize(11f);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(dp(16), 0, dp(16), 0);
        thumbStrip.addView(emptyHint);

        // ── Action buttons: Camera | Gallery ─────────────────────────────────────
        LinearLayout actionRow = new LinearLayout(getContext());
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(dp(14), dp(6), dp(14), dp(6));
        actionRow.setGravity(Gravity.CENTER);
        root.addView(actionRow);

        cameraBtn = actionButton("📷  Camera");
        cameraBtn.setOnClickListener(v -> openCamera());
        actionRow.addView(cameraBtn, buttonParams());

        android.view.View divider = new android.view.View(getContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 1));
        actionRow.addView(divider);

        galleryBtn = actionButton("🖼  Gallery");
        galleryBtn.setOnClickListener(v -> openGallery());
        actionRow.addView(galleryBtn, buttonParams());

        // ── Bottom bar: Skip | Submit ────────────────────────────────────────────
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.parseColor("#0D0D0D"));
        bar.setPadding(dp(14), dp(10), dp(14), dp(14));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar);

        Button skipBtn = new Button(getContext());
        skipBtn.setText("Skip");
        skipBtn.setTextColor(Color.parseColor("#AAAAAA"));
        skipBtn.setBackgroundColor(Color.TRANSPARENT);
        skipBtn.setOnClickListener(v -> {
            dismiss();
            if (callback != null) callback.onReady(new ArrayList<>());
        });
        bar.addView(skipBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bar.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1f));

        submitBtn = new Button(getContext());
        submitBtn.setText("Submit");
        submitBtn.setTextColor(Color.WHITE);
        submitBtn.setTypeface(null, Typeface.BOLD);
        submitBtn.setBackgroundColor(Color.parseColor("#1B5E20"));
        submitBtn.setOnClickListener(v -> {
            dismiss();
            if (callback != null) callback.onReady(new ArrayList<>(photos));
        });
        bar.addView(submitBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void dismiss() {
        loader.shutdownNow();
        super.dismiss();
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void openCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                getContext().checkSelfPermission(Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getContext(), "Camera permission required", Toast.LENGTH_SHORT).show();
            return;
        }
        new PhotoCaptureDialog(getContext(), pluginContext, uri -> {
            String error = PhotoAttachmentManager.validate(getContext(), uri);
            if (error != null) {
                PhotoAttachmentManager.deleteTempFile(uri);
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                return;
            }
            addPhoto(uri);
        }).show();
    }

    // ── Gallery ───────────────────────────────────────────────────────────────

    private void openGallery() {
        String storagePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                getContext().checkSelfPermission(storagePermission)
                        != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getContext(), "Storage permission required to access gallery",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        new GalleryPickerDialog(getContext(), uris -> {
            int remaining = PhotoAttachmentManager.MAX_PHOTOS - photos.size();
            List<Uri> accepted = uris.subList(0, Math.min(uris.size(), remaining));
            for (Uri uri : accepted) {
                String error = PhotoAttachmentManager.validate(getContext(), uri);
                if (error != null) {
                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                    continue;
                }
                addPhoto(uri);
            }
        }).show();
    }

    // ── Thumbnail management ──────────────────────────────────────────────────

    private void addPhoto(Uri uri) {
        if (photos.size() >= PhotoAttachmentManager.MAX_PHOTOS) {
            Toast.makeText(getContext(),
                    "Maximum " + PhotoAttachmentManager.MAX_PHOTOS + " photos per submission",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        photos.add(uri);
        updateButtonStates();

        // Remove empty-hint if present
        View hint = thumbStrip.findViewWithTag("empty_hint");
        if (hint != null) thumbStrip.removeView(hint);

        // Add a thumbnail cell
        int index = photos.size() - 1;
        FrameLayout cell = buildThumbCell(uri, index);
        thumbStrip.addView(cell);

        // Load thumbnail async
        loader.execute(() -> {
            Bitmap bm = PhotoAttachmentManager.loadThumbnail(getContext(), uri);
            main.post(() -> {
                ImageView iv = cell.findViewWithTag("img_" + index);
                if (iv != null && bm != null) iv.setImageBitmap(bm);
            });
        });
    }

    private void removePhoto(int index) {
        if (index < 0 || index >= photos.size()) return;
        Uri removed = photos.remove(index);
        PhotoAttachmentManager.deleteTempFile(removed);
        rebuildThumbStrip();
        updateButtonStates();
    }

    private FrameLayout buildThumbCell(Uri uri, int index) {
        int cellPx = dp(THUMB_SIZE);

        FrameLayout cell = new FrameLayout(getContext());
        LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(cellPx, cellPx);
        cellLp.setMargins(dp(4), dp(4), dp(4), dp(4));
        cell.setLayoutParams(cellLp);

        ImageView iv = new ImageView(getContext());
        iv.setTag("img_" + index);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(Color.parseColor("#2A2A2A"));
        cell.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Remove badge (×) — top-right corner
        TextView removeBtn = new TextView(getContext());
        removeBtn.setText("✕");
        removeBtn.setTextColor(Color.WHITE);
        removeBtn.setTextSize(10f);
        removeBtn.setGravity(Gravity.CENTER);
        removeBtn.setBackgroundColor(Color.argb(210, 180, 20, 20));
        int badgeSize = dp(20);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(badgeSize, badgeSize);
        badgeLp.gravity = Gravity.TOP | Gravity.END;
        removeBtn.setLayoutParams(badgeLp);
        removeBtn.setOnClickListener(v -> removePhoto(photos.indexOf(uri)));
        cell.addView(removeBtn);

        return cell;
    }

    private void rebuildThumbStrip() {
        thumbStrip.removeAllViews();
        if (photos.isEmpty()) {
            TextView emptyHint = new TextView(getContext());
            emptyHint.setTag("empty_hint");
            emptyHint.setText("No photos added yet.\nUse Camera or Gallery below.");
            emptyHint.setTextColor(Color.parseColor("#555555"));
            emptyHint.setTextSize(11f);
            emptyHint.setGravity(Gravity.CENTER);
            emptyHint.setPadding(dp(16), 0, dp(16), 0);
            thumbStrip.addView(emptyHint);
            return;
        }
        for (int i = 0; i < photos.size(); i++) {
            final int idx = i;
            final Uri uri = photos.get(i);
            FrameLayout cell = buildThumbCell(uri, idx);
            thumbStrip.addView(cell);
            loader.execute(() -> {
                Bitmap bm = PhotoAttachmentManager.loadThumbnail(getContext(), uri);
                main.post(() -> {
                    ImageView iv = cell.findViewWithTag("img_" + idx);
                    if (iv != null && bm != null) iv.setImageBitmap(bm);
                });
            });
        }
    }

    private void updateButtonStates() {
        int n = photos.size();
        boolean atMax = n >= PhotoAttachmentManager.MAX_PHOTOS;
        countLabel.setText(n + " / " + PhotoAttachmentManager.MAX_PHOTOS);
        cameraBtn.setEnabled(!atMax);
        cameraBtn.setAlpha(atMax ? 0.4f : 1f);
        galleryBtn.setEnabled(!atMax);
        galleryBtn.setAlpha(atMax ? 0.4f : 1f);
        submitBtn.setText(n > 0 ? "Submit (" + n + " photo" + (n == 1 ? "" : "s") + ")" : "Submit");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Button actionButton(String label) {
        Button btn = new Button(getContext());
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13f);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setBackgroundColor(Color.parseColor("#2A2A2A"));
        btn.setPadding(dp(16), dp(10), dp(16), dp(10));
        return btn;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        return lp;
    }

    private int dp(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }
}
