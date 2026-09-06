package com.diamon.civil.core.util;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

/**
 * Helper for managing CPU WakeLocks during intensive FEA calculations.
 * Uses PARTIAL_WAKE_LOCK with automatic safety timeouts to prevent CPU sleep
 * and avoid unexpected battery drain.
 */
public class WakeLockHelper {
    private static final String TAG = "WakeLockHelper";
    private static final long DEFAULT_TIMEOUT_MS = 15 * 60 * 1000L; // 15 minutes safety timeout

    private final Context context;
    private final String tag;
    private PowerManager.WakeLock wakeLock;

    public WakeLockHelper(Context context, String tag) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.tag = (tag != null && !tag.isEmpty()) ? tag : "FeaSolver";
    }

    /**
     * Acquires a PARTIAL_WAKE_LOCK with default safety timeout (15 min).
     */
    public synchronized void acquire() {
        acquire(DEFAULT_TIMEOUT_MS);
    }

    /**
     * Acquires a PARTIAL_WAKE_LOCK with specified safety timeout.
     */
    public synchronized void acquire(long timeoutMs) {
        try {
            if (context == null) return;
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CalculoEstructural:" + tag);
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(timeoutMs);
                Log.d(TAG, "WakeLock acquired for " + tag + " with timeout " + timeoutMs + "ms");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to acquire WakeLock: " + t.getMessage());
        }
    }

    /**
     * Releases the WakeLock if currently held.
     */
    public synchronized void release() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "WakeLock released for " + tag);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to release WakeLock: " + t.getMessage());
        }
    }

    public synchronized boolean isHeld() {
        return wakeLock != null && wakeLock.isHeld();
    }
}
