package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeaturedRulesTest {
    private static final String TV_JSON = "{\"results\":["
            + "{\"adult\":false,\"backdrop_path\":\"/s.jpg\",\"id\":1399,\"name\":\"Game of Thrones\",\"first_air_date\":\"2011-04-17\",\"vote_average\":8.4,\"vote_count\":25000,\"overview\":\"Seven noble families\\nfight.\"},"
            + "{\"adult\":false,\"backdrop_path\":null,\"id\":2,\"name\":\"No Picture\",\"first_air_date\":\"2020-01-01\",\"vote_average\":8.0,\"vote_count\":900},"
            + "{\"adult\":false,\"backdrop_path\":\"/t.jpg\",\"id\":3,\"name\":\"Thin\",\"first_air_date\":\"2020-01-01\",\"vote_average\":9.0,\"vote_count\":3}]}";

    private static FeaturedRules.Title t(String type, long id, String name, String tag) {
        return new FeaturedRules.Title(type, id, name, "2024", 7.5, tag, "o", "https://image.tmdb.org/t/p/w780/" + id + ".jpg", new ArrayList<String>());
    }

    @Test
    public void showsUseNameAndFirstAirDateAndThinOnesAreDropped() {
        List<FeaturedRules.Title> l = FeaturedRules.titles(TV_JSON, FeaturedRules.TV, "New", 100);
        assertEquals(1, l.size());
        assertEquals("Game of Thrones", l.get(0).title);
        assertEquals("2011", l.get(0).year);
        assertEquals("Seven noble families fight.", l.get(0).overview);        // newline flattened
        assertEquals("https://image.tmdb.org/t/p/original/s.jpg", l.get(0).backdrop);
        assertEquals("★ 8.4 · Series · 2011", FeaturedRules.meta(l.get(0)));
    }

    @Test
    public void aDetailsObjectParsesLikeAOneItemList() {
        String one = "{\"id\":603,\"title\":\"The Matrix\",\"release_date\":\"1999-03-30\",\"backdrop_path\":\"/m.jpg\",\"vote_average\":8.2,\"vote_count\":25000,\"overview\":\"A hacker.\"}";
        List<FeaturedRules.Title> l = FeaturedRules.titles(one, FeaturedRules.MOVIE, "Recommended", 0);
        assertEquals(1, l.size());
        assertEquals(603, l.get(0).id);
    }

    @Test
    public void mixInterleavesKindsAndDropsRepeatsAndSecondFilms() {
        List<FeaturedRules.Title> rec = Arrays.asList(t("movie", 1, "Alpha Saga", "Recommended"), t("movie", 2, "Alpha Saga 2", "Recommended"));
        List<FeaturedRules.Title> fresh = Arrays.asList(t("tv", 10, "Brand New Show", "New"), t("movie", 1, "Alpha Saga", "New"));
        List<FeaturedRules.Title> top = Arrays.asList(t("tv", 20, "Classic Series", "Top rated"));
        List<FeaturedRules.Title> out = FeaturedRules.mix(rec, fresh, top, 10);
        List<String> names = new ArrayList<>();
        for (FeaturedRules.Title x : out) names.add(x.title);
        assertEquals(Arrays.asList("Alpha Saga", "Brand New Show", "Classic Series"), names);   // Alpha Two shares a franchise; the repeat is dropped
        assertEquals(2, FeaturedRules.mix(rec, fresh, top, 2).size());
    }

    @Test
    public void providersLineNamesOnlyKnownApps() {
        assertEquals("On Netflix · JioHotstar", FeaturedRules.providersLine(Arrays.asList("com.netflix.ninja", "unknown.app", "in.startv.hotstar")));
        assertEquals("", FeaturedRules.providersLine(new ArrayList<String>()));
    }

    @Test
    public void cacheRoundTripsAndRefusesBadLines() {
        FeaturedRules.Title a = new FeaturedRules.Title("tv", 5, "A\tTitle", "2024", 8.25, "New", "line1\nline2", "https://image.tmdb.org/t/p/w780/x.jpg", Arrays.asList("com.netflix.ninja", "in.startv.hotstar"));
        List<FeaturedRules.Title> back = FeaturedRules.fromTsv(FeaturedRules.toTsv(Arrays.asList(a)));
        assertEquals(1, back.size());
        assertEquals("A Title", back.get(0).title);
        assertEquals("line1 line2", back.get(0).overview);
        assertEquals(Arrays.asList("com.netflix.ninja", "in.startv.hotstar"), back.get(0).pkgs);
        assertEquals(8.25, back.get(0).rating, 0);
        assertTrue(FeaturedRules.fromTsv("tv\t1\tT\t2020\t7\tNew\to\thttp://insecure/x.jpg\t\n").isEmpty());
        assertTrue(FeaturedRules.fromTsv("tv\tNaN\tT\n\nbad\n").isEmpty());
        assertFalse(FeaturedRules.toTsv(Arrays.asList(a)).contains("line1\nline2"));
    }

    @Test
    public void theHourPicksAPageAndAShuffleThatChangeEveryHour() {
        long h = 3_600_000L;
        assertEquals(java.util.Arrays.asList(1, 2, 3, 1), java.util.Arrays.asList(FeaturedRules.pageForHour(0), FeaturedRules.pageForHour(h), FeaturedRules.pageForHour(2 * h), FeaturedRules.pageForHour(3 * h)));
        assertEquals(FeaturedRules.pageForHour(5 * h), FeaturedRules.pageForHour(5 * h + 3_599_000L));   // same hour, same page
        List<Integer> base = java.util.Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertEquals(FeaturedRules.shuffled(base, 42), FeaturedRules.shuffled(base, 42));
        assertFalse(FeaturedRules.shuffled(base, 42).equals(FeaturedRules.shuffled(base, 43)));
        assertEquals(10, new java.util.HashSet<>(FeaturedRules.shuffled(base, 7)).size());              // nothing lost or doubled
        assertTrue(FeaturedRules.discoverUrl("movie", java.util.Arrays.asList(8), "popularity.desc", null, 1, 6.0, 2).endsWith("&page=2"));
    }

    @Test
    public void longTitlesGetASmallerSizeAndAShorterSummary() {
        assertEquals(58, FeaturedRules.titleSizeSp("Dragon Ball Super: Beerus"));                       // 25 chars: one line at full size
        assertEquals(44, FeaturedRules.titleSizeSp("Harry Potter and the Prisoner of Azkaban"));         // 40 chars: wraps
        assertEquals(36, FeaturedRules.titleSizeSp("Harry Potter and the Deathly Hallows: Part 2 Extended Edition"));
        assertEquals(58, FeaturedRules.titleSizeSp(null));
        assertEquals(3, FeaturedRules.overviewLines("The Godfather"));
        assertEquals(2, FeaturedRules.overviewLines("Harry Potter and the Prisoner of Azkaban"));
    }

    @Test
    public void discoverUrlsAreOnTheApiHostWithPipeEncoded() {
        String url = FeaturedRules.discoverUrl("movie", Arrays.asList(8, 119), "popularity.desc", "2025-01-01", 150, 6.5);
        assertTrue(url, url.contains("with_watch_providers=8%7C119"));
        assertTrue(url.contains("primary_release_date.gte=2025-01-01"));
        assertTrue(Net.allowed(url));
        assertTrue(FeaturedRules.discoverUrl("tv", Arrays.asList(8), "vote_average.desc", null, 1500, 7.0).contains("/discover/tv"));
        assertTrue(Net.allowed(FeaturedRules.providersUrl("tv", 1399)));
        assertTrue(Net.allowed(FeaturedRules.tvSearchUrl("How I Met Your Mother")));
    }

    @Test
    public void measuredTitleSizeKeepsOneLineWhenItFits() {
        FeaturedRules.Measure half = (text, sp) -> text.length() * sp * 0.5f;   // a fake font: half an em per letter
        String pirates = "Pirates of the Caribbean: At World's End";   // 40 letters
        int sp = FeaturedRules.titleSizeSp(pirates, 1000f, half);
        assertTrue(half.width(pirates, sp) <= 1000f);                   // fits on one line, so no orphan word
        assertTrue(sp >= 46 && sp < 58);
        assertEquals(58, FeaturedRules.titleSizeSp("Dune", 1000f, half));
        assertEquals(36, FeaturedRules.titleSizeSp("Harry Potter and the Deathly Hallows: Part 2 Extended Edition", 1000f, half));
        assertEquals(44, FeaturedRules.titleSizeSp("Harry Potter and the Prisoner of Azkaban", 0f, half));   // width unknown: the length rule
    }
}
