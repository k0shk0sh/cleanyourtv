package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

/**
 * Brings back the volume ramp on firmware that drops key auto-repeat while a key-filtering service is bound
 * (docs/devices/tcl-55p71b-g09-key-repeat.md). The first press still goes to the system untouched; while the
 * button stays down, this steps the volume itself until it comes up. If real repeats do arrive, it stands back.
 * Never consumes a key. config.xml volume_ramp.
 */
final class VolumeRamp {
    /** How long a press must be held before the ramp starts, like the platform's key repeat. */
    private static final long START_DELAY_MS = 400;
    private static final long STEP_MS = 90;
    /** A lost key-up must not run the volume to its end: stop after this many steps. */
    private static final int MAX_STEPS = 40;

    private final AudioManager audio;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int direction;
    private int steps;

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (direction == 0 || steps++ >= MAX_STEPS) {
                stop();
                return;
            }
            audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE,
                    AudioManager.FLAG_SHOW_UI);
            handler.postDelayed(this, STEP_MS);
        }
    };

    VolumeRamp(Context ctx) {
        audio = ctx.getSystemService(AudioManager.class);
    }

    /** Watches every key the helper sees; always returns false so the key goes on as before. */
    boolean onKey(KeyEvent event) {
        int code = event.getKeyCode();
        int dir = code == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE
                : code == KeyEvent.KEYCODE_VOLUME_DOWN ? AudioManager.ADJUST_LOWER : 0;
        if (dir == 0) {
            if (direction != 0 && event.getAction() == KeyEvent.ACTION_DOWN) stop();
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                stop();
                direction = dir;
                handler.postDelayed(step, START_DELAY_MS);
            } else {
                stop();   // the platform's own repeat got through: it ramps, not us
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            stop();
        }
        return false;
    }

    void stop() {
        handler.removeCallbacks(step);
        direction = 0;
        steps = 0;
    }
}
