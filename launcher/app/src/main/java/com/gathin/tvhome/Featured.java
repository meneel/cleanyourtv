package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The titles the Home carousel cycles through: films and shows like what Watch Next holds, new releases, and top-rated
 * ones, all streaming in India on an app this TV has, from TMDB; kept 12 hours in a cache file so Home never waits on
 * the network. Uses the same token file as Recs.
 */
final class Featured {
    private static final String TAG = "TvHomeFeatured";
    private static final int PER_LIST = 6;

    private Featured() {
    }

    private static File cacheFile(Context ctx) {
        return new File(ctx.getFilesDir(), "featured-v2.tsv");
    }

    static List<FeaturedRules.Title> cached(Context ctx) {
        try {
            File f = cacheFile(ctx);
            return f.isFile() ? FeaturedRules.fromTsv(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)) : new ArrayList<FeaturedRules.Title>();
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();
        }
    }

    static boolean stale(Context ctx) {
        long modified = cacheFile(ctx).lastModified();
        return modified == 0 || System.currentTimeMillis() - modified >= TmdbRules.MAX_AGE_MS;
    }

    private static String get(String url, String token) throws IOException {
        return Net.getText(url, token);
    }

    /** Asks TMDB and rewrites the cache; the titles, or null when nothing was learned (the old cache stays). Worker thread. */
    static List<FeaturedRules.Title> refresh(Context ctx) {
        String token = Recs.token(ctx);
        if (token == null) return null;
        PackageManager pm = ctx.getPackageManager();
        List<String> apps = new ArrayList<>();
        List<Integer> providerIds = new ArrayList<>();
        for (String pkg : TmdbRules.knownPackages()) {
            if (!Recs.installed(pm, pkg)) continue;
            apps.add(pkg);
            providerIds.add(TmdbRules.providerId(pkg));
        }
        if (apps.isEmpty()) return null;
        String recent18 = LocalDate.now().minusMonths(18).toString(), recent12 = LocalDate.now().minusMonths(12).toString();
        try {
            List<String> films = WatchNextReader.movieTitles(ctx, 40), shows = WatchNextReader.seriesTitles(ctx, 20);
            Set<String> watched = new HashSet<>();
            for (String t : films) watched.add(TmdbRules.normal(t));
            for (String t : shows) watched.add(TmdbRules.normal(t));

            // Because of what was watched: films from up to two series, and one show.
            List<FeaturedRules.Title> forFilms = new ArrayList<>(), forShows = new ArrayList<>();
            Set<String> franchises = new HashSet<>();
            for (int i = 0; i < films.size() && franchises.size() < 2; i++) {
                if (!franchises.add(TmdbRules.franchise(films.get(i)))) continue;
                long id = TmdbRules.firstMovieId(get(TmdbRules.searchUrl(films.get(i)), token));
                if (id >= 0) forFilms.addAll(first(FeaturedRules.titles(get(FeaturedRules.recommendationsUrl(FeaturedRules.MOVIE, id), token), FeaturedRules.MOVIE, FeaturedRules.TAG_FOR_YOU, 300), PER_LIST));
            }
            if (!shows.isEmpty()) {
                long id = TmdbRules.firstMovieId(get(FeaturedRules.tvSearchUrl(shows.get(0)), token));
                if (id >= 0) forShows.addAll(first(FeaturedRules.titles(get(FeaturedRules.recommendationsUrl(FeaturedRules.TV, id), token), FeaturedRules.TV, FeaturedRules.TAG_FOR_YOU, 150), PER_LIST));
            }
            List<FeaturedRules.Title> recommended = alternate(forShows, forFilms);

            List<FeaturedRules.Title> freshMovies = FeaturedRules.titles(get(FeaturedRules.discoverUrl(FeaturedRules.MOVIE, providerIds, "popularity.desc", recent18, 150, 6.5), token), FeaturedRules.MOVIE, FeaturedRules.TAG_NEW, 0);
            List<FeaturedRules.Title> freshShows = FeaturedRules.titles(get(FeaturedRules.discoverUrl(FeaturedRules.TV, providerIds, "popularity.desc", recent12, 80, 7.0), token), FeaturedRules.TV, FeaturedRules.TAG_NEW, 0);
            List<FeaturedRules.Title> topMovies = FeaturedRules.titles(get(FeaturedRules.discoverUrl(FeaturedRules.MOVIE, providerIds, "vote_average.desc", null, 3000, 7.5), token), FeaturedRules.MOVIE, FeaturedRules.TAG_TOP, 0);
            List<FeaturedRules.Title> topShows = FeaturedRules.titles(get(FeaturedRules.discoverUrl(FeaturedRules.TV, providerIds, "vote_average.desc", null, 1500, 8.0), token), FeaturedRules.TV, FeaturedRules.TAG_TOP, 0);

            List<FeaturedRules.Title> candidates = FeaturedRules.mix(dropWatched(recommended, watched),
                    dropWatched(alternate(freshShows, freshMovies), watched), dropWatched(alternate(topShows, topMovies), watched), 16);
            List<FeaturedRules.Title> out = new ArrayList<>();
            for (FeaturedRules.Title t : candidates) {
                if (out.size() >= FeaturedRules.MAX_TITLES) break;
                Set<Integer> on = TmdbRules.flatrate(get(FeaturedRules.providersUrl(t.type, t.id), token), TmdbRules.REGION);
                List<String> pkgs = new ArrayList<>();
                for (String pkg : apps) if (on.contains(TmdbRules.providerId(pkg))) pkgs.add(pkg);
                if (!pkgs.isEmpty()) out.add(t.withTagAndPkgs(t.tag, pkgs));   // a title on none of this TV's apps is of no use
            }
            if (out.isEmpty()) return null;
            write(ctx, out);
            return out;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "featured titles not updated: " + e);
            return null;
        }
    }

    private static List<FeaturedRules.Title> first(List<FeaturedRules.Title> l, int n) {
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    private static List<FeaturedRules.Title> alternate(List<FeaturedRules.Title> a, List<FeaturedRules.Title> b) {
        List<FeaturedRules.Title> out = new ArrayList<>();
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            if (i < a.size()) out.add(a.get(i));
            if (i < b.size()) out.add(b.get(i));
        }
        return out;
    }

    private static List<FeaturedRules.Title> dropWatched(List<FeaturedRules.Title> l, Set<String> watched) {
        List<FeaturedRules.Title> out = new ArrayList<>();
        for (FeaturedRules.Title t : l) if (!watched.contains(TmdbRules.normal(t.title))) out.add(t);
        return out;
    }

    private static void write(Context ctx, List<FeaturedRules.Title> titles) {
        try {
            File f = cacheFile(ctx), tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), FeaturedRules.toTsv(titles).getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "could not cache featured titles: " + e);
        }
    }
}
