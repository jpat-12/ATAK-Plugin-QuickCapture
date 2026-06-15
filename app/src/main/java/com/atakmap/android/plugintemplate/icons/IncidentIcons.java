package com.atakmap.android.plugintemplate.icons;

/**
 * Typed constants for every icon in the CAP "Incident Icons" iconset.
 * UID matches the value in iconset.xml.
 */
public final class IncidentIcons {

    public static final String ICONSET_UID =
            "412c43f948b1664a3a0b513336b6c32382b13289a6ed2e91dd31e23d9d52a683";

    private static final String BASE = ICONSET_UID + "/Incident Icons/";

    public static final String ANIMAL                 = BASE + "Animal.png";
    public static final String AREA_COMMAND_POST      = BASE + "Area Command Post.png";
    public static final String CAP_ASSET_REPORT       = BASE + "CAP Asset Report.png";
    public static final String CAP_REPEATER           = BASE + "CAP Repeater.png";
    public static final String CLUE                   = BASE + "CLUE.png";
    public static final String COMMAND_POST           = BASE + "Command Post.png";
    public static final String CRASH_SITE             = BASE + "Crash Site.png";
    public static final String ELECTRICAL             = BASE + "Electrical.png";
    public static final String ELT_SIGNAL             = BASE + "ELT Signal.png";
    public static final String EMERGENCY_OPS_CENTER   = BASE + "Emergency Operations Center.png";
    public static final String EMS                    = BASE + "EMS.png";
    public static final String FIRE                   = BASE + "Fire.png";
    public static final String FLOOD                  = BASE + "Flood.png";
    public static final String HAZARD_HAZ_MATERIALS   = BASE + "Hazard, Haz Materials.png";
    public static final String HAZARD_OTHER           = BASE + "Hazard, Other.png";
    public static final String HELICOPTER_LZ          = BASE + "Helicopter Landing Zone.png";
    public static final String INCIDENT_COMMAND_POST  = BASE + "Incident Command Post.png";
    public static final String INITIAL_PLANNING_POINT = BASE + "Initial Planning Point.png";
    public static final String JOINT_OPS_CENTER       = BASE + "Joint Operations Center.png";
    public static final String MACC                   = BASE + "Multi-Agency Coordination Center.png";
    public static final String NO_CELLULAR            = BASE + "No Cellular Connection.png";
    public static final String PLACEHOLDER_OTHER      = BASE + "Placeholder Other.png";
    public static final String PLT_SIGNAL             = BASE + "PLT SIgnal.png";
    public static final String REPORTING_PARTY        = BASE + "Reporting-Party.png";
    public static final String STAGING_AREA           = BASE + "Staging Area.png";
    public static final String STRUCTURE_DAMAGED      = BASE + "Structure, Damaged.png";
    public static final String STRUCTURE_DESTROYED    = BASE + "Structure, Destroyed.png";
    public static final String STRUCTURE_FAILED       = BASE + "Structure, Failed.png";
    public static final String STRUCTURE_NO_DAMAGE    = BASE + "Structure, No-Damage.png";
    public static final String ROUTE_BLOCK            = BASE + "Transportation, Route Block.png";

    /**
     * All icons in display order.
     * Columns: [0] iconset path for ATAK marker, [1] display name, [2] asset filename
     * The asset filename is used to load the PNG directly from assets/icons/.
     */
    public static final String[][] ALL = {
        {COMMAND_POST,           "Command Post",      "Command Post.png"},
        {INCIDENT_COMMAND_POST,  "Incident CP",       "Incident Command Post.png"},
        {AREA_COMMAND_POST,      "Area CP",           "Area Command Post.png"},
        {STAGING_AREA,           "Staging Area",      "Staging Area.png"},
        {HELICOPTER_LZ,          "Helo LZ",           "Helicopter Landing Zone.png"},
        {INITIAL_PLANNING_POINT, "Initial PP",        "Initial Planning Point.png"},
        {REPORTING_PARTY,        "Reporting Party",   "Reporting-Party.png"},
        {CLUE,                   "CLUE",              "CLUE.png"},
        {ELT_SIGNAL,             "ELT Signal",        "ELT Signal.png"},
        {PLT_SIGNAL,             "PLT Signal",        "PLT SIgnal.png"},
        {CAP_ASSET_REPORT,       "CAP Asset",         "CAP Asset Report.png"},
        {CAP_REPEATER,           "CAP Repeater",      "CAP Repeater.png"},
        {EMS,                    "EMS",               "EMS.png"},
        {EMERGENCY_OPS_CENTER,   "EOC",               "Emergency Operations Center.png"},
        {JOINT_OPS_CENTER,       "JOC",               "Joint Operations Center.png"},
        {MACC,                   "MACC",              "Multi-Agency Coordination Center.png"},
        {CRASH_SITE,             "Crash Site",        "Crash Site.png"},
        {FIRE,                   "Fire",              "Fire.png"},
        {FLOOD,                  "Flood",             "Flood.png"},
        {ELECTRICAL,             "Electrical",        "Electrical.png"},
        {HAZARD_HAZ_MATERIALS,   "HazMat",            "Hazard, Haz Materials.png"},
        {HAZARD_OTHER,           "Hazard",            "Hazard, Other.png"},
        {ANIMAL,                 "Animal",            "Animal.png"},
        {NO_CELLULAR,            "No Cell",           "No Cellular Connection.png"},
        {ROUTE_BLOCK,            "Route Block",       "Transportation, Route Block.png"},
        {STRUCTURE_DAMAGED,      "Struct Damaged",    "Structure, Damaged.png"},
        {STRUCTURE_DESTROYED,    "Struct Destroyed",  "Structure, Destroyed.png"},
        {STRUCTURE_FAILED,       "Struct Failed",     "Structure, Failed.png"},
        {STRUCTURE_NO_DAMAGE,    "Struct OK",         "Structure, No-Damage.png"},
        {PLACEHOLDER_OTHER,      "Other",             "Placeholder Other.png"},
    };

    private IncidentIcons() {}
}
