package app.spicetify.extension.spotify.settings;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLException;

/** Local observations only: no probe requests, credentials, response bodies, or authentication changes. */
public final class ConnectionDiagnostics {
    private static final String PUBLISHER = "6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd";
    private static volatile String lastHomeFailure = "No failure captured in this process";

    private ConnectionDiagnostics() {}

    /** Observes the exception Spotify already handles; it must never alter that error path. */
    public static void recordHomeFailure(Throwable failure) {
        if (failure == null) return;
        try {
            StringBuilder classes = new StringBuilder();
            String category = "unclassified";
            Integer status = null;
            Throwable current = failure;
            for (int depth = 0; current != null && depth < 8; depth++) {
                if (current instanceof java.util.concurrent.CancellationException) return;
                if (depth > 0) classes.append(" -> ");
                classes.append(current.getClass().getName());
                if (current instanceof UnknownHostException) category = "DNS lookup failed";
                else if (current instanceof SSLException) category = "TLS connection failed";
                else if (current instanceof SocketTimeoutException) category = "connection timed out";
                else if (current instanceof ConnectException) category = "connection failed";
                else if (current instanceof LinkageError || current instanceof ClassCastException)
                    category = "client code/linkage failure";
                for (Class<?> type = current.getClass(); type != null; type = type.getSuperclass()) {
                    if (!type.getName().equals("retrofit2.HttpException")) continue;
                    // Verified in 9.1.94: Spotify reads this same field into its loading-error event.
                    Field code = type.getDeclaredField("a");
                    if (code.getType() == int.class) {
                        code.setAccessible(true);
                        int value = code.getInt(current);
                        if (value >= 100 && value <= 599) status = value;
                    }
                }
                Throwable next = current.getCause();
                if (next == current) break;
                current = next;
            }
            lastHomeFailure = (status == null ? category : "HTTP " + status) + ": " + classes;
            Log.w("SpicetifyConnection", lastHomeFailure);
        } catch (Throwable ignored) {
            // Diagnostic failures cannot replace Spotify's original error.
        }
    }

    public static String report(Context context) {
        StringBuilder report = new StringBuilder("Spicetify connection diagnostics\n");
        report.append("Android API: ").append(Build.VERSION.SDK_INT).append('\n');
        try {
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
            report.append("App: ").append(info.packageName).append(' ').append(info.versionName).append(" (")
                    .append(Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode).append(")\n");
            Signature[] certificates = Build.VERSION.SDK_INT >= 28
                    ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners() : info.signatures;
            report.append("Signer: ").append(signer(certificates)).append('\n');
        } catch (Exception ignored) {
            report.append("App/signature information unavailable\n");
        }
        report.append("Customizations paused this launch: ").append(PatchSettings.customizationsPaused()).append('\n');
        report.append("Pause requested for next launch: ").append(PatchSettings.pauseRequested()).append('\n');
        List<String> installed = new ArrayList<>();
        if (InstalledPatches.cleanSharing()) installed.add("sharing");
        if (InstalledPatches.themeColors()) installed.add("theme");
        if (InstalledPatches.homePins()) installed.add("home pins");
        if (InstalledPatches.extensions()) installed.add("extensions");
        if (InstalledPatches.hidePremiumTab()) installed.add("premium tab");
        if (InstalledPatches.hideBrandAds()) installed.add("home/browse ads");
        if (InstalledPatches.hidePlayerAdCards()) installed.add("player cards");
        if (InstalledPatches.serverFiles()) installed.add("server files");
        report.append("Installed features: ").append(installed).append('\n');
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = manager == null ? null : manager.getActiveNetwork();
            NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
            if (capabilities == null) report.append("Active network: unavailable\n");
            else report.append("Internet capability: ").append(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                    .append("; Android validated: ").append(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                    .append("; captive portal: ").append(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
                    .append("; VPN transport: ").append(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).append('\n');
        } catch (RuntimeException ignored) {
            report.append("Network state unavailable\n");
        }
        report.append("Last Home failure: ").append(lastHomeFailure).append('\n');
        report.append("Android network validation does not prove Spotify catalog access. HTTP rejection alone does not identify its cause.\n");
        report.append("This report omits accounts, tokens, URLs and response bodies.");
        return report.toString();
    }

    private static String signer(Signature[] signatures) throws Exception {
        if (signatures == null || signatures.length == 0) return "unavailable";
        for (Signature signature : signatures) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            if (PUBLISHER.equals(hex.toString())) return "Spotify publisher";
        }
        return "different from Spotify publisher (modified installation)";
    }
}
