package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Home carousel: one card above the dock that turns to the next title every 8 seconds (new, top rated and
 * recommended films and shows), with Left and Right to turn it by hand and OK to open the title's page. It holds
 * still while it has focus. All motion is a fade; the pictures are fetched once through PosterCache.
 */
final class FeaturedCarousel {
    private static final long TURN_MS = 8000L;

    private final Activity activity;
    private final View card;
    private final ImageView art;
    private final TextView tag, title, meta, providers;
    private final LinearLayout dots, info;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float density;
    private List<FeaturedRules.Title> titles = new ArrayList<>();
    private Bitmap[] arts = new Bitmap[0];
    private boolean[] asked = new boolean[0];
    private int index;
    private boolean running, wanted;
    private final Runnable turn = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (!card.hasFocus()) step(1);
            ui.postDelayed(this, TURN_MS);
        }
    };

    FeaturedCarousel(Activity activity, View card, Consumer<FeaturedRules.Title> onOpen) {
        this.activity = activity;
        this.card = card;
        this.density = activity.getResources().getDisplayMetrics().density;
        art = card.findViewById(R.id.featArt);
        tag = card.findViewById(R.id.featTag);
        title = card.findViewById(R.id.featTitle);
        meta = card.findViewById(R.id.featMeta);
        providers = card.findViewById(R.id.featProviders);
        dots = card.findViewById(R.id.featDots);
        info = card.findViewById(R.id.featInfo);
        final float radius = 22f * density;
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        card.setOnClickListener(v -> {
            if (!titles.isEmpty()) onOpen.accept(titles.get(index));
        });
    }

    /** The new list; the card keeps showing what it was showing when the same title is still in it. */
    void setTitles(List<FeaturedRules.Title> list) {
        String shown = titles.isEmpty() ? null : titles.get(index).key();
        titles = new ArrayList<>(list);
        arts = new Bitmap[titles.size()];
        asked = new boolean[titles.size()];
        index = 0;
        for (int i = 0; i < titles.size() && shown != null; i++) if (titles.get(i).key().equals(shown)) index = i;
        buildDots();
        if (titles.isEmpty()) {
            card.setVisibility(View.GONE);
            return;
        }
        display(false);
        if (wanted) revealIfHidden();
    }

    void start() {
        if (running) return;
        running = true;
        ui.postDelayed(turn, TURN_MS);
    }

    void stop() {
        running = false;
        ui.removeCallbacks(turn);
    }

    /** The shelf is showing: put the card at {@code y} (root coordinates) and fade it in. */
    void show(float y) {
        wanted = true;
        card.setY(y);
        revealIfHidden();
    }

    /** The shelf is gone (focus went below the dock): fade out, unless the card itself holds focus. */
    void hide() {
        wanted = false;
        if (card.hasFocus() || card.getVisibility() != View.VISIBLE) return;
        card.animate().cancel();
        card.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (!wanted && !card.hasFocus()) card.setVisibility(View.GONE);
        }).start();
    }

    private void revealIfHidden() {
        if (titles.isEmpty()) return;
        card.animate().cancel();
        if (card.getVisibility() != View.VISIBLE) {
            card.setAlpha(0f);
            card.setVisibility(View.VISIBLE);
        }
        card.animate().alpha(1f).setDuration(260).start();
    }

    /** Left and Right turn the card while it has focus; true when the key was used. */
    boolean handleKey(KeyEvent event) {
        if (!card.hasFocus() || titles.size() < 2) return false;
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) step(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
        return true;
    }

    private void step(int by) {
        if (titles.size() < 2) return;
        index = (index + by + titles.size()) % titles.size();
        display(true);
        ui.removeCallbacks(turn);
        if (running) ui.postDelayed(turn, TURN_MS);
    }

    private void display(boolean animate) {
        if (titles.isEmpty()) return;
        FeaturedRules.Title t = titles.get(index);
        Runnable apply = () -> {
            tag.setText(t.tag.toUpperCase(java.util.Locale.getDefault()));
            title.setText(t.title);
            meta.setText(FeaturedRules.meta(t));
            providers.setText(FeaturedRules.providersLine(t.pkgs));
            if (arts[index] != null) art.setImageBitmap(arts[index]);
            else art.setImageDrawable(null);
            paintDots();
        };
        if (animate) {
            info.animate().cancel();
            art.animate().cancel();
            art.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                apply.run();
                art.animate().alpha(1f).setDuration(420).start();
            }).start();
            info.animate().alpha(0f).setDuration(180).withEndAction(() -> info.animate().alpha(1f).setDuration(420).start()).start();
        } else {
            apply.run();
        }
        load(index);
        load((index + 1) % titles.size());
    }

    private void load(int i) {
        if (asked[i] || arts[i] != null) return;
        asked[i] = true;
        final String url = titles.get(i).backdrop;
        PosterCache.load(activity, url, (int) (372 * density), (int) (210 * density), bitmap -> {
            if (bitmap == null || i >= arts.length || !titles.get(i).backdrop.equals(url)) return;
            arts[i] = bitmap;
            if (i == index) art.setImageBitmap(bitmap);
        });
    }

    private void buildDots() {
        dots.removeAllViews();
        for (int i = 0; i < titles.size(); i++) {
            View dot = new View(activity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (6 * density), (int) (6 * density));
            lp.setMarginStart(i == 0 ? 0 : (int) (5 * density));
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.feat_dot);
            dots.addView(dot);
        }
    }

    private void paintDots() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            lp.width = (int) ((i == index ? 18 : 6) * density);
            dot.setLayoutParams(lp);
            dot.setAlpha(i == index ? 1f : 0.45f);
        }
    }
}
