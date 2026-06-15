package com.atakmap.android.plugintemplate.messages.model;

/**
 * A named GeoChat message template with placeholder variables.
 *
 * <p>Supported tokens (replaced at send time):
 * <ul>
 *   <li>{callsign}  — self callsign from ATAK
 *   <li>{lat_dms}   — latitude in DMS format
 *   <li>{lng_dms}   — longitude in DMS format
 *   <li>{lat_dd}    — latitude in decimal degrees
 *   <li>{lng_dd}    — longitude in decimal degrees
 *   <li>{alt_m}     — altitude in metres (or "N/A")
 *   <li>{battery}   — device battery percentage
 *   <li>{time_utc}  — current time in HH:mmZ format
 *   <li>{status}    — left for user to fill at send time
 *   <li>{task}      — left for user to fill at send time
 * </ul>
 */
public class MessageTemplate {

    public final String id;       // Unique identifier; built-ins prefixed "builtin_"
    public final String name;     // Display name shown in the list
    public final String template; // Raw template string with {tokens}

    public MessageTemplate(String id, String name, String template) {
        this.id       = id;
        this.name     = name;
        this.template = template;
    }

    /** True if this is one of the bundled default templates. */
    public boolean isBuiltIn() {
        return id != null && id.startsWith("builtin_");
    }
}
