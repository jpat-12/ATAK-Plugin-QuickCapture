package com.atakmap.android.plugintemplate;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.plugin.R;
import com.atakmap.android.plugintemplate.qc.ArcGisQuickCaptureClient;
import com.atakmap.android.plugintemplate.qc.QuickCaptureProject;
import com.atakmap.coremap.maps.coords.GeoPoint;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FieldTakDropDownReceiver extends DropDownReceiver implements OnStateListener {
    public static final String SHOW = "com.atakmap.android.plugintemplate.SHOW_FIELDTAK";

    private final Context pluginContext;
    private final View root;
    private final ArcGisQuickCaptureClient client = new ArcGisQuickCaptureClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService iconWorker = Executors.newFixedThreadPool(2);
    private final LinearLayout projectArea;
    private final LinearLayout setupContent;
    private final TextView setupToggle;
    private final TextView setupLabel;
    private final EditText linkEdit;
    private final EditText tokenEdit;
    private final TextView title;
    private final TextView status;
    private QuickCaptureProject project;
    private final Map<String, Object> projectInputValues = new LinkedHashMap<>();

    public FieldTakDropDownReceiver(MapView mapView, Context pluginContext) {
        super(mapView);
        this.pluginContext = pluginContext;
        root = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);
        linkEdit = root.findViewById(R.id.qc_link);
        tokenEdit = root.findViewById(R.id.qc_token);
        title = root.findViewById(R.id.qc_title);
        status = root.findViewById(R.id.qc_status);
        projectArea = root.findViewById(R.id.qc_project_area);
        setupContent = root.findViewById(R.id.qc_setup_content);
        setupToggle = root.findViewById(R.id.qc_setup_toggle);
        setupLabel = root.findViewById(R.id.qc_setup_label);
        root.findViewById(R.id.qc_setup_header).setOnClickListener(v ->
                setSetupExpanded(setupContent.getVisibility() != View.VISIBLE));
        root.findViewById(R.id.qc_scan).setOnClickListener(v -> scan());
        root.findViewById(R.id.qc_load).setOnClickListener(v -> load(linkEdit.getText().toString()));
        root.findViewById(R.id.qc_clear).setOnClickListener(v -> clear());
        setRetain(true);
        restore();
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
        if (project == null) return;
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
                button.setBackground(card);
                button.setElevation(dp(3));
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = 0;
                lp.height = dp(104);
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                lp.setMargins(dp(5), dp(5), dp(5), dp(5));
                button.setLayoutParams(lp);
                button.setOnClickListener(v -> {
                    if (project.isReferenceLink(template)) {
                        showReferenceLink(template);
                    } else {
                        capture(template);
                    }
                });
                grid.addView(button);
                loadButtonIcon(button, template);
            }
            projectArea.addView(section);
        }
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

    private void loadButtonIcon(Button button, QuickCaptureProject.Template template) {
        if (template.image == null || template.image.isEmpty()) return;
        final String token = tokenEdit.getText().toString().trim();
        iconWorker.execute(() -> {
            android.graphics.Bitmap bitmap = client.downloadTemplateIcon(project, template, token, dp(42));
            if (bitmap == null) return;
            getMapView().post(() -> {
                BitmapDrawable drawable = new BitmapDrawable(pluginContext.getResources(), bitmap);
                drawable.setBounds(0, 0, dp(42), dp(42));
                button.setCompoundDrawables(null, drawable, null, null);
                button.setCompoundDrawablePadding(dp(5));
            });
        });
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
        status.setText("Submitting " + template.label + " directly to Feature Layer...");
        final QuickCaptureProject activeProject = project;
        final String callsign = getMapView().getDeviceCallsign();
        final String token = tokenEdit.getText().toString().trim();
        worker.execute(() -> {
            try {
                JSONObject attributes = activeProject.resolveAttributes(template, inputs, callsign, point);
                client.addFeature(activeProject.urlFor(template), token, point, attributes);
                getMapView().post(() -> status.setText("Captured " + template.label + " at "
                        + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())));
            } catch (Exception e) {
                getMapView().post(() -> {
                    status.setText("Submission failed");
                    toast(e.getMessage());
                });
            }
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
        worker.shutdownNow();
        iconWorker.shutdownNow();
    }
    @Override public void onDropDownSelectionRemoved() {}
    @Override public void onDropDownVisible(boolean visible) {}
    @Override public void onDropDownSizeChanged(double width, double height) {}
    @Override public void onDropDownClose() {}
}
