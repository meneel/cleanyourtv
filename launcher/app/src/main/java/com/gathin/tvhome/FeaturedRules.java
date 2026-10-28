package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The Featured carousel's titles (films and shows): TMDB addresses and replies, mixing, display text, cache lines. */
final class FeaturedRules {
    static final String BACKDROP = "https://image.tmdb.org/t/p/w1280";
    static final int MAX_TITLES = 10, MAX_OVERVIEW = 420;
    static final String MOVIE = "movie", TV = "tv";
    static final String TAG_FOR_YOU = "Recommended", TAG_NEW = "New", TAG_TOP = "Top rated";

    private FeaturedRules() {
    }

    static final class Title {
        final String type, title, year, tag, overview, backdrop;
        final long id;
        final double rating;
        final List<String> pkgs;

        Title(String type, long id, String title, String year, double rating, String tag, String overview,
              String backdrop, List<String> pkgs) {
            this.type = type;
            this.id = id;
            this.title = title;
            this.year = year;
            this.rating = rating;
            this.tag = tag;
            this.overview = overview;
            this.backdrop = backdrop;
            this.pkgs = pkgs;
        }

        Title withTagAndPkgs(String newTag, List<String> newPkgs) {
            return new Title(type, id, title, year, rating, newTag, overview, backdrop, newPkgs);
        }

        String key() {
            return type + ":" + id;
        }
    }

    /** Which page of TMDB's results to read: 1, 2 or 3 as the hour moves on, so the same query gives different titles. */
    static int pageForHour(long nowMs) {
        return 1 + (int) ((nowMs / 3_600_000L) % 3);
    }

    /** The list in an order fixed by {@code seed} (the hour): the same hour gives the same order, the next hour another. */
    static <T> List<T> shuffled(List<T> list, long seed) {
        List<T> out = new ArrayList<>(list);
        java.util.Collections.shuffle(out, new java.util.Random(seed));
        return out;
    }

    static String discoverUrl(String type, List<Integer> providerIds, String sort, String dateGte, int minVotes, double minRating, int page) {
        return discoverUrl(type, providerIds, sort, dateGte, minVotes, minRating) + "&page=" + page;
    }

    /** discover for one kind of title on any of the given TMDB provider ids (pipe = OR), in the region. */
    static String discoverUrl(String type, List<Integer> providerIds, String sort, String dateGte, int minVotes, double minRating) {
        StringBuilder ids = new StringBuilder();
        for (int id : providerIds) ids.append(ids.length() == 0 ? "" : "%7C").append(id);
        String dateKey = MOVIE.equals(type) ? "primary_release_date.gte" : "first_air_date.gte";
        return TmdbRules.API + "/discover/" + type + "?include_adult=false&sort_by=" + sort + "&vote_count.gte=" + minVotes
                + "&vote_average.gte=" + minRating + (dateGte == null ? "" : "&" + dateKey + "=" + dateGte)
                + "&with_watch_monetization_types=flatrate&watch_region=" + TmdbRules.REGION + "&with_watch_providers=" + ids;
    }

    static String tvSearchUrl(String title) {
        return TmdbRules.API + "/search/tv?include_adult=false&query=" + TmdbRules.enc(title);
    }

    static String recommendationsUrl(String type, long id) {
        return TmdbRules.API + "/" + type + "/" + id + "/recommendations";
    }

    static String providersUrl(String type, long id) {
        return TmdbRules.API + "/" + type + "/" + id + "/watch/providers";
    }

    static String detailsUrl(String type, long id) {
        return TmdbRules.API + "/" + type + "/" + id;
    }

