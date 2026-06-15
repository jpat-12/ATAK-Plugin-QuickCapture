package com.atakmap.android.plugintemplate.qc;

import com.atakmap.coremap.maps.coords.GeoPoint;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QuickCaptureProject {
    public String source;
    public String itemId;
    public String sourceItemId;
    public String title = "QuickCapture";
    public final Map<String, String> dataSources = new LinkedHashMap<>();
    public final List<Template> templates = new ArrayList<>();
    public final Map<String, UserInput> userInputs = new LinkedHashMap<>();
    private final Map<String, Integer> groupColumns = new LinkedHashMap<>();

    public static QuickCaptureProject parse(String source, String title, JSONObject root) throws Exception {
        QuickCaptureProject project = new QuickCaptureProject();
        project.source = source;
        project.title = title;
        JSONObject body = root.optJSONObject("project");
        if (body == null) body = root.optJSONObject("application");
        if (body == null) body = root;
        project.itemId = body.optString("itemId");
        JSONArray sources = body.optJSONArray("dataSources");
        if (sources != null) for (int i = 0; i < sources.length(); i++) {
            JSONObject ds = sources.getJSONObject(i);
            project.dataSources.put(ds.optString("dataSourceId", String.valueOf(i)), ds.optString("url"));
        }
        JSONArray inputs = body.optJSONArray("userInputs");
        if (inputs != null) for (int i = 0; i < inputs.length(); i++) {
            UserInput input = UserInput.parse(inputs.getJSONObject(i));
            project.userInputs.put(input.id, input);
        }
        Map<String, String> templateGroups = new LinkedHashMap<>();
        JSONArray groups = body.optJSONArray("templateGroups");
        if (groups != null) for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            String name = group.optString("label", group.optString("name", "Capture"));
            project.groupColumns.put(name, Math.max(1, group.optInt("columns", 2)));
            JSONArray ids = group.optJSONArray("templateIds");
            if (ids != null) for (int j = 0; j < ids.length(); j++) templateGroups.put(ids.getString(j), name);
            JSONArray embeddedTemplates = group.optJSONArray("templates");
            if (embeddedTemplates != null) for (int j = 0; j < embeddedTemplates.length(); j++) {
                Template template = Template.parse(embeddedTemplates.getJSONObject(j));
                template.group = name;
                project.templates.add(template);
            }
        }
        JSONArray templates = body.optJSONArray("templates");
        if (templates != null) for (int i = 0; i < templates.length(); i++) {
            Template template = Template.parse(templates.getJSONObject(i));
            template.group = templateGroups.get(template.id);
            project.templates.add(template);
        }
        if (project.templates.isEmpty() && project.dataSources.size() == 1) {
            Template template = new Template();
            template.id = "default";
            template.label = project.title;
            template.dataSourceId = project.dataSources.keySet().iterator().next();
            project.templates.add(template);
        }
        if (project.templates.isEmpty()) throw new Exception("Project contains no supported capture templates");
        return project;
    }

    public int columnsFor(String group) {
        Integer count = groupColumns.get(group);
        return count == null ? 2 : count;
    }

    public String urlFor(Template template) throws Exception {
        String url = dataSources.get(template.dataSourceId);
        if (url == null || url.isEmpty()) throw new Exception("Template has no Feature Service data source");
        return url;
    }

    public boolean hasCaptureTarget(Template template) {
        String url = dataSources.get(template.dataSourceId);
        return url != null && !url.isEmpty();
    }

    public boolean isReferenceLink(Template template) {
        return !hasCaptureTarget(template) && template.url != null && !template.url.isEmpty();
    }

    public List<UserInput> inputsFor(Template template) {
        List<UserInput> result = new ArrayList<>();
        for (String id : template.inputIds) {
            UserInput input = userInputs.get(id);
            if (input != null && !"project".equalsIgnoreCase(input.mode)) result.add(input);
        }
        return result;
    }

    public List<UserInput> projectInputs() {
        List<UserInput> result = new ArrayList<>();
        for (UserInput input : userInputs.values()) {
            if ("project".equalsIgnoreCase(input.mode)) result.add(input);
        }
        return result;
    }

    public JSONObject resolveAttributes(Template template, Map<String, Object> inputs,
                                        String callsign, GeoPoint point) {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, Object> field : template.fields.entrySet()) {
            Object raw = field.getValue();
            if (raw == null || raw == JSONObject.NULL) continue;
            if (raw instanceof String) {
                String value = (String) raw;
                if (value.startsWith("${userInput:") && value.endsWith("}")) {
                    raw = inputs.get(value.substring(12, value.length() - 1));
                } else {
                    raw = resolveVariable(value, callsign, point);
                }
            }
            if (raw == null || raw == JSONObject.NULL) continue;
            try { result.put(field.getKey(), raw); } catch (Exception ignored) {}
        }
        return result;
    }

    private Object resolveVariable(String value, String callsign, GeoPoint point) {
        if ("${captureTime}".equals(value)) return System.currentTimeMillis();
        if ("${latitude}".equals(value)) return point.getLatitude();
        if ("${longitude}".equals(value)) return point.getLongitude();
        if ("${altitude}".equals(value)) return point.getAltitude();
        if ("${username}".equals(value) || "${callsign}".equals(value)
                || "${deviceid}".equals(value)) return callsign == null ? "" : callsign;
        if ("${currentproject}".equals(value)) return title;
        // Do not submit unresolved QuickCapture sensor variables into typed ArcGIS fields.
        // Avoid String.matches here: Android's ICU regex parser rejects this otherwise-valid
        // Java expression on some ATAK-supported Android releases.
        if (isUnresolvedVariable(value)) return null;
        return value.replace("${username}", callsign == null ? "" : callsign)
                .replace("${callsign}", callsign == null ? "" : callsign)
                .replace("${deviceid}", callsign == null ? "" : callsign)
                .replace("${currentproject}", title)
                .replace("${latitude}", String.format(Locale.US, "%.7f", point.getLatitude()))
                .replace("${longitude}", String.format(Locale.US, "%.7f", point.getLongitude()))
                .replace("${altitude}", String.format(Locale.US, "%.2f", point.getAltitude()));
    }

    static boolean isUnresolvedVariable(String value) {
        return value != null && value.length() > 3
                && value.startsWith("${") && value.endsWith("}");
    }

    public static final class Template {
        public String id;
        public String label;
        public String color = "#315D84";
        public String group;
        public String dataSourceId;
        public String image;
        public String url;
        public String type;
        public boolean photoRequired;
        public final Map<String, Object> fields = new LinkedHashMap<>();
        public final List<String> inputIds = new ArrayList<>();

        static Template parse(JSONObject json) {
            Template t = new Template();
            t.id = json.optString("templateId", json.optString("id"));
            t.type = json.optString("type", "button");
            JSONObject capture = json.optJSONObject("captureInfo");
            t.dataSourceId = json.optString("dataSourceId",
                    capture == null ? "0" : capture.optString("dataSourceId", "0"));
            JSONObject display = json.optJSONObject("displayInfo");
            t.label = display == null ? t.id : display.optString("label", t.id);
            if (display != null) t.color = display.optString("color", t.color);
            if (display != null) t.image = display.optString("image", null);
            JSONObject urlInfo = json.optJSONObject("urlInfo");
            if (urlInfo != null) t.url = urlInfo.optString("url", null);
            JSONObject camera = json.optJSONObject("cameraInfo");
            t.photoRequired = camera != null && camera.optBoolean("required", false);
            JSONArray fields = json.optJSONArray("fieldInfos");
            if (fields != null) for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.optJSONObject(i);
                if (field == null) continue;
                String value = String.valueOf(field.opt("value"));
                t.fields.put(field.optString("fieldName"), field.opt("value"));
                if (value.startsWith("${userInput:") && value.endsWith("}")) {
                    String id = value.substring(12, value.length() - 1);
                    if (!t.inputIds.contains(id)) t.inputIds.add(id);
                }
            }
            return t;
        }
    }

    public static final class UserInput {
        public String id;
        public String label;
        public boolean required;
        public boolean numeric;
        public String mode;
        public final List<Choice> choices = new ArrayList<>();

        static UserInput parse(JSONObject json) {
            UserInput input = new UserInput();
            input.id = json.optString("id");
            input.label = json.optString("label", input.id);
            input.required = json.optBoolean("required", false);
            input.mode = json.optString("mode", "button");
            String type = json.optString("fieldType").toLowerCase(Locale.US);
            input.numeric = type.contains("integer") || type.contains("double") || type.contains("single");
            JSONObject domain = json.optJSONObject("domain");
            JSONArray values = domain == null ? null : domain.optJSONArray("codedValues");
            if (values != null) for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value != null) input.choices.add(new Choice(value.optString("name"), value.opt("code")));
            }
            return input;
        }
    }

    public static final class Choice {
        public final String name;
        public final Object code;
        Choice(String name, Object code) { this.name = name; this.code = code; }
    }
}
