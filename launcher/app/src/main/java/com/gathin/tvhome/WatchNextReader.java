package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the Continue-watching rows other apps publish to the TV provider (docs/watch-next.md). Needs the
 * READ_TV_LISTINGS permission; without it, or on any failure, the answer is an empty list and Home shows no row.
 * A query with a selection needs a privilege a sideloaded app lacks, so the whole table is read and ContinueRules
 * does the filtering. Nothing from a row is logged.
 */
final class WatchNextReader {
    private static final String TAG = "TvHomeWatch";
    private static final Uri WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program");
    /** A provider can hold hundreds of stale rows; read no more than this. */
    private static final int ROW_LIMIT = 200;

    private WatchNextReader() {
    }

    static List<ContinueRules.Card> read(Context ctx, long nowMs, int maxAgeDays, int hidePercent, int max) {
        List<ContinueRules.Row> rows = new ArrayList<>();
        try (Cursor c = open(ctx)) {
            if (c == null) return Collections.emptyList();
            int id = c.getColumnIndex("_id");
            int pkg = c.getColumnIndex("package_name");
            int title = c.getColumnIndex("title");
            int intent = c.getColumnIndex("intent_uri");
            int poster = c.getColumnIndex("poster_art_uri");
            int type = c.getColumnIndex("watch_next_type");
            int season = c.getColumnIndex("season_display_number");
            int episode = c.getColumnIndex("episode_display_number");
            int position = c.getColumnIndex("last_playback_position_millis");
            int duration = c.getColumnIndex("duration_millis");
            int engaged = c.getColumnIndex("last_engagement_time_utc_millis");
            int seen = 0;
            while (c.moveToNext() && seen++ < ROW_LIMIT) {
                try {
                    rows.add(new ContinueRules.Row(number(c, id), text(c, pkg), text(c, title), text(c, intent),
                            text(c, poster), (int) number(c, type), (int) number(c, season), (int) number(c, episode),
                            number(c, position), number(c, duration), number(c, engaged)));
                } catch (RuntimeException e) {
                    Log.w(TAG, "skipped a row: " + e);
                }
            }
        } catch (SecurityException e) {
            Log.i(TAG, "READ_TV_LISTINGS is not granted: no Continue watching row");
            return Collections.emptyList();
        } catch (RuntimeException e) {
            Log.w(TAG, "could not read Watch Next: " + e);
            return Collections.emptyList();
        }
        return ContinueRules.select(rows, nowMs, maxAgeDays, hidePercent, max);
    }

    /** Newest first, so the row cap drops the stale rows; the provider's own order if it refuses a sort order. */
    private static Cursor open(Context ctx) {
        try {
            return ctx.getContentResolver().query(WATCH_NEXT, null, null, null, "last_engagement_time_utc_millis DESC");
        } catch (SecurityException e) {
            throw e;
        } catch (RuntimeException e) {
            Log.w(TAG, "no sort order: " + e);
            return ctx.getContentResolver().query(WATCH_NEXT, null, null, null, null);
        }
    }

    /**
     * Titles of the films in Watch Next (rows with no season or episode), newest first, finished or not: the seeds
     * for recommendations and the list of what was already watched. Empty when access is missing.
     */
    static List<String> movieTitles(Context ctx, int limit) {
        return titles(ctx, limit, false);
    }

    /** Like movieTitles, for the rows that have a season or an episode: the shows. */
    static List<String> seriesTitles(Context ctx, int limit) {
        return titles(ctx, limit, true);
    }

    private static List<String> titles(Context ctx, int limit, boolean series) {
        List<String> out = new ArrayList<>();
        try (Cursor c = open(ctx)) {
            if (c == null) return out;
            int title = c.getColumnIndex("title");
            int season = c.getColumnIndex("season_display_number");
            int episode = c.getColumnIndex("episode_display_number");
            int seen = 0;
            while (c.moveToNext() && seen++ < ROW_LIMIT && out.size() < limit) {
                String t = text(c, title);
                boolean isSeries = number(c, season) != 0 || number(c, episode) != 0;
                if (t == null || t.trim().isEmpty() || isSeries != series) continue;
                if (!out.contains(t.trim())) out.add(t.trim());
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "could not read titles: " + e);
        }
        return out;
    }

    private static String text(Cursor c, int column) {
        return column < 0 || c.isNull(column) ? null : c.getString(column);
    }

    /** A number column, or 0 when it is missing, null or not numeric (season and episode are text columns). */
    private static long number(Cursor c, int column) {
        return column < 0 || c.isNull(column) ? 0L : c.getLong(column);
    }
}
