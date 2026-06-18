package com.atakmap.android.plugintemplate;

import android.app.Dialog;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.plugintemplate.qc.PhotoAttachmentManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dialog that shows the device photo gallery (via MediaStore) as a scrollable grid
 * and allows the user to select up to {@link PhotoAttachmentManager#MAX_PHOTOS} images.
 *
 * <p>Does not require {@code startActivityForResult} — queries MediaStore directly so
 * it works within an ATAK plugin DropDownReceiver context.
 */
public class GalleryPickerDialog extends Dialog {

    public interface Callback {
        void onPhotosSelected(List<Uri> uris);
    }

    private static final int   COLUMNS     = 3;
    private static final int   THUMB_SIZE  = 96;   // dp per cell
    private static final int   MAX_ITEMS   = 60;   // how many recent photos to show

    private final Callback           callback;
    private final ExecutorService    loader = Executors.newFixedThreadPool(3);
    private final Handler            main   = new Handler(Looper.getMainLooper());
    private final Set<Uri>           selected = new HashSet<>();
    private       Button             doneBtn;

    public GalleryPickerDialog(Context atakContext, Callback callback) {
        super(atakContext);
        this.callback = callback;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                              ViewGroup.LayoutParams.MATCH_PARENT);
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
        title.setText("Select Photos");
        title.setTextColor(Color.parseColor("#DFB228"));
        title.setTextSize(15f);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        Button closeBtn = new Button(getContext());
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn);

        // ── Hint ────────────────────────────────────────────────────────────────
        TextView hint = new TextView(getContext());
        hint.setText("Tap to select · max " + PhotoAttachmentManager.MAX_PHOTOS + " photos");
        hint.setTextColor(Color.parseColor("#888888"));
        hint.setTextSize(11f);
        hint.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.addView(hint);

        // ── Grid ────────────────────────────────────────────────────────────────
        ScrollView scroll = new ScrollView(getContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(scroll);

        GridLayout grid = new GridLayout(getContext());
        grid.setColumnCount(COLUMNS);
        scroll.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        populateGrid(grid);

        // ── Bottom bar ───────────────────────────────────────────────────────────
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.parseColor("#0D0D0D"));
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar);

        Button cancelBtn = new Button(getContext());
        cancelBtn.setText("Cancel");
        cancelBtn.setTextColor(Color.parseColor("#AAAAAA"));
        cancelBtn.setBackgroundColor(Color.TRANSPARENT);
        cancelBtn.setOnClickListener(v -> dismiss());
        bar.addView(cancelBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.addView(spacer(), new LinearLayout.LayoutParams(0, 1, 1f));

        doneBtn = new Button(getContext());
        doneBtn.setText("Add Photos");
        doneBtn.setTextColor(Color.WHITE);
        doneBtn.setTypeface(null, Typeface.BOLD);
        doneBtn.setBackgroundColor(Color.parseColor("#1B5E20"));
        doneBtn.setEnabled(false);
        doneBtn.setAlpha(0.5f);
        doneBtn.setOnClickListener(v -> {
            dismiss();
            if (callback != null) callback.onPhotosSelected(new ArrayList<>(selected));
        });
        bar.addView(doneBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void dismiss() {
        loader.shutdownNow();
        super.dismiss();
    }

    // ── Grid population ───────────────────────────────────────────────────────

    private void populateGrid(GridLayout grid) {
        List<Uri> imageUris = queryRecentImages();
        if (imageUris.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("No photos found on this device.");
            empty.setTextColor(Color.parseColor("#888888"));
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            grid.addView(empty);
            return;
        }

        int cellSizePx = dp(THUMB_SIZE);

        for (Uri uri : imageUris) {
            FrameLayout cell = new FrameLayout(getContext());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width  = cellSizePx;
            lp.height = cellSizePx;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(1), dp(1), dp(1), dp(1));
            cell.setLayoutParams(lp);

            ImageView thumb = new ImageView(getContext());
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(Color.parseColor("#2A2A2A"));
            cell.addView(thumb, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Selection overlay (checkmark)
            TextView checkOverlay = new TextView(getContext());
            checkOverlay.setText("✓");
            checkOverlay.setTextSize(22f);
            checkOverlay.setTextColor(Color.WHITE);
            checkOverlay.setTypeface(null, Typeface.BOLD);
            checkOverlay.setGravity(Gravity.CENTER);
            checkOverlay.setBackgroundColor(Color.argb(160, 30, 94, 32));
            checkOverlay.setVisibility(View.GONE);
            cell.addView(checkOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            cell.setOnClickListener(v -> toggleSelection(uri, checkOverlay));
            grid.addView(cell);

            // Load thumbnail asynchronously
            loader.execute(() -> {
                Bitmap bm = PhotoAttachmentManager.loadThumbnail(getContext(), uri);
                main.post(() -> {
                    if (bm != null) thumb.setImageBitmap(bm);
                });
            });
        }
    }

    private void toggleSelection(Uri uri, TextView checkOverlay) {
        if (selected.contains(uri)) {
            selected.remove(uri);
            checkOverlay.setVisibility(View.GONE);
        } else {
            if (selected.size() >= PhotoAttachmentManager.MAX_PHOTOS) {
                Toast.makeText(getContext(),
                        "Maximum " + PhotoAttachmentManager.MAX_PHOTOS + " photos",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            selected.add(uri);
            checkOverlay.setVisibility(View.VISIBLE);
        }
        int n = selected.size();
        doneBtn.setEnabled(n > 0);
        doneBtn.setAlpha(n > 0 ? 1f : 0.5f);
        doneBtn.setText(n > 0 ? "Add " + n + " Photo" + (n == 1 ? "" : "s") : "Add Photos");
    }

    private List<Uri> queryRecentImages() {
        List<Uri> uris = new ArrayList<>();
        String[] projection = {MediaStore.Images.Media._ID};
        String sortOrder    = MediaStore.Images.Media.DATE_MODIFIED + " DESC";
        try (Cursor cursor = getContext().getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder)) {
            if (cursor == null) return uris;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (cursor.moveToNext() && uris.size() < MAX_ITEMS) {
                long id = cursor.getLong(idCol);
                uris.add(ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
            }
        } catch (Exception ignored) {}
        return uris;
    }

    private View spacer() {
        return new View(getContext());
    }

    private int dp(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }
}
