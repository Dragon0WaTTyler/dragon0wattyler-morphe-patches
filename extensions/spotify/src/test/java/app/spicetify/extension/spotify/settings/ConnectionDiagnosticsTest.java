package app.spicetify.extension.spotify.settings;

import android.app.Application;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class ConnectionDiagnosticsTest {
    private Application application;

    @Before public void initialize() {
        application = RuntimeEnvironment.getApplication();
        application.deleteSharedPreferences("spicetify_patch_settings");
        PatchSettings.initialize(application);
    }

    @After public void restore() {
        PatchSettings.setPauseRequested(false);
        PatchSettings.initialize(application);
    }

    @Test public void pauseStartsAfterRestartAndKeepsUserChoices() {
        PatchSettings.setHideBrandAdsEnabled(true);
        PatchSettings.setCleanSharingEnabled(false);
        PatchSettings.setPauseRequested(true);
        assertFalse(PatchSettings.customizationsPaused());
        assertTrue(PatchSettings.restartRequired());
        PatchSettings.initialize(application);
        assertTrue(PatchSettings.customizationsPaused());
        assertFalse(PatchSettings.hideBrandAdsEnabled());
        assertFalse(PatchSettings.hidePlayerAdCardsEnabled());
        assertFalse(PatchSettings.hidePremiumTabEnabled());
        assertFalse(PatchSettings.cleanSharingEnabled());
        assertFalse(PatchSettings.restartRequired());
        PatchSettings.setPauseRequested(false);
        assertTrue(PatchSettings.customizationsPaused());
        PatchSettings.initialize(application);
        assertTrue(PatchSettings.hideBrandAdsEnabled());
        assertFalse(PatchSettings.cleanSharingEnabled());
    }

    @Test public void errorReportIncludesStatusAndTypesButNoMessagesOrSessionData() {
        ConnectionDiagnostics.recordHomeFailure(new RuntimeException("outer-secret",
                new retrofit2.HttpException(403, "https://private.invalid/me?access_token=private-secret")));
        String report = ConnectionDiagnostics.report(application);
        assertTrue(report.contains("HTTP 403"));
        assertTrue(report.contains("retrofit2.HttpException"));
        assertFalse(report.contains("private-secret"));
        assertFalse(report.contains("private.invalid"));
        assertFalse(report.contains("outer-secret"));
    }

    @Test public void transportErrorsAndNormalCancellationAreDistinguished() {
        ConnectionDiagnostics.recordHomeFailure(new UnknownHostException("private-host"));
        String report = ConnectionDiagnostics.report(application);
        assertTrue(report.contains("DNS lookup failed"));
        assertFalse(report.contains("private-host"));
        ConnectionDiagnostics.recordHomeFailure(new CancellationException("normal navigation"));
        assertEquals(report, ConnectionDiagnostics.report(application));
        ConnectionDiagnostics.recordHomeFailure(null);
        assertEquals(report, ConnectionDiagnostics.report(application));
    }
}
