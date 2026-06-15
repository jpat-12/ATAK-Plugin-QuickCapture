package com.atakmap.android.plugintemplate.messages;

import android.content.Context;

import com.atakmap.android.plugintemplate.messages.model.MessageTemplate;

import java.util.Locale;

/**
 * Substitutes live ATAK/device field values into a {@link MessageTemplate}.
 *
 * <p>Tokens that are filled automatically:
 * <ul>
 *   <li>{callsign}, {lat_dms}, {lng_dms}, {lat_dd}, {lng_dd}
 *   <li>{alt_m}, {battery}, {time_utc}
 * </ul>
 *
 * <p>Tokens intentionally left for the user to fill in manually at send time:
 * <ul>
 *   <li>{status} — operational status (e.g. "GREEN", "YELLOW", "RED")
 *   <li>{task}   — task number or description
 * </ul>
 */
public final class TemplateRenderer {

    private TemplateRenderer() {}

    /** Renders the template, substituting all resolvable tokens. */
    public static String render(MessageTemplate template, Context context) {
        return render(template.template, context);
    }

    /**
     * Renders a raw template string, substituting all resolvable tokens.
     * {status} and {task} are left as-is for manual editing.
     */
    public static String render(String template, Context context) {
        String callsign = AtakFieldResolver.getCallsign();
        double lat      = AtakFieldResolver.getLatitude();
        double lng      = AtakFieldResolver.getLongitude();
        double altM     = AtakFieldResolver.getAltitudeMeters();
        int    bat      = AtakFieldResolver.getBatteryPercent(context);
        String latDms   = AtakFieldResolver.getLatDms();
        String lngDms   = AtakFieldResolver.getLngDms();
        String timeUtc  = AtakFieldResolver.getTimeUtc();

        String altStr = Double.isNaN(altM) ? "N/A"
                                           : String.format(Locale.US, "%.0f", altM);
        String batStr = bat < 0 ? "??" : String.valueOf(bat);

        return template
                .replace("{callsign}",  callsign)
                .replace("{lat_dms}",   latDms)
                .replace("{lng_dms}",   lngDms)
                .replace("{lat_dd}",    String.format(Locale.US, "%.5f", lat))
                .replace("{lng_dd}",    String.format(Locale.US, "%.5f", lng))
                .replace("{alt_m}",     altStr)
                .replace("{battery}",   batStr)
                .replace("{time_utc}",  timeUtc);
        // {status} and {task} intentionally not replaced — user fills them in before sending
    }
}
