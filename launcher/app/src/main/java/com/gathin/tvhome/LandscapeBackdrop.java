package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The landscape Home: the featured layer's picture slot shows a generated scene for the hour (see LandscapeArt) behind a
 * normal Home, with none of the featured screen's text or keys. Drawn once off the main thread at 960x540, redrawn when
 * the mood (dawn, day, dusk, night) or the day changes, and released when Home leaves the screen.
 */
final class LandscapeBackdrop {
    private static final int W = 960, H = 540;
    private final View root;
    private final ImageView art;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Bitmap shown;
    private int shownMood = -1;
    private long shownDay = -1;

    LandscapeBackdrop(Activity activity, View root) {
        this.root = root;
        art = root.findViewById(R.id.featArt);
        for (int id : new int[]{R.id.featInfo, R.id.featHint, R.id.featSoft, R.id.featHeavy, R.id.featArt2}) root.findViewById(id).setVisibility(View.GONE);
        root.setFocusable(false);
        root.setClickable(false);
        root.setVisibility(View.VISIBLE);
    }

    /** Home is on screen: draw the scene if there is none for this hour. */
    void start() {
        final LocalDateTime now = LocalDateTime.now();
        final int mood = LandscapeRules.mood(now.getHour());
        final long day = now.toLocalDate().toEpochDay();
        if (shown != null && mood == shownMood && day == shownDay) return;
        worker.execute(() -> {
            final Bitmap scene = LandscapeArt.render(W, H, now.getHour(), day);
            ui.post(() -> {
                shown = scene;
                shownMood = mood;
                shownDay = day;
                art.setImageBitmap(scene);
            });
        });
    }

    /** Home left the screen or memory is short. */
    void release() {
        art.setImageDrawable(null);
        shown = null;
        shownMood = -1;
        shownDay = -1;
    }
}
