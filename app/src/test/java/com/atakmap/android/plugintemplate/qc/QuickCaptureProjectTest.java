package com.atakmap.android.plugintemplate.qc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuickCaptureProjectTest {
    @Test
    public void cardWithUrlAndCaptureTargetPrefersCapture() {
        QuickCaptureProject project = new QuickCaptureProject();
        project.dataSources.put("layer", "https://example.com/FeatureServer/0");
        QuickCaptureProject.Template template = template("layer");
        assertTrue(project.hasCaptureTarget(template));
        assertFalse(project.isReferenceLink(template));
    }

    @Test
    public void cardWithUrlAndNoCaptureTargetIsReferenceLink() {
        QuickCaptureProject project = new QuickCaptureProject();
        QuickCaptureProject.Template template = template("missing");
        assertFalse(project.hasCaptureTarget(template));
        assertTrue(project.isReferenceLink(template));
    }

    @Test
    public void unresolvedVariableDetectionDoesNotUseRegex() {
        assertTrue(QuickCaptureProject.isUnresolvedVariable("${positionSource}"));
        assertFalse(QuickCaptureProject.isUnresolvedVariable("prefix ${positionSource}"));
        assertFalse(QuickCaptureProject.isUnresolvedVariable("${broken"));
    }

    private QuickCaptureProject.Template template(String dataSourceId) {
        QuickCaptureProject.Template template = new QuickCaptureProject.Template();
        template.dataSourceId = dataSourceId;
        template.url = "https://example.com/help";
        return template;
    }
}
