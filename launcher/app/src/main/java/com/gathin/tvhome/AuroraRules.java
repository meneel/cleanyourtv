package com.gathin.tvhome;

/** The Aurora screensaver's colours by time of day. No Android types, so it is unit-tested. */
final class AuroraRules {
    /** Three deep, saturated colours per mood; the fields are drawn translucent on near-black, so they glow. */
    private static final int[][] NIGHT = {{0xFF3B2BD9, 0xFF0FA3A3, 0xFF8E2BD6}};   // indigo, teal, violet
    private static final int[][] MORNING = {{0xFFFF8A3D, 0xFFE0457B, 0xFF3D7CFF}};  // amber, rose, blue
    private static final int[][] DAY = {{0xFF1E88E5, 0xFF00B3A4, 0xFF7C4DFF}};      // blue, aqua, violet
    private static final int[][] EVENING = {{0xFFE0452B, 0xFFB5308F, 0xFF3A55E0}};  // ember, magenta, indigo

    private AuroraRules() {
    }

    /** The three field colours for an hour of the day (0 to 23). */
    static int[] palette(int hour) {
        if (hour >= 5 && hour < 9) return MORNING[0];
        if (hour >= 9 && hour < 17) return DAY[0];
        if (hour >= 17 && hour < 21) return EVENING[0];
        return NIGHT[0];
    }

    /** How bright the fields are: dimmer at night, so a dark room is not lit up. 0.35 to 1. */
    static float intensity(int hour) {
        if (hour >= 23 || hour < 5) return 0.35f;
        if (hour >= 21 || hour < 7) return 0.6f;
        return 1f;
    }

    /** A number in [lo, hi] from a unit random value; keeps drift targets inside the screen. */
    static float between(float lo, float hi, float unit) {
        return lo + (hi - lo) * Math.max(0f, Math.min(1f, unit));
    }
}
