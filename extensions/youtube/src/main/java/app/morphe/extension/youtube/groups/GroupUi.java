/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageButton;
import android.text.TextUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.theme.ThemeUtils;
import static app.morphe.extension.shared.StringRef.str;

final class GroupUi {
    static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static int foreground() { return ThemeUtils.getAppForegroundColor(); }
    static int background() { return ThemeUtils.getAppBackgroundColor(); }
    static int muted() { return mix(foreground(), background(), 0.65f); }
    static int control() { return mix(foreground(), background(), 0.10f); }
    static int mix(int a, int b, float part) {
        int r = Math.round(((a >> 16) & 255) * part + ((b >> 16) & 255) * (1 - part));
        int g = Math.round(((a >> 8) & 255) * part + ((b >> 8) & 255) * (1 - part));
        int blue = Math.round((a & 255) * part + (b & 255) * (1 - part)); return 0xff000000 | r << 16 | g << 8 | blue;
    }
    static TextView text(Context c, CharSequence label, int size) {
        TextView view = new TextView(c); view.setText(label); view.setTextSize(size); view.setTextColor(foreground()); return view;
    }
    static TextView button(Context c, CharSequence label, boolean selected, Runnable action) {
        TextView view = text(c, label, 14); view.setGravity(Gravity.CENTER); view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(selected ? background() : foreground());
        GradientDrawable shape = new GradientDrawable(); shape.setCornerRadius(dp(c, 12)); shape.setColor(selected ? foreground() : control());
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337f7f7f), shape, null));
        view.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8)); view.setMinHeight(dp(c, 48));
        view.setContentDescription(label); view.setFocusable(true); view.setClickable(true); view.setSelected(selected);
        view.setOnClickListener(v -> action.run()); return view;
    }
    /** A 32dp chip face in a 48dp touch row, matching the reference category-chip density. */
    static TextView chip(Context c, CharSequence label, boolean selected, Runnable action) {
        TextView view = actionText(c, label, action);
        view.setTextColor(selected ? background() : foreground());
        view.setSelected(selected); view.setMaxWidth(dp(c, 240));
        GradientDrawable face = new GradientDrawable(); face.setCornerRadius(dp(c, 8));
        face.setColor(selected ? foreground() : control());
        GradientDrawable mask = new GradientDrawable(); mask.setCornerRadius(dp(c, 8)); mask.setColor(0xffffffff);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(mix(foreground(), background(), 0.18f)),
                new InsetDrawable(face, 0, dp(c, 8), 0, dp(c, 8)),
                new InsetDrawable(mask, 0, dp(c, 8), 0, dp(c, 8))));
        return view;
    }
    /** Unfilled action: management must not look like a selectable group. */
    static TextView secondary(Context c, CharSequence label, Runnable action) {
        TextView view = actionText(c, label, action);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337f7f7f), null, null));
        return view;
    }
    private static TextView actionText(Context c, CharSequence label, Runnable action) {
        TextView view = new TextView(c) {
            @Override public CharSequence getAccessibilityClassName() { return android.widget.Button.class.getName(); }
        };
        view.setText(label); view.setTextSize(14); view.setTextColor(foreground());
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER); view.setIncludeFontPadding(false);
        view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        view.setMinHeight(dp(c, 48)); view.setMinWidth(dp(c, 48));
        view.setContentDescription(label); view.setFocusable(true); view.setClickable(true);
        view.setOnClickListener(v -> action.run()); return view;
    }
    static ImageButton overflow(Context c, CharSequence label) {
        ImageButton button = new ImageButton(c);
        button.setImageDrawable(new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas) {
                paint.setColor(foreground());
                float x = getBounds().exactCenterX(), y = getBounds().exactCenterY();
                for (int i = -1; i <= 1; i++) canvas.drawCircle(x, y + i * dp(c, 5), dp(c, 1) * 1.5f, paint);
            }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
            @Override public int getIntrinsicWidth() { return dp(c, 24); }
            @Override public int getIntrinsicHeight() { return dp(c, 24); }
        });
        GradientDrawable mask = new GradientDrawable(); mask.setShape(GradientDrawable.OVAL); mask.setColor(0xffffffff);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337f7f7f), null, mask));
        button.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        button.setContentDescription(label); button.setTooltipText(label);
        button.setMinimumHeight(dp(c, 48)); button.setMinimumWidth(dp(c, 48)); return button;
    }
    static LinearLayout vertical(Context c) { LinearLayout layout = new LinearLayout(c); layout.setOrientation(LinearLayout.VERTICAL); layout.setBackgroundColor(background()); return layout; }
    static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext(); if (next == context) break; context = next;
        }
        return context instanceof Activity ? (Activity) context : null;
    }
    static void channel(Context context, String id) {
        if (!GroupBackup.isChannelId(id)) return;
        launch(context, new Uri.Builder().scheme("https").authority("www.youtube.com").appendPath("channel").appendPath(id).build());
    }
    static void video(Context context, String id) {
        if (!id.matches("[A-Za-z0-9_-]{11}")) return;
        launch(context, new Uri.Builder().scheme("https").authority("www.youtube.com").appendPath("watch").appendQueryParameter("v", id).build());
    }
    private static void launch(Context context, Uri uri) {
        try {
            Activity activity = activity(context); if (activity == null) return;
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(context.getPackageName()));
        } catch (RuntimeException failure) { Utils.showToastLong(str("morphe_pockettube_channel_failed")); }
    }
    static String initials(String value) {
        String clean = value.trim(); if (clean.isEmpty()) return "•";
        return clean.substring(0, clean.offsetByCodePoints(0, 1));
    }
    private GroupUi() {}
}
