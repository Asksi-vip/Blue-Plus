package com.blue.plus.player.watchdog;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class LiveWatchdog {
    private static final long TIMEOUT_MS = 5000; // 5 seconds freeze tolerance
    private final Handler handler;
    private final Runnable timeoutRunnable;
    private WatchdogListener listener;
    private boolean isWatching = false;

    public interface WatchdogListener {
        void onStreamFrozen();
    }

    public LiveWatchdog() {
        handler = new Handler(Looper.getMainLooper());
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (isWatching && listener != null) {
                    Log.w("LiveWatchdog", "Stream frozen! Triggering reconnect...");
                    listener.onStreamFrozen();
                }
            }
        };
    }

    public void setListener(WatchdogListener listener) {
        this.listener = listener;
    }

    public void startWatching() {
        isWatching = true;
        reset();
    }

    public void stopWatching() {
        isWatching = false;
        handler.removeCallbacks(timeoutRunnable);
    }

    public void reset() {
        if (!isWatching) return;
        handler.removeCallbacks(timeoutRunnable);
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    }
}
