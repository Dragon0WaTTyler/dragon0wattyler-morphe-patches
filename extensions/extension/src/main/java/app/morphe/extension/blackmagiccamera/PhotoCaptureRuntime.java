package app.morphe.extension.blackmagiccamera;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime bridge for the two optional Blackmagic Camera patches. */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class PhotoCaptureRuntime {
    private static final Object LOCK = new Object();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<Activity, Ui> UIS = new WeakHashMap<>();
    private static final Map<Object, ReaderSlot> READERS = new WeakHashMap<>();
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile boolean photoModeEnabled;
    private static volatile boolean recordingCaptureEnabled;
    private static volatile boolean recording;
    private static volatile byte[] latestPixels;
    private static volatile int latestWidth;
    private static volatile int latestHeight;
    private static volatile int latestRowStride;
    private static volatile int latestPixelStride;
    private static int activityGeneration;
    private static final AtomicBoolean saving = new AtomicBoolean(false);

    private PhotoCaptureRuntime() { }

    public static void enablePhotoMode(Activity activity) {
        photoModeEnabled = true;
        attachUi(activity);
    }

    public static void enablePhotosWhileRecording(Activity activity) {
        recordingCaptureEnabled = true;
        attachUi(activity);
    }

    public static void onVideoRecorderState(Object state) {
        String name = String.valueOf(state);
        recording = "READY".equalsIgnoreCase(name) || name.toUpperCase().contains("RECORDING");
        synchronized (LOCK) {
            for (Ui ui : UIS.values()) ui.refresh();
        }
    }

    public static ImageReader resolveReader(Object manager, Object pipelineConfig, Size previewSize, ImageReader existing) {
        if ((!photoModeEnabled && !recordingCaptureEnabled) || manager == null || previewSize == null) return existing;
        synchronized (LOCK) {
            ReaderSlot previous = READERS.get(manager);
            if (existing != null) {
                if (previous != null && previous.reader != existing) previous.close();
                READERS.remove(manager);
                return existing;
            }
            if (previous != null && !previous.closed) return previous.reader;
            try {
                Size output = scaledSize(previewSize);
                ImageReader reader = ImageReader.newInstance(output.getWidth(), output.getHeight(), PixelFormat.RGBA_8888, 3);
                    ReaderSlot slot = new ReaderSlot(manager, reader);
                READERS.put(manager, slot);
                setReaderField(manager, reader);
                    clearLatestFrame();
                reader.setOnImageAvailableListener(slot::onImage, slot.handler);
                return reader;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    public static void onActivityDestroyed(Activity activity) {
        final int expectedGeneration;
        synchronized (LOCK) {
            Ui ui = UIS.remove(activity);
            if (ui != null) ui.remove();
            expectedGeneration = activityGeneration;
        }
        MAIN_HANDLER.postDelayed(() -> {
            synchronized (LOCK) {
                if (expectedGeneration != activityGeneration) return;
                for (Activity active : UIS.keySet()) {
                    if (!active.isFinishing() && !active.isDestroyed()) return;
                }
                for (ReaderSlot slot : READERS.values()) slot.close();
                READERS.clear();
                clearLatestFrame();
            }
        }, 1200L);
    }

    private static Size scaledSize(Size size) {
        int width = size.getWidth();
        int height = size.getHeight();
        try {
            Class<?> helper = Class.forName("defpackage.kx2");
            Method method = helper.getDeclaredMethod("b", int.class, int.class);
            method.setAccessible(true);
            Object result = method.invoke(null, width, height);
            if (result instanceof Size) return (Size) result;
        } catch (Throwable ignored) { }
        double scale = Math.min(1.0, 1280.0 / Math.max(width, height));
        int outWidth = Math.max(2, ((int) (width * scale)) & ~1);
        int outHeight = Math.max(2, ((int) (height * scale)) & ~1);
        return new Size(outWidth, outHeight);
    }

    private static void setReaderField(Object manager, ImageReader reader) {
        try {
            Field field = manager.getClass().getField("s");
            field.set(manager, reader);
        } catch (Throwable ignored) { }
    }

    private static void attachUi(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            synchronized (LOCK) {
                activityGeneration++;
                Ui ui = UIS.get(activity);
                if (ui == null) {
                    ui = new Ui(activity);
                    UIS.put(activity, ui);
                }
                ui.install();
            }
        });
    }

    private static void capture(Activity activity) {
        if (activity == null || !saving.compareAndSet(false, true)) {
            if (activity != null) toast(activity, "Photo is being saved");
            return;
        }
        byte[] bytes;
        int width;
        int height;
        int rowStride;
        int pixelStride;
        synchronized (LOCK) {
            bytes = latestPixels;
            width = latestWidth;
            height = latestHeight;
            rowStride = latestRowStride;
            pixelStride = latestPixelStride;
        }
        if (bytes == null || width < 1 || height < 1) {
            saving.set(false);
            toast(activity, "Camera frame is not ready");
            return;
        }
        Context context = activity.getApplicationContext();
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        SAVE_EXECUTOR.execute(() -> {
            Uri uri = null;
            try {
                Bitmap bitmap = makeBitmap(bytes, width, height, rowStride, pixelStride);
                int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
                if (degrees != 0) {
                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                    matrix.postRotate(degrees);
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    bitmap.recycle();
                    bitmap = rotated;
                }
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
                    if (stream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream)) throw new IllegalStateException("JPEG encoding failed");
                }
                bitmap.recycle();
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    context.getContentResolver().update(uri, ready, null, null);
                }
                toast(activity, "Photo saved");
            } catch (Throwable error) {
                if (uri != null) {
                    try { context.getContentResolver().delete(uri, null, null); } catch (Throwable ignored) { }
                }
                toast(activity, "Could not save photo");
            } finally {
                saving.set(false);
            }
        });
    }

    private static Bitmap makeBitmap(byte[] bytes, int width, int height, int rowStride, int pixelStride) {
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = y * rowStride;
            for (int x = 0; x < width; x++) {
                int offset = row + x * pixelStride;
                int r = bytes[offset] & 0xff;
                int g = bytes[offset + 1] & 0xff;
                int b = bytes[offset + 2] & 0xff;
                int a = pixelStride > 3 ? bytes[offset + 3] & 0xff : 255;
                pixels[y * width + x] = Color.argb(a, r, g, b);
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static void toast(Activity activity, String message) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }

    private static void clearLatestFrame() {
        latestPixels = null;
        latestWidth = 0;
        latestHeight = 0;
        latestRowStride = 0;
        latestPixelStride = 0;
    }

    private static final class ReaderSlot {
        final WeakReference<Object> managerRef;
        final ImageReader reader;
        final HandlerThread thread;
        final Handler handler;
        volatile boolean closed;

        ReaderSlot(Object manager, ImageReader reader) {
            managerRef = new WeakReference<>(manager);
            this.reader = reader;
            thread = new HandlerThread("BmcPhotoFrames");
            thread.start();
            handler = new Handler(thread.getLooper());
        }

        void onImage(ImageReader source) {
            Image image = null;
            try {
                image = source.acquireLatestImage();
                if (image == null || image.getPlanes().length == 0) return;
                Image.Plane plane = image.getPlanes()[0];
                ByteBuffer buffer = plane.getBuffer().duplicate();
                byte[] copy = new byte[buffer.remaining()];
                buffer.get(copy);
                synchronized (LOCK) {
                    latestWidth = image.getWidth();
                    latestHeight = image.getHeight();
                    latestRowStride = plane.getRowStride();
                    latestPixelStride = plane.getPixelStride();
                    latestPixels = copy;
                }
            } catch (Throwable ignored) {
            } finally {
                if (image != null) image.close();
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            try { reader.setOnImageAvailableListener(null, null); } catch (Throwable ignored) { }
            try { reader.close(); } catch (Throwable ignored) { }
            Object manager = managerRef.get();
            if (manager != null) {
                try {
                    if (manager.getClass().getField("s").get(manager) == reader) {
                        manager.getClass().getField("s").set(manager, null);
                    }
                } catch (Throwable ignored) { }
            }
            thread.quitSafely();
        }
    }

    private static final class Ui {
        final WeakReference<Activity> activityRef;
        FrameLayout overlay;
        TextView mode;
        TextView shutter;
        TextView recordingShutter;
        boolean photoSelected;

        Ui(Activity activity) { activityRef = new WeakReference<>(activity); }

        void install() {
            Activity activity = activityRef.get();
            if (activity == null) return;
            View content = activity.findViewById(android.R.id.content);
            if (!(content instanceof ViewGroup)) return;
            if (overlay != null && overlay.getParent() == content) { refresh(); return; }
            FrameLayout layer = new FrameLayout(activity);
            layer.setClipChildren(false);
            layer.setClipToPadding(false);
            ((ViewGroup) content).addView(layer, new ViewGroup.LayoutParams(-1, -1));
            overlay = layer;

            mode = label(activity, "VIDEO   /   PHOTO", 11, 0xFFE7E7E7, 0xCC171717);
            FrameLayout.LayoutParams modeLp = new FrameLayout.LayoutParams(dp(activity, 142), dp(activity, 40), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            modeLp.bottomMargin = dp(activity, 126);
            layer.addView(mode, modeLp);
            mode.setOnClickListener(v -> {
                if (recording) { toast(activity, "Stop recording to change mode"); return; }
                photoSelected = !photoSelected;
                refresh();
            });

            shutter = label(activity, "●", 28, Color.WHITE, 0xFFE8E8E8);
            FrameLayout.LayoutParams shotLp = new FrameLayout.LayoutParams(dp(activity, 68), dp(activity, 68), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            shotLp.bottomMargin = dp(activity, 44);
            layer.addView(shutter, shotLp);
            shutter.setOnClickListener(v -> capture(activity));

            recordingShutter = label(activity, "●", 20, Color.WHITE, 0xCC303030);
            FrameLayout.LayoutParams recLp = new FrameLayout.LayoutParams(dp(activity, 52), dp(activity, 52), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            recLp.bottomMargin = dp(activity, 52);
            recLp.leftMargin = dp(activity, 88);
            layer.addView(recordingShutter, recLp);
            recordingShutter.setOnClickListener(v -> capture(activity));
            refresh();
        }

        void refresh() {
            if (mode == null) return;
            Activity activity = activityRef.get();
            if (activity == null) return;
            mode.setVisibility(photoModeEnabled ? View.VISIBLE : View.GONE);
            mode.setText(photoSelected ? "PHOTO" : "VIDEO");
            mode.setContentDescription(photoSelected ? "Photo mode selected. Switch to video mode" : "Video mode selected. Switch to photo mode");
            shutter.setVisibility(photoModeEnabled && photoSelected ? View.VISIBLE : View.GONE);
            recordingShutter.setVisibility(recordingCaptureEnabled && recording ? View.VISIBLE : View.GONE);
        }

        void remove() {
            if (overlay != null && overlay.getParent() instanceof ViewGroup) ((ViewGroup) overlay.getParent()).removeView(overlay);
            overlay = null;
            mode = null;
            shutter = null;
            recordingShutter = null;
        }
    }

    private static TextView label(Context context, String text, int sp, int color, int background) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        if ("●".equals(text)) {
            shape.setShape(GradientDrawable.OVAL);
            shape.setStroke(dp(context, 3), Color.WHITE);
        } else {
            shape.setCornerRadius(dp(context, 20));
            shape.setStroke(dp(context, 1), 0x66FFFFFF);
        }
        view.setBackground(shape);
        view.setElevation(dp(context, 5));
        view.setContentDescription(text.contains("PHOTO") ? "Blackmagic photo controls" : "Capture photo");
        return view;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
