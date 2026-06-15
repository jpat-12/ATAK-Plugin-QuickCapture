package com.atakmap.android.plugintemplate.topo;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends periodic PLI (Position Location Information) reports to CALTopo.
 * Runs on its own background thread; thread-safe.
 */
public class PLIReporter {

    private static final String TAG = "FieldTak.PLIReporter";

    public interface StatusListener {
        void onStatus(boolean connected, String message, long lastReportMs);
    }

    private final CalTopoApiClient  client   = new CalTopoApiClient();
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FieldTak-PLI");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?>  future;
    private CalTopoSession      session;
    private StatusListener      listener;

    private final AtomicLong    lastReportMs   = new AtomicLong(0);
    private final AtomicInteger backoffSeconds = new AtomicInteger(0);
    private volatile int        intervalSeconds = 15;
    private volatile boolean    running = false;

    public synchronized void start(CalTopoSession session, int intervalSeconds,
                                   StatusListener listener) {
        stop();
        this.session         = session;
        this.intervalSeconds = intervalSeconds;
        this.listener        = listener;
        this.running         = true;
        backoffSeconds.set(0);

        future = executor.scheduleAtFixedRate(
                this::tick, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        running = false;
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    public synchronized void shutdown() {
        stop();
        listener = null;
        session = null;
        executor.shutdownNow();
    }

    public boolean isRunning() { return running; }
    public long    lastReportMs() { return lastReportMs.get(); }

    // -------------------------------------------------------------------------

    private void tick() {
        if (!running || session == null) return;

        // Back-off delay (e.g. after repeated errors)
        int backoff = backoffSeconds.get();
        if (backoff > 0) {
            backoffSeconds.set(Math.max(0, backoff - intervalSeconds));
            return;
        }

        try {
            MapView mv   = MapView.getMapView();
            if (mv == null) return;

            PointMapItem self = mv.getSelfMarker();
            if (self == null) return;

            GeoPoint pt  = self.getPoint();
            if (pt == null || !pt.isValid()) return;

            String callsign = self.getMetaString("callsign", "UNKNOWN");
            String uid      = self.getUID();
            String deviceId = callsign + "_" + uid;

            double alt = pt.getAltitude();
            if (Double.isNaN(alt)) alt = 0.0; // CALTopo rejects NaN altitude

            String url = session.positionReportUrl(
                    deviceId, pt.getLatitude(), pt.getLongitude(),
                    alt, System.currentTimeMillis());

            int code = client.sendPositionReport(url);

            if (code == 200) {
                lastReportMs.set(System.currentTimeMillis());
                backoffSeconds.set(0);
                notifyStatus(true, "Connected", lastReportMs.get());
            } else if (code == 401 || code == 403) {
                running = false;
                notifyStatus(false, "Auth rejected (HTTP " + code + ") — check connect key", 0);
            } else if (code == 404) {
                running = false;
                notifyStatus(false, "Map not found (HTTP 404)", 0);
            } else if (code == 429) {
                backoffSeconds.set(60);
                notifyStatus(false, "Rate limited — waiting 60 s", lastReportMs.get());
            } else if (code >= 500) {
                backoffSeconds.set(intervalSeconds * 3);
                notifyStatus(false, "Server error (HTTP " + code + ")", lastReportMs.get());
            } else if (code < 0) {
                notifyStatus(false, "No network", lastReportMs.get());
            }

        } catch (Exception e) {
            Log.e(TAG, "tick error", e);
            notifyStatus(false, "Error: " + e.getMessage(), lastReportMs.get());
        }
    }

    private void notifyStatus(boolean ok, String msg, long ts) {
        StatusListener l = listener;
        if (l != null) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> l.onStatus(ok, msg, ts));
        }
    }
}
