package com.atakmap.android.plugintemplate.icons;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.UUID;

/**
 * Places a CAP incident marker on the ATAK map by dispatching a CoT event.
 *
 * <p>Using CoT dispatch (rather than direct {@code MapGroup.addItem}) avoids native-level
 * crashes caused by manipulating map state outside ATAK's GL/render thread.
 * ATAK's internal dispatcher receives the event, creates the marker, and renders it
 * exactly as if the CoT came from the network.
 */
public final class IconDropper {

    private static final String TAG = "FieldTak.IconDropper";

    /**
     * Places a marker at {@code point} by dispatching an internal CoT event.
     *
     * @param iconPath  Iconset path, e.g. from {@link IncidentIcons} — used in {@code <usericon>}.
     * @param filename  Bare filename (e.g. "Command Post.png") — used for CoT type lookup.
     * @param title     Label shown under the marker.
     * @param point     Where to place the marker.
     * @return {@code true} if the CoT event was dispatched without error.
     */
    public static boolean place(String iconPath, String filename,
                                String title, GeoPoint point) {
        try {
            MapView mv = MapView.getMapView();
            if (mv == null) {
                Log.e(TAG, "MapView unavailable");
                return false;
            }

            // Marker lives for 10 years — effectively permanent until the user deletes it
            CoordinatedTime now   = new CoordinatedTime();
            CoordinatedTime stale = new CoordinatedTime(
                    now.getMilliseconds() + 10L * 365 * 24 * 3600 * 1000);

            double lat = point.getLatitude();
            double lon = point.getLongitude();
            double hae = point.isValid() ? point.getAltitude() : 0.0;
            if (Double.isNaN(hae)) hae = 0.0;

            CotEvent event = new CotEvent();
            event.setUID(UUID.randomUUID().toString());
            event.setType(CotTypeMap.forFilename(filename));
            event.setHow("h-g-i-g-o");   // human — GPS input — ground object
            event.setTime(now);
            event.setStart(now);
            event.setStale(stale);
            event.setPoint(new CotPoint(lat, lon, hae, 9_999_999.0, 9_999_999.0));

            CotDetail detail = new CotDetail();

            // Callsign shown in ATAK as the marker label
            CotDetail contact = new CotDetail("contact");
            contact.setAttribute("callsign", title);
            detail.addChild(contact);

            // Custom icon — ATAK resolves this from the installed iconset
            CotDetail userIcon = new CotDetail("usericon");
            userIcon.setAttribute("iconsetpath", iconPath);
            detail.addChild(userIcon);

            // <archive/> makes ATAK persist the marker across restarts
            detail.addChild(new CotDetail("archive"));

            // Mark as user-placed so ATAK shows the edit/delete handles
            CotDetail remarks = new CotDetail("remarks");
            remarks.setInnerText(title);
            detail.addChild(remarks);

            event.setDetail(detail);

            // Internal dispatch only — stays on this device (not broadcast to network)
            CotMapComponent.getInternalDispatcher().dispatch(event, null);

            Log.d(TAG, "Placed " + title + " at " + lat + ", " + lon);
            return true;

        } catch (Throwable t) {
            // Catch Throwable (not just Exception) to handle native ATAK errors
            Log.e(TAG, "place() failed for " + title, t);
            return false;
        }
    }

    private IconDropper() {}
}
