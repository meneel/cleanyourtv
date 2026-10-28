package com.gathin.tvhome;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** TMDB addresses, replies and the ranking of recommendations. No Android types, so it is unit-tested. */
final class TmdbRules {
    static final String API = "https://api.themoviedb.org/3";
    static final String IMAGE = "https://image.tmdb.org/t/p/w500";
    static final String REGION = "IN";
    static final int MAX_CARDS = 6, FRANCHISE_CAP = 2;
    static final long MAX_AGE_MS = 12L * 60 * 60 * 1000;

    /** TMDB watch-provider id, the TV app that plays it, and its name on the card. */
    private static final int[] PROVIDER_ID = {8, 119, 350, 2336, 315};
    private static final String[] PROVIDER_PKG = {"com.netflix.ninja", "com.amazon.firebat",
        "com.apple.atve.amazon.appletv", "in.startv.hotstar", "com.viewlift.hoichoiprod"};
    private static final String[] PROVIDER_NAME = {"Netflix", "Prime Video", "Apple TV", "JioHotstar", "hoichoi"};

    private TmdbRules() {
    }

    static final class Movie {
        final long id;
        final String title, backdrop, year;
        final double vote;
        final int votes;

        Movie(long id, String title, String backdrop, String year, double vote, int votes) {
            this.id = id;
            this.title = title;
            this.backdrop = backdrop;
            this.year = year;
            this.vote = vote;
            this.votes = votes;
        }
    }

    static String providerPackage(int providerId) {
        for (int i = 0; i < PROVIDER_ID.length; i++) if (PROVIDER_ID[i] == providerId) return PROVIDER_PKG[i];
        return null;
    }

    static String providerName(String pkg) {
        for (int i = 0; i < PROVIDER_PKG.length; i++) if (PROVIDER_PKG[i].equals(pkg)) return PROVIDER_NAME[i];
        return "";
    }

    static int providerId(String pkg) {
        for (int i = 0; i < PROVIDER_PKG.length; i++) if (PROVIDER_PKG[i].equals(pkg)) return PROVIDER_ID[i];
        return -1;
    }

    /** The packages this feature knows a provider for, in a stable order. */
    static List<String> knownPackages() {
        List<String> l = new ArrayList<>();
        Collections.addAll(l, PROVIDER_PKG);
        return l;
    }

    static String searchUrl(String title) {
        return API + "/search/movie?include_adult=false&query=" + enc(title);
    }

    static String recommendationsUrl(long movieId) {
        return API + "/movie/" + movieId + "/recommendations";
    }

    static String providersUrl(long movieId) {
        return API + "/movie/" + movieId + "/watch/providers";
    }

    /** The most popular, well-voted films on one provider in the region. */
    static String discoverUrl(int providerId) {
        return API + "/discover/movie?include_adult=false&sort_by=popularity.desc&vote_count.gte=300&vote_average.gte=6.3"
                + "&with_watch_monetization_types=flatrate&watch_region=" + REGION + "&with_watch_providers=" + providerId;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }

    /** The id of the first search hit, or -1. */
    static long firstMovieId(String json) {
        Map<String, Object> root = MiniJson.object(MiniJson.parse(json));
        List<Object> results = root == null ? null : MiniJson.array(root.get("results"));
        if (results == null || results.isEmpty()) return -1;
        Map<String, Object> first = MiniJson.object(results.get(0));
        return first == null ? -1 : (long) MiniJson.number(first, "id", -1);
    }

    /** The films of a "results" list that can be shown: not adult, with a title and a landscape picture. */
    static List<Movie> movies(String json) {
        List<Movie> out = new ArrayList<>();
        Map<String, Object> root = MiniJson.object(MiniJson.parse(json));
        List<Object> results = root == null ? null : MiniJson.array(root.get("results"));
        if (results == null) return out;
        for (Object o : results) {
            Map<String, Object> m = MiniJson.object(o);
            if (m == null || MiniJson.bool(m, "adult", false)) continue;
            String title = MiniJson.string(m, "title");
            String backdrop = MiniJson.string(m, "backdrop_path");
            long id = (long) MiniJson.number(m, "id", -1);
            if (title == null || title.trim().isEmpty() || backdrop == null || backdrop.isEmpty() || id < 0) continue;
            out.add(new Movie(id, title.trim(), backdrop, year(MiniJson.string(m, "release_date")),
                    MiniJson.number(m, "vote_average", 0), (int) MiniJson.number(m, "vote_count", 0)));
        }
        return out;
    }

    static String year(String releaseDate) {
        return releaseDate != null && releaseDate.length() >= 4 && releaseDate.substring(0, 4).matches("\\d{4}")
                ? releaseDate.substring(0, 4) : "";
    }

