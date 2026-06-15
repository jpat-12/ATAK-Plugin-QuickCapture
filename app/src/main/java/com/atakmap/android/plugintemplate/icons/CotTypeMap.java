package com.atakmap.android.plugintemplate.icons;

import java.util.HashMap;
import java.util.Map;

/** Maps icon filenames to appropriate CoT type strings. */
public final class CotTypeMap {

    private static final Map<String, String> MAP = new HashMap<>();

    static {
        // Command / coordination
        MAP.put("Command Post.png",                    "a-f-G-U-C");
        MAP.put("Incident Command Post.png",           "a-f-G-U-C");
        MAP.put("Area Command Post.png",               "a-f-G-U-C");
        MAP.put("Joint Operations Center.png",         "a-f-G-U-C");
        MAP.put("Emergency Operations Center.png",     "a-f-G-U-C");
        MAP.put("Multi-Agency Coordination Center.png","a-f-G-U-C");
        MAP.put("Staging Area.png",                    "a-f-G-U-C-F");
        MAP.put("Initial Planning Point.png",          "a-f-G-U-C");
        // Aviation
        MAP.put("Helicopter Landing Zone.png",         "a-f-G-E-V-A-H");
        // Medical
        MAP.put("EMS.png",                             "a-f-G-E-V-C-E");
        // Unknown / reporting
        MAP.put("Reporting-Party.png",                 "a-u-G");
        MAP.put("CLUE.png",                            "a-u-G");
        MAP.put("ELT Signal.png",                      "a-u-G");
        MAP.put("PLT SIgnal.png",                      "a-u-G");
        MAP.put("Animal.png",                          "a-u-G");
        // Hazard / incident
        MAP.put("Fire.png",                            "b-m-p-s-m");
        MAP.put("Flood.png",                           "b-m-p-s-m");
        MAP.put("Hazard, Haz Materials.png",           "b-m-p-s-m");
        MAP.put("Hazard, Other.png",                   "b-m-p-s-m");
        MAP.put("Electrical.png",                      "b-m-p-s-m");
        MAP.put("Crash Site.png",                      "b-m-p-s-m");
        MAP.put("No Cellular Connection.png",          "b-m-p-s-p");
    }

    /** Returns the CoT type for the given icon filename, falling back to a generic. */
    public static String forFilename(String filename) {
        return MAP.getOrDefault(filename, "a-f-G-E-V-A");
    }

    private CotTypeMap() {}
}
