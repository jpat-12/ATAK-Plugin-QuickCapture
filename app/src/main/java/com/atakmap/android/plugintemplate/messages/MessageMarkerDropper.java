package com.atakmap.android.plugintemplate.messages;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.UUID;

/** Drops a shared message marker at the sender's current location. */
public final class MessageMarkerDropper {

    private static final String TAG = "FieldTak.MessageMarker";
    private static final String ICON_PATH =
            "a56794afc2e730e4c4fb77580780a8d348e6096726cb2df03d869e9eb943c9af"
                    + "/FieldTAK Messages/Message.png";

    public static boolean place(String title, String message) {
        try {
            MapView mapView = MapView.getMapView();
            PointMapItem self = mapView != null ? mapView.getSelfMarker() : null;
            GeoPoint point = self != null ? self.getPoint() : null;
            if (point == null || !point.isValid()) return false;

            CoordinatedTime now = new CoordinatedTime();
            CoordinatedTime stale = new CoordinatedTime(
                    now.getMilliseconds() + 24L * 60 * 60 * 1000);
            double altitude = point.getAltitude();
            if (Double.isNaN(altitude)) altitude = 0.0;

            CotEvent event = new CotEvent();
            event.setUID("fieldtak-message-" + UUID.randomUUID());
            event.setType("a-u-G");
            event.setHow("h-g-i-g-o");
            event.setTime(now);
            event.setStart(now);
            event.setStale(stale);
            event.setPoint(new CotPoint(point.getLatitude(), point.getLongitude(),
                    altitude, 9_999_999.0, 9_999_999.0));

            CotDetail detail = new CotDetail();
            CotDetail contact = new CotDetail("contact");
            contact.setAttribute("callsign", title);
            detail.addChild(contact);

            CotDetail icon = new CotDetail("usericon");
            icon.setAttribute("iconsetpath", ICON_PATH);
            detail.addChild(icon);

            CotDetail remarks = new CotDetail("remarks");
            remarks.setInnerText(message);
            detail.addChild(remarks);
            event.setDetail(detail);

            CotMapComponent.getInternalDispatcher().dispatch(event, null);
            CotMapComponent.getExternalDispatcher().dispatch(event, null);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to place message marker", t);
            return false;
        }
    }

    private MessageMarkerDropper() {}
}
