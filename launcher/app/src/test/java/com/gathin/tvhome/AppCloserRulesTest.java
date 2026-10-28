package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppCloserRulesTest {
    private static final String OWN = "com.gathin.tvhome";

    @Test
    public void musicAppsAndHomesOwnDependenciesAreKept() {
        assertTrue(AppCloserRules.keep("com.spotify.tv.android", OWN));
        assertTrue(AppCloserRules.keep("com.google.android.apps.youtube.music", OWN));
        assertTrue(AppCloserRules.keep("com.amazon.music.tv", OWN));
        assertTrue(AppCloserRules.keep(OWN, OWN));
        assertTrue(AppCloserRules.keep("tv.dumbtv.home", OWN));            // closing it would break the Home hook
        assertTrue(AppCloserRules.keep("com.amazon.tv.launcher", OWN));
        assertTrue(AppCloserRules.keep("com.android.settings", OWN));
        assertTrue(AppCloserRules.keep("", OWN));
        assertTrue(AppCloserRules.keep(null, OWN));
    }

    @Test
    public void streamingAndOtherAppsAreClosed() {
        assertFalse(AppCloserRules.keep("com.netflix.ninja", OWN));
        assertFalse(AppCloserRules.keep("com.amazon.firebat", OWN));
        assertFalse(AppCloserRules.keep("in.startv.hotstar", OWN));
        assertFalse(AppCloserRules.keep("com.amazon.firetv.youtube", OWN));   // the YouTube video app is not YouTube Music
        assertFalse(AppCloserRules.keep("com.apple.atve.amazon.appletv", OWN));
    }
}
