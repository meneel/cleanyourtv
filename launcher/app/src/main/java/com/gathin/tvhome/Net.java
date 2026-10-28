package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The one bounded https GET for text replies (weather, IP lookup, recommendations): https only, a fixed list of
 * hosts, no cookies, 5 s timeouts, 256 KB cap, a 200 or nothing. Posters go through PosterCache.
 */
final class Net {
    static final Set<String> HOSTS = new HashSet<>(Arrays.asList("api.open-meteo.com", "ipwho.is", "api.themoviedb.org"));
    private static final int MS = 5000, MAX_BYTES = 256 * 1024;

    private Net() {
    }

    static boolean allowed(String url) {
        try {
            URL u = new URL(url);
            return "https".equals(u.getProtocol()) && u.getUserInfo() == null && HOSTS.contains(u.getHost().toLowerCase(java.util.Locale.ROOT));
        } catch (java.net.MalformedURLException | RuntimeException e) {
            return false;
        }
    }

    /** The reply body, or null for a refused address, a non-200, or a body over the cap. Call off the main thread. */
    static String getText(String url) throws IOException {
        if (!allowed(url)) return null;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(MS);
            conn.setReadTimeout(MS);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(false);   // a redirect could leave the allowed hosts
            conn.setRequestProperty("User-Agent", "tvhome");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (out.size() + n > MAX_BYTES) return null;
                    out.write(buf, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }
}
