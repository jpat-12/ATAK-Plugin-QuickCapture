package com.atakmap.android.plugintemplate.qc;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.plugintemplate.qc.model.QcField;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen dialog that presents the parsed ArcGIS Feature Service fields as
 * a fillable form.  GPS coordinates and callsign are pre-filled and read-only;
 * all other user-editable fields get labelled {@link EditText} inputs.
 *
 * <p>When the user taps Submit, {@link OnSubmitListener#onSubmit(String)} is
 * called with a JSON attributes fragment ready for the {@code addFeatures} body.
 */
public class QcFormDialog extends Dialog {

    private static final String TAG = "FieldTak.QcForm";

    public interface OnSubmitListener {
        /** @param attributesJson Comma-separated key:value JSON pairs (no outer braces). */
        void onSubmit(String attributesJson);
    }

    private final List<QcField>    fields;
    private final String           projectName;
    private final double           lat, lon, altM;
    private final String           callsign;
    private final OnSubmitListener submitListener;

    /** Parallel to {@link #fields} — the EditText for each editable field. */
    private final List<EditText> fieldEdits = new ArrayList<>();

    public QcFormDialog(Context atakContext,
                        String projectName,
                        List<QcField> fields,
                        double lat, double lon, double altM,
                        String callsign,
                        OnSubmitListener submitListener) {
        super(atakContext);
        this.projectName    = projectName;
        this.fields         = fields;
        this.lat            = lat;
        this.lon            = lon;
        this.altM           = altM;
        this.callsign       = callsign;
        this.submitListener = submitListener;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                              ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Root ──────────────────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1A1A1A"));
        setContentView(root);

        // ── Header ────────────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.parseColor("#0D0D0D"));
        header.setPadding(dp(12), dp(10), dp(6), dp(10));
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header);

        TextView titleTv = new TextView(getContext());
        titleTv.setText(projectName);
        titleTv.setTextColor(Color.parseColor("#DFB228"));
        titleTv.setTextSize(15f);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(titleTv);

        Button closeBtn = new Button(getContext());
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(14f);
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn);

        // ── Scrollable form ───────────────────────────────────────────────────
        ScrollView scroll = new ScrollView(getContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setPadding(dp(14), dp(8), dp(14), dp(8));

        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(form);
        root.addView(scroll);

        // Auto-populated section
        sectionHeader(form, "AUTO-POPULATED");
        readOnlyRow(form, "Location",
                String.format(Locale.US, "%.6f°N  %.6f°E", lat, lon));
        if (!Double.isNaN(altM)) {
            readOnlyRow(form, "Altitude",
                    String.format(Locale.US, "%.1f m HAE", altM));
        }
        if (callsign != null && !callsign.isEmpty()) {
            readOnlyRow(form, "Callsign", callsign);
        }

        // User-editable fields
        if (!fields.isEmpty()) {
            sectionHeader(form, "SURVEY FIELDS");
            fieldEdits.clear();
            for (QcField f : fields) {
                fieldLabel(form, f.label + (f.nullable ? "" : " *"));

                EditText et = new EditText(getContext());
                et.setHint(f.label);
                et.setTextColor(Color.WHITE);
                et.setHintTextColor(Color.parseColor("#555555"));
                et.setBackgroundColor(Color.parseColor("#2A2A2A"));
                et.setPadding(dp(8), dp(8), dp(8), dp(8));

                if (f.isInteger()) {
                    et.setInputType(InputType.TYPE_CLASS_NUMBER
                            | InputType.TYPE_NUMBER_FLAG_SIGNED);
                } else if (f.isDecimal()) {
                    et.setInputType(InputType.TYPE_CLASS_NUMBER
                            | InputType.TYPE_NUMBER_FLAG_SIGNED
                            | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                } else {
                    et.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                    if (f.length > 0) {
                        et.setFilters(new InputFilter[]{
                                new InputFilter.LengthFilter(f.length)});
                    }
                }

                LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                etLp.setMargins(0, dp(2), 0, dp(10));
                et.setLayoutParams(etLp);
                form.addView(et);
                fieldEdits.add(et);
            }

            TextView legend = new TextView(getContext());
            legend.setText("* required field");
            legend.setTextColor(Color.parseColor("#888888"));
            legend.setTextSize(10f);
            form.addView(legend);

        } else {
            TextView note = new TextView(getContext());
            note.setText("No additional fields required.\n"
                       + "Your GPS location will be submitted automatically.");
            note.setTextColor(Color.parseColor("#AAAAAA"));
            note.setTextSize(12f);
            LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            noteLp.setMargins(0, dp(10), 0, 0);
            note.setLayoutParams(noteLp);
            form.addView(note);
        }

        // ── Submit button ─────────────────────────────────────────────────────
        Button submitBtn = new Button(getContext());
        submitBtn.setText("Submit Report");
        submitBtn.setTextColor(Color.WHITE);
        submitBtn.setTextSize(14f);
        submitBtn.setTypeface(null, Typeface.BOLD);
        submitBtn.setBackgroundColor(Color.parseColor("#1B5E20")); // dark green
        LinearLayout.LayoutParams submitLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        submitLp.setMargins(dp(14), dp(6), dp(14), dp(14));
        submitBtn.setLayoutParams(submitLp);
        submitBtn.setOnClickListener(v -> onSubmitClicked());
        root.addView(submitBtn);
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private void onSubmitClicked() {
        // Validate required fields
        for (int i = 0; i < fields.size(); i++) {
            QcField f   = fields.get(i);
            String  val = fieldEdits.get(i).getText().toString().trim();
            if (!f.nullable && val.isEmpty()) {
                Toast.makeText(getContext(),
                        "\"" + f.label + "\" is required",
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Build attributes JSON fragment
        StringBuilder attrs = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            QcField f   = fields.get(i);
            String  val = fieldEdits.get(i).getText().toString().trim();
            if (val.isEmpty()) continue;

            if (attrs.length() > 0) attrs.append(",");
            attrs.append("\"").append(f.name).append("\":");
            if (f.isNumeric()) {
                attrs.append(val);                                            // bare number
            } else {
                attrs.append("\"").append(val.replace("\"", "\\\"")).append("\"");
            }
        }

        if (callsign != null && !callsign.isEmpty()) {
            if (attrs.length() > 0) attrs.append(",");
            attrs.append("\"callsign\":\"").append(callsign).append("\"");
        }

        String json = attrs.toString();
        Log.d(TAG, "Submitting attributes: " + json);
        dismiss();
        if (submitListener != null) submitListener.onSubmit(json);
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private void sectionHeader(LinearLayout parent, String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#DFB228"));
        tv.setTextSize(11f);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(14), 0, dp(4));
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private void fieldLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#AAAAAA"));
        tv.setTextSize(11f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(2));
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private void readOnlyRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        row.setBackgroundColor(Color.parseColor("#0D0D0D"));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(3));
        row.setLayoutParams(rowLp);

        TextView lbl = new TextView(getContext());
        lbl.setText(label);
        lbl.setTextColor(Color.parseColor("#888888"));
        lbl.setTextSize(12f);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(lbl);

        TextView val = new TextView(getContext());
        val.setText(value);
        val.setTextColor(Color.WHITE);
        val.setTextSize(12f);
        row.addView(val);

        parent.addView(row);
    }

    private int dp(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }
}
