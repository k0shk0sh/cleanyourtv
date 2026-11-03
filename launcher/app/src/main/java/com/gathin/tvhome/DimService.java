package com.gathin.tvhome;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Dims the whole screen, over every app, Home and the system's own menus, with a black layer that never takes
 * focus or keys (Dim screen, under the Settings pill's menu). An accessibility overlay, because only that window
 * type sits above everything and is kept alive by the system; it asks for no events and filters no keys, so held
 * volume keeps ramping. The first level picked adds the service to the enabled list (WRITE_SECURE_SETTINGS, pm grant);
 * it stays there, idle with no window, while Off, so Off can fade out like any other change. The level is the
 * share of light taken away, 0 to MAX; on an OLED that is the same share less light in the room.
 */
public class DimService extends AccessibilityService {
    static final int[] LEVELS = {0, 30, 50, 70, 80, 90};
    private static final int MAX = 90;
    private static final long FADE_MS = 1200;
    private static final String PREFS = "dim";
    private static final String LEVEL = "level";

    private View layer;
    private SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (p, key) -> {
        if (LEVEL.equals(key)) show(level(this));
    };

    static int level(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(LEVEL, 0);
    }

    /** Saves the level and makes sure the service runs to show it; false when the setting can't be written. */
    static boolean set(Context context, int level) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return false;
        level = Math.max(0, Math.min(MAX, level));
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(LEVEL, level).commit();
        try {
            ContentResolver cr = context.getContentResolver();
            String self = new ComponentName(context, DimService.class).flattenToShortString();
            String list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            List<String> enabled = new ArrayList<>();
            if (list != null && !list.equals("null")) {
                for (String entry : list.split(":")) {
                    if (entry.isEmpty()) continue;
                    ComponentName name = ComponentName.unflattenFromString(entry);
                    if (self.equals(name == null ? null : name.flattenToShortString())) return true;
                    enabled.add(entry);
                }
            }
            if (level == 0) return true;
            enabled.add(self);
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, String.join(":", enabled));
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(listener);
        show(level(this));
    }

    private void show(int level) {
        WindowManager wm = getSystemService(WindowManager.class);
        // Opaque black with the view's alpha as the level, so a change fades instead of jumping in a dark room.
        float alpha = Math.min(level, MAX) / 100f;
        if (layer != null) {
            View old = layer;
            old.animate().cancel();
            old.animate().alpha(alpha).setDuration(FADE_MS).withEndAction(() -> {
                if (alpha == 0f && old.isAttachedToWindow()) wm.removeView(old);
            }).start();
            if (level <= 0) layer = null;
            return;
        }
        if (level <= 0) return;
        layer = new View(this);
        layer.setBackgroundColor(Color.BLACK);
        layer.setAlpha(0f);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        wm.addView(layer, lp);
        layer.animate().alpha(alpha).setDuration(FADE_MS).start();
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(listener);
        if (layer != null && layer.isAttachedToWindow()) getSystemService(WindowManager.class).removeView(layer);
        layer = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
