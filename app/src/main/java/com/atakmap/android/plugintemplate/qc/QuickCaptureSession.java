package com.atakmap.android.plugintemplate.qc;

/**
 * Immutable snapshot of a resolved ArcGIS Quick Capture project.
 *
 * <p>Created by scanning a QC share-link QR code and resolving the item metadata
 * via the ArcGIS sharing REST API.
 */
public final class QuickCaptureSession {

    /** ArcGIS Online item ID parsed from the share URL. */
    public final String itemId;

    /**
     * REST URL of the Feature Service layer to submit records to.
     * Typically ends with {@code /FeatureServer/0}.
     */
    public final String featureServiceUrl;

    /**
     * ArcGIS access token (user-supplied or obtained via OAuth).
     * May be empty for publicly editable layers.
     */
    public final String token;

    /** Human-readable project name from the item metadata. */
    public final String projectName;

    public QuickCaptureSession(String itemId, String featureServiceUrl,
                                String token, String projectName) {
        this.itemId            = itemId;
        this.featureServiceUrl = featureServiceUrl;
        this.token             = token;
        this.projectName       = projectName;
    }

    /** Full addFeatures endpoint URL. */
    public String addFeaturesUrl() {
        // Ensure we target the correct layer (layer 0 by default)
        String base = featureServiceUrl;
        if (!base.endsWith("/0")) base += "/0";
        return base + "/addFeatures";
    }

    @Override
    public String toString() {
        return projectName + " (" + itemId + ")";
    }
}
