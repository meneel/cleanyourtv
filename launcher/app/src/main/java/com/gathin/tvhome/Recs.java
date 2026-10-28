package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The "Recommended for you" cards: films similar to the ones in Watch Next, or popular on the streaming apps this TV
 * has, from TMDB, kept in a cache file for 12 hours so Home never waits on the network. The TMDB read token lives in
 * a file on the TV (files/tmdb_token), never in the theme or the source. Without it the feature does nothing.
 */
final class Recs {
    private static final String TAG = "TvHomeRecs";
    private static final int SEEDS = 3, PER_SEED = 12, RANKED = 8, TOP_UP_PER_APP = 2;

    private Recs() {
    }

    private static File cacheFile(Context ctx) {
        return new File(ctx.getFilesDir(), "recs-v2.tsv");   // v2: the ranking changed, old caches are ignored
    }

    static List<ContinueRules.Card> cached(Context ctx) {
        try {
            File f = cacheFile(ctx);
            return f.isFile() ? TmdbRules.fromTsv(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)) : new ArrayList<ContinueRules.Card>();
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();
        }
    }

    static boolean stale(Context ctx) {
        long modified = cacheFile(ctx).lastModified();
        return modified == 0 || System.currentTimeMillis() - modified >= TmdbRules.MAX_AGE_MS;
    }

    /** The token from files/tmdb_token, or null when there is none or it does not look like one. */
    private static String token(Context ctx) {
        try {
            File dir = Config.dir(ctx);
            File f = dir == null ? null : new File(dir, "tmdb_token");
            if (f == null || !f.isFile() || f.length() > 2048) return null;
            String t = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            return t.matches("[A-Za-z0-9._-]{20,1500}") ? t : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean installed(PackageManager pm, String pkg) {
        return pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
    }

    /**
     * Asks TMDB and rewrites the cache; returns the cards, or null when nothing was done (no token, or the network
     * failed before anything was learned) so the old cache stays. Call off the main thread.
     */
    static List<ContinueRules.Card> refresh(Context ctx) {
        String token = token(ctx);
        if (token == null) {
            Log.i(TAG, "no TMDB token in files/tmdb_token: no recommendations");
            return null;
        }
        PackageManager pm = ctx.getPackageManager();
        List<String> apps = new ArrayList<>();
        for (String pkg : TmdbRules.knownPackages()) if (installed(pm, pkg)) apps.add(pkg);
        if (apps.isEmpty()) return null;
        List<String> watched = WatchNextReader.movieTitles(ctx, 40);
        Set<String> watchedNormal = new HashSet<>();
        for (String t : watched) watchedNormal.add(TmdbRules.normal(t));
        try {
            List<List<TmdbRules.Movie>> perSeed = new ArrayList<>();
            Set<Long> seedIds = new HashSet<>();
            Set<String> seedFranchises = new HashSet<>();
            for (int i = 0; i < watched.size() && perSeed.size() < SEEDS; i++) {
                if (!seedFranchises.add(TmdbRules.franchise(watched.get(i)))) continue;   // one seed per series
                long id = TmdbRules.firstMovieId(Net.getText(TmdbRules.searchUrl(watched.get(i)), token));
                if (id < 0) continue;
                seedIds.add(id);
                List<TmdbRules.Movie> list = TmdbRules.movies(Net.getText(TmdbRules.recommendationsUrl(id), token));
                perSeed.add(list.size() > PER_SEED ? list.subList(0, PER_SEED) : list);
            }
            List<ContinueRules.Card> out = new ArrayList<>();
            Set<Long> used = new HashSet<>(seedIds);
            for (TmdbRules.Movie m : TmdbRules.rank(perSeed, seedIds, watchedNormal, RANKED)) {
                if (out.size() >= TmdbRules.MAX_CARDS) break;
                String pkg = firstInstalled(TmdbRules.flatrate(Net.getText(TmdbRules.providersUrl(m.id), token), TmdbRules.REGION), apps);
                if (pkg == null) continue;   // not on a service this TV has
                out.add(TmdbRules.card(m, pkg));
                used.add(m.id);
            }
            for (String pkg : apps) {
                if (out.size() >= TmdbRules.MAX_CARDS) break;
                int taken = 0;
                for (TmdbRules.Movie m : TmdbRules.movies(Net.getText(TmdbRules.discoverUrl(TmdbRules.providerId(pkg)), token))) {
                    if (out.size() >= TmdbRules.MAX_CARDS || taken >= TOP_UP_PER_APP) break;
                    if (used.contains(m.id) || watchedNormal.contains(TmdbRules.normal(m.title))) continue;
                    if (franchiseCount(out, m.title) >= TmdbRules.FRANCHISE_CAP) continue;
                    out.add(TmdbRules.card(m, pkg));
                    used.add(m.id);
                    taken++;
                }
            }
            if (out.isEmpty()) return null;
            write(ctx, out);
            return out;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "recommendations not updated: " + e);
            return null;
        }
    }

    private static int franchiseCount(List<ContinueRules.Card> cards, String title) {
        int n = 0;
        for (ContinueRules.Card c : cards) if (TmdbRules.franchise(c.title).equals(TmdbRules.franchise(title))) n++;
        return n;
    }

    private static String firstInstalled(Set<Integer> providerIds, List<String> apps) {
        for (String pkg : apps) if (providerIds.contains(TmdbRules.providerId(pkg))) return pkg;
        return null;
    }

    private static void write(Context ctx, List<ContinueRules.Card> cards) {
        try {
            File f = cacheFile(ctx), tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), TmdbRules.toTsv(cards).getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "could not cache recommendations: " + e);
        }
    }
}
