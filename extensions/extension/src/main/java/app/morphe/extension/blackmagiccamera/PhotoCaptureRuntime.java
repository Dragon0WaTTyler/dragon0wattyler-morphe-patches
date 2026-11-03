package app.morphe.extension.blackmagiccamera;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime bridge for the two optional Blackmagic Camera patches. */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class PhotoCaptureRuntime {
    private static final String PREVIEW_SURFACE_CLASS = "defpackage.tt";
    private static final int MAX_CAPTURE_PIXELS = 12_000_000;
    private static final Object LOCK = new Object();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<Activity, Ui> UIS = new WeakHashMap<>();
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean saving = new AtomicBoolean(false);
    private static volatile WeakReference<Activity> lastCameraActivity = new WeakReference<>(null);

    private static volatile boolean extensionLoaded;
    private static volatile boolean photoModeEnabled;
    private static volatile boolean photoModeSelected;
    private static volatile boolean recordingCaptureEnabled;
    private static volatile boolean recording;
    private static volatile String coreRecordingState = "unknown";
    private static volatile String lastUiStatus = "Waiting for Blackmagic main screen";
    private static volatile String lastPixelCopyStatus = "No frame copied yet";
    private static volatile String lastCaptureStatus = "No photo saved yet";

    static {
        extensionLoaded = true;
    }

    private PhotoCaptureRuntime() { }

    /** Called early from MainActivity.onCreate; it only arms the optional feature. */
    public static void enablePhotoMode(Activity activity) {
        photoModeEnabled = true;
        rememberActivity(activity);
        recordStatus(activity, "Photo mode hook armed; waiting for camera content");
        refreshFeatureUi(activity);
    }

    /** Called early from MainActivity.onStart; it only arms the optional feature. */
    public static void enablePhotosWhileRecording(Activity activity) {
        recordingCaptureEnabled = true;
        rememberActivity(activity);
        recordStatus(activity, "Recording-photo hook armed; waiting for camera content");
        refreshFeatureUi(activity);
    }

    /**
     * Called after MainActivity's permission-gated Compose setContentView call.
     * The callback argument is the verified MainActivity$loadMainScreen$1 lambda;
     * its captured Activity is obtained by type, not by an obfuscated field name.
     */
    public static void onMainScreenLoaded(Object loader) {
        Activity activity = findCapturedActivity(loader);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Activity fallback = lastCameraActivity.get();
            if (fallback != null && !fallback.isFinishing() && !fallback.isDestroyed()) {
                activity = fallback;
                lastUiStatus = "Main-screen Activity resolved from the patch lifecycle hook";
            }
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            lastUiStatus = "Main camera content loaded, but its Activity could not be resolved";
            return;
        }
        attachUi(activity);
    }

    /** Receives the app's actual NOT_RECORDING / RECORDING / PAUSED state. */
    public static void onCoreRecordingState(Object state) {
        String name = state == null ? "unknown" : String.valueOf(state);
        coreRecordingState = name;
        recording = "RECORDING".equalsIgnoreCase(name);
        synchronized (LOCK) {
            for (Ui ui : UIS.values()) MAIN_HANDLER.post(() -> refreshUiSafely(ui));
        }
    }

    public static void onActivityDestroyed(Activity activity) {
        synchronized (LOCK) {
            Ui ui = UIS.remove(activity);
            if (ui != null) ui.remove();
        }
        if (lastCameraActivity.get() == activity) lastCameraActivity = new WeakReference<>(null);
    }

    private static void rememberActivity(Activity activity) {
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            lastCameraActivity = new WeakReference<>(activity);
        }
    }

    private static void installUiSafely(Ui ui, Activity activity) {
        try {
            ui.install();
        } catch (Throwable error) {
            lastUiStatus = "UI attachment failed: " + error.getClass().getSimpleName();
            toast(activity, "Photo patch UI failed: " + error.getClass().getSimpleName());
        }
    }

    private static void refreshUiSafely(Ui ui) {
        try {
            ui.refresh();
        } catch (Throwable error) {
            Activity activity = ui.activityRef.get();
            lastUiStatus = "UI refresh failed: " + error.getClass().getSimpleName();
            toast(activity, "Photo patch UI update failed: " + error.getClass().getSimpleName());
        }
    }

    /** Updates an already-installed UI if another independently selected patch arms later. */
    private static void refreshFeatureUi(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(() -> {
            synchronized (LOCK) {
                Ui ui = UIS.get(activity);
                if (ui != null) installUiSafely(ui, activity);
            }
        });
    }

    private static Activity findCapturedActivity(Object loader) {
        if (loader == null) return null;
        for (Class<?> type = loader.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Activity.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(loader);
                    if (value instanceof Activity) return (Activity) value;
                } catch (Throwable error) {
                    lastUiStatus = "Could not read camera Activity from screen loader: " + error.getClass().getSimpleName();
                }
            }
        }
        return null;
    }

    private static void attachUi(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        lastUiStatus = "Attaching controls to the loaded camera screen";
        activity.runOnUiThread(() -> {
            synchronized (LOCK) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                Ui ui = UIS.get(activity);
                if (ui == null) {
                    ui = new Ui(activity);
                    UIS.put(activity, ui);
                }
                installUiSafely(ui, activity);
            }
        });
    }

    private static void capture(Activity activity) {
        if (activity == null || !saving.compareAndSet(false, true)) {
            if (activity != null) toast(activity, "Photo is being saved");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            failCapture(activity, null, "PixelCopy requires Android 7 or newer");
            return;
        }

        SurfaceView preview = findPreviewSurface(activity);
        if (preview == null) {
            failCapture(activity, null, "Blackmagic preview surface is not ready");
            return;
        }
        SurfaceHolder holder = preview.getHolder();
        if (holder == null || holder.getSurface() == null || !holder.getSurface().isValid()
                || preview.getWidth() < 2 || preview.getHeight() < 2) {
            failCapture(activity, null, "Blackmagic preview frame is not ready");
            return;
        }

        double scale = Math.min(1.0, Math.sqrt((double) MAX_CAPTURE_PIXELS
                / ((long) preview.getWidth() * preview.getHeight())));
        final int width = Math.max(2, (int) (preview.getWidth() * scale));
        final int height = Math.max(2, (int) (preview.getHeight() * scale));
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (Throwable error) {
            failCapture(activity, null, "Could not allocate photo frame: " + error.getClass().getSimpleName());
            return;
        }

        lastPixelCopyStatus = "Copying preview frame…";
        try {
            PixelCopy.request(preview, bitmap, result -> {
                if (result != PixelCopy.SUCCESS) {
                    bitmap.recycle();
                    failCapture(activity, null, "Preview copy failed (PixelCopy " + result + ")");
                    return;
                }
                lastPixelCopyStatus = "Frame copied " + width + "×" + height;
                synchronized (LOCK) {
                    Ui ui = UIS.get(activity);
                    if (ui != null) {
                        ui.frameAvailable = true;
                        ui.refreshStatus();
                    }
                }
                saveBitmap(activity.getApplicationContext(), activity, bitmap);
            }, MAIN_HANDLER);
        } catch (Throwable error) {
            bitmap.recycle();
            failCapture(activity, null, "Preview copy failed: " + error.getClass().getSimpleName());
        }
    }

    private static void saveBitmap(Context context, Activity activity, Bitmap bitmap) {
        SAVE_EXECUTOR.execute(() -> {
            Uri uri = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "BMC_" + System.currentTimeMillis() + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Blackmagic Camera");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }
                uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("MediaStore insert failed");
                try (OutputStream stream = context.getContentResolver().openOutputStream(uri)) {
                    if (stream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream)) {
                        throw new IllegalStateException("JPEG encoding failed");
                    }
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    context.getContentResolver().update(uri, ready, null, null);
                }
                lastCaptureStatus = "Saved " + uri;
                toast(activity, "Photo saved");
            } catch (Throwable error) {
                if (uri != null) {
                    try { context.getContentResolver().delete(uri, null, null); } catch (Throwable ignored) { }
                }
                lastCaptureStatus = "Save failed: " + error.getClass().getSimpleName();
                toast(activity, "Could not save photo");
            } finally {
                bitmap.recycle();
                saving.set(false);
            }
        });
    }

    private static void failCapture(Activity activity, Bitmap bitmap, String message) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        lastCaptureStatus = message;
        lastPixelCopyStatus = message;
        saving.set(false);
        toast(activity, message);
        synchronized (LOCK) {
            for (Ui ui : UIS.values()) ui.refreshStatus();
        }
    }

    private static void recordStatus(Activity activity, String message) {
        lastUiStatus = message;
        synchronized (LOCK) {
            Ui ui = activity == null ? null : UIS.get(activity);
            if (ui != null) ui.refreshStatus();
        }
    }

    private static SurfaceView findPreviewSurface(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        return findPreviewSurface(content);
    }

    private static SurfaceView findPreviewSurface(View view) {
        if (view == null) return null;
        if (view instanceof SurfaceView && PREVIEW_SURFACE_CLASS.equals(view.getClass().getName())) {
            return (SurfaceView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                SurfaceView result = findPreviewSurface(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void showDiagnostics(Activity activity) {
        Ui ui;
        synchronized (LOCK) {
            ui = UIS.get(activity);
        }
        SurfaceView preview = findPreviewSurface(activity);
        String sourceStatus = "unavailable";
        if (preview != null) {
            SurfaceHolder holder = preview.getHolder();
            boolean valid = holder != null && holder.getSurface() != null && holder.getSurface().isValid();
            sourceStatus = (valid ? "ready" : "waiting")
                    + " (" + preview.getWidth() + "×" + preview.getHeight() + ")";
        }
        String details = "Extension loaded: " + yesNo(extensionLoaded)
                + "\nPhoto Mode patch: " + yesNo(photoModeEnabled)
                + "\nPhoto Mode selected: " + yesNo(photoModeSelected)
                + "\nPhotos While Recording patch: " + yesNo(recordingCaptureEnabled)
                + "\nMain screen hook: " + (ui == null ? "not received" : "received")
                + "\nUI attached: " + (ui != null && ui.isAttached() ? "yes" : "no")
                + "\nCamera preview source: " + sourceStatus
                + "\nControl placement: " + (ui == null ? "not attached" : ui.controlAnchorStatus)
                + "\nPreview frame available: " + (ui != null && ui.frameAvailable ? "yes" : "not confirmed")
                + "\nUI status: " + lastUiStatus
                + "\nVideo state: " + coreRecordingState
                + "\nLast preview copy: " + lastPixelCopyStatus
                + "\nLast capture: " + lastCaptureStatus;
        new AlertDialog.Builder(activity)
                .setTitle("Blackmagic Photo Patches")
                .setMessage(details)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static void toast(Activity activity, String message) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class Ui {
        final WeakReference<Activity> activityRef;
        FrameLayout overlay;
        ViewGroup content;
        ViewGroup listenerRoot;
        LinearLayout controls;
        TextView mode;
        TextView shutter;
        TextView recordingShutter;
        TextView status;
        ViewTreeObserver.OnGlobalLayoutListener layoutListener;
        Surface probedSurface;
        Runnable probeRetry;
        int probeGeneration;
        boolean frameAvailable;
        boolean probePending;
        int probeAttempts;
        boolean removed;
        long lastAnchorLookupMs;
        SurfaceView lastAnchorPreview;
        Rect recordButtonBounds;
        String controlAnchorStatus = "record control not checked";

        Ui(Activity activity) {
            activityRef = new WeakReference<>(activity);
        }

        void install() {
            Activity activity = activityRef.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            View contentView = activity.findViewById(android.R.id.content);
            if (!(contentView instanceof ViewGroup)) {
                lastUiStatus = "Camera content root is unavailable";
                return;
            }
            ViewGroup root = (ViewGroup) contentView;
            ViewGroup previousContent = content;
            boolean contentChanged = previousContent != root;
            if (contentChanged && layoutListener != null && listenerRoot != null) {
                ViewTreeObserver oldObserver = listenerRoot.getViewTreeObserver();
                if (oldObserver.isAlive()) oldObserver.removeOnGlobalLayoutListener(layoutListener);
                listenerRoot = null;
            }
            content = root;

            if (overlay == null || overlay.getParent() != root) {
                if (overlay != null && overlay.getParent() instanceof ViewGroup) {
                    ((ViewGroup) overlay.getParent()).removeView(overlay);
                }
                overlay = new FrameLayout(activity);
                overlay.setClipChildren(false);
                overlay.setClipToPadding(false);
                overlay.setClickable(false);
                overlay.setFocusable(false);
                overlay.setBackgroundColor(Color.TRANSPARENT);
                overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                overlay.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View view) {
                        removed = false;
                    }

                    @Override public void onViewDetachedFromWindow(View view) {
                        if (removed) return;
                        MAIN_HANDLER.post(() -> {
                            Activity current = activityRef.get();
                            if (current != null && !current.isFinishing() && !current.isDestroyed()) {
                                installUiSafely(Ui.this, current);
                            }
                        });
                    }
                });
                root.addView(overlay, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
            }

            ensureControls(activity);
            if (controls.getParent() != overlay) {
                if (controls.getParent() instanceof ViewGroup) {
                    ((ViewGroup) controls.getParent()).removeView(controls);
                }
                overlay.addView(controls, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
            if (layoutListener == null) {
                layoutListener = () -> {
                    Activity current = activityRef.get();
                    if (current == null || current.isFinishing() || current.isDestroyed()) return;
                    if (overlay == null || overlay.getParent() != content) {
                        MAIN_HANDLER.post(() -> installUiSafely(Ui.this, current));
                        return;
                    }
                    refreshUiSafely(Ui.this);
                };
            }
            if (contentChanged || listenerRoot != root) {
                root.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
                listenerRoot = root;
            }

            lastUiStatus = "Controls attached to android.R.id.content above the camera screen";
            removed = false;
            refresh();
        }

        private void ensureControls(Activity activity) {
            boolean featureSetChanged = (mode != null) != photoModeEnabled
                    || (recordingShutter != null) != recordingCaptureEnabled;
            if (featureSetChanged && controls != null) {
                if (controls.getParent() instanceof ViewGroup) {
                    ((ViewGroup) controls.getParent()).removeView(controls);
                }
                controls = null;
                mode = null;
                shutter = null;
                recordingShutter = null;
                status = null;
            }
            if (controls == null) createControls(activity);
        }

        private void createControls(Activity activity) {
            controls = new LinearLayout(activity);
            controls.setOrientation(LinearLayout.VERTICAL);
            controls.setGravity(Gravity.CENTER_HORIZONTAL);
            controls.setClipChildren(false);
            controls.setClipToPadding(false);
            controls.setClickable(false);

            if (photoModeEnabled) {
                mode = label(activity, "VIDEO MODE", 12, Color.WHITE, 0xD9161719, false);
                controls.addView(mode, new LinearLayout.LayoutParams(dp(activity, 116), dp(activity, 42)));
                mode.setOnClickListener(v -> {
                    photoModeSelected = !photoModeSelected;
                    refresh();
                });
                mode.setOnLongClickListener(v -> {
                    showDiagnostics(activity);
                    return true;
                });

                shutter = label(activity, "◉", 28, 0xFF202124, 0xFFECEFF1, true);
                LinearLayout.LayoutParams photoLp = new LinearLayout.LayoutParams(dp(activity, 66), dp(activity, 66));
                photoLp.topMargin = dp(activity, 10);
                controls.addView(shutter, photoLp);
                shutter.setContentDescription("Take photo from the Blackmagic camera preview");
                shutter.setOnClickListener(v -> capture(activity));
                shutter.setOnLongClickListener(v -> {
                    showDiagnostics(activity);
                    return true;
                });
            }

            if (recordingCaptureEnabled) {
                recordingShutter = label(activity, "●", 22, Color.WHITE, 0xCC303030, true);
                LinearLayout.LayoutParams recordLp = new LinearLayout.LayoutParams(dp(activity, 56), dp(activity, 56));
                recordLp.topMargin = dp(activity, 10);
                controls.addView(recordingShutter, recordLp);
                recordingShutter.setContentDescription("Take a photo while video recording continues");
                recordingShutter.setOnClickListener(v -> capture(activity));
                recordingShutter.setOnLongClickListener(v -> {
                    showDiagnostics(activity);
                    return true;
                });
            }

            status = new TextView(activity);
            status.setTextColor(0xFFE7E7E7);
            status.setTextSize(9);
            status.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            status.setGravity(Gravity.CENTER);
            status.setBackground(makeBackground(0xCC171717, dp(activity, 8), 0, Color.TRANSPARENT));
            LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 25));
            statusLp.topMargin = dp(activity, 7);
            controls.addView(status, statusLp);
            status.setOnClickListener(v -> showDiagnostics(activity));
            overlay.addView(controls, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        void refresh() {
            Activity activity = activityRef.get();
            if (activity == null || controls == null) return;
            controls.setVisibility(photoModeEnabled || recordingCaptureEnabled ? View.VISIBLE : View.GONE);
            if (mode != null) mode.setText(photoModeSelected ? "PHOTO MODE" : "VIDEO MODE");
            if (shutter != null) shutter.setVisibility(photoModeSelected ? View.VISIBLE : View.GONE);
            if (recordingShutter != null) {
                recordingShutter.setVisibility(recordingCaptureEnabled && recording ? View.VISIBLE : View.GONE);
            }
            refreshStatus();
            positionControls(activity);
        }

        void refreshStatus() {
            if (status == null) return;
            Activity activity = activityRef.get();
            if (activity == null) return;
            SurfaceView preview = findPreviewSurface(activity);
            if (preview == null) {
                if (probedSurface != null || probePending || probeRetry != null) resetProbe(null);
                if (!"PREVIEW WAITING".contentEquals(status.getText())) status.setText("PREVIEW WAITING");
                status.setTextColor(0xFFFFD166);
                return;
            }
            SurfaceHolder holder = preview.getHolder();
            Surface surface = holder == null ? null : holder.getSurface();
            boolean ready = surface != null && surface.isValid()
                    && preview.getWidth() > 0 && preview.getHeight() > 0;
            if (surface != probedSurface) {
                resetProbe(surface);
            }
            if (!ready) frameAvailable = false;
            if (ready && !frameAvailable) probeFrame(preview, surface);
            String nextText = !ready ? "PREVIEW WAITING" : frameAvailable ? "FRAME READY" : "WAITING FOR FRAME";
            if (!nextText.contentEquals(status.getText())) status.setText(nextText);
            status.setTextColor(frameAvailable ? 0xFF8CE99A : 0xFFFFD166);
        }

        private void resetProbe(Surface surface) {
            if (probeRetry != null) MAIN_HANDLER.removeCallbacks(probeRetry);
            probeRetry = null;
            probeGeneration++;
            probedSurface = surface;
            frameAvailable = false;
            probePending = false;
            probeAttempts = 0;
        }

        private void retryFrameProbe(Surface surface, int generation) {
            long delay = probeAttempts == 0 || probeAttempts >= 6 ? 2000L : 500L;
            boolean newBurst = probeAttempts == 0 || probeAttempts >= 6;
            if (probeRetry != null) return;
            Runnable retry = () -> {
                if (probeRetry != null) probeRetry = null;
                if (generation != probeGeneration || surface != probedSurface || frameAvailable) return;
                Activity activity = activityRef.get();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                if (newBurst) probeAttempts = 0;
                refreshStatus();
                if (generation == probeGeneration && surface == probedSurface
                        && !frameAvailable && !probePending && probeAttempts == 0) {
                    retryFrameProbe(surface, generation);
                }
            };
            probeRetry = retry;
            MAIN_HANDLER.postDelayed(retry, delay);
        }

        private void probeFrame(SurfaceView preview, Surface surface) {
            if (probePending || probeRetry != null || frameAvailable || probeAttempts >= 6
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return;
            }
            probePending = true;
            probeAttempts++;
            final int generation = probeGeneration;
            final Bitmap probeBitmap;
            try {
                probeBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            } catch (Throwable error) {
                probePending = false;
                lastPixelCopyStatus = "Frame probe allocation failed: " + error.getClass().getSimpleName();
                retryFrameProbe(surface, generation);
                return;
            }
            try {
                PixelCopy.request(preview, probeBitmap, result -> {
                    probeBitmap.recycle();
                    if (generation != probeGeneration || surface != probedSurface) return;
                    probePending = false;
                    if (result == PixelCopy.SUCCESS) {
                        frameAvailable = true;
                        lastPixelCopyStatus = "Preview frame confirmed by PixelCopy";
                        refreshStatus();
                        return;
                    }
                    lastPixelCopyStatus = "Frame probe pending (PixelCopy " + result + ")";
                    retryFrameProbe(surface, generation);
                }, MAIN_HANDLER);
            } catch (Throwable error) {
                probeBitmap.recycle();
                if (generation != probeGeneration) return;
                probePending = false;
                lastPixelCopyStatus = "Frame probe failed: " + error.getClass().getSimpleName();
                retryFrameProbe(surface, generation);
            }
        }

        private void positionControls(Activity activity) {
            if (controls == null || overlay == null || overlay.getWidth() <= 0 || overlay.getHeight() <= 0) return;
            controls.measure(
                    View.MeasureSpec.makeMeasureSpec(overlay.getWidth(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(overlay.getHeight(), View.MeasureSpec.AT_MOST)
            );
            Rect rootBounds = new Rect();
            overlay.getGlobalVisibleRect(rootBounds);
            SurfaceView preview = findPreviewSurface(activity);
            Rect previewBounds = new Rect();
            boolean hasPreviewBounds = preview != null && preview.getWidth() > 0 && preview.getHeight() > 0
                    && preview.getGlobalVisibleRect(previewBounds);
            if (preview != lastAnchorPreview) {
                lastAnchorPreview = preview;
                lastAnchorLookupMs = 0;
                recordButtonBounds = null;
            }
            long now = android.os.SystemClock.uptimeMillis();
            if (hasPreviewBounds && now - lastAnchorLookupMs >= 1000L) {
                lastAnchorLookupMs = now;
                recordButtonBounds = findRecordButtonBounds(activity, preview, previewBounds);
                controlAnchorStatus = recordButtonBounds == null
                        ? "Record control semantics unavailable; using preview anchor"
                        : "Blackmagic record control located from Compose accessibility bounds";
            }

            Rect controlBounds = chooseControlBounds(
                    rootBounds,
                    hasPreviewBounds ? previewBounds : rootBounds,
                    recordButtonBounds,
                    controls.getMeasuredWidth(),
                    controls.getMeasuredHeight(),
                    dp(activity, 12),
                    dp(activity, 14),
                    dp(activity, 18)
            );
            if (controlBounds == null) {
                controlAnchorStatus = "No collision-free area; added controls hidden";
                controls.setVisibility(View.GONE);
                return;
            }
            controls.setVisibility(photoModeEnabled || recordingCaptureEnabled ? View.VISIBLE : View.GONE);
            int[] rootLocation = new int[2];
            overlay.getLocationOnScreen(rootLocation);
            int left = controlBounds.left - rootLocation[0];
            int top = controlBounds.top - rootLocation[1];
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) controls.getLayoutParams();
            if (params == null) params = new FrameLayout.LayoutParams(-2, -2);
            int gravity = Gravity.TOP | Gravity.LEFT;
            if (params.gravity != gravity || params.leftMargin != left || params.topMargin != top) {
                params.gravity = gravity;
                params.leftMargin = left;
                params.topMargin = top;
                controls.setLayoutParams(params);
            }
        }

        private static Rect chooseControlBounds(
                Rect root,
                Rect preview,
                Rect record,
                int width,
                int height,
                int gap,
                int horizontalMargin,
                int verticalMargin
        ) {
            Rect content = new Rect(root);
            if (width <= 0 || height <= 0 || width > content.width() || height > content.height()) return null;
            if (record != null && !record.isEmpty()) {
                int centerX = record.centerX();
                int centerY = record.centerY();
                Rect[] adjacent = new Rect[]{
                        new Rect(record.right + gap, centerY - height / 2,
                                record.right + gap + width, centerY - height / 2 + height),
                        new Rect(record.left - gap - width, centerY - height / 2,
                                record.left - gap, centerY - height / 2 + height),
                        new Rect(centerX - width / 2, record.top - gap - height,
                                centerX - width / 2 + width, record.top - gap),
                        new Rect(centerX - width / 2, record.bottom + gap,
                                centerX - width / 2 + width, record.bottom + gap + height),
                };
                for (Rect candidate : adjacent) {
                    if (content.contains(candidate) && !Rect.intersects(candidate, record)) return candidate;
                }
            }

            Rect area = preview == null || preview.isEmpty() ? content : new Rect(preview);
            if (!content.contains(area)) area.intersect(content);
            int[][] corners = record == null
                    ? new int[][]{
                            {area.left + horizontalMargin, area.top + verticalMargin},
                            {area.right - horizontalMargin - width, area.top + verticalMargin},
                            {area.left + horizontalMargin, area.bottom - verticalMargin - height},
                            {area.right - horizontalMargin - width, area.bottom - verticalMargin - height},
                    }
                    : new int[][]{
                            {area.right - horizontalMargin - width, area.bottom - verticalMargin - height},
                            {area.left + horizontalMargin, area.bottom - verticalMargin - height},
                            {area.right - horizontalMargin - width, area.top + verticalMargin},
                            {area.left + horizontalMargin, area.top + verticalMargin},
                    };
            for (int[] corner : corners) {
                Rect candidate = new Rect(corner[0], corner[1], corner[0] + width, corner[1] + height);
                if (content.contains(candidate) && (record == null || !Rect.intersects(candidate, record))) {
                    return candidate;
                }
            }

            int scanStep = Math.max(1, gap * 2);
            Rect previewPlacement = findAvailableBounds(
                    area, record, width, height, horizontalMargin, verticalMargin, scanStep);
            if (previewPlacement != null) return previewPlacement;
            return findAvailableBounds(content, record, width, height,
                    horizontalMargin, verticalMargin, scanStep);
        }

        private static Rect findAvailableBounds(
                Rect area,
                Rect record,
                int width,
                int height,
                int horizontalMargin,
                int verticalMargin,
                int step
        ) {
            int minLeft = area.left + horizontalMargin;
            int maxLeft = area.right - horizontalMargin - width;
            int minTop = area.top + verticalMargin;
            int maxTop = area.bottom - verticalMargin - height;
            if (minLeft > maxLeft || minTop > maxTop) return null;
            for (int top = minTop; top <= maxTop; top += step) {
                for (int left = minLeft; left <= maxLeft; left += step) {
                    Rect candidate = new Rect(left, top, left + width, top + height);
                    if (record == null || !Rect.intersects(candidate, record)) return candidate;
                }
            }
            Rect lastCorner = new Rect(maxLeft, maxTop, maxLeft + width, maxTop + height);
            if (record == null || !Rect.intersects(lastCorner, record)) return lastCorner;
            return null;
        }

        private static Rect findRecordButtonBounds(Activity activity, SurfaceView preview, Rect previewBounds) {
            View root = activity.findViewById(android.R.id.content);
            String recordLabel = resourceString(activity, "record");
            String stopLabel = resourceString(activity, "stop_recording");
            int[] budget = {256};
            return findRecordButtonBounds(root, previewBounds, recordLabel, stopLabel, budget);
        }

        private static Rect findRecordButtonBounds(
                View view,
                Rect previewBounds,
                String recordLabel,
                String stopLabel,
                int[] budget
        ) {
            if (view == null || budget[0] <= 0) return null;
            AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
            if (provider != null) {
                AccessibilityNodeInfo root = null;
                try {
                    root = view.createAccessibilityNodeInfo();
                    Rect found = findRecordButtonBounds(root, previewBounds, recordLabel, stopLabel, budget);
                    if (found != null) return found;
                } catch (Throwable ignored) {
                    // A missing or unavailable semantics tree falls back to preview-relative placement.
                } finally {
                    if (root != null) root.recycle();
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount() && budget[0] > 0; i++) {
                    Rect found = findRecordButtonBounds(group.getChildAt(i), previewBounds,
                            recordLabel, stopLabel, budget);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private static Rect findRecordButtonBounds(
                AccessibilityNodeInfo node,
                Rect previewBounds,
                String recordLabel,
                String stopLabel,
                int[] budget
        ) {
            if (node == null || budget[0]-- <= 0) return null;
            CharSequence description = node.getContentDescription();
            CharSequence text = node.getText();
            if ((matchesRecordLabel(description, recordLabel, stopLabel)
                    || matchesRecordLabel(text, recordLabel, stopLabel))) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (Rect.intersects(bounds, previewBounds) && bounds.width() >= 32 && bounds.height() >= 32) {
                    return bounds;
                }
            }
            for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    Rect found = findRecordButtonBounds(child, previewBounds, recordLabel, stopLabel, budget);
                    if (found != null) return found;
                } catch (Throwable ignored) {
                    // Some Compose providers invalidate virtual nodes during a layout pass.
                } finally {
                    if (child != null) child.recycle();
                }
            }
            return null;
        }

        private static boolean matchesRecordLabel(CharSequence value, String recordLabel, String stopLabel) {
            if (value == null) return false;
            String candidate = value.toString().trim().toLowerCase(Locale.ROOT);
            return candidate.equals(recordLabel) || candidate.equals(stopLabel)
                    || candidate.equals("record") || candidate.equals("stop recording");
        }

        private static String resourceString(Context context, String name) {
            int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
            if (id == 0) return "";
            try {
                return context.getString(id).trim().toLowerCase(Locale.ROOT);
            } catch (Throwable ignored) {
                return "";
            }
        }

        boolean isAttached() {
            return overlay != null && overlay.getParent() == content && overlay.isAttachedToWindow();
        }

        void remove() {
            removed = true;
            resetProbe(null);
            if (listenerRoot != null && layoutListener != null) {
                ViewTreeObserver observer = listenerRoot.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(layoutListener);
            }
            if (overlay != null && overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
            overlay = null;
            controls = null;
            mode = null;
            shutter = null;
            recordingShutter = null;
            status = null;
            layoutListener = null;
            listenerRoot = null;
            content = null;
        }
    }

    private static TextView label(Context context, String text, int sp, int color, int background, boolean circle) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setBackground(makeBackground(background, circle ? dp(context, 40) : dp(context, 18),
                circle ? dp(context, 3) : dp(context, 1), circle ? Color.WHITE : 0x66FFFFFF));
        view.setElevation(dp(context, 5));
        view.setContentDescription("Blackmagic photo patch controls");
        return view;
    }

    private static GradientDrawable makeBackground(int color, int radius, int stroke, int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        if (stroke > 0) shape.setStroke(stroke, strokeColor);
        return shape;
    }
}
