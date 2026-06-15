package com.atakmap.android.plugintemplate.messages;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Reads live ATAK / device state and provides field values for message template placeholders.
 * All methods are safe to call from any thread.
 */
public final class AtakFieldResolver {

    private AtakFieldResolver() {}

    /** Self callsign as set in ATAK preferences, or "UNKNOWN". */
    public static String getCallsign() {
        PointMapItem self = getSelf();
        if (self == null) return "UNKNOWN";
        return self.getMetaString("callsign", "UNKNOWN");
    }

    /** Self latitude in decimal degrees, or 0.0 if unavailable. */
    public static double getLatitude() {
        GeoPoint pt = getPoint();
        return pt != null ? pt.getLatitude() : 0.0;
    }

    /** Self longitude in decimal degrees, or 0.0 if unavailable. */
    public static double getLongitude() {
        GeoPoint pt = getPoint();
        return pt != null ? pt.getLongitude() : 0.0;
    }

    /** Self altitude in metres (HAE), or Double.NaN if unavailable. */
    public static double getAltitudeMeters() {
        GeoPoint pt = getPoint();
        return (pt != null && pt.isValid()) ? pt.getAltitude() : Double.NaN;
    }

    /** Latitude formatted as DMS, e.g. {@code 38°53'23.4"N}. */
    public static String getLatDms() {
        return toDms(getLatitude(), true);
    }

    /** Longitude formatted as DMS, e.g. {@code 077°02'11.5"W}. */
    public static String getLngDms() {
        return toDms(getLongitude(), false);
    }

    /**
     * Device battery percentage 0–100, or {@code -1} if unavailable.
     * Requires a valid Android {@link Context}.
     */
    public static int getBatteryPercent(Context context) {
        try {
            Intent b = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return -1;
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return -1;
            return Math.round((level / (float) scale) * 100);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Current UTC time formatted as {@code HH:mmZ}. */
    public static String getTimeUtc() {
        return new SimpleDateFormat("HH:mm'Z'", Locale.US).format(new Date());
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static PointMapItem getSelf() {
        MapView mv = MapView.getMapView();
        return mv != null ? mv.getSelfMarker() : null;
    }

    private static GeoPoint getPoint() {
        PointMapItem self = getSelf();
        return self != null ? self.getPoint() : null;
    }

    /**
     * Converts decimal degrees to DMS notation.
     *
     * @param dec   Decimal degrees value.
     * @param isLat {@code true} for latitude (N/S), {@code false} for longitude (E/W).
     */
    private static String toDms(double dec, boolean isLat) {
        String hem = isLat ? (dec >= 0 ? "N" : "S") : (dec >= 0 ? "E" : "W");
        dec = Math.abs(dec);
        int    deg      = (int) dec;
        double minFull  = (dec - deg) * 60.0;
        int    min      = (int) minFull;
        double sec      = (minFull - min) * 60.0;
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", deg, min, sec, hem);
    }
}