    /** The titles in a "results" list (films use title/release_date, shows name/first_air_date), or one details object. */
    static List<Title> titles(String json, String type, String tag, int minVotes) {
        List<Title> out = new ArrayList<>();
        Object parsed = MiniJson.parse(json);
        Map<String, Object> root = MiniJson.object(parsed);
        List<Object> results = root == null ? null : MiniJson.array(root.get("results"));
        if (results == null && root != null && root.get("id") != null) results = java.util.Collections.<Object>singletonList(root);
        if (results == null) return out;
        for (Object o : results) {
            Map<String, Object> m = MiniJson.object(o);
            if (m == null || MiniJson.bool(m, "adult", false)) continue;
            String name = MiniJson.string(m, MOVIE.equals(type) ? "title" : "name");
            String backdrop = MiniJson.string(m, "backdrop_path");
            long id = (long) MiniJson.number(m, "id", -1);
            if (name == null || name.trim().isEmpty() || backdrop == null || backdrop.isEmpty() || id < 0) continue;
            if (MiniJson.number(m, "vote_count", 0) < minVotes) continue;
            String overview = clean(MiniJson.string(m, "overview"));
            if (overview.length() > MAX_OVERVIEW) overview = overview.substring(0, MAX_OVERVIEW - 1).trim() + "…";
            out.add(new Title(type, id, name.trim(), TmdbRules.year(MiniJson.string(m, MOVIE.equals(type) ? "release_date" : "first_air_date")),
                    MiniJson.number(m, "vote_average", 0), tag, overview, BACKDROP + backdrop, new ArrayList<String>()));
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    /**
     * Interleaves the three lists (recommended, new, top rated) so the carousel keeps changing kind, drops repeats,
     * and keeps one title per franchise.
     */
    static List<Title> mix(List<Title> recommended, List<Title> fresh, List<Title> top, int max) {
        List<Title> out = new ArrayList<>();
        Set<String> seen = new HashSet<>(), franchises = new HashSet<>();
        List<List<Title>> lists = new ArrayList<>();
        lists.add(recommended);
        lists.add(fresh);
        lists.add(top);
        int[] at = new int[3];
        boolean progressed = true;
        while (out.size() < max && progressed) {
            progressed = false;
            for (int l = 0; l < 3 && out.size() < max; l++) {
                while (at[l] < lists.get(l).size()) {
                    Title t = lists.get(l).get(at[l]++);
                    progressed = true;
                    if (!seen.add(t.key()) || !franchises.add(TmdbRules.franchise(t.title))) continue;
                    out.add(t);
                    break;
                }
            }
        }
        return out;
    }

    /** "On Netflix · JioHotstar" or "" when the title is on none of the TV's apps. */
    static String providersLine(List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String pkg : pkgs) {
            String name = TmdbRules.providerName(pkg);
            if (name.isEmpty()) continue;
            sb.append(sb.length() == 0 ? "On " : " · ").append(name);
        }
        return sb.toString();
    }

    /** "★ 8.1 · Movie · 2025". */
    /**
     * The title's text size: 58 sp while it fits one line (about 27 characters in the 720 dp column), and smaller for
     * longer ones, which wrap to two lines; the extra line must not push the block up into the header.
     */
    static int titleSizeSp(String title) {
        int n = title == null ? 0 : title.length();
        if (n <= 27) return 58;
        if (n <= 46) return 44;
        return 36;
    }

    /** Summary lines to show: three under a one-line title, two under a longer one (which takes a second line). */
    static int overviewLines(String title) {
        return titleSizeSp(title) == 58 ? 3 : 2;
    }

    static String meta(Title t) {
        StringBuilder sb = new StringBuilder();
        if (t.rating > 0) sb.append("★ ").append(String.format(Locale.ROOT, "%.1f", t.rating)).append(" · ");
        sb.append(MOVIE.equals(t.type) ? "Movie" : "Series");
        if (!t.year.isEmpty()) sb.append(" · ").append(t.year);
        return sb.toString();
    }

    static String toTsv(List<Title> titles) {
        StringBuilder sb = new StringBuilder();
        for (Title t : titles) {
            sb.append(t.type).append('\t').append(t.id).append('\t').append(clean(t.title)).append('\t').append(t.year).append('\t')
                    .append(t.rating).append('\t').append(clean(t.tag)).append('\t').append(clean(t.overview)).append('\t')
                    .append(t.backdrop).append('\t').append(String.join(",", t.pkgs)).append('\n');
        }
        return sb.toString();
    }

    static List<Title> fromTsv(String tsv) {
        List<Title> out = new ArrayList<>();
        if (tsv == null) return out;
        for (String line : tsv.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length != 9 || !(MOVIE.equals(f[0]) || TV.equals(f[0])) || f[2].isEmpty()) continue;
            if (PosterRules.kind(f[7]) != PosterRules.NETWORK) continue;   // only https pictures
            try {
                List<String> pkgs = new ArrayList<>();
                for (String p : f[8].split(",")) if (!p.isEmpty()) pkgs.add(p);
                out.add(new Title(f[0], Long.parseLong(f[1]), f[2], f[3], Double.parseDouble(f[4]), f[5], f[6], f[7], pkgs));
            } catch (NumberFormatException e) {
                // skip a damaged line
            }
        }
        return out;
    }
}
