package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Deletes what Home no longer uses from its own cache folders (downloaded posters and anything else under the app's
 * cache directories) once every two days: files not touched for a week, and half-finished downloads. A poster that is
 * still shown is touched on every read, so it stays. Other apps' caches are not reachable from an app; tools/trim-caches.sh
 * does those over adb. Runs off the main thread, at most once per {@link #EVERY_MS}.
 */
final class CacheSweep {
    private static final String TAG = "TvHomeSweep";
    private static final String PREFS = "cache_sweep", LAST = "last";
    static final long EVERY_MS = 2 * 24 * 60 * 60 * 1000L, MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L;

    private CacheSweep() {
    }

    static void maybeRun(Context ctx) {
        final Context app = ctx.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(LAST, 0);
        if (last != 0 && now - last >= 0 && now - last < EVERY_MS) return;
        prefs.edit().putLong(LAST, now).apply();
        new Thread(() -> {
            long freed = 0;
            try {
                freed += sweep(app.getCacheDir(), now);
                File external = app.getExternalCacheDir();
                if (external != null) freed += sweep(external, now);
            } catch (RuntimeException e) {
                Log.w(TAG, "cache sweep failed: " + e);
            }
            Log.i(TAG, "cache sweep freed " + freed / 1024 + " KB");
        }, "cache-sweep").start();
    }

    /** Stale files under dir, then folders left empty (but not dir itself); returns the bytes freed. */
    private static long sweep(File dir, long now) {
        File[] all = dir.listFiles();
        if (all == null) return 0;
        long freed = 0;
        List<PosterRules.Cached> files = new ArrayList<>();
        for (File f : all) {
            if (f.isDirectory()) {
                freed += sweep(f, now);
                String[] left = f.list();
                if (left != null && left.length == 0) f.delete();
            } else {
                files.add(new PosterRules.Cached(f.getName(), f.length(), f.lastModified()));
            }
        }
        for (String name : PosterRules.stale(files, now, MAX_AGE_MS)) {
            File f = new File(dir, name);
            long size = f.length();
            if (f.delete()) freed += size;
        }
        return freed;
    }
}
