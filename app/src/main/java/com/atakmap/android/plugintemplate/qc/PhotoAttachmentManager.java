package com.atakmap.android.plugintemplate.qc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Static helpers for photo attachment lifecycle: temp files, thumbnails, validation. */
public final class PhotoAttachmentManager {

    public static final int  MAX_PHOTOS     = 5;
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final int THUMB_PX       = 300;

    private PhotoAttachmentManager() {}

    /**
     * Creates a fresh temp JPEG file in the app's external Pictures directory.
     * The returned file is the output target for a camera capture intent.
     */
    public static File createCaptureFile(Context context) throws IOException {
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "QuickCapture");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create capture directory: " + dir);
        }
        return new File(dir, "QC_" + System.currentTimeMillis() + ".jpg");
    }

    /**
     * Loads a downsampled thumbnail (≤ THUMB_PX px on the longest side).
     * Opens the URI twice — once to read bounds, once to decode — to stay memory-safe.
     */
    public static Bitmap loadThumbnail(Context context, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, bounds);
            }
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = Math.max(1, longest / THUMB_PX);
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is, null, decode);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns a human-readable error string if the photo fails validation,
     * or {@code null} if the photo is acceptable.
     */
    public static String validate(Context context, Uri uri) {
        long size = fileSizeBytes(context, uri);
        if (size == 0) return "Could not read photo file";
        if (size > MAX_FILE_BYTES) return "Photo exceeds 10 MB limit";
        String mime = context.getContentResolver().getType(uri);
        if (mime != null && !mime.startsWith("image/")) return "File is not an image";
        return null;
    }

    /** Returns the MIME type of the URI, defaulting to image/jpeg. */
    public static String getMimeType(Context context, Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        return (mime != null && !mime.isEmpty()) ? mime : "image/jpeg";
    }

    /** Returns the display filename for a URI, falling back to the last path segment. */
    public static String getFilename(Context context, Uri uri) {
        try (android.database.Cursor cursor = context.getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) {}
        String segment = uri.getLastPathSegment();
        return segment != null ? segment : "photo.jpg";
    }

    /** Deletes a file-scheme URI's underlying file (silently ignores failures). */
    public static void deleteTempFile(Uri uri) {
        if (uri != null && "file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) new File(path).delete();
        }
    }

    private static long fileSizeBytes(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return 0;
            long count = 0;
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) >= 0) count += n;
            return count;
        } catch (Exception e) {
            return 0;
        }
    }
}
