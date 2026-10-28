package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NowPlayingRulesTest {
    @Test
    public void followsThePlayingSessionBeforeAPausedOne() {
        assertEquals(1, NowPlayingRules.pick(new int[]{2, 3, 0}));
        assertEquals(0, NowPlayingRules.pick(new int[]{6, 3}));        // buffering counts as playing
        assertEquals(2, NowPlayingRules.pick(new int[]{0, 1, 2}));      // only a paused one: that one
        assertEquals(-1, NowPlayingRules.pick(new int[]{0, 1, 7}));     // none, stopped, error
        assertEquals(-1, NowPlayingRules.pick(new int[]{}));
    }

    @Test
    public void aPauseIsKeptForTwoMinutesThenGoes() {
        assertTrue(NowPlayingRules.visible(3, 999_999_999L));            // playing: whatever the age
        assertTrue(NowPlayingRules.visible(2, 10_000));
        assertFalse(NowPlayingRules.visible(2, NowPlayingRules.PAUSE_KEEP_MS));
        assertFalse(NowPlayingRules.visible(2, -5));                     // a clock that went backwards hides it
        assertFalse(NowPlayingRules.visible(0, 0));
        assertFalse(NowPlayingRules.visible(1, 0));
    }

    @Test
    public void subtitlePrefersTheArtist() {
        assertEquals("Zain Zohaib", NowPlayingRules.subtitle(" Zain Zohaib ", "Album"));
        assertEquals("Album", NowPlayingRules.subtitle("", "Album"));
        assertEquals("", NowPlayingRules.subtitle(null, null));
    }
}
