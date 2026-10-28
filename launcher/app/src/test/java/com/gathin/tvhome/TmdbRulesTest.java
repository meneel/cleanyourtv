package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TmdbRulesTest {
    // Shapes captured from the real API on 2026-10-28 (trimmed).
    private static final String SEARCH = "{\"page\":1,\"results\":[{\"adult\":false,\"backdrop_path\":\"/hziiv14OpD73u9gAak4XDDfBKa2.jpg\",\"id\":671,"
            + "\"title\":\"Harry Potter and the Philosopher's Stone\",\"release_date\":\"2001-11-16\",\"vote_average\":7.9,\"vote_count\":28000}],\"total_results\":1}";
    private static final String RECS = "{\"page\":1,\"results\":["
            + "{\"adult\":false,\"backdrop_path\":\"/a.jpg\",\"id\":767,\"title\":\"Harry Potter and the Half-Blood Prince\",\"release_date\":\"2009-07-15\",\"vote_average\":7.682,\"vote_count\":19000},"
            + "{\"adult\":true,\"backdrop_path\":\"/b.jpg\",\"id\":1,\"title\":\"Adult One\",\"release_date\":\"2009-07-15\",\"vote_average\":8.0,\"vote_count\":9000},"
            + "{\"adult\":false,\"backdrop_path\":null,\"id\":2,\"title\":\"No Picture\",\"release_date\":\"2010-01-01\",\"vote_average\":8.0,\"vote_count\":9000},"
            + "{\"adult\":false,\"backdrop_path\":\"/c.jpg\",\"id\":673,\"title\":\"Harry Potter and the Prisoner of Azkaban\",\"release_date\":\"2004-05-31\",\"vote_average\":8.0,\"vote_count\":20000}]}";
    private static final String PROVIDERS = "{\"id\":671,\"results\":{\"IN\":{\"link\":\"x\",\"buy\":[{\"provider_id\":2,\"provider_name\":\"Apple TV Store\"}],"
            + "\"flatrate\":[{\"provider_id\":2336,\"provider_name\":\"JioHotstar\"}]},\"US\":{\"flatrate\":[{\"provider_id\":8,\"provider_name\":\"Netflix\"}]}}}";

    @Test
    public void jsonReaderHandlesRealRepliesAndRejectsBrokenOnes() {
        assertNotNull(MiniJson.parse(RECS));
        assertEquals("a \"quoted\" é line\n", MiniJson.string(MiniJson.object(MiniJson.parse("{\"k\":\"a \\\"quoted\\\" \\u00e9 line\\n\"}")), "k"));
        assertEquals(-1.5e2, MiniJson.number(MiniJson.object(MiniJson.parse("{\"n\": -1.5e2}")), "n", 0), 0);
        assertNull(MiniJson.parse("{\"a\":"));
        assertNull(MiniJson.parse("{\"a\":1} trailing"));
        assertNull(MiniJson.parse(null));
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 100; i++) deep.append('[');
        assertNull("nesting is capped", MiniJson.parse(deep.toString()));
    }

    @Test
    public void searchGivesTheFirstId() {
        assertEquals(671, TmdbRules.firstMovieId(SEARCH));
        assertEquals(-1, TmdbRules.firstMovieId("{\"results\":[]}"));
        assertEquals(-1, TmdbRules.firstMovieId("not json"));
    }

    @Test
    public void listsDropAdultAndPicturelessFilms() {
        List<TmdbRules.Movie> m = TmdbRules.movies(RECS);
        assertEquals(2, m.size());
        assertEquals("2009", m.get(0).year);
        assertEquals(673, m.get(1).id);
    }

    @Test
    public void providersAreReadForTheRegionAndFlatrateOnly() {
        Set<Integer> in = TmdbRules.flatrate(PROVIDERS, "IN");
        assertEquals(Collections.singleton(2336), in);                 // the Apple TV Store (buy) row is not a stream
        assertEquals(Collections.singleton(8), TmdbRules.flatrate(PROVIDERS, "US"));
        assertTrue(TmdbRules.flatrate(PROVIDERS, "FR").isEmpty());
        assertEquals("in.startv.hotstar", TmdbRules.providerPackage(2336));
        assertNull(TmdbRules.providerPackage(9999));
    }

    @Test
    public void rankingSumsSeedsAndSkipsWhatWasWatched() {
        TmdbRules.Movie a = new TmdbRules.Movie(1, "Alpha", "/a", "2020", 7.0, 500);
        TmdbRules.Movie b = new TmdbRules.Movie(2, "Beta", "/b", "2021", 7.0, 500);
        TmdbRules.Movie c = new TmdbRules.Movie(3, "Gamma", "/c", "2022", 8.0, 500);
        TmdbRules.Movie thin = new TmdbRules.Movie(4, "Thin", "/d", "2022", 9.0, 5);
        TmdbRules.Movie low = new TmdbRules.Movie(5, "Low", "/e", "2022", 4.0, 5000);
        List<List<TmdbRules.Movie>> seeds = Arrays.asList(Arrays.asList(a, b, thin, low), Arrays.asList(b, c));
        List<TmdbRules.Movie> out = TmdbRules.rank(seeds, new HashSet<Long>(), new HashSet<String>(), 10);
        assertEquals("b is in both lists, c is rated highest, a is first in only one", Arrays.asList(2L, 1L, 3L), ids(out));
        Set<Long> seedIds = new HashSet<>(Arrays.asList(2L));
        Set<String> watched = new HashSet<>(Arrays.asList(TmdbRules.normal("GAMMA!")));
        assertEquals(Arrays.asList(1L), ids(TmdbRules.rank(seeds, seedIds, watched, 10)));
        assertEquals(1, TmdbRules.rank(seeds, new HashSet<Long>(), new HashSet<String>(), 1).size());
    }

    @Test
    public void noFranchiseTakesMoreThanTwoCards() {
        List<TmdbRules.Movie> hp = new ArrayList<>();
        for (int i = 1; i <= 5; i++) hp.add(new TmdbRules.Movie(10 + i, "Harry Potter and the Part " + i, "/h", "2005", 8.0, 9000));
        TmdbRules.Movie other = new TmdbRules.Movie(99, "Zodiac", "/z", "2007", 7.0, 3000);
        List<TmdbRules.Movie> out = TmdbRules.rank(Arrays.asList(hp, Arrays.asList(other)), new HashSet<Long>(), new HashSet<String>(), 10);
        assertEquals(3, out.size());                                   // two Harry Potters and Zodiac
        assertTrue(ids(out).contains(99L));
        assertEquals(TmdbRules.franchise("Harry Potter and the Part 1"), TmdbRules.franchise("Harry Potter: Another"));
        assertFalse(TmdbRules.franchise("Zodiac").equals(TmdbRules.franchise("Zoolander")));
        assertEquals("zodiac", TmdbRules.franchise("Zodiac"));
    }

    private static List<Long> ids(List<TmdbRules.Movie> l) {
        List<Long> r = new ArrayList<>();
        for (TmdbRules.Movie m : l) r.add(m.id);
        return r;
    }

    @Test
    public void cardsRoundTripThroughTheCacheAndRefuseNonHttpsPictures() {
        TmdbRules.Movie m = new TmdbRules.Movie(767, "Half-Blood Prince", "/a.jpg", "2009", 7.7, 19000);
        ContinueRules.Card card = TmdbRules.card(m, "in.startv.hotstar");
        assertEquals("JioHotstar · 2009", card.subtitle);
        assertEquals("https://image.tmdb.org/t/p/w500/a.jpg", card.posterUri);
        List<ContinueRules.Card> back = TmdbRules.fromTsv(TmdbRules.toTsv(Arrays.asList(card)));
        assertEquals(Arrays.asList(card), back);
        assertTrue(TmdbRules.fromTsv("rec:1\tp\tT\tS\thttp://insecure/x.jpg\n").isEmpty());
        assertTrue(TmdbRules.fromTsv("junk\n\nrec:1\tp\n").isEmpty());
        assertFalse(TmdbRules.toTsv(Arrays.asList(new ContinueRules.Card("rec:9", "p", "a\tb\nc", "", "", null, -1f))).contains("\nc"));
    }

    @Test
    public void urlsAreEncodedAndStayOnTheApiHost() {
        String url = TmdbRules.searchUrl("Harry Potter & the \"Stone\"");
        assertTrue(url, url.contains("query=Harry+Potter+%26+the+%22Stone%22"));
        assertTrue(Net.allowed(url));
        assertTrue(Net.allowed(TmdbRules.recommendationsUrl(671)));
        assertTrue(Net.allowed(TmdbRules.providersUrl(671)));
        assertTrue(Net.allowed(TmdbRules.discoverUrl(8)));
    }
}
