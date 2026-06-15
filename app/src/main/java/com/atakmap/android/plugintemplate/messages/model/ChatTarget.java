package com.atakmap.android.plugintemplate.messages.model;

/**
 * Represents a GeoChat destination — either an individual contact or a group.
 */
public class ChatTarget {

    public enum Kind { CONTACT, GROUP }

    /**
     * Broadcasts to everyone — the UID and chatroom name must both be exactly
     * "All Chat Rooms" for ATAK's chat router to recognise the group.
     */
    public static final ChatTarget ALL_CHAT = new ChatTarget(
            "All Chat Rooms", "All Chat Rooms", Kind.GROUP);

    public final String uid;      // ATAK device UID or group UID
    public final String callsign; // Human-readable name shown in the UI
    public final Kind   kind;

    public ChatTarget(String uid, String callsign, Kind kind) {
        this.uid      = uid;
        this.callsign = callsign;
        this.kind     = kind;
    }

    @Override
    public String toString() {
        return callsign;
    }
}
