# Photo & Attachments Feature — Implementation Plan
**Branch:** `Photo&Attachments`  
**Role:** Esri Solutions Engineer + Android Software Engineer  
**Target:** ArcGIS QuickCapture-compatible photo attachment support in the ATAK plugin

---

## How ArcGIS Feature Service Attachments Work

Before writing a line of code, understand the REST contract:

1. A feature layer must have `hasAttachments: true` in its layer definition (set in ArcGIS Online/Enterprise when the layer is created).
2. Attachments are **linked to an existing feature** using its server-assigned `OBJECTID`.
3. The upload call is a **multipart/form-data POST** to:
   ```
   POST <featureServiceUrl>/<layerId>/<objectId>/addAttachment
   Content-Type: multipart/form-data
   Body fields:
     f=json
     attachment=<binary file stream>
   ```
4. The response returns `{ "addAttachmentResult": { "objectId": <attachmentId>, "success": true } }`.
5. **Implication for our flow:** Photos cannot be uploaded until AFTER the feature is submitted and we have an OBJECTID. The submit-then-upload sequence is non-negotiable.

---

## Architecture Overview

```
User taps template button
        │
        ▼
QcFormDialog (modified)
  ├── existing text/numeric fields
  └── NEW: PhotoAttachmentSection
        ├── [Camera] button  → CameraCapture intent → temp file → thumbnail preview
        ├── [Gallery] button → MediaPicker intent   → URI      → thumbnail preview
        └── Photo queue (List<Uri>)
        │
        ▼ (user taps Submit)
ArcGisQuickCaptureClient.addFeature()   ← unchanged
        │ returns OBJECTID
        ▼
NEW: ArcGisQuickCaptureClient.addAttachment(featureServiceUrl, layerId, objectId, Uri, context)
        │  ← called once per photo in sequence
        ▼
Done — success/failure toast per attachment
```

---

## Step-by-Step Implementation Plan

### Step 1 — Verify Attachment Support in Layer Definition

**Why:** Not every feature layer has attachments enabled. We need to surface this early and gracefully degrade when unsupported.

**What to do:**

1. In `ArcGisQuickCaptureClient.java`, add a new method:
   ```java
   public boolean fetchHasAttachments(String featureServiceUrl) throws IOException
   ```
   - GET `<featureServiceUrl>?f=json` (the layer JSON endpoint)
   - Parse `"hasAttachments": true/false` from the response
   - Cache the result in a `Map<String, Boolean>` keyed by URL

2. In `FieldTakDropDownReceiver.java`, after the project loads and feature URLs are resolved, call `fetchHasAttachments()` for each data source URL in the background. Store results.

3. Pass a `boolean supportsAttachments` flag into `QcFormDialog` when constructing it.

**Files touched:** `ArcGisQuickCaptureClient.java`, `FieldTakDropDownReceiver.java`, `QcFormDialog.java`

---

### Step 2 — Android Permissions & FileProvider

**Why:** Android 10+ (API 29+) removed WRITE_EXTERNAL_STORAGE for apps targeting API 29+. Camera intents require a content URI via FileProvider, not a file path. READ_MEDIA_IMAGES replaced READ_EXTERNAL_STORAGE on API 33+.

**AndroidManifest.xml changes:**

```xml
<!-- Add alongside existing CAMERA permission -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES"
    android:minSdkVersion="33" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- FileProvider for camera capture temp files -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

**New file: `app/src/main/res/xml/file_paths.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path
        name="captured_photos"
        path="Pictures/QuickCapture/" />
</paths>
```

**FileProvider authority constant** — add to `FieldTakDropDownReceiver.java` or a new `AppConstants.java`:
```java
public static final String FILE_PROVIDER_AUTHORITY =
    BuildConfig.APPLICATION_ID + ".fileprovider";
