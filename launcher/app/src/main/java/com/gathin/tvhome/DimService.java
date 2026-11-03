package com.gathin.tvhome;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dims the whole screen, over every app, Home and the system's own menus, with a black layer that never takes
 * focus or keys (Dim screen, under the Settings pill's menu). An accessibility overlay, because only that window
 * type sits above everything and is kept alive by the system; it asks for no events and filters no keys, so held
 * volume keeps ramping. The first level picked adds the service to the enabled list (WRITE_SECURE_SETTINGS, pm grant);
 * it stays there, idle with no window, while Off, so Off can fade out like any other change. The level is the
 * share of light taken away, 0 to MAX; on an OLED that is the same share less light in the room. With a night
 * schedule (DimSchedule) the service follows the clock: it re-checks at each switch time, when the screen turns on
 * (timers stop while the TV is in standby) and when the clock or time zone changes.
 */
public class DimService extends AccessibilityService {
    static final int[] LEVELS = {0, 30, 50, 70, 80, 90};
    private static final int MAX = 90;
    private static final long FADE_MS = 1200;
    private static final String PREFS = "dim";
    private static final String LEVEL = "level";
    /** DimSchedule's period the level was picked in; the pick holds until the next switch time. */
    private static final String PICKED_IN = "picked_in";

    private View layer;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::refresh;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (p, key) -> {
        if (LEVEL.equals(key) || PICKED_IN.equals(key)) refresh();
    };
    private final BroadcastReceiver clock = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    static DimSchedule schedule(Context context) {
        Resources res = context.getResources();
        return new DimSchedule(res.getInteger(R.integer.dim_night_start_minutes),
                res.getInteger(R.integer.dim_night_end_minutes),
                Math.max(0, Math.min(MAX, res.getInteger(R.integer.dim_night_level))));
    }

    /** The level showing now: the menu pick, or the schedule's once the pick's period has passed. */
    static int level(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        return schedule(context).level(LocalDateTime.now(), p.getInt(LEVEL, 0), p.getString(PICKED_IN, null));
    }

    /** Saves a menu pick and makes sure the service runs to show it; false when the setting can't be written. */
    static boolean set(Context context, int level) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return false;
        level = Math.max(0, Math.min(MAX, level));
        DimSchedule s = schedule(context);
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(LEVEL, level)
                .putString(PICKED_IN, s.on() ? s.periodStart(LocalDateTime.now()).toString() : null).commit();
        return level == 0 || enable(context);
    }

    /** Home calls this when a night schedule is set, so the service runs before anyone opens the menu. */
    static void keepScheduled(Context context) {
        if (schedule(context).on() && context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED) enable(context);
    }

    private static boolean enable(Context context) {
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
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clock, filter);
        refresh();
    }

    /** Shows the level for now and wakes again at the next switch time. */
    private void refresh() {
        show(level(this));
        handler.removeCallbacks(refresh);
        DimSchedule s = schedule(this);
        if (!s.on()) return;
        LocalDateTime now = LocalDateTime.now();
        handler.postDelayed(refresh, Duration.between(now, s.nextSwitch(now)).toMillis() + 1000);
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
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(listener);
            unregisterReceiver(clock);
        }
        handler.removeCallbacks(refresh);
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
