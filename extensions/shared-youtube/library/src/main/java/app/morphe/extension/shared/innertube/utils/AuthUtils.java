/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.shared.innertube.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("unused")
public class AuthUtils {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String PAGE_ID_HEADER = "X-Goog-PageId";
    private static final String VISITOR_ID_HEADER = "X-Goog-Visitor-Id";
    @NonNull
    private static String authorization = "";
    @NonNull
    private static String pageId = "";
    @NonNull
    private static String visitorId = "";
    private static volatile boolean incognitoStatus = false;
    // Independent Groups groups.6.1: invalidate ephemeral native capabilities on context changes.
    private static final java.util.concurrent.atomic.AtomicLong contextGeneration = new java.util.concurrent.atomic.AtomicLong();
    public static long getContextGeneration() { return contextGeneration.get(); }

    /**
     * Injection point.
     */
    public static void setRequestHeaders(String url, Map<String, String> requestHeaders) {
        if (requestHeaders != null && !requestHeaders.isEmpty()) {
            String newlyLoadedAuthorization = requestHeaders.get(AUTHORIZATION_HEADER);
            String newlyLoadedVisitorId = requestHeaders.get(VISITOR_ID_HEADER);

            if (Utils.isNotEmpty(newlyLoadedAuthorization) &&
                    Utils.isNotEmpty(newlyLoadedVisitorId)) {
                boolean changed = !authorization.equals(newlyLoadedAuthorization) || !visitorId.equals(newlyLoadedVisitorId);
                authorization = newlyLoadedAuthorization;
                visitorId = newlyLoadedVisitorId;
                if (changed) contextGeneration.incrementAndGet();
            }
        }
    }

    /**
     * Injection point.
     */
    public static void setPageId(@Nullable String newlyPageIDHeaderValue) {
        if (newlyPageIDHeaderValue != null && !pageId.equals(newlyPageIDHeaderValue)) {
            pageId = newlyPageIDHeaderValue;
            contextGeneration.incrementAndGet();

            Logger.printDebug(() -> "new PageID Header value loaded: " + newlyPageIDHeaderValue);
        }
    }

    /**
     * Injection point.
     */
    public static void setIncognitoStatus(boolean newlyLoadedIncognitoStatus) {
        if (incognitoStatus != newlyLoadedIncognitoStatus) { incognitoStatus = newlyLoadedIncognitoStatus; contextGeneration.incrementAndGet(); }
    }

    public static Map<String, String> getRequestHeader() {
        return Map.of(
            AUTHORIZATION_HEADER, authorization,
            VISITOR_ID_HEADER, visitorId,
            PAGE_ID_HEADER, pageId
        );
    }

    @NonNull
    public static String getAuthorization() {
        return authorization;
    }

    @NonNull
    public static String getVisitorId() {
        return visitorId;
    }

    /** Independent Groups groups.6, modified 2026-11-02: observe existing native privacy state. */
    public static boolean isIncognito() { return incognitoStatus; }

    public static boolean isNotLoggedIn() {
        return authorization.isEmpty() || (pageId.isEmpty() && incognitoStatus);
    }
}