```

**Runtime permission request:** Camera is already runtime-checked for QR scanning. Add `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` check before gallery picker launch using the same pattern used in `QrScanDialog`.

**Files touched:** `AndroidManifest.xml`, new `res/xml/file_paths.xml`

---

### Step 3 — PhotoAttachmentManager (New Utility Class)

**Why:** Isolate all camera/gallery/file lifecycle logic in one place so `QcFormDialog` stays clean.

**New file:** `app/src/main/java/com/atakmap/android/plugintemplate/qc/PhotoAttachmentManager.java`

**Responsibilities:**

```java
public class PhotoAttachmentManager {

    // Create temp file + return its content URI (for camera intent)
    public Uri createCaptureUri(Context context)
    
    // Build the camera intent that writes to the given URI
    public Intent buildCameraIntent(Uri outputUri)
    
    // Build the gallery picker intent (handles API 33+ MediaPicker)
    public Intent buildGalleryIntent()
    
    // Given a URI (camera or gallery), produce a thumbnail Bitmap
    // Downsamples to ~300px wide; does not load full res into memory
    public Bitmap loadThumbnail(Context context, Uri photoUri)
    
    // Return the file size in bytes for a given URI
    public long getFileSizeBytes(Context context, Uri uri)
    
    // Validate file: < 10 MB, JPEG or PNG only
    // Returns null on pass, or an error string on fail
    public String validatePhoto(Context context, Uri uri)
}
```

**Key implementation notes:**
- Use `Intent.ACTION_IMAGE_CAPTURE` with `EXTRA_OUTPUT` set to the FileProvider URI
- For gallery on API 33+: `Intent(MediaStore.ACTION_PICK_IMAGES)` with `EXTRA_PICK_IMAGES_MAX`
- For gallery on API 32-: `Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)`
- Thumbnail: `BitmapFactory.Options.inSampleSize` to avoid OOM on large photos
- Store pending camera URI in `onSaveInstanceState` if dialog is recreated (rotation)

**Files touched:** New file only

---

### Step 4 — Modify QcFormDialog for Photo UI

**Why:** The user needs to see a photo section in the same form where they fill out attributes. Keeps the workflow linear.

**UI changes to `QcFormDialog.java`:**

When `supportsAttachments == true`, append a photo section below the attribute fields:

```
┌──────────────────────────────────┐
│  Field 1: [____________]         │
│  Field 2: [____________]         │
│  ...                             │
├──────────────────────────────────┤
│  Photos (optional)               │
│  ┌────┐  ┌────┐  ┌────┐         │
│  │IMG1│  │IMG2│  │ +  │ camera  │
│  └────┘  └────┘  └────┘ gallery │
│  Tap photo to remove             │
└──────────────────────────────────┘
│           [SUBMIT]               │
└──────────────────────────────────┘
```

**Layout additions (inline or separate include):**
- `TextView` "Photos (optional)" — section header
- `HorizontalScrollView` containing a `LinearLayout` of thumbnails
- Two `ImageButton`s: Camera and Gallery icons
- Each thumbnail: 80x80dp `ImageView` with an "×" overlay for removal
- Enforce a max photo count (e.g., 5) — disable buttons at limit

**Activity result handling:**
- `QcFormDialog` needs to handle `onActivityResult` for both camera and gallery
- Since this is a `Dialog` (not a `Fragment`), delegate to the hosting `FieldTakDropDownReceiver` or convert to `DialogFragment` — **recommended: convert to `DialogFragment`** so it owns its own `registerForActivityResult` launchers

**Alternative if DialogFragment conversion is too large in scope:**
- Pass `ActivityResultLauncher` references from `FieldTakDropDownReceiver` into `QcFormDialog`
- Store pending URIs in `FieldTakDropDownReceiver` and update the dialog via callback

**Submit callback change — expand `OnSubmitListener`:**
```java
// Old:
void onSubmit(String attributesJson);

// New:
void onSubmit(String attributesJson, List<Uri> photoUris);
```

**Files touched:** `QcFormDialog.java`, layout XML for the form page

---

### Step 5 — Add Attachment Upload to ArcGisQuickCaptureClient

**Why:** This is the Esri REST API call that makes attachments appear in AGOL/Portal.

**New method in `ArcGisQuickCaptureClient.java`:**

```java
public record AttachmentResult(boolean success, String error) {}

