package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Which Continue-watching rows Home shows, in what order, and how a card reads. */
public class ContinueRulesTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static ContinueRules.Row cont(long id, String title, long pos, long dur, long daysAgo) {
        return new ContinueRules.Row(id, "app.a", title, "app://" + id, "https://img/" + id, ContinueRules.TYPE_CONTINUE,
                0, 0, pos, dur, NOW - daysAgo * DAY);
    }

    private static List<ContinueRules.Card> pick(List<ContinueRules.Row> rows) {
        return ContinueRules.select(rows, NOW, 90, 92, 4);
    }

    private static List<String> titles(List<ContinueRules.Card> cards) {
        List<String> out = new ArrayList<>();
        for (ContinueRules.Card c : cards) out.add(c.title);
        return out;
    }

    @Test
    public void startedTitlesShowWithProgress() {
        List<ContinueRules.Card> cards = pick(Arrays.asList(cont(1, "Film", 50_000, 100_000, 1)));
        assertEquals(1, cards.size());
        assertEquals(0.5f, cards.get(0).progress, 0.0001f);
    }

    @Test
    public void finishedTitlesAreHiddenAtTheLimit() {
        assertEquals(1, pick(Arrays.asList(cont(1, "A", 91_999, 100_000, 1))).size());
        assertEquals(0, pick(Arrays.asList(cont(2, "B", 92_000, 100_000, 1))).size());
    }

    @Test
    public void oldTitlesAreHiddenAfterTheAgeLimit() {
        assertEquals(1, pick(Arrays.asList(cont(1, "A", 10, 100, 90))).size());
        assertEquals(0, pick(Arrays.asList(cont(2, "B", 10, 100, 91))).size());
    }

    @Test
    public void notStartedOrBrokenProgressIsDropped() {
        assertEquals(0, pick(Arrays.asList(cont(1, "A", 0, 100_000, 1))).size());
        assertEquals(0, pick(Arrays.asList(cont(2, "B", 5_000, 0, 1))).size());
        assertEquals(0, pick(Arrays.asList(cont(3, "C", -5, 100, 1))).size());
    }

    @Test
    public void nextEpisodeShowsWithoutABar() {
        ContinueRules.Row next = new ContinueRules.Row(7, "app.a", "Show", "app://7", null, ContinueRules.TYPE_NEXT,
                5, 10, 0, 0, NOW - DAY);
        List<ContinueRules.Card> cards = pick(Arrays.asList(next));
        assertEquals(1, cards.size());
        assertEquals(-1f, cards.get(0).progress, 0f);
        assertEquals("S5 E10", cards.get(0).subtitle);
    }

    @Test
    public void otherTypesAreDropped() {
        ContinueRules.Row fresh = new ContinueRules.Row(1, "app.a", "New", "app://1", null, 2, 0, 0, 0, 0, NOW);
        ContinueRules.Row list = new ContinueRules.Row(2, "app.a", "Listed", "app://2", null, 3, 0, 0, 0, 0, NOW);
        assertEquals(0, pick(Arrays.asList(fresh, list)).size());
    }

    @Test
    public void episodeLabelNeedsBothNumbers() {
        ContinueRules.Row onlyEpisode = new ContinueRules.Row(1, "app.a", "A", "app://1", null, ContinueRules.TYPE_NEXT, 0, 3, 0, 0, NOW);
        ContinueRules.Row negative = new ContinueRules.Row(2, "app.a", "B", "app://2", null, ContinueRules.TYPE_NEXT, -1, -1, 0, 0, NOW - 1);
        List<ContinueRules.Card> cards = pick(Arrays.asList(onlyEpisode, negative));
        assertEquals("", cards.get(0).subtitle);
        assertEquals("", cards.get(1).subtitle);
    }

    @Test
    public void newestFirstAndUnknownTimeLast() {
        ContinueRules.Row unknown = new ContinueRules.Row(3, "app.a", "Unknown", "app://3", null, ContinueRules.TYPE_CONTINUE,
                0, 0, 10, 100, 0);
        List<ContinueRules.Card> cards = pick(Arrays.asList(cont(1, "Older", 10, 100, 20), unknown, cont(2, "Newer", 10, 100, 2)));
        assertEquals(Arrays.asList("Newer", "Older", "Unknown"), titles(cards));
    }

    @Test
    public void equalTimesKeepTheProviderOrder() {
        List<ContinueRules.Card> cards = pick(Arrays.asList(cont(1, "First", 10, 100, 5), cont(2, "Second", 10, 100, 5)));
        assertEquals(Arrays.asList("First", "Second"), titles(cards));
    }

    @Test
    public void oneCardPerPackageAndTitleNewestWins() {
        ContinueRules.Row other = new ContinueRules.Row(9, "app.b", "Same", "app://9", null, ContinueRules.TYPE_CONTINUE,
                0, 0, 10, 100, NOW - 3 * DAY);
        List<ContinueRules.Card> cards = pick(Arrays.asList(cont(1, "Same", 10, 100, 9), cont(2, "Same", 20, 100, 1), other));
        assertEquals(2, cards.size());
        assertEquals("app://2", cards.get(0).intentUri);
        assertEquals("app.b", cards.get(1).pkg);
    }

    @Test
    public void capAndEmptyInputs() {
        List<ContinueRules.Row> six = new ArrayList<>();
        for (int i = 1; i <= 6; i++) six.add(cont(i, "T" + i, 10, 100, i));
        assertEquals(4, ContinueRules.select(six, NOW, 90, 92, 4).size());
        assertEquals(6, ContinueRules.select(six, NOW, 90, 92, 8).size());
        assertEquals(0, ContinueRules.select(six, NOW, 90, 92, 0).size());
        assertEquals(0, ContinueRules.select(null, NOW, 90, 92, 4).size());
        assertEquals(0, ContinueRules.select(Collections.<ContinueRules.Row>emptyList(), NOW, 90, 92, 4).size());
    }

    @Test
    public void rowsMissingFieldsAreDropped() {
        ContinueRules.Row noTitle = new ContinueRules.Row(1, "app.a", "  ", "app://1", null, 0, 0, 0, 10, 100, NOW - DAY);
        ContinueRules.Row noLink = new ContinueRules.Row(2, "app.a", "B", null, null, 0, 0, 0, 10, 100, NOW - DAY);
        ContinueRules.Row noPackage = new ContinueRules.Row(3, null, "C", "app://3", null, 0, 0, 0, 10, 100, NOW - DAY);
        assertEquals(0, pick(Arrays.asList(null, noTitle, noLink, noPackage)).size());
    }

    @Test
    public void longTitlesAreCutWithAnEllipsis() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append('x');
        String cut = pick(Arrays.asList(cont(1, sb.toString(), 10, 100, 1))).get(0).title;
        assertEquals(ContinueRules.TITLE_LIMIT, cut.length());
        assertTrue(cut.endsWith("…"));
    }

    @Test
    public void aCutNeverSplitsASurrogatePair() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 58; i++) sb.append('a');
        sb.append("😀");   // an emoji, two chars, straddling the cut
        for (int i = 0; i < 20; i++) sb.append('b');
        String cut = pick(Arrays.asList(cont(1, sb.toString(), 10, 100, 1))).get(0).title;
        assertTrue(cut.length() <= ContinueRules.TITLE_LIMIT);
        assertFalse(Character.isHighSurrogate(cut.charAt(cut.length() - 2)));
    }

    @Test
    public void keyNamesPackageAndRow() {
        assertEquals("watch:app.a:42", pick(Arrays.asList(cont(42, "T", 10, 100, 1))).get(0).key);
    }

    @Test
    public void sameRowsGiveEqualCards() {
        List<ContinueRules.Row> rows = Arrays.asList(cont(1, "T", 10, 100, 1));
        assertEquals(pick(rows), pick(rows));
        assertFalse(pick(rows).equals(pick(Arrays.asList(cont(1, "T", 11, 100, 1)))));
    }

    @Test
    public void clampKeepsSettingsInRange() {
        assertEquals(1, ContinueRules.clamp(-3, ContinueRules.MIN_MAX, ContinueRules.MAX_MAX));
        assertEquals(8, ContinueRules.clamp(99, ContinueRules.MIN_MAX, ContinueRules.MAX_MAX));
        assertEquals(4, ContinueRules.clamp(4, ContinueRules.MIN_MAX, ContinueRules.MAX_MAX));
    }
}
