package com.gathin.tvhome;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * Keeps Android's "turn off while watching" timeout (secure attentive_timeout) at keep_attentive_timeout_ms.
 * Philips TPTvService resets it to -1 (never) at every boot unless it is 4, 6 or 8 hours, and these TVs reboot
 * themselves in standby every night or two, so a shorter value set over adb is gone by the next evening. Home
 * puts the value back when it starts and whenever the setting goes back to -1 while Home's process lives. Any
 * other value someone picks in the TV's menu is left alone. Needs WRITE_SECURE_SETTINGS (pm grant); 0 is off.
 */
final class AttentiveTimeout {
    private static final String KEY = "attentive_timeout";
    private static ContentObserver observer;

    private AttentiveTimeout() {
    }

    static void keep(Context context) {
        int ms = context.getResources().getInteger(R.integer.keep_attentive_timeout_ms);
        if (ms <= 0 || observer != null) return;
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return;
        ContentResolver cr = context.getApplicationContext().getContentResolver();
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                restore(cr, ms);
            }
        };
        cr.registerContentObserver(Settings.Secure.getUriFor(KEY), false, observer);
        restore(cr, ms);
    }

    private static void restore(ContentResolver cr, int ms) {
        try {
            if (Settings.Secure.getInt(cr, KEY, -1) == -1) Settings.Secure.putInt(cr, KEY, ms);
        } catch (RuntimeException ignored) {
            // The setting is the system's; a refusal leaves the TV's own value.
        }
    }
}
