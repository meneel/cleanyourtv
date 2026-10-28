package com.gathin.tvhome;

/** Which media session the Now Playing card follows, and when it shows. No Android types, so it is unit-tested. */
final class NowPlayingRules {
    /** PlaybackState.STATE_* values. */
    static final int PAUSED = 2, PLAYING = 3, BUFFERING = 6;
    /** A paused session stays on the card this long, so a pause can be undone from Home; then it goes. */
    static final long PAUSE_KEEP_MS = 2L * 60 * 1000;

    private NowPlayingRules() {
    }

    static boolean playing(int state) {
        return state == PLAYING || state == BUFFERING;
    }

    /** The index of the session to show: the first one playing, else the first paused one, else -1. */
    static int pick(int[] states) {
        int paused = -1;
        for (int i = 0; i < states.length; i++) {
            if (playing(states[i])) return i;
            if (states[i] == PAUSED && paused < 0) paused = i;
        }
        return paused;
    }

    /** Playing always shows; paused shows for PAUSE_KEEP_MS after the pause; anything else hides. */
    static boolean visible(int state, long msSinceUpdate) {
        if (playing(state)) return true;
        return state == PAUSED && msSinceUpdate >= 0 && msSinceUpdate < PAUSE_KEEP_MS;
    }

    /** The artist line: the artist, or the album when there is none, or empty. */
    static String subtitle(String artist, String album) {
        if (artist != null && !artist.trim().isEmpty()) return artist.trim();
        return album == null ? "" : album.trim();
    }
}
