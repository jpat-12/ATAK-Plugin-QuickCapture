package com.atakmap.android.plugintemplate.qc.model;

/**
 * Represents a single user-editable field in an ArcGIS Feature Service layer.
 *
 * <p>System fields (OBJECTID, GlobalID, geometry, editor-tracking fields) are
 * filtered out before this model is created.
 */
public final class QcField {

    /** ArcGIS REST field type constants we support. */
    public static final String TYPE_STRING  = "esriFieldTypeString";
    public static final String TYPE_INT     = "esriFieldTypeInteger";
    public static final String TYPE_SMALL   = "esriFieldTypeSmallInteger";
    public static final String TYPE_DOUBLE  = "esriFieldTypeDouble";
    public static final String TYPE_SINGLE  = "esriFieldTypeSingle";
    public static final String TYPE_DATE    = "esriFieldTypeDate";

    /** The field's REST name — used as the JSON key when submitting. */
    public final String name;

    /** Human-readable display label (alias). Falls back to {@link #name}. */
    public final String label;

    /** ArcGIS field type string. */
    public final String type;

    /** {@code false} means the field is required. */
    public final boolean nullable;

    /** Maximum character length for string fields; -1 if not applicable. */
    public final int length;

    public QcField(String name, String label, String type,
                   boolean nullable, int length) {
        this.name     = name;
        this.label    = (label != null && !label.isEmpty()) ? label : name;
        this.type     = type != null ? type : TYPE_STRING;
        this.nullable = nullable;
        this.length   = length;
    }

    /** Returns {@code true} if this field accepts numeric (integer) input. */
    public boolean isInteger() {
        return TYPE_INT.equals(type) || TYPE_SMALL.equals(type);
    }

    /** Returns {@code true} if this field accepts decimal (floating-point) input. */
    public boolean isDecimal() {
        return TYPE_DOUBLE.equals(type) || TYPE_SINGLE.equals(type);
    }

    /** Returns {@code true} if this field should use a numeric keyboard. */
    public boolean isNumeric() {
        return isInteger() || isDecimal();
    }

    @Override
    public String toString() {
        return label + " (" + type + ")";
    }
}
