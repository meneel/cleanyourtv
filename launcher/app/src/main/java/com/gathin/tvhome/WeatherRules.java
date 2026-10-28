package com.gathin.tvhome;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Weather and IP-location rules with no Android types: URLs, parsing the two JSON replies, labels, when to refetch. */
final class WeatherRules {
    static final long OK_MS = 30L * 60 * 1000, RETRY_MS = 5L * 60 * 1000, LOCATION_MS = 24L * 60 * 60 * 1000;
    static final String IP_URL = "https://ipwho.is/?fields=success,latitude,longitude,city";

    private static final Pattern CURRENT = Pattern.compile("\"current\"\\s*:\\s*\\{");
    private static final Pattern SUCCESS = Pattern.compile("\"success\"\\s*:\\s*true");
    private static final Pattern TEMP = Pattern.compile("\"temperature_2m\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern CODE = Pattern.compile("\"weather_code\"\\s*:\\s*(\\d+)");
    private static final Pattern LAT = Pattern.compile("\"latitude\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LON = Pattern.compile("\"longitude\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern CITY = Pattern.compile("\"city\"\\s*:\\s*\"([^\"]{1,40})\"");

    private WeatherRules() {
    }

    static final class Reading {
        final int tempC, code;

        Reading(int tempC, int code) {
            this.tempC = tempC;
            this.code = code;
        }
    }

    static final class Place {
        final double lat, lon;
        final String city;

        Place(double lat, double lon, String city) {
            this.lat = lat;
            this.lon = lon;
            this.city = city;
        }
    }

    static boolean validCoordinates(double lat, double lon) {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 && !(lat == 0 && lon == 0);
    }

    static String url(double lat, double lon) {
        return String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code&timezone=auto",
                lat, lon);
    }

    /** The "current" block's temperature and WMO code, or null for anything else. */
    static Reading parse(String json) {
        if (json == null) return null;
        Matcher cur = CURRENT.matcher(json);
        if (!cur.find()) return null;
        int at = cur.start();
        String block = json.substring(at);
        int end = block.indexOf('}');
        if (end > 0) block = block.substring(0, end);
        Matcher t = TEMP.matcher(block), c = CODE.matcher(block);
        if (!t.find() || !c.find()) return null;
        double temp = Double.parseDouble(t.group(1));
        if (temp < -90 || temp > 70) return null;
        return new Reading((int) Math.round(temp), Integer.parseInt(c.group(1)));
    }

    /** The IP lookup's reply, or null when it did not succeed or the point is not a real place. */
    static Place parseLocation(String json) {
        if (json == null || !SUCCESS.matcher(json).find()) return null;
        Matcher la = LAT.matcher(json), lo = LON.matcher(json), ci = CITY.matcher(json);
        if (!la.find() || !lo.find()) return null;
        double lat = Double.parseDouble(la.group(1)), lon = Double.parseDouble(lo.group(1));
        if (!validCoordinates(lat, lon)) return null;
        return new Place(lat, lon, ci.find() ? ci.group(1) : "");
    }

    static String label(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "Rain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Snow";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "";
    }

    /** "Kolkata 31° · Partly cloudy" (the view upper-cases it). */
    static String line(String city, Reading r) {
        StringBuilder sb = new StringBuilder();
        if (city != null && !city.isEmpty()) sb.append(city).append("  ");
        sb.append(r.tempC).append('°');
        String label = label(r.code);
        if (!label.isEmpty()) sb.append(" · ").append(label);
        return sb.toString();
    }

    /** True when the last attempt is old enough to try again (sooner after a failure). */
    static boolean due(long lastAttemptMs, boolean lastOk, long nowMs) {
        return lastAttemptMs == 0 || nowMs - lastAttemptMs >= (lastOk ? OK_MS : RETRY_MS);
    }
}
