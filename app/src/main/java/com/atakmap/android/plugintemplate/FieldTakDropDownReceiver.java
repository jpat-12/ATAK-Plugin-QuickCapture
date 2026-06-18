package com.atakmap.android.plugintemplate;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.importfiles.sort.ImportUserIconSetSort;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.quickcapture.plugin.R;
import com.atakmap.android.plugintemplate.qc.ArcGisQuickCaptureClient;
import com.atakmap.android.plugintemplate.qc.QuickCaptureProject;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FieldTakDropDownReceiver extends DropDownReceiver implements OnStateListener {
    public static final String SHOW = "com.atakmap.android.quickcapture.SHOW_PLUGIN";

    private final Context pluginContext;
    private final View root;
    private final ArcGisQuickCaptureClient client = new ArcGisQuickCaptureClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService iconWorker = Executors.newSingleThreadExecutor();
    private final LinearLayout projectArea;
    private final LinearLayout setupContent;
    private final TextView setupToggle;
    private final TextView setupLabel;
    private final EditText linkEdit;
    private final EditText tokenEdit;
    private final TextView title;
    private final TextView status;
    private final TextView qcCount;
    private int captureCount = 0;
    private QuickCaptureProject project;
    private final Map<String, Object> projectInputValues = new LinkedHashMap<>();
    private final Map<String, Marker> layerMarkers = new LinkedHashMap<>();
    /** templateId → CoT UID of the immediate-feedback temp marker */
    private final Map<String, String> tempMarkerUids = new LinkedHashMap<>();
    /** CoT UID → ArcGIS OBJECTID, for syncing edits back to the feature layer */
    private final Map<String, Long> markerObjectIds = new LinkedHashMap<>();
    /** CoT UID → feature layer URL, so we know which layer to update */
    private final Map<String, String> markerLayerUrls = new LinkedHashMap<>();
    private MapEventDispatcher.MapEventDispatchListener dragSyncListener;
    private final Map<String, View> checkmarks = new LinkedHashMap<>();
    private final Map<String, String> templateIconUris = new LinkedHashMap<>();
    private String projectIconsetUid = null;
    private final Map<String, byte[]> iconsetPngBytes = new LinkedHashMap<>();  // filename → PNG
    private final Map<String, String> templateFilenames = new LinkedHashMap<>(); // templateId → filename
    private QuickCaptureProject.Template pendingTemplate;
    private Map<String, Object> pendingInputs;
    private MapEventDispatcher.MapEventDispatchListener placementListener;

    public FieldTakDropDownReceiver(MapView mapView, Context pluginContext) {
        super(mapView);
        this.pluginContext = pluginContext;
        root = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);
        linkEdit = root.findViewById(R.id.qc_link);
        tokenEdit = root.findViewById(R.id.qc_token);
        title = root.findViewById(R.id.qc_title);
        status = root.findViewById(R.id.qc_status);
        qcCount = root.findViewById(R.id.qc_count);
        projectArea = root.findViewById(R.id.qc_project_area);
        setupContent = root.findViewById(R.id.qc_setup_content);
        setupToggle = root.findViewById(R.id.qc_setup_toggle);
        setupLabel = root.findViewById(R.id.qc_setup_label);
        root.findViewById(R.id.qc_setup_header).setOnClickListener(v ->
                setSetupExpanded(setupContent.getVisibility() != View.VISIBLE));
        root.findViewById(R.id.qc_scan).setOnClickListener(v -> scan());
        root.findViewById(R.id.qc_load).setOnClickListener(v -> load(linkEdit.getText().toString()));
        root.findViewById(R.id.qc_clear).setOnClickListener(v -> clear());
        root.findViewById(R.id.qc_sync).setOnClickListener(v -> syncAll());
        setRetain(true);
        restore();
        dragSyncListener = event -> {
            MapItem item = event.getItem();
            if (item == null) return;
            String uid = item.getUID();
            Long objectId = markerObjectIds.get(uid);
            String layerUrl = markerLayerUrls.get(uid);
            if (objectId == null || layerUrl == null || !(item instanceof Marker)) return;
            GeoPoint pt = ((Marker) item).getPoint();
            final String token = tokenEdit.getText().toString().trim();
            worker.execute(() -> {
                try {
                    client.updateFeature(layerUrl, token, objectId, pt, new JSONObject());
                } catch (Exception ignored) {}
            });
        };
        getMapView().getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_DRAG_DROPPED, dragSyncListener);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SHOW.equals(intent.getAction())) {
            showDropDown(root, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);
        }
    }

    private void scan() {
        new QrScanDialog(getMapView().getContext(), pluginContext, value -> {
            linkEdit.setText(value);
            load(value);
        }).show();
    }

    private void load(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            toast("Scan a QuickCapture QR code or enter a project link");
            return;
        }
        final String source = raw.trim();
        final String token = tokenEdit.getText().toString().trim();
        preferences().edit().putString("source", source).putString("token", token).apply();
        status.setText("Downloading project...");
        worker.execute(() -> {
            try {
                QuickCaptureProject loaded = client.downloadProject(source, token);
                getMapView().post(() -> showProject(loaded));
            } catch (Exception e) {
                getMapView().post(() -> {
                    status.setText("Could not load project");
                    toast(e.getMessage());
                });
            }
        });
    }

    private void showProject(QuickCaptureProject loaded) {
        project = loaded;
        title.setText(loaded.title);
        status.setText(loaded.templates.size() + " capture button(s)");
        linkEdit.setText(loaded.source);
        preferences().edit()
                .putString("source", loaded.source)
                .putString("token", tokenEdit.getText().toString().trim())
                .apply();
        projectIconsetUid = extractIconsetUid(loaded.source, loaded.itemId);
        iconsetPngBytes.clear();
        templateFilenames.clear();
        templateIconUris.clear();
        askForProjectInputs();
    }

    private void askForProjectInputs() {
        List<QuickCaptureProject.UserInput> inputs = project.projectInputs();
        if (inputs.isEmpty()) {
            finishProjectOpen();
            return;
        }
        showInputDialog("Project Setup", inputs, values -> {
            projectInputValues.clear();
            projectInputValues.putAll(values);
            finishProjectOpen();
        });
    }

    private void finishProjectOpen() {
        setSetupExpanded(false);
        renderButtons();
    }

    private void renderButtons() {
        projectArea.removeAllViews();
        checkmarks.clear();
        if (project == null) return;
        final Map<String, Button> buttonMap = new LinkedHashMap<>();
        Map<String, List<QuickCaptureProject.Template>> groups = new LinkedHashMap<>();
        for (QuickCaptureProject.Template template : project.templates) {
            String group = template.group == null || template.group.isEmpty() ? "CAPTURE" : template.group;
            groups.computeIfAbsent(group, key -> new ArrayList<>()).add(template);
        }
        for (Map.Entry<String, List<QuickCaptureProject.Template>> entry : groups.entrySet()) {
            LinearLayout section = new LinearLayout(pluginContext);
            section.setOrientation(LinearLayout.VERTICAL);
            section.setBackgroundResource(R.drawable.qc_group_bg);
            LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sectionLp.setMargins(0, dp(5), 0, dp(8));
            section.setLayoutParams(sectionLp);

            LinearLayout header = new LinearLayout(pluginContext);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(6), 0, dp(4), dp(3));
            TextView heading = text(entry.getKey(), 14, Color.WHITE);
            heading.setTypeface(null, Typeface.BOLD);
            heading.setPadding(0, 0, 0, 0);
            header.addView(heading, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView toggle = text("▲", 17, Color.WHITE);
            toggle.setGravity(Gravity.CENTER);
            toggle.setPadding(dp(12), 0, dp(8), 0);
            header.addView(toggle);
            section.addView(header);

            GridLayout grid = new GridLayout(pluginContext);
            grid.setColumnCount(project.columnsFor(entry.getKey()));
            section.addView(grid, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            header.setOnClickListener(v -> {
                boolean expanded = grid.getVisibility() == View.VISIBLE;
                grid.setVisibility(expanded ? View.GONE : View.VISIBLE);
                toggle.setText(expanded ? "▼" : "▲");
            });
            for (QuickCaptureProject.Template template : entry.getValue()) {
                Button button = new Button(pluginContext);
                button.setText(project.isReferenceLink(template)
                        ? "LINK\n" + template.label : template.label);
                int background = parseColor(template.color, "#078FC5");
                button.setTextColor(contrastColor(background));
                button.setTextSize(14);
                button.setTypeface(null, Typeface.BOLD);
                button.setGravity(Gravity.CENTER);
                button.setAllCaps(false);
                GradientDrawable card = new GradientDrawable();
                card.setColor(background);
                card.setStroke(dp(2), Color.WHITE);
                card.setCornerRadius(dp(9));
                GradientDrawable rippleMask = new GradientDrawable();
                rippleMask.setColor(Color.WHITE);
                rippleMask.setCornerRadius(dp(9));
                button.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), card, rippleMask));
                button.setElevation(dp(3));
                button.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                button.setOnClickListener(v -> {
                    if (project.isReferenceLink(template)) {
                        showReferenceLink(template);
                    } else {
                        capture(template);
                    }
                });
                button.setOnLongClickListener(v -> {
                    if (!project.isReferenceLink(template)) startMapPlacement(template);
                    return true;
                });
                // Checkmark overlay — shown briefly on successful capture
                TextView check = new TextView(pluginContext);
                check.setText("✓");
                check.setTextSize(42);
                check.setTextColor(Color.WHITE);
                check.setTypeface(null, Typeface.BOLD);
                check.setGravity(Gravity.CENTER);
                GradientDrawable checkBg = new GradientDrawable();
                checkBg.setColor(Color.argb(220, 34, 170, 34));
                checkBg.setCornerRadius(dp(9));
                check.setBackground(checkBg);
                check.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                check.setVisibility(View.GONE);
                FrameLayout wrapper = new FrameLayout(pluginContext);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = 0;
                lp.height = dp(104);
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                lp.setMargins(dp(5), dp(5), dp(5), dp(5));
                wrapper.setLayoutParams(lp);
                wrapper.addView(button);
                wrapper.addView(check);
                checkmarks.put(template.id, check);
                buttonMap.put(template.id, button);
                grid.addView(wrapper);
            }
            projectArea.addView(section);
        }
        loadAllIcons(buttonMap);
    }

    private void capture(QuickCaptureProject.Template template) {
        List<QuickCaptureProject.UserInput> inputs = project.inputsFor(template);
        if (inputs.isEmpty()) {
            submit(template, new LinkedHashMap<>(projectInputValues));
            return;
        }
        showInputDialog(template.label, inputs, values -> {
            Map<String, Object> merged = new LinkedHashMap<>(projectInputValues);
            merged.putAll(values);
            submit(template, merged);
        });
    }

    private interface InputCallback {
        void onValues(Map<String, Object> values);
    }

    private void showInputDialog(String dialogTitle, List<QuickCaptureProject.UserInput> inputs,
                                 InputCallback callback) {
        LinearLayout form = new LinearLayout(pluginContext);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(4), dp(16), dp(4));
        Map<String, View> views = new LinkedHashMap<>();
        for (QuickCaptureProject.UserInput input : inputs) {
            form.addView(text(input.label + (input.required ? " *" : ""), 12, Color.LTGRAY));
            View field;
            if (!input.choices.isEmpty()) {
                Spinner spinner = new Spinner(pluginContext);
                List<String> names = new ArrayList<>();
                for (QuickCaptureProject.Choice choice : input.choices) names.add(choice.name);
                spinner.setAdapter(new ArrayAdapter<>(pluginContext,
                        android.R.layout.simple_spinner_dropdown_item, names));
                field = spinner;
            } else {
                EditText edit = new EditText(pluginContext);
                edit.setTextColor(Color.WHITE);
                edit.setSingleLine(false);
                edit.setInputType(input.numeric
                        ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                field = edit;
            }
            form.addView(field, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            views.put(input.id, field);
        }
        ScrollView scroll = new ScrollView(pluginContext);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(getMapView().getContext())
                .setTitle(dialogTitle).setView(scroll).setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    Map<String, Object> values = readInputs(inputs, views);
                    if (values == null) return;
                    dialog.dismiss();
                    callback.onValues(values);
                }));
        dialog.show();
    }

    /** Downloads all template icons, then prompts the user to import the iconset into ATAK. */
    private void loadAllIcons(Map<String, Button> buttons) {
        final QuickCaptureProject proj = project;
        final String uid = projectIconsetUid;
        if (proj == null || uid == null) return;
        final String token = tokenEdit.getText().toString().trim();
        iconWorker.execute(() -> {
            Map<String, android.graphics.Bitmap> bitmaps = new LinkedHashMap<>();
            for (QuickCaptureProject.Template t : proj.templates) {
                if (t.image == null || t.image.isEmpty()) continue;
                if (proj.isReferenceLink(t)) continue;
                android.graphics.Bitmap bm = client.downloadTemplateIcon(proj, t, token, 32);
                if (bm == null) continue;
                // Normalize to exactly 32×32 — ATAK iconsets expect this size
                if (bm.getWidth() != 32 || bm.getHeight() != 32) {
                    android.graphics.Bitmap scaled =
                            android.graphics.Bitmap.createScaledBitmap(bm, 32, 32, true);
                    bm.recycle();
                    bm = scaled;
                }
                String filename = t.image.replaceAll("(?i)\\.svg$", ".png").replace("/", "_");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                if (!bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)) continue;
                iconsetPngBytes.put(filename, baos.toByteArray());
                templateFilenames.put(t.id, filename);
                bitmaps.put(t.id, bm);
            }
            // Build the iconset ZIP in memory while still on the background thread
            final byte[] zipBytes = buildIconsetZipBytes(uid, proj.title);
            // Write PNGs to disk so setIcon() can use file:// URIs — these work immediately
            // without requiring the iconset to be in ATAK's DB, avoiding the race where
            // ATAK's CoT archive processor overrides setIcon() with a dot.
            File iconDir = new File(
                    android.os.Environment.getExternalStorageDirectory(),
                    "atak/tools/quickcapture/" + uid);
            //noinspection ResultOfMethodCallIgnored
            iconDir.mkdirs();
            Map<String, String> uris = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : templateFilenames.entrySet()) {
                String filename = e.getValue();
                byte[] bytes = iconsetPngBytes.get(filename);
                if (bytes != null) {
                    File f = new File(iconDir, filename);
                    try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(bytes); }
                    catch (Exception ignored) {}
                    uris.put(e.getKey(), "file://" + f.getAbsolutePath());
                }
            }
            getMapView().post(() -> {
                templateIconUris.putAll(uris);
                for (Map.Entry<String, android.graphics.Bitmap> e : bitmaps.entrySet()) {
                    Button btn = buttons.get(e.getKey());
                    if (btn == null) continue;
                    BitmapDrawable drawable = new BitmapDrawable(pluginContext.getResources(), e.getValue());
                    drawable.setBounds(0, 0, dp(42), dp(42));
                    btn.setCompoundDrawables(null, drawable, null, null);
                    btn.setCompoundDrawablePadding(dp(5));
                }
                if (zipBytes != null) {
                    promptIconsetImport(proj.title, uid, zipBytes);
                }
            });
        });
    }

    private byte[] buildIconsetZipBytes(String uid, String projectTitle) {
        if (iconsetPngBytes.isEmpty()) return null;
        try {
            String name = projectTitle != null && !projectTitle.isEmpty() ? projectTitle : "QuickCapture";
            StringBuilder xml = new StringBuilder();
            xml.append("<iconset name=\"").append(xmlEscape(name)).append("\"")
               .append(" uid=\"").append(uid).append("\"")
               .append(" defaultGroup=\"QuickCapture\"")
               .append(" skipResize=\"false\" version=\"1\">\n");
            for (String filename : iconsetPngBytes.keySet()) {
                xml.append("  <icon name=\"").append(xmlEscape(filename)).append("\" />\n");
            }
            xml.append("</iconset>");

            ByteArrayOutputStream zipBuf = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(zipBuf)) {
                zos.putNextEntry(new ZipEntry("iconset.xml"));
                zos.write(xml.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                for (Map.Entry<String, byte[]> e : iconsetPngBytes.entrySet()) {
                    zos.putNextEntry(new ZipEntry("QuickCapture/" + e.getKey()));
                    zos.write(e.getValue());
                    zos.closeEntry();
                }
            }
            return zipBuf.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private void promptIconsetImport(String projectName, String uid, byte[] zipBytes) {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle("Import Iconset")
                .setMessage("Import \"" + projectName + "\" icons into ATAK's icon library?")
                .setPositiveButton("Import", (d, w) -> iconWorker.execute(() -> {
                    try {
                        File dir = new File(
                                android.os.Environment.getExternalStorageDirectory(), "atak/iconsets");
                        //noinspection ResultOfMethodCallIgnored
                        dir.mkdirs();
                        File zipFile = new File(dir, "QC-" + uid + ".zip");
                        try (FileOutputStream fos = new FileOutputStream(zipFile)) {
                            fos.write(zipBytes);
                        }
                        // Use ATAK's native iconset import pipeline — same code path as
                        // the "Add Iconset" button in the Iconset Manager
                        ImportUserIconSetSort importer =
                                new ImportUserIconSetSort(getMapView().getContext(), false);
                        importer.beginImport(zipFile);
                        AtakBroadcast.getInstance().sendBroadcast(
                                new android.content.Intent("com.atakmap.app.REFRESH_ICONSET"));
                    } catch (Exception ignored) {}
                }))
                .setNegativeButton("Skip", null)
                .show();
    }

    private void applyTemplateIcon(Marker m, QuickCaptureProject.Template template) {
        String path = templateIconUris.get(template.id);
        if (path == null) return;
        try {
            m.setIcon(new Icon.Builder()
                    .setImageUri(Icon.STATE_DEFAULT, path)
                    .build());
        } catch (Exception ignored) {}
    }

    private static String extractIconsetUid(String source, String itemId) {
        if (itemId != null && itemId.matches("[0-9a-f]{32}")) return itemId;
        String key = (itemId != null && !itemId.isEmpty()) ? itemId
                   : (source != null && !source.trim().isEmpty() ? source.trim() : "quickcapture");
        try {
            byte[] hash = java.security.MessageDigest.getInstance("MD5")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ignored) {
            return "00000000000000000000000000000001";
        }
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Map<String, Object> readInputs(List<QuickCaptureProject.UserInput> inputs,
                                            Map<String, View> views) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (QuickCaptureProject.UserInput input : inputs) {
            View view = views.get(input.id);
            Object value;
            if (view instanceof Spinner) {
                int index = ((Spinner) view).getSelectedItemPosition();
                value = index >= 0 && index < input.choices.size() ? input.choices.get(index).code : "";
            } else {
                value = ((EditText) view).getText().toString().trim();
            }
            if (input.required && String.valueOf(value).isEmpty()) {
                toast(input.label + " is required");
                return null;
            }
            values.put(input.id, value);
        }
        return values;
    }

    private void submit(QuickCaptureProject.Template template, Map<String, Object> inputs) {
        GeoPoint point = getMapView().getSelfMarker() == null ? null : getMapView().getSelfMarker().getPoint();
        if (point == null || !point.isValid()) {
            toast("ATAK GPS position is unavailable");
            return;
        }
        submit(template, inputs, point);
    }

    private void submit(QuickCaptureProject.Template template, Map<String, Object> inputs, GeoPoint point) {
        status.setText("Submitting " + template.label + "...");
        final QuickCaptureProject activeProject = project;
        final String callsign = getMapView().getDeviceCallsign();
        final String token = tokenEdit.getText().toString().trim();
        worker.execute(() -> {
            try {
                JSONObject attributes = activeProject.resolveAttributes(template, inputs, callsign, point);
                String layerUrl = activeProject.urlFor(template);
                final long objectId = client.addFeature(layerUrl, token, point, attributes);
                getMapView().post(() -> {
                    String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                    status.setText("Captured " + template.label + " at " + time);
                    captureCount++;
                    qcCount.setText(captureCount + (captureCount == 1 ? " pt" : " pts"));
                    qcCount.setVisibility(View.VISIBLE);
                    toast(template.label + " captured at " + time);
                    dropMarker(template, point, objectId, layerUrl);
                    showCheckmark(template);
                });
                // Pull the full layer — confirms capture and shows all team captures
                try {
                    final List<ArcGisQuickCaptureClient.FeatureRecord> records =
                            client.queryFeatures(layerUrl, token);
                    getMapView().post(() -> updateLayerMarkers(template, records));
                } catch (Exception ignored) {}
            } catch (Exception e) {
                getMapView().post(() -> {
                    status.setText("Submission failed");
                    toast(e.getMessage());
                });
            }
        });
    }

    private void startMapPlacement(QuickCaptureProject.Template template) {
        cancelPlacement();
        List<QuickCaptureProject.UserInput> inputs = project.inputsFor(template);
        if (inputs.isEmpty()) {
            beginPlacement(template, new LinkedHashMap<>(projectInputValues));
        } else {
            showInputDialog(template.label, inputs, values -> {
                Map<String, Object> merged = new LinkedHashMap<>(projectInputValues);
                merged.putAll(values);
                beginPlacement(template, merged);
            });
        }
    }

    private void beginPlacement(QuickCaptureProject.Template template, Map<String, Object> inputs) {
        pendingTemplate = template;
        pendingInputs   = inputs;
        status.setText("Tap the map to place " + template.label + "...");
        toast("Tap map to capture " + template.label);
        closeDropDown();
        placementListener = event -> {
            android.graphics.PointF sp = event.getPointF();
            GeoPointMetaData gpm = getMapView().inverseWithElevation(sp.x, sp.y);
            GeoPoint point = gpm != null ? gpm.get() : null;
            cancelPlacement();
            if (point != null && point.isValid()) submit(template, inputs, point);
        };
        getMapView().getMapEventDispatcher().pushListeners();
        getMapView().getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        getMapView().getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, placementListener);
    }

    private void cancelPlacement() {
        if (placementListener != null) {
            getMapView().getMapEventDispatcher().popListeners();
            placementListener = null;
        }
        pendingTemplate = null;
        pendingInputs   = null;
    }

    private void dropMarker(QuickCaptureProject.Template template, GeoPoint point,
                             long objectId, String layerUrl) {
        // Replace any previous temp marker for this template
        String oldUid = tempMarkerUids.remove(template.id);
        if (oldUid != null) {
            markerObjectIds.remove(oldUid);
            markerLayerUrls.remove(oldUid);
            dispatchCotDelete(oldUid);
        }
        try {
            String uid = "qc-" + UUID.randomUUID();
            tempMarkerUids.put(template.id, uid);
            if (objectId >= 0) markerObjectIds.put(uid, objectId);
            if (layerUrl != null) markerLayerUrls.put(uid, layerUrl);

            CoordinatedTime now = new CoordinatedTime();
            // Stale after 5 hours — long enough to survive a session
            CoordinatedTime stale = new CoordinatedTime(now.getMilliseconds() + 5L * 60 * 60 * 1000);

            CotEvent event = new CotEvent();
            event.setUID(uid);
            event.setType("a-f-G-U-C");
            event.setHow("m-g");
            event.setTime(now);
            event.setStart(now);
            event.setStale(stale);
            double hae = Double.isNaN(point.getAltitude()) ? CotPoint.UNKNOWN : point.getAltitude();
            event.setPoint(new CotPoint(point.getLatitude(), point.getLongitude(),
                    hae, CotPoint.UNKNOWN, CotPoint.UNKNOWN));

            CotDetail detail = new CotDetail();

            CotDetail contact = new CotDetail("contact");
            contact.setAttribute("callsign", "QC: " + template.label);
            detail.addChild(contact);

            CotDetail remarks = new CotDetail("remarks");
            remarks.setInnerText("QuickCapture: " + template.label
                    + " at " + new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
            detail.addChild(remarks);

            // Custom icon via the imported iconset
            String filename = templateFilenames.get(template.id);
            if (filename != null && projectIconsetUid != null) {
                CotDetail usericon = new CotDetail("usericon");
                usericon.setAttribute("iconsetpath",
                        projectIconsetUid + "/QuickCapture/" + filename);
                detail.addChild(usericon);
            }

            // Persist this marker across ATAK restarts
            detail.addChild(new CotDetail("archive"));

            event.setDetail(detail);
            CotMapComponent.getInstance().getInternalDispatcher().dispatch(event, null);

            // The internal dispatcher processes synchronously on the main thread, so the
            // Marker already exists in the map group. Set its icon directly using the
            // sqlite:// URI — this doesn't depend on the iconset DB import completing first.
            String iconUri = templateIconUris.get(template.id);
            if (iconUri != null) {
                MapItem item = getMapView().getRootGroup().deepFindUID(uid);
                if (item instanceof Marker) {
                    ((Marker) item).setIcon(new Icon.Builder()
                            .setImageUri(Icon.STATE_DEFAULT, iconUri)
                            .build());
                }
            }
        } catch (Exception ignored) {}
    }

    private void dispatchCotDelete(String uid) {
        try {
            CoordinatedTime now = new CoordinatedTime();
            CotEvent event = new CotEvent();
            event.setUID(uid);
            event.setType("t-x-d-d");
            event.setHow("m-g");
            event.setTime(now);
            event.setStart(now);
            event.setStale(now);
            event.setPoint(new CotPoint(0, 0, 0, CotPoint.UNKNOWN, CotPoint.UNKNOWN));
            event.setDetail(new CotDetail());
            CotMapComponent.getInstance().getInternalDispatcher().dispatch(event, null);
        } catch (Exception ignored) {}
    }

    private void showCheckmark(QuickCaptureProject.Template template) {
        View check = checkmarks.get(template.id);
        if (check == null) return;
        check.setAlpha(1f);
        check.setVisibility(View.VISIBLE);
        check.animate().alpha(0f).setStartDelay(700).setDuration(400)
                .withEndAction(() -> check.setVisibility(View.GONE)).start();
    }

    private void updateLayerMarkers(QuickCaptureProject.Template template,
                                     List<ArcGisQuickCaptureClient.FeatureRecord> records) {
        // Sync confirmed — replace the temporary immediate-feedback marker
        String tempUid = tempMarkerUids.remove(template.id);
        if (tempUid != null) {
            markerObjectIds.remove(tempUid);
            markerLayerUrls.remove(tempUid);
            dispatchCotDelete(tempUid);
        }
        // Remove stale synced markers for this template's layer
        String prefix = "qcl-" + template.id + "-";
        List<String> stale = new ArrayList<>();
        for (String uid : layerMarkers.keySet()) {
            if (uid.startsWith(prefix)) stale.add(uid);
        }
        for (String uid : stale) {
            Marker m = layerMarkers.remove(uid);
            if (m != null) getMapView().getRootGroup().removeItem(m);
        }
        // Place fresh markers from the server
        int color = parseColor(template.color, "#078FC5");
        for (int i = 0; i < records.size(); i++) {
            ArcGisQuickCaptureClient.FeatureRecord rec = records.get(i);
            String uid = prefix + (rec.objectId >= 0 ? rec.objectId : i);
            try {
                GeoPoint point = new GeoPoint(rec.lat, rec.lon);
                Marker m = new Marker(point, uid);
                m.setType("a-f-G-U-C");
                m.setTitle("QC: " + template.label);
                m.setMetaString("callsign", "QC: " + template.label);
                m.setMetaInteger("color", color);
                m.setMetaString("remarks", "Synced from Feature Layer"
                        + (rec.objectId >= 0 ? " #" + rec.objectId : ""));
                m.setMetaBoolean("removable", false);
                applyTemplateIcon(m, template);
                getMapView().getRootGroup().addItem(m);
                layerMarkers.put(uid, m);
            } catch (Exception ignored) {}
        }
    }

    private void syncAll() {
        if (project == null) { toast("No project loaded"); return; }
        final QuickCaptureProject activeProject = project;
        final String token = tokenEdit.getText().toString().trim();
        status.setText("Syncing from server...");
        worker.execute(() -> {
            int count = 0;
            for (QuickCaptureProject.Template template : activeProject.templates) {
                try {
                    if (activeProject.isReferenceLink(template)) continue;
                    final List<ArcGisQuickCaptureClient.FeatureRecord> records =
                            client.queryFeatures(activeProject.urlFor(template), token);
                    getMapView().post(() -> updateLayerMarkers(template, records));
                    count++;
                } catch (Exception ignored) {}
            }
            final int synced = count;
            getMapView().post(() -> status.setText(synced > 0
                    ? "Synced " + synced + " layer(s) from server"
                    : "No layers available to sync"));
        });
    }

    private void restore() {
        String source = preferences().getString("source", "");
        String token = preferences().getString("token", "");
        linkEdit.setText(source);
        tokenEdit.setText(token);
        if (!source.isEmpty()) load(source);
    }

    private void clear() {
        project = null;
        projectInputValues.clear();
        title.setText("QuickCapture");
        status.setText("Scan a project QR code to begin");
        linkEdit.setText("");
        tokenEdit.setText("");
        projectArea.removeAllViews();
        setSetupExpanded(true);
        preferences().edit().clear().apply();
        captureCount = 0;
        qcCount.setVisibility(View.GONE);
        for (Marker m : layerMarkers.values()) getMapView().getRootGroup().removeItem(m);
        layerMarkers.clear();
        for (String uid : tempMarkerUids.values()) dispatchCotDelete(uid);
        tempMarkerUids.clear();
        markerObjectIds.clear();
        markerLayerUrls.clear();
        checkmarks.clear();
        templateIconUris.clear();
        projectIconsetUid = null;
        iconsetPngBytes.clear();
        templateFilenames.clear();
    }

    private SharedPreferences preferences() {
        return getMapView().getContext().getSharedPreferences("quickcapture", Context.MODE_PRIVATE);
    }

    private void setSetupExpanded(boolean expanded) {
        setupContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        setupToggle.setText(expanded ? "▲" : "▼");
        setupLabel.setTextColor(expanded ? Color.WHITE : Color.parseColor("#AEB3B8"));
        setupToggle.setTextColor(expanded ? Color.WHITE : Color.parseColor("#AEB3B8"));
    }

    private void openExternalLink(String rawUrl) {
        String fallback = rawUrl;
        Uri uri = Uri.parse(rawUrl);
        if ("arcgis-survey123".equalsIgnoreCase(uri.getScheme())) {
            String itemId = uri.getQueryParameter("itemId");
            if (itemId == null) itemId = uri.getQueryParameter("itemid");
            if (itemId != null) {
                fallback = "https://survey123.arcgis.app/?itemID=" + itemId;
            }
        }

        if (tryOpenUrl(rawUrl)) return;
        if (!fallback.equals(rawUrl) && tryOpenUrl(fallback)) return;
        toast("No app or browser is available to open this link");
    }

    private void showReferenceLink(QuickCaptureProject.Template template) {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle(template.label)
                .setMessage("This is a reference link. The QuickCapture project does not define "
                        + "a Feature Layer record for this card.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Open Link", (dialog, which) -> openExternalLink(template.url))
                .show();
    }

    private boolean tryOpenUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getMapView().getContext().startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int contrastColor(int background) {
        double luminance = 0.299 * Color.red(background)
                + 0.587 * Color.green(background) + 0.114 * Color.blue(background);
        return luminance > 175 ? Color.parseColor("#222222") : Color.WHITE;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(pluginContext);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(dp(6), dp(8), dp(6), dp(4));
        return view;
    }

    private int parseColor(String color, String fallback) {
        try { return Color.parseColor(color); } catch (Exception ignored) { return Color.parseColor(fallback); }
    }

    private int dp(int value) {
        return Math.round(value * pluginContext.getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(getMapView().getContext(), message == null ? "Operation failed" : message,
                Toast.LENGTH_LONG).show();
    }

    @Override public void disposeImpl() {
        cancelPlacement();
        if (dragSyncListener != null) {
            getMapView().getMapEventDispatcher().removeMapEventListener(
                    MapEvent.ITEM_DRAG_DROPPED, dragSyncListener);
            dragSyncListener = null;
        }
        worker.shutdownNow();
        iconWorker.shutdownNow();
    }
    @Override public void onDropDownSelectionRemoved() {}
    @Override public void onDropDownVisible(boolean visible) {
        if (visible && placementListener != null) cancelPlacement();
    }
    @Override public void onDropDownSizeChanged(double width, double height) {}
    @Override public void onDropDownClose() {}
}
