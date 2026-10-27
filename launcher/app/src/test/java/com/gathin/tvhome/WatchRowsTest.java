package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Where the Continue-watching section sits in the grid and how it is padded to whole rows. */
public class WatchRowsTest {
    @Test
    public void fillersPadTheCardsToWholeRows() {
        assertEquals(0, WatchRows.fillersFor(0, 4));
        assertEquals(3, WatchRows.fillersFor(1, 4));
        assertEquals(2, WatchRows.fillersFor(2, 4));
        assertEquals(1, WatchRows.fillersFor(3, 4));
        assertEquals(0, WatchRows.fillersFor(4, 4));
        assertEquals(3, WatchRows.fillersFor(5, 4));
        assertEquals(0, WatchRows.fillersFor(8, 4));
    }

    @Test
    public void nonsenseColumnsGiveNoFillers() {
        assertEquals(0, WatchRows.fillersFor(3, 0));
        assertEquals(0, WatchRows.fillersFor(3, -2));
        assertEquals(0, WatchRows.fillersFor(-1, 4));
    }

    @Test
    public void oneRowOfTwoCards() {
        WatchRows r = WatchRows.of(4, 2, 4);
        assertEquals(8, r.end());
        assertTrue(r.inFirstRow(4));
        assertTrue(r.inFirstRow(7));
        assertFalse(r.inFirstRow(3));
        assertFalse(r.inFirstRow(8));
        assertTrue(r.inLastRow(4));
        assertTrue(r.inLastRow(7));
        assertFalse(r.inLastRow(8));
    }

    @Test
    public void twoRowsKeepTheHeadingOnTheFirstAndTheGapOnTheLast() {
        WatchRows r = WatchRows.of(4, 6, 4);   // 6 cards + 2 fillers = rows 4..7 and 8..11
        assertEquals(12, r.end());
        assertTrue(r.inFirstRow(5));
        assertFalse(r.inFirstRow(8));
        assertFalse(r.inLastRow(5));
        assertTrue(r.inLastRow(8));
        assertTrue(r.inLastRow(11));
        assertFalse(r.inLastRow(12));
    }

    @Test
    public void noCardsMeansNoRows() {
        WatchRows r = WatchRows.of(4, 0, 4);
        assertEquals(4, r.end());
        assertFalse(r.inFirstRow(4));
        assertFalse(r.inLastRow(4));
    }
}
