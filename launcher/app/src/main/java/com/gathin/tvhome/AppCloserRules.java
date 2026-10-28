package com.gathin.tvhome;

/** Which apps the launcher leaves running when Home comes back: music apps and the few things Home itself depends on. */
final class AppCloserRules {
    private AppCloserRules() {
    }

    /** True for a package that must not be closed. */
    static boolean keep(String pkg, String own) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(own)) return true;
        if (pkg.equals("tv.dumbtv.home") || pkg.equals("com.amazon.tv.launcher")) return true;   // the Home hook and Amazon's launcher
        if (pkg.equals("android") || pkg.startsWith("com.android.")) return true;
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        return p.contains("spotify") || p.contains("music");   // Spotify, YouTube Music, Amazon Music and the like
    }
}
