package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeatherRulesTest {
    private static final String OPEN_METEO = "{\"latitude\":22.5,\"longitude\":88.25,\"current_units\":{\"time\":\"iso8601\","
            + "\"temperature_2m\":\"°C\",\"weather_code\":\"wmo code\"},\"current\":{\"time\":\"2026-10-28T16:00\","
            + "\"interval\":900,\"temperature_2m\":31.6,\"weather_code\":2}}";

    @Test
    public void parsesTheCurrentBlockNotTheUnitsBlock() {
        WeatherRules.Reading r = WeatherRules.parse(OPEN_METEO);
        assertNotNull(r);
        assertEquals(32, r.tempC);   // 31.6 rounds, and the "°C" unit string is not mistaken for a number
        assertEquals(2, r.code);
        assertEquals("Kolkata  \u26C5 32\u00B0 \u00B7 Partly cloudy", WeatherRules.line("Kolkata", r, false));
    }

    @Test
    public void refusesWhatIsNotAReading() {
        assertNull(WeatherRules.parse(null));
        assertNull(WeatherRules.parse("{\"error\":true,\"reason\":\"nope\"}"));
        assertNull(WeatherRules.parse("{\"current\":{\"temperature_2m\":999.0,\"weather_code\":1}}"));
        assertNull(WeatherRules.parse("{\"current\":{\"temperature_2m\":20.0}}"));
    }

    @Test
    public void ipLookupNeedsSuccessAndARealPlace() {
        WeatherRules.Place p = WeatherRules.parseLocation("{\"success\":true,\"latitude\":22.5726,\"longitude\":88.3639,\"city\":\"Kolkata\"}");
        assertNotNull(p);
        assertEquals("Kolkata", p.city);
        assertEquals(22.5726, p.lat, 1e-6);
        assertNull(WeatherRules.parseLocation("{\"success\":false,\"message\":\"Invalid IP address\"}"));
        assertNull(WeatherRules.parseLocation("{\"success\":true,\"latitude\":0,\"longitude\":0,\"city\":\"\"}"));
        assertNull(WeatherRules.parseLocation("{\"success\":true,\"latitude\":123.0,\"longitude\":10.0}"));
    }

    @Test
    public void ipLookupParsesTheRealPrettyPrintedReply() {
        // Captured from https://ipwho.is/?fields=success,latitude,longitude,city: indented, a space after each colon.
        String real = "{\n    \"success\": true,\n    \"city\": \"Kolkata\",\n    \"latitude\": 22.5626267,\n    \"longitude\": 88.3630447\n}";
        WeatherRules.Place p = WeatherRules.parseLocation(real);
        assertNotNull(p);
        assertEquals("Kolkata", p.city);
        assertEquals(88.3630447, p.lon, 1e-6);
        WeatherRules.Reading r = WeatherRules.parse("{\"current\": {\"temperature_2m\": 26.4, \"weather_code\": 3}}");
        assertNotNull(r);
        assertEquals(26, r.tempC);
    }

    @Test
    public void urlHasTwoDecimalsAndNoLocaleComma() {
        assertTrue(WeatherRules.url(22.5726, 88.3639).contains("latitude=22.57&longitude=88.36&"));
        assertTrue(Net.allowed(WeatherRules.url(22.5726, 88.3639)));
        assertTrue(Net.allowed(WeatherRules.IP_URL));
    }

    @Test
    public void refetchTimingIsSoonerAfterAFailure() {
        long now = 10_000_000L;
        assertTrue(WeatherRules.due(0, true, now));
        assertFalse(WeatherRules.due(now - 60_000, true, now));
        assertFalse(WeatherRules.due(now - 60_000, false, now));
        assertTrue(WeatherRules.due(now - WeatherRules.RETRY_MS, false, now));
        assertFalse(WeatherRules.due(now - WeatherRules.RETRY_MS, true, now));
        assertTrue(WeatherRules.due(now - WeatherRules.OK_MS, true, now));
    }

    @Test
    public void netRefusesAnythingButItsHostsOverHttps() {
        assertFalse(Net.allowed("http://api.open-meteo.com/v1/forecast"));
        assertFalse(Net.allowed("https://api.open-meteo.com.evil.example/v1"));
        assertFalse(Net.allowed("https://user:pw@api.open-meteo.com/v1"));
        assertFalse(Net.allowed("https://example.com/"));
        assertFalse(Net.allowed("file:///etc/passwd"));
        assertFalse(Net.allowed("not a url"));
        assertTrue(Net.allowed("https://api.themoviedb.org/3/trending/movie/week"));
    }

    @Test
    public void emojiFollowsTheCodeAndTheTimeOfDay() {
        assertEquals("\u2600\uFE0F", WeatherRules.emoji(0, true));
        assertEquals("\uD83C\uDF19", WeatherRules.emoji(0, false));       // clear night: moon
        assertEquals("\u26C5", WeatherRules.emoji(2, true));
        assertEquals("\u2601\uFE0F", WeatherRules.emoji(2, false));        // no sun behind a night cloud
        assertEquals("\uD83C\uDF27\uFE0F", WeatherRules.emoji(63, true));
        assertEquals("\u26C8\uFE0F", WeatherRules.emoji(95, false));
        assertEquals("", WeatherRules.emoji(1234, true));                   // an unknown code shows no emoji, not a wrong one
    }

    @Test
    public void parsesIsDayAndDefaultsToDay() {
        assertFalse(WeatherRules.parse("{\"current\":{\"temperature_2m\":20.0,\"weather_code\":0,\"is_day\":0}}").day);
        assertTrue(WeatherRules.parse("{\"current\":{\"temperature_2m\":20.0,\"weather_code\":0,\"is_day\":1}}").day);
        assertTrue(WeatherRules.parse(OPEN_METEO).day);
        assertTrue(WeatherRules.url(22.5, 88.3).contains("is_day"));
    }

    @Test
    public void fahrenheitIsConvertedAndRounded() {
        WeatherRules.Reading r = new WeatherRules.Reading(26, 3, true);
        assertEquals("Kolkata  \u2601\uFE0F 26\u00B0 \u00B7 Overcast", WeatherRules.line("Kolkata", r, false));
        assertEquals("Kolkata  \u2601\uFE0F 79\u00B0 \u00B7 Overcast", WeatherRules.line("Kolkata", r, true));
        assertEquals("\u2744\uFE0F 14\u00B0 \u00B7 Snow", WeatherRules.line("", new WeatherRules.Reading(-10, 71, true), true));
    }
}