public AttachmentResult addAttachment(
    String featureServiceUrl,   // base URL (no trailing slash)
    int layerId,                // typically 0
    int objectId,               // server-assigned OBJECTID from addFeature
    Uri photoUri,
    Context context
) throws IOException
```

**Implementation notes:**
- Endpoint: `<featureServiceUrl>/<layerId>/<objectId>/addAttachment`
- Use `OkHttpClient` (already a transitive dependency via existing API calls)
- Build a `MultipartBody` with:
  - Part "f": `"json"`
  - Part "token": the active session token (if any)
  - Part "attachment": file bytes from `ContentResolver.openInputStream(uri)`; set filename and MIME type
- Parse response JSON: `addAttachmentResult.success`
- MIME type: detect from URI extension or `ContentResolver.getType(uri)` — default to `image/jpeg`
- Max retry: 1 retry on network timeout; surface errors to caller

**Files touched:** `ArcGisQuickCaptureClient.java`

---

### Step 6 — Wire Upload Into Submission Flow (FieldTakDropDownReceiver)

**Why:** The existing submission path calls `addFeature()` and gets an OBJECTID. We need to follow up with attachment uploads.

**Changes in `FieldTakDropDownReceiver.java`:**

Find the existing `QcFormDialog.OnSubmitListener` implementation (where `addFeature()` is called) and extend it:

```java
// Pseudocode — existing flow extended
worker.submit(() -> {
    int objectId = client.addFeature(featureUrl, geometry, attributes, token);
    
    if (objectId > 0 && !photoUris.isEmpty()) {
        int successCount = 0;
        for (Uri photo : photoUris) {
            AttachmentResult r = client.addAttachment(featureUrl, 0, objectId, photo, ctx);
            if (r.success()) successCount++;
        }
        // Post result toast on main thread
        String msg = successCount + "/" + photoUris.size() + " photos uploaded";
        runOnUiThread(() -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }
    
    // Clean up temp files (camera captures) after upload
    for (Uri photo : photoUris) {
        if ("file".equals(photo.getScheme())) {
            new File(photo.getPath()).delete();
        }
    }
});
```

**Error handling:**
- If `addFeature()` fails: show error, do NOT attempt attachment upload, keep photos so user can retry
- If attachment upload fails: show partial success toast; feature IS saved, attachments are not — log failure per-attachment

**Files touched:** `FieldTakDropDownReceiver.java`

---

### Step 7 — Project Model: Parse Attachment Support Flag

**Why:** The ArcGIS QuickCapture project JSON may include layer-level metadata. Parsing it here avoids repeated API calls.

**Changes in `QuickCaptureProject.java`:**

- In the `Template` inner class, add field: `public boolean supportsAttachments`
- During `parse()`, for each template, check the resolved feature service URL
- Alternatively (simpler): check at runtime in Step 1 and pass the flag in at dialog creation time — this approach is preferred since it keeps the model lean

**Files touched:** Minimal — `QuickCaptureProject.java` if flag is model-level, or handled entirely in `FieldTakDropDownReceiver.java`

---

### Step 8 — UI Layout Updates

**New/modified layout files:**

| File | Change |
|------|--------|
| `res/layout/dialog_qc_form.xml` | Add `<include layout="@layout/section_photo_attachment" />` at bottom |
| New: `res/layout/section_photo_attachment.xml` | Reusable photo section: header text, HorizontalScrollView of thumbnails, Camera + Gallery buttons |
| New: `res/layout/item_photo_thumbnail.xml` | Single 80x80dp thumbnail card with remove overlay |
| `res/layout-port/dialog_qc_form.xml` | Mirror changes for portrait variant |

**Drawables needed:**
- `ic_camera.xml` (vector, white on dark) — use Material Design camera icon
- `ic_gallery.xml` (vector, white on dark) — use Material Design photo_library icon
- `ic_remove_photo.xml` (small × badge, red circle) — overlay on thumbnails

---

### Step 9 — Testing Checklist

**Unit tests (new):**
- [ ] `PhotoAttachmentManagerTest` — thumbnail sizing, file validation (size, MIME type)
- [ ] `ArcGisQuickCaptureClientAttachmentTest` — mock HTTP; verify multipart body structure, error parsing

**Manual integration tests:**
- [ ] Connect to an AGOL feature service with `hasAttachments: true`
- [ ] Connect to a feature service with `hasAttachments: false` — confirm photo section is hidden
- [ ] Capture photo with camera → verify thumbnail appears
- [ ] Pick from gallery → verify thumbnail appears
- [ ] Remove a thumbnail → verify it disappears and upload count decreases
- [ ] Submit with 0 photos — no attachment calls made
- [ ] Submit with 3 photos — all 3 attached to the feature in AGOL
- [ ] Submit with oversized photo (>10 MB) — validation error shown before submit
- [ ] Network failure during attachment upload — feature saved, partial failure toast shown
- [ ] Rotate screen during form fill — photo queue survives rotation
- [ ] Test on API 28 device (legacy storage) and API 33+ device (MediaPicker)

---

### Step 10 — Cleanup & Polish

- [ ] Delete temp camera files after successful upload (see Step 6)
- [ ] Show a spinner/progress indicator during attachment upload (can reuse existing sync spinner pattern)
- [ ] Limit photos to 5 per submission (disable camera/gallery buttons at limit; show count "3/5")
- [ ] Log attachment upload results to ATAK LogTag ("QuickCapture")
- [ ] Update `PLUGIN_VERSION` to `1.5.0` in `app/build.gradle` (new feature warrants minor bump)

---

## File Change Summary

| File | Change Type |
|------|------------|
| `AndroidManifest.xml` | Add READ_MEDIA_IMAGES, READ_EXTERNAL_STORAGE permissions; add FileProvider |
| `res/xml/file_paths.xml` | New — FileProvider paths config |
| `ArcGisQuickCaptureClient.java` | Add `fetchHasAttachments()` and `addAttachment()` methods |
| `QcFormDialog.java` | Add photo section UI + expand OnSubmitListener to include photo URIs |
| `FieldTakDropDownReceiver.java` | Wire attachment upload after `addFeature()`; check `hasAttachments` on connect |
| `PhotoAttachmentManager.java` | New — camera intent, gallery intent, thumbnail, validation |
| `res/layout/section_photo_attachment.xml` | New — photo section layout |
| `res/layout/item_photo_thumbnail.xml` | New — single thumbnail card |
| `res/drawable/ic_camera.xml` | New — camera button icon |
| `res/drawable/ic_gallery.xml` | New — gallery button icon |
| `res/drawable/ic_remove_photo.xml` | New — thumbnail remove badge |
| `app/build.gradle` | Version bump to 1.5.0 |
| `.gitignore` | Added `*.apk`, `*.aab` ✅ (already done) |

---

## Key Esri API Decisions

**Q: Should we use the ArcGIS Runtime SDK or raw REST?**  
A: Raw REST. The plugin already communicates via raw OkHttp REST calls to avoid the 60+ MB Runtime SDK AAR dependency. Stay consistent.

**Q: Should attachments be uploaded in parallel or sequentially?**  
A: Sequentially, with a short delay between calls. The ArcGIS REST `/addAttachment` endpoint is synchronous server-side, and hammering it in parallel on a busy AGOL service can cause 503s. Sequential is safer and simpler to reason about for error handling.

**Q: What if the feature service is hosted on a private ArcGIS Enterprise with a self-signed cert?**  
A: The plugin already uses a custom OkHttpClient (check for `trustAllCerts` or similar). The attachment upload must use the same client instance — do not create a second OkHttpClient.

**Q: Should we support video attachments?**  
A: Not in this phase. Photos only — videos are large, and the upload experience would need resumable upload handling. Defer to a future iteration.

**Q: What about ArcGIS token auth?**  
A: The token from `QcSessionStore` must be included in the multipart body as the `token` field. The `addAttachment` endpoint requires the same auth as `addFeatures`.