    /** The provider ids that stream (flat rate) a title in a region; empty when none or unreadable. */
    static Set<Integer> flatrate(String json, String region) {
        Set<Integer> out = new HashSet<>();
        Map<String, Object> root = MiniJson.object(MiniJson.parse(json));
        Map<String, Object> results = root == null ? null : MiniJson.object(root.get("results"));
        Map<String, Object> place = results == null ? null : MiniJson.object(results.get(region));
        List<Object> flat = place == null ? null : MiniJson.array(place.get("flatrate"));
        if (flat == null) return out;
        for (Object o : flat) {
            Map<String, Object> p = MiniJson.object(o);
            double id = MiniJson.number(p, "provider_id", -1);
            if (id >= 0) out.add((int) id);
        }
        return out;
    }

    /** The first two words, normalised: "Harry Potter and the ..." and "Harry Potter: ..." share a key. */
    static String franchise(String title) {
        String[] words = title == null ? new String[0] : title.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, words.length); i++) sb.append(normal(words[i]));
        return sb.toString();
    }

    /** Lower-case letters and digits only, so "Harry Potter: The Film" and "harry potter the film" match. */
    static String normal(String title) {
        return title == null ? "" : title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * The films to look at, best first: each seed's recommendation list counts by how high the film sits in it, summed
     * over seeds, plus a little for its rating. Films already watched, the seeds themselves, thin ratings and low
     * scores are left out.
     */
    static List<Movie> rank(List<List<Movie>> perSeed, Set<Long> excludeIds, Set<String> excludeTitles, int max) {
        final Map<Long, Double> score = new HashMap<>();
        Map<Long, Movie> byId = new HashMap<>();
        for (List<Movie> list : perSeed) {
            for (int i = 0; i < list.size(); i++) {
                Movie m = list.get(i);
                if (excludeIds.contains(m.id) || excludeTitles.contains(normal(m.title))) continue;
                if (m.votes < 100 || m.vote < 6.0) continue;
                byId.put(m.id, m);
                Double old = score.get(m.id);
                score.put(m.id, (old == null ? m.vote / 20.0 : old) + 1.0 / (1 + i));
            }
        }
        List<Movie> ranked = new ArrayList<>(byId.values());
        Collections.sort(ranked, new Comparator<Movie>() {
            @Override
            public int compare(Movie a, Movie b) {
                int c = Double.compare(score.get(b.id), score.get(a.id));
                return c != 0 ? c : Long.compare(a.id, b.id);
            }
        });
        // One series must not fill the row: at most FRANCHISE_CAP films with the same first two words.
        List<Movie> varied = new ArrayList<>();
        Map<String, Integer> perFranchise = new HashMap<>();
        for (Movie m : ranked) {
            String key = franchise(m.title);
            Integer n = perFranchise.get(key);
            if (n != null && n >= FRANCHISE_CAP) continue;
            perFranchise.put(key, n == null ? 1 : n + 1);
            varied.add(m);
            if (varied.size() >= max) break;
        }
        return varied;
    }

    /** The card for a film on a provider's app: "Netflix · 2023" under the picture. */
    static ContinueRules.Card card(Movie m, String pkg) {
        String name = providerName(pkg);
        String sub = name.isEmpty() ? m.year : (m.year.isEmpty() ? name : name + " · " + m.year);
        return new ContinueRules.Card("rec:" + m.id, pkg, m.title, sub, "", IMAGE + m.backdrop, -1f);
    }

    /** One tab-separated line per card, for the on-disk cache. */
    static String toTsv(List<ContinueRules.Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (ContinueRules.Card c : cards) {
            sb.append(clean(c.key)).append('\t').append(clean(c.pkg)).append('\t').append(clean(c.title)).append('\t')
                    .append(clean(c.subtitle)).append('\t').append(clean(c.posterUri == null ? "" : c.posterUri)).append('\n');
        }
        return sb.toString();
    }

    static List<ContinueRules.Card> fromTsv(String tsv) {
        List<ContinueRules.Card> out = new ArrayList<>();
        if (tsv == null) return out;
        for (String line : tsv.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length != 5 || !f[0].startsWith("rec:") || f[1].isEmpty() || f[2].isEmpty()) continue;
            if (!f[4].isEmpty() && PosterRules.kind(f[4]) != PosterRules.NETWORK) continue;   // only https pictures
            out.add(new ContinueRules.Card(f[0], f[1], f[2], f[3], "", f[4].isEmpty() ? null : f[4], -1f));
        }
        return out;
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
