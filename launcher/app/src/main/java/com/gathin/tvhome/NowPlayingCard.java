package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * The header's Now Playing card: album art, title, artist and previous / play-pause / next for whatever media session
 * is playing, hidden when nothing is. Registers only while Home is on screen (start/stop), so it costs nothing in the
 * background. Needs the notification-listener grant (see NowPlayingListener); without it the card stays hidden.
 */
final class NowPlayingCard {
    private static final String TAG = "TvHomeNowPlaying";

    private final Context ctx;
    private final View card;
    private final ImageView art;
    private final TextView title, artist;
    private final ImageButton prev, play, next;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private MediaSessionManager manager;
    private ComponentName listener;
    private MediaController current;
    private String artKey = "";
    private boolean started;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = list -> choose(list);
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            refresh();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            render();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };
    private final Runnable recheck = this::refresh;

    NowPlayingCard(Activity activity, View card) {
        this.ctx = activity;
        this.card = card;
        art = card.findViewById(R.id.npArt);
        title = card.findViewById(R.id.npTitle);
        artist = card.findViewById(R.id.npArtist);
        prev = card.findViewById(R.id.npPrev);
        play = card.findViewById(R.id.npPlay);
        next = card.findViewById(R.id.npNext);
        final float radius = 10f * activity.getResources().getDisplayMetrics().density;
        art.setClipToOutline(true);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        prev.setOnClickListener(v -> { if (current != null) current.getTransportControls().skipToPrevious(); });
        next.setOnClickListener(v -> { if (current != null) current.getTransportControls().skipToNext(); });
        play.setOnClickListener(v -> {
            if (current == null) return;
            PlaybackState s = current.getPlaybackState();
            if (s != null && NowPlayingRules.playing(s.getState())) current.getTransportControls().pause();
            else current.getTransportControls().play();
        });
    }

    /** Home is on screen: follow the sessions. */
    void start() {
        if (started) return;
        started = true;
        try {
            manager = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            listener = new ComponentName(ctx, NowPlayingListener.class);
            manager.addOnActiveSessionsChangedListener(sessions, listener);
            choose(manager.getActiveSessions(listener));
        } catch (SecurityException e) {
            Log.i(TAG, "notification-listener access is not granted: no Now Playing card");
            hide();
        } catch (RuntimeException e) {
            Log.w(TAG, "media sessions unavailable: " + e);
            hide();
        }
    }

    /** Home left the screen: let go of everything. */
    void stop() {
        if (!started) return;
        started = false;
        ui.removeCallbacks(recheck);
        try {
            if (manager != null) manager.removeOnActiveSessionsChangedListener(sessions);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not unregister: " + e);
        }
        follow(null);
    }

    private void choose(List<MediaController> list) {
        MediaController chosen = null;
        if (list != null && !list.isEmpty()) {
            int[] states = new int[list.size()];
            for (int i = 0; i < states.length; i++) {
                PlaybackState s = list.get(i).getPlaybackState();
                states[i] = s == null ? 0 : s.getState();
            }
            int index = NowPlayingRules.pick(states);
            if (index >= 0) chosen = list.get(index);
        }
        follow(chosen);
        refresh();
    }

    private void follow(MediaController controller) {
        if (current != null && controller != null && current.getSessionToken().equals(controller.getSessionToken())) return;
        if (current != null) current.unregisterCallback(callback);
        current = controller;
        artKey = "";
        if (current != null) current.registerCallback(callback, ui);
    }

    private void refresh() {
        ui.removeCallbacks(recheck);
        if (current == null) {
            hide();
            return;
        }
        PlaybackState s = current.getPlaybackState();
        int state = s == null ? 0 : s.getState();
        long age = s == null ? -1 : SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime();
        if (!NowPlayingRules.visible(state, age)) {
            hide();
            return;
        }
        if (!NowPlayingRules.playing(state)) ui.postDelayed(recheck, Math.max(1000, NowPlayingRules.PAUSE_KEEP_MS - age + 500));
        play.setImageResource(NowPlayingRules.playing(state) ? R.drawable.ic_np_pause : R.drawable.ic_np_play);
        long actions = s == null ? 0 : s.getActions();
        enable(prev, (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0);
        enable(next, (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0);
        render();
        if (card.getVisibility() != View.VISIBLE) {
            card.setAlpha(0f);
            card.setVisibility(View.VISIBLE);
            card.animate().alpha(1f).setDuration(250).start();
        }
    }

    private void hide() {
        card.animate().cancel();
        card.setVisibility(View.GONE);
    }

    private static void enable(View button, boolean on) {
        button.setEnabled(on);
        button.setAlpha(on ? 1f : 0.35f);
    }

    private void render() {
        if (current == null) return;
        MediaMetadata md = current.getMetadata();
        if (md == null) return;
        String name = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (name == null) name = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        title.setText(name == null ? "" : name);
        String by = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (by == null) by = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (by == null) by = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        artist.setText(NowPlayingRules.subtitle(by, md.getString(MediaMetadata.METADATA_KEY_ALBUM)));
        Bitmap bitmap = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bitmap == null) bitmap = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bitmap == null) bitmap = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (bitmap != null) {
            artKey = "bitmap";
            art.setImageBitmap(bitmap);
            return;
        }
        String uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (uri == null) uri = md.getString(MediaMetadata.METADATA_KEY_ART_URI);
        if (uri == null) uri = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        if (uri != null && !uri.equals(artKey)) {
            final String wanted = uri;
            artKey = uri;
            art.setImageDrawable(appIcon());   // until the picture arrives
            PosterCache.load(ctx, uri, 256, 256, loaded -> {
                if (loaded != null && wanted.equals(artKey)) art.setImageBitmap(loaded);
            });
        } else if (uri == null && artKey.isEmpty()) {
            art.setImageDrawable(appIcon());
        }
    }

    private Drawable appIcon() {
        try {
            return ctx.getPackageManager().getApplicationIcon(current.getPackageName());
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }
}
