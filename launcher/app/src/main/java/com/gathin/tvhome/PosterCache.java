package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Poster pictures for the Continue-watching row. This is the ONLY class that opens a network connection
 * (OnlyPosterCacheOpensConnectionsTest), and the launcher holds INTERNET for nothing else.
 *
 * Rules (docs: specs/2026-10-28-continue-watching-design.md): https only, or content:// through ContentResolver;
 * at most 2 MB; 5 s timeouts; only images; decoded down-sampled to about the tile size in RGB_565; no cookies,
 * no account, a neutral User-Agent; a disk cache of at most 24 files and 24 MB, oldest evicted. One worker
 * thread. Every failure ends as a null bitmap; nothing is thrown to the caller.
 */
final class PosterCache {
    interface Callback {
        /** Called on the main thread; null when the poster could not be had. */
        void done(Bitmap bitmap);
    }

    private static final String TAG = "TvHomePoster";
    private static final int CONNECT_MS = 5000, READ_MS = 5000, MAX_BYTES = 2 * 1024 * 1024;
    private static final int CACHE_FILES = 48;
    private static final long CACHE_BYTES = 64L * 1024 * 1024;
    private static final long MAX_PIXELS = 40L * 1000 * 1000;
    /** What one poster may decode to (RGB_565, 2 bytes a pixel: 4 MB). */
    private static final long MAX_DECODED_PIXELS = 2L * 1000 * 1000;
    /** How an image is fetched and decoded: the size it may be, the pixels it may decode to, and in what colour depth. */
    private static final class Profile {
        final int maxBytes;
        final long maxPixels;
        final Bitmap.Config config;
        final boolean mutable;

        Profile(int maxBytes, long maxPixels, Bitmap.Config config, boolean mutable) {
            this.maxBytes = maxBytes;
            this.maxPixels = maxPixels;
            this.config = config;
            this.mutable = mutable;
        }
    }

    /** Posters and cards: small, 16-bit colour. */
    private static final Profile SMALL = new Profile(MAX_BYTES, MAX_DECODED_PIXELS, Bitmap.Config.RGB_565, false);
    /** Featured backdrops: up to 8 MB on the wire, a full 1080p picture in 32-bit colour, mutable so edges can be drawn in place. */
    private static final Profile LARGE = new Profile(8 * 1024 * 1024, 2_400_000L, Bitmap.Config.ARGB_8888, true);
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private PosterCache() {
    }

    static void load(Context ctx, String uri, int width, int height, Callback cb) {
        load(ctx, uri, width, height, SMALL, cb);
    }

    /** A full-screen picture (the featured backdrops): 32-bit colour at up to 1080p, never downsampled below the screen. */
    static void loadLarge(Context ctx, String uri, int width, int height, Callback cb) {
        load(ctx, uri, width, height, LARGE, cb);
    }

    private static void load(Context ctx, String uri, int width, int height, Profile profile, Callback cb) {
        final int kind = PosterRules.kind(uri);
        if (kind == PosterRules.NONE) {
            cb.done(null);
            return;
        }
        final Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = fetch(app, uri, kind, width, height, profile);
            } catch (Exception | OutOfMemoryError e) {
                Log.w(TAG, "a poster could not be loaded: " + e);
            }
            final Bitmap result = bitmap;
            UI.post(() -> cb.done(result));
        });
    }

    private static Bitmap fetch(Context ctx, String uri, int kind, int width, int height, Profile profile) throws IOException {
        byte[] data;
        if (kind == PosterRules.CONTENT) {
            try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(uri))) {
                data = readBounded(in, profile.maxBytes);
            }
        } else {
            File dir = new File(ctx.getCacheDir(), "posters");
            File file = new File(dir, PosterRules.key(uri));
            if (file.isFile()) {
                file.setLastModified(System.currentTimeMillis());
                try (InputStream in = new FileInputStream(file)) {
                    data = readBounded(in, profile.maxBytes);
                }
            } else {
                data = download(uri, profile.maxBytes);
                if (data != null) store(dir, file, data);
            }
        }
        return data == null ? null : decode(data, width, height, profile);
    }

    /** The bytes of an https address, or null: not a 200, not an image, too big, or any network trouble. */
    private static byte[] download(String uri, int maxBytes) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(uri).openConnection();
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(true);   // the platform never follows a redirect to another scheme
            conn.setRequestProperty("User-Agent", "tvhome");
            conn.setRequestProperty("Accept", "image/*");
            if (conn.getResponseCode() != 200) return null;
            String type = conn.getContentType();
            if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/")) return null;
            if (conn.getContentLengthLong() > maxBytes) return null;
            try (InputStream in = conn.getInputStream()) {
                return readBounded(in, maxBytes);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** All of the stream, or null when it is empty-handed or longer than the poster limit. */
    private static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        if (in == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > maxBytes) return null;
            out.write(buf, 0, n);
        }
        return out.size() == 0 ? null : out.toByteArray();
    }

    private static void store(File dir, File file, byte[] data) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File tmp = new File(dir, file.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
            }
            if (!tmp.renameTo(file)) tmp.delete();
            prune(dir);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "a poster could not be cached: " + e);
        }
    }

    private static void prune(File dir) {
        File[] all = dir.listFiles();
        if (all == null) return;
        List<PosterRules.Cached> cached = new ArrayList<>();
        for (File f : all) {
            if (f.isFile() && !f.getName().endsWith(".tmp")) {
                cached.add(new PosterRules.Cached(f.getName(), f.length(), f.lastModified()));
            }
        }
        for (String name : PosterRules.evict(cached, CACHE_FILES, CACHE_BYTES)) new File(dir, name).delete();
    }

    private static Bitmap decode(byte[] data, int width, int height, Profile profile) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0 || (long) o.outWidth * o.outHeight > MAX_PIXELS) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = PosterRules.sampleSize(o.outWidth, o.outHeight, width, height, profile.maxPixels);
        o.inPreferredConfig = profile.config;
        o.inMutable = profile.mutable;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }
}
