package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;

/**
 * The weather reading Home's header and the screensaver share: held in memory for the life of the process, refetched
 * on a worker thread when due (30 min after a success, 5 after a failure), the place from the IP once a day.
 */
final class WeatherStore {
    private static WeatherRules.Reading reading;
    private static WeatherRules.Place place;
    private static long attemptAt, placeAt;
    private static boolean ok, busy;

    private WeatherStore() {
    }

    static synchronized WeatherRules.Reading reading() {
        return reading;
    }

    static synchronized WeatherRules.Place place() {
        return place;
    }

    /**
     * Fetches when due and then runs {@code done} on the main thread; does nothing (and never calls {@code done}) when a
     * fetch is already running or not yet due. {@code lat}/{@code lon} are the fallback when the IP lookup fails.
     */
    static void refreshIfDue(Context ctx, boolean useIp, double lat, double lon, Runnable done) {
        final long now = System.currentTimeMillis();
        final boolean lookUp;
        final WeatherRules.Place known;
        synchronized (WeatherStore.class) {
            if (busy || !WeatherRules.due(attemptAt, ok, now)) return;
            busy = true;
            attemptAt = now;
            lookUp = useIp && (place == null || now - placeAt >= WeatherRules.LOCATION_MS);
            known = place;
        }
        new Thread(() -> {
            WeatherRules.Reading r = null;
            WeatherRules.Place p = known;
            try {
                if (lookUp) {
                    WeatherRules.Place found = WeatherRules.parseLocation(Net.getText(WeatherRules.IP_URL));
                    if (found != null) p = found;
                }
                double la = p != null ? p.lat : lat, lo = p != null ? p.lon : lon;
                if (WeatherRules.validCoordinates(la, lo)) r = WeatherRules.parse(Net.getText(WeatherRules.url(la, lo)));
            } catch (IOException | RuntimeException e) {
                Log.w("TvHomeWeather", "weather not updated: " + e);
            }
            synchronized (WeatherStore.class) {
                busy = false;
                ok = r != null;
                if (lookUp) placeAt = now;
                if (p != null) place = p;
                if (r != null) reading = r;
            }
            if (done != null) new Handler(Looper.getMainLooper()).post(done);
        }, "weather").start();
    }
}
