package com.atakmap.android.plugintemplate.messages;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.plugintemplate.messages.model.ChatTarget;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.UUID;

/**
 * Sends a GeoChat message (CoT type {@code "b-t-f"}) via ATAK's internal CoT dispatcher.
 *
 * <p>Messages are routed to the specified {@link ChatTarget}. For group chat use
 * {@link ChatTarget#ALL_CHAT}. For an individual contact supply their device UID.
 */
public final class GeoChatSender {

    private static final String TAG = "FieldTak.GeoChatSender";

    private GeoChatSender() {}

    /**
     * Dispatches a GeoChat CoT event.
     *
     * @param text   Fully rendered message text (no tokens remaining).
     * @param target Recipient contact or group.
     * @return {@code true} if the event was dispatched without error.
     */
    public static boolean send(String text, ChatTarget target) {
        try {
            MapView mv = MapView.getMapView();
            if (mv == null) {
                Log.e(TAG, "MapView unavailable");
                return false;
            }

            PointMapItem self = mv.getSelfMarker();
            if (self == null) {
                Log.e(TAG, "Self-marker unavailable");
                return false;
            }

            GeoPoint pt      = self.getPoint();
            String senderUid = self.getUID();
            String callsign  = self.getMetaString("callsign", "UNKNOWN");
            String msgId     = UUID.randomUUID().toString();

            CoordinatedTime now   = new CoordinatedTime();
            CoordinatedTime stale = new CoordinatedTime(now.getMilliseconds() + 60_000L);

            double lat  = (pt != null) ? pt.getLatitude()  : 0.0;
            double lon  = (pt != null) ? pt.getLongitude() : 0.0;
            double hae  = (pt != null && pt.isValid()) ? pt.getAltitude() : 0.0;

            // ── Build CoT event ──────────────────────────────────────────────
            CotEvent event = new CotEvent();
            event.setUID(msgId);
            event.setType("b-t-f");
            event.setHow("h-g-i-g-o");
            event.setTime(now);
            event.setStart(now);
            event.setStale(stale);
            event.setPoint(new CotPoint(lat, lon, hae, 9_999_999.0, 9_999_999.0));

            CotDetail detail = new CotDetail();

            boolean isGroup = (target.kind == ChatTarget.Kind.GROUP);

            // <__chat>
            CotDetail chat = new CotDetail("__chat");
            chat.setAttribute("id",             target.uid);
            chat.setAttribute("senderCallsign", callsign);
            // chatroom must exactly match the group/contact identifier ATAK expects
            chat.setAttribute("chatroom",       target.uid);
            chat.setAttribute("groupOwner",     "false");
            chat.setAttribute("messageId",      msgId);

            // <chatgrp> — uid1 is only used for direct (point-to-point) messages;
            // group broadcasts (All Chat Rooms, etc.) use only uid0 + id.
            CotDetail chatGrp = new CotDetail("chatgrp");
            chatGrp.setAttribute("uid0", senderUid);
            chatGrp.setAttribute("id",   target.uid);
            if (!isGroup) {
                chatGrp.setAttribute("uid1", target.uid);
            }
            chat.addChild(chatGrp);
            detail.addChild(chat);

            // <link>
            CotDetail link = new CotDetail("link");
            link.setAttribute("uid",      senderUid);
            link.setAttribute("type",     "a-f-G-U-C");
            link.setAttribute("relation", "p-p");
            detail.addChild(link);

            // <remarks>
            CotDetail remarks = new CotDetail("remarks");
            remarks.setAttribute("source", "BAO.F.ATAK." + senderUid);
            remarks.setAttribute("to",     target.uid);
            remarks.setAttribute("time",   now.toString());
            remarks.setInnerText(text);
            detail.addChild(remarks);

            // <__serverdestination> — only needed for direct contact routing
            if (!isGroup) {
                CotDetail dst = new CotDetail("__serverdestination");
                dst.setAttribute("destinations", target.uid);
                detail.addChild(dst);
            }

            event.setDetail(detail);

            // ── Dispatch ──────────────────────────────────────────────────────
            // Internal: makes the message appear in ATAK's own chat window.
            // External: sends it over the TAK server / mesh network.
            CotMapComponent.getInternalDispatcher().dispatch(event, null);
            CotMapComponent.getExternalDispatcher().dispatch(event, null);
            Log.d(TAG, "GeoChat sent to " + target.callsign + " (group=" + isGroup + "): " + text);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to send GeoChat message", e);
            return false;
        }
    }
}
