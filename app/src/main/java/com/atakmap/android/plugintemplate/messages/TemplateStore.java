package com.atakmap.android.plugintemplate.messages;

import android.content.Context;

import com.atakmap.android.plugintemplate.messages.model.MessageTemplate;

import java.util.ArrayList;
import java.util.List;

/** Provides the streamlined field GeoChat templates. */
public final class TemplateStore {

    public static final MessageTemplate[] DEFAULTS = {
        new MessageTemplate("builtin_ops",
                "Ops Normal",
                "OPS NORMAL: {callsign}, {lat_dms} {lng_dms}, Bat {battery}%, {time_utc}"),
        new MessageTemplate("builtin_clue",
                "Clue Report",
                "CLUE: {callsign} - {lat_dms} {lng_dms} - Description: [DESCRIBE]"),
        new MessageTemplate("builtin_movement",
                "Arrived / Departing",
                "{callsign} [ARRIVED / DEPARTING] at {lat_dms} {lng_dms}, {time_utc}"),
        new MessageTemplate("builtin_request",
                "Request Assistance",
                "REQUEST ASSISTANCE: {callsign} at {lat_dms} {lng_dms} - [NATURE] - Bat {battery}%")
    };

    private TemplateStore() {}

    public static List<MessageTemplate> load(Context ctx) {
        List<MessageTemplate> list = new ArrayList<>();
        for (MessageTemplate template : DEFAULTS) list.add(template);
        return list;
    }

    public static void removeCustom(Context ctx, String id) {
        // Custom templates are no longer exposed.
    }
}
