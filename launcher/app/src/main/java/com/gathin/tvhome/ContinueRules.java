package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which Continue-watching rows Home shows, in what order and how a card reads. No Android and no I/O, so a
 * host test pins it. The rows come from another app's data, so every field is treated as untrusted.
 */
final class ContinueRules {
    static final int TYPE_CONTINUE = 0, TYPE_NEXT = 1;
    static final int DEFAULT_MAX = 4, MIN_MAX = 1, MAX_MAX = 8;
    static final int DEFAULT_AGE_DAYS = 90, MIN_AGE_DAYS = 1, MAX_AGE_DAYS = 365;
    static final int DEFAULT_HIDE_PERCENT = 92, MIN_HIDE_PERCENT = 50, MAX_HIDE_PERCENT = 100;
    static final int TITLE_LIMIT = 60;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private ContinueRules() {
    }

    /** One row as the TV provider holds it. */
    static final class Row {
        final long id;
        final String pkg, title, intentUri, posterUri;
        final int type, season, episode;
        final long positionMs, durationMs, engagedMs;

        Row(long id, String pkg, String title, String intentUri, String posterUri, int type, int season, int episode,
            long positionMs, long durationMs, long engagedMs) {
            this.id = id;
            this.pkg = pkg;
            this.title = title;
            this.intentUri = intentUri;
            this.posterUri = posterUri;
            this.type = type;
            this.season = season;
            this.episode = episode;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.engagedMs = engagedMs;
        }
    }

    /** One card to show. Value equality lets Home skip a rebuild when nothing changed. */
    static final class Card {
        final String key, pkg, title, subtitle, intentUri, posterUri;
        /** 0..1 for a started title; -1 for "up next", which has no bar. */
        final float progress;

        Card(String key, String pkg, String title, String subtitle, String intentUri, String posterUri, float progress) {
            this.key = key;
            this.pkg = pkg;
            this.title = title;
            this.subtitle = subtitle;
            this.intentUri = intentUri;
            this.posterUri = posterUri;
            this.progress = progress;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Card)) return false;
            Card c = (Card) o;
            return key.equals(c.key) && pkg.equals(c.pkg) && title.equals(c.title) && subtitle.equals(c.subtitle)
                    && intentUri.equals(c.intentUri) && Objects.equals(posterUri, c.posterUri)
                    && Float.compare(progress, c.progress) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, pkg, title, subtitle, intentUri, posterUri, progress);
        }
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** The cards to show: filtered, newest first, one per (package, title), at most {@code max}. */
    static List<Card> select(List<Row> rows, long nowMs, int maxAgeDays, int hidePercent, int max) {
        if (rows == null || max < 1) return Collections.emptyList();
        long maxAgeMs = (long) maxAgeDays * DAY_MS;
        List<Row> kept = new ArrayList<>();
        for (Row r : rows) {
            if (r == null || blank(r.title) || blank(r.intentUri) || blank(r.pkg)) continue;
            if (r.engagedMs > 0 && nowMs - r.engagedMs > maxAgeMs) continue;
            if (r.type == TYPE_CONTINUE) {
                if (r.durationMs <= 0 || r.positionMs <= 0) continue;
                if (r.positionMs * 100 >= (long) hidePercent * r.durationMs) continue;
            } else if (r.type != TYPE_NEXT) {
                continue;
            }
            kept.add(r);
        }
        // Newest first; a row with no time is last. The sort is stable, so ties keep the provider's order.
        Collections.sort(kept, (a, b) -> {
            boolean ka = a.engagedMs > 0, kb = b.engagedMs > 0;
            if (ka != kb) return ka ? -1 : 1;
            return ka ? Long.compare(b.engagedMs, a.engagedMs) : 0;
        });
        Set<String> seen = new HashSet<>();
        List<Card> out = new ArrayList<>();
        for (Row r : kept) {
            String title = cut(r.title, TITLE_LIMIT);
            if (!seen.add(r.pkg + "\n" + title)) continue;
            String subtitle = r.season > 0 && r.episode > 0 ? "S" + r.season + " E" + r.episode : "";
            float progress = r.type == TYPE_CONTINUE ? (float) r.positionMs / (float) r.durationMs : -1f;
            out.add(new Card("watch:" + r.pkg + ":" + r.id, r.pkg, title, subtitle, r.intentUri, r.posterUri, progress));
            if (out.size() >= max) break;
        }
        return out;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Trims, and cuts to {@code limit} characters ending in an ellipsis, never between the halves of an emoji. */
    private static String cut(String s, int limit) {
        String t = s.trim();
        if (t.length() <= limit) return t;
        int end = limit - 1;
        if (end > 0 && Character.isHighSurrogate(t.charAt(end - 1))) end--;
        return t.substring(0, end) + "…";
    }
}
