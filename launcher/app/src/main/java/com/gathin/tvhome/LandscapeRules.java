package com.gathin.tvhome;

/** The generated landscape's colours by hour. No Android types, so it is unit-tested. */
final class LandscapeRules {
    static final int DAWN = 0, DAY = 1, DUSK = 2, NIGHT = 3;

    private LandscapeRules() {
    }

    static int mood(int hour) {
        if (hour >= 5 && hour < 8) return DAWN;
        if (hour >= 8 && hour < 17) return DAY;
        if (hour >= 17 && hour < 20) return DUSK;
        return NIGHT;
    }

    /** Sky from the top down: top, middle, horizon. */
    static int[] sky(int mood) {
        switch (mood) {
            case DAWN: return new int[]{0xFF2A3A6B, 0xFFC9778A, 0xFFF7C58F};
            case DAY: return new int[]{0xFF1F5FA8, 0xFF6FB1E6, 0xFFCDE7F6};
            case DUSK: return new int[]{0xFF2E2563, 0xFFBF4A6E, 0xFFF39A58};
            default: return new int[]{0xFF050818, 0xFF101B44, 0xFF27386B};
        }
    }

    /** Four mountain ridges, far to near: each darker and closer to black than the one behind. */
    static int[] ridges(int mood) {
        switch (mood) {
            case DAWN: return new int[]{0xFF8E6F9A, 0xFF5E4A7A, 0xFF392E55, 0xFF1A1630};
            case DAY: return new int[]{0xFF7FA6CC, 0xFF4E7AA6, 0xFF2D4E74, 0xFF142A44};
            case DUSK: return new int[]{0xFF9A4D78, 0xFF63335F, 0xFF3A2145, 0xFF160E26};
            default: return new int[]{0xFF2A3A6B, 0xFF1A2650, 0xFF0F1736, 0xFF070B1C};
        }
    }

    /** The sun or moon: its colour, and whether it is the moon (with stars in the sky). */
    static int bodyColor(int mood) {
        switch (mood) {
            case DAWN: return 0xFFFFE2B0;
            case DAY: return 0xFFFFF6D8;
            case DUSK: return 0xFFFFC98A;
            default: return 0xFFEAF0FF;
        }
    }

    static boolean stars(int mood) {
        return mood == NIGHT;
    }

    /** A ridge's height line at {@code x} (0 to 1): a sum of three sines, smooth and never flat; {@code seed} moves them. */
    static float ridge(float x, int layer, long seed) {
        double p1 = (seed * 0.37 + layer * 1.7) % 6.28318, p2 = (seed * 0.61 + layer * 2.9) % 6.28318, p3 = (seed * 0.83 + layer * 4.1) % 6.28318;
        double v = 0.55 * Math.sin(x * (2.2 + layer * 0.5) * 3.14159 + p1) + 0.30 * Math.sin(x * (5.1 + layer) * 3.14159 + p2) + 0.15 * Math.sin(x * (11.3 + layer * 2) * 3.14159 + p3);
        return (float) ((v + 1.0) / 2.0);   // 0..1
    }
}
