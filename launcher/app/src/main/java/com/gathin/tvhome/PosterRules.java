package com.gathin.tvhome;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Which poster addresses may be fetched, and the cache's key and eviction maths. No Android and no I/O, so a
 * host test pins it. Poster addresses come from other apps' data, so they are untrusted: plain character loops,
 * no regular expressions.
 */
final class PosterRules {
    static final int NONE = 0, NETWORK = 1, CONTENT = 2;
    static final int MAX_URI = 2048;

    private PosterRules() {
    }

    /** NETWORK for an https address with a host and no user info, CONTENT for a content:// address, else NONE. */
    static int kind(String uri) {
        if (uri == null || uri.isEmpty() || uri.length() > MAX_URI) return NONE;
        for (int i = 0; i < uri.length(); i++) {
            if (uri.charAt(i) <= ' ') return NONE;
        }
        String lower = uri.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) {
            String rest = uri.substring(8);
            int end = rest.length();
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '/' || c == '?' || c == '#') {
                    end = i;
                    break;
                }
            }
            String authority = rest.substring(0, end);
            if (authority.isEmpty() || authority.indexOf('@') >= 0) return NONE;
            return NETWORK;
        }
        if (lower.startsWith("content://")) return uri.length() > "content://".length() ? CONTENT : NONE;
        return NONE;
    }

    /** SHA-256 of the address as 64 lower-case hex characters: the cache file's name. */
    /**
     * The BitmapFactory sample size: halve while the picture stays at least tile size on both sides, then keep
     * halving until it decodes to at most {@code maxPixels}, so one extreme-aspect picture cannot take the memory.
     */
    static int sampleSize(int w, int h, int tileW, int tileH, long maxPixels) {
        int s = 1;
        while (tileW > 0 && tileH > 0 && w / (s * 2) >= tileW && h / (s * 2) >= tileH) s *= 2;
        while ((long) (w / s) * (h / s) > maxPixels) s *= 2;
        return s;
    }

    static String key(String uri) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(uri.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One file in the poster cache. */
    static final class Cached {
        final String name;
        final long size, modified;

        Cached(String name, long size, long modified) {
            this.name = name;
            this.size = size;
            this.modified = modified;
        }
    }

    /** Files not touched for {@code maxAgeMs} (reads touch a poster), and leftover ".tmp" downloads older than an hour. */
    static List<String> stale(List<Cached> files, long now, long maxAgeMs) {
        List<String> drop = new ArrayList<>();
        for (Cached c : files) {
            long age = now - c.modified;
            if (age > maxAgeMs || (c.name.endsWith(".tmp") && age > 60 * 60 * 1000L)) drop.add(c.name);
        }
        return drop;
    }

    /** The names to delete, oldest first, until at most {@code maxFiles} files and {@code maxBytes} bytes remain. */
    static List<String> evict(List<Cached> files, int maxFiles, long maxBytes) {
        List<Cached> sorted = new ArrayList<>(files);
        Collections.sort(sorted, (a, b) -> Long.compare(a.modified, b.modified));
        long total = 0;
        for (Cached c : sorted) total += c.size;
        int count = sorted.size();
        List<String> drop = new ArrayList<>();
        for (Cached c : sorted) {
            if (count <= maxFiles && total <= maxBytes) break;
            drop.add(c.name);
            count--;
            total -= c.size;
        }
        return drop;
    }
}
