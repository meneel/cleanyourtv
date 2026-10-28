package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.PorterDuff;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The Featured screen: a full-screen layer that turns to the next film or show every 9 seconds (new, top rated and
 * recommended on this TV's apps).
 *
 * Built for a GPU that cannot blend full-screen layers. Each picture is baked once, off the main thread, into three
 * bitmaps: the sharp one with its dark edges drawn in, and a soft and a heavy blur made with the platform's RenderScript
 * Gaussian blur (the routine blur libraries use on this Android version) on a downscaled copy, then stretched to the
 * screen. Nothing is blurred while the screen is drawn. Changing picture is a slide of two opaque layers, changing blur
 * level fades one overlay, and a layer that ends up covered is taken out of the draw. Everything is released when Home
 * leaves the screen.
 *
 * Levels: SHARP (the featured screen has focus: the dock only peeks under glass), SOFT (the dock is up: the picture is
 * still readable behind it) and HEAVY (the header, music controls and settings, has focus).
 */
final class FeaturedCarousel {
    static final int SHARP = 0, SOFT = 1, HEAVY = 2;
    private static final String TAG = "TvHomeFeatured";
    private static final long TURN_MS = 9000L, SLIDE_MS = 420L, FADE_MS = 260L, TEXT_OUT_MS = 130L, TEXT_IN_MS = 260L;
    private static final int KEEP_SHARP = 2;
    /** Going up to the featured screen: the blur fades first, then the dock slides and the text returns. */
    static final long SEQUENCE_MS = 240L;
    /** How much of the picture's height the blurred top and bottom bands cover. */
    static final float EDGE_TOP = 0.26f, EDGE_BOTTOM = 0.30f;
    /** Blur copies: the size they are blurred at (width in pixels), the Gaussian radius (1 to 25) and the darkening. */
    static final int SOFT_WIDTH = 384, SOFT_RADIUS = 7, SOFT_SHADE = 0x4D;
    static final int HEAVY_WIDTH = 128, HEAVY_RADIUS = 22, HEAVY_SHADE = 0x73;

    private final Activity activity;
    private final View root;
    private final ImageView art, art2, soft, heavy;
    private final TextView tag, title, meta, overview, providers;
    private final LinearLayout dots, info;
    private final View hint;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService baker = Executors.newSingleThreadExecutor();
    private final float density;
    private RenderScript rs;   // only while a picture is being baked (baker thread): its runtime holds several MB
    private List<FeaturedRules.Title> titles = new ArrayList<>();
    private final Map<Integer, Bitmap> sharp = new LinkedHashMap<Integer, Bitmap>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Entry<Integer, Bitmap> eldest) {
            return size() > KEEP_SHARP;
        }
    };
    private Bitmap[] softs = new Bitmap[0], heavies = new Bitmap[0];
    private boolean[] loading = new boolean[0];
    private int index, level = SHARP, generation, setGeneration;
    private boolean running, sliding;
    private Bitmap slideTarget;
    private final Runnable turn = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (level == SHARP) step(1);   // behind the dock or the header it holds still
            ui.postDelayed(this, TURN_MS);
        }
    };

    FeaturedCarousel(Activity activity, View root, Consumer<FeaturedRules.Title> onOpen) {
        this.activity = activity;
        this.root = root;
        this.density = activity.getResources().getDisplayMetrics().density;
        art = root.findViewById(R.id.featArt);
        art2 = root.findViewById(R.id.featArt2);
        soft = root.findViewById(R.id.featSoft);
        heavy = root.findViewById(R.id.featHeavy);
        tag = root.findViewById(R.id.featTag);
        title = root.findViewById(R.id.featTitle);
        meta = root.findViewById(R.id.featMeta);
        overview = root.findViewById(R.id.featOverview);
        providers = root.findViewById(R.id.featProviders);
        dots = root.findViewById(R.id.featDots);
        info = root.findViewById(R.id.featInfo);
        hint = root.findViewById(R.id.featHint);
        // Fading a group of text views asks for an off-screen copy of the whole group every frame; fade each one instead.
        info.forceHasOverlappingRendering(false);
        dots.forceHasOverlappingRendering(false);
        root.setOnClickListener(v -> {
            if (!titles.isEmpty() && level == SHARP) onOpen.accept(titles.get(index));
        });
    }

    boolean hasTitles() {
        return !titles.isEmpty();
    }

    boolean hasFocus() {
        return root.hasFocus();
    }

    View view() {
        return root;
    }

    void focus() {
        root.requestFocus();
    }

    /** The new list; the screen keeps showing what it was showing when that title is still in it. */
    void setTitles(List<FeaturedRules.Title> list) {
        String shown = titles.isEmpty() ? null : titles.get(index).key();
        titles = new ArrayList<>(list);
        dropBitmaps();
        softs = new Bitmap[titles.size()];
        heavies = new Bitmap[titles.size()];
        loading = new boolean[titles.size()];
        setGeneration++;
        index = 0;
        for (int i = 0; i < titles.size() && shown != null; i++) if (titles.get(i).key().equals(shown)) index = i;
        buildDots();
        if (titles.isEmpty()) {
            root.setVisibility(View.GONE);
            return;
        }
        root.setVisibility(View.VISIBLE);
        display(false);
    }

    void start() {
        if (running) return;
        running = true;
        ui.postDelayed(turn, TURN_MS);
        if (!titles.isEmpty() && sharp.isEmpty()) {   // pictures were released while Home was away: fetch them again
            softs = new Bitmap[titles.size()];
            heavies = new Bitmap[titles.size()];
            loading = new boolean[titles.size()];
            setGeneration++;
            display(false);
        }
    }

    void stop() {
        running = false;
        ui.removeCallbacks(turn);
    }

    /** Home left the screen (or memory is short): let go of every picture and the RenderScript context. */
    void release() {
        stop();
        finishSlide();
        setGeneration++;
        dropBitmaps();
        for (int i = 0; i < softs.length; i++) {
            softs[i] = null;
            heavies[i] = null;
            loading[i] = false;
        }
        art.setImageDrawable(null);
        art2.setImageDrawable(null);
        soft.setImageDrawable(null);
        heavy.setImageDrawable(null);
    }

    private void dropBitmaps() {
        sharp.clear();
    }

    /** SHARP, SOFT or HEAVY. At most one overlay is blended at a time, and covered layers are dropped when it ends. */
    void setLevel(int newLevel, boolean animate) {
        final int old = level;
        level = newLevel;
        final int g = ++generation;
        finishSlide();
        art.setVisibility(View.VISIBLE);
        final float textTo = newLevel == SHARP ? 1f : 0f;
        final Runnable settle = () -> {
            if (g != generation) return;
            art.setVisibility(newLevel == SHARP ? View.VISIBLE : View.INVISIBLE);
            soft.setVisibility(newLevel == SOFT ? View.VISIBLE : View.GONE);
            heavy.setVisibility(newLevel == HEAVY ? View.VISIBLE : View.GONE);
            soft.setAlpha(1f);
            heavy.setAlpha(1f);
        };
        soft.animate().cancel();
        heavy.animate().cancel();
        info.animate().cancel();
        hint.animate().cancel();
        if (!animate || old == newLevel) {
            info.animate().setStartDelay(0);
            hint.animate().setStartDelay(0);
            info.setAlpha(textTo);
            hint.setAlpha(textTo);
            settle.run();
            return;
        }
        // Coming back to SHARP the text waits for the blur to finish fading: one full-screen change at a time.
        long wait = newLevel == SHARP ? SEQUENCE_MS : 0L;
        info.animate().setStartDelay(wait).alpha(textTo).setDuration(newLevel == SHARP ? TEXT_IN_MS : TEXT_OUT_MS).start();
        hint.animate().setStartDelay(wait).alpha(textTo).setDuration(newLevel == SHARP ? TEXT_IN_MS : TEXT_OUT_MS).start();
        if (newLevel == SOFT) {
            soft.setVisibility(View.VISIBLE);
            if (old == SHARP) {
                soft.setAlpha(0f);
                soft.animate().alpha(1f).setDuration(FADE_MS).withEndAction(settle).start();
            } else {   // from heavy: the soft copy sits fully under the heavy one, which fades away
                soft.setAlpha(1f);
                heavy.setVisibility(View.VISIBLE);
                heavy.animate().alpha(0f).setDuration(FADE_MS).withEndAction(settle).start();
            }
        } else if (newLevel == HEAVY) {
            heavy.setVisibility(View.VISIBLE);
            heavy.setAlpha(0f);
            heavy.animate().alpha(1f).setDuration(FADE_MS).withEndAction(settle).start();
        } else {   // back to sharp: the overlay that is up fades away over the sharp picture
            ImageView top = old == HEAVY ? heavy : soft;
            top.setVisibility(View.VISIBLE);
            top.animate().alpha(0f).setDuration(FADE_MS).withEndAction(settle).start();
        }
    }

    /** Left and Right turn it by hand while it has focus and shows sharp; true when the key was used. */
    boolean handleKey(KeyEvent event) {
        if (!root.hasFocus() || level != SHARP || titles.size() < 2) return false;
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) step(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
        return true;
    }

    private void step(int by) {
        if (titles.size() < 2) return;
        index = (index + by + titles.size()) % titles.size();
        display(true, by);
        ui.removeCallbacks(turn);
        if (running) ui.postDelayed(turn, TURN_MS);
    }

    private void display(boolean animate) {
        display(animate, 1);
    }

    private void display(boolean animate, int direction) {
        if (titles.isEmpty()) return;
        final int shown = index;
        final FeaturedRules.Title t = titles.get(shown);
        finishSlide();
        Runnable setText = () -> {
            if (shown != index) return;
            tag.setText(t.tag.toUpperCase(java.util.Locale.getDefault()));
            title.setText(t.title);
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, FeaturedRules.titleSizeSp(t.title));
            overview.setMaxLines(FeaturedRules.overviewLines(t.title));
            meta.setText(FeaturedRules.meta(t));
            overview.setText(t.overview);
            providers.setText(FeaturedRules.providersLine(t.pkgs));
            paintDots();
        };
        Bitmap next = sharp.get(shown);
        if (animate && level == SHARP && next != null) {
            // Slide: the new picture glides in over the old one, which drifts the other way a little. Both are opaque.
            float w = root.getWidth() > 0 ? root.getWidth() : 1920f;
            sliding = true;
            slideTarget = next;
            art2.setImageBitmap(next);
            paintBlurs(shown);   // the blurred copies must follow the title too, or the dock shows the previous title's blur
            art2.setTranslationX(direction * w);
            art2.setVisibility(View.VISIBLE);
            art.animate().translationX(-direction * w * 0.25f).setDuration(SLIDE_MS).setInterpolator(new DecelerateInterpolator(1.4f)).start();
            art2.animate().translationX(0f).setDuration(SLIDE_MS).setInterpolator(new DecelerateInterpolator(1.4f)).withEndAction(this::finishSlide).start();
            info.animate().cancel();
            info.animate().alpha(0f).setDuration(TEXT_OUT_MS).withEndAction(() -> {
                setText.run();
                info.animate().alpha(1f).setDuration(TEXT_IN_MS).start();
            }).start();
        } else {
            setText.run();
            paint(shown);
        }
        load(shown);
        load((shown + 1) % titles.size());
    }

    /** Ends a slide at once: the incoming picture becomes the main one and the second layer goes. */
    private void finishSlide() {
        if (!sliding) return;
        sliding = false;
        art.animate().cancel();
        art2.animate().cancel();
        art.setImageBitmap(slideTarget);
        paintBlurs(index);
        art.setTranslationX(0f);
        art2.setTranslationX(0f);
        art2.setVisibility(View.GONE);
        art2.setImageDrawable(null);
        slideTarget = null;
    }

    private void paint(int i) {
        art.setImageBitmap(sharp.get(i));
        paintBlurs(i);
    }

    /** The soft and heavy copies for title {@code i}: always the title that is shown, whichever way it got there. */
    private void paintBlurs(int i) {
        if (i < 0 || i >= softs.length) return;
        soft.setImageBitmap(softs[i]);
        heavy.setImageBitmap(heavies[i]);
        warm(soft);
        warm(heavy);
    }

    /**
     * Draws a hidden layer once so its texture is on the GPU before it is needed: the first draw uploads it, and doing
     * that on the first frame of a transition was a visible hitch. Visible at near-zero alpha for one frame, then gone.
     */
    private void warm(ImageView layer) {
        if (level != SHARP || layer.getDrawable() == null) return;
        layer.setVisibility(View.VISIBLE);
        layer.setAlpha(0.01f);
        layer.post(() -> {
            if (level == SHARP) layer.setVisibility(View.GONE);
            layer.setAlpha(1f);
        });
    }

    private void load(int i) {
        if (sharp.containsKey(i) || loading[i]) return;
        loading[i] = true;
        final String url = titles.get(i).backdrop;
        final int gen = setGeneration;
        PosterCache.load(activity, url, 1280, 720, bitmap -> {
            if (gen != setGeneration || i >= loading.length) return;
            if (bitmap == null || !titles.get(i).backdrop.equals(url)) {
                loading[i] = false;
                return;
            }
            // Baking draws gradients, scales and blurs bitmaps: off the main thread.
            baker.execute(() -> {
                if (gen != setGeneration) return;
                Bitmap s0 = softs[i], h0 = heavies[i];
                try {
                    if (s0 == null || h0 == null) rs = RenderScript.create(activity.getApplicationContext());
                } catch (RuntimeException | LinkageError e) {
                    Log.w(TAG, "RenderScript unavailable, using the box blur: " + e);
                    rs = null;
                }
                try {
                    if (s0 == null) s0 = blurCopy(bitmap, SOFT_WIDTH, SOFT_RADIUS, SOFT_SHADE);
                    if (h0 == null) h0 = blurCopy(bitmap, HEAVY_WIDTH, HEAVY_RADIUS, HEAVY_SHADE);
                } finally {
                    if (rs != null) {
                        rs.destroy();   // its runtime is released the moment both blurs are made
                        rs = null;
                    }
                }
                final Bitmap s = s0, h = h0;
                // The sharp picture gets the soft blur drawn into its top and bottom edges, so the header and the dock read.
                final Bitmap baked = bakeSharp(bitmap, s);
                ui.post(() -> {
                    if (gen != setGeneration || i >= loading.length) return;
                    loading[i] = false;
                    if (baked != null) sharp.put(i, baked);
                    softs[i] = s;
                    heavies[i] = h;
                    if (i == index) {
                        paintBlurs(i);
                        if (!sliding) art.setImageBitmap(sharp.get(i));
                    }
                });
            });
        });
    }

    /** A copy of the picture with the three dark edges (left for the text, bottom for the dock, top for the header) drawn in. */
    static Bitmap bakeSharp(Bitmap src, Bitmap softBlur) {
        try {
            Bitmap out = src.copy(Bitmap.Config.RGB_565, true);
            if (out == null) return null;
            Canvas c = new Canvas(out);
            int w = out.getWidth(), h = out.getHeight();
            Paint p = new Paint();
            if (softBlur != null) {
                // Progressive blur: the soft copy, stretched to the picture, shows through a gradient mask that is fully
                // opaque at the top and bottom edges and clear in the middle (ComposeShader DST_IN keeps the copy where the
                // mask is opaque). The header sits on the top band and the dock on the bottom one.
                BitmapShader blurShader = new BitmapShader(softBlur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                android.graphics.Matrix fit = new android.graphics.Matrix();
                fit.setScale(w / (float) softBlur.getWidth(), h / (float) softBlur.getHeight());
                blurShader.setLocalMatrix(fit);
                Paint band = new Paint(Paint.FILTER_BITMAP_FLAG);
                band.setShader(new ComposeShader(blurShader,
                        new LinearGradient(0, 0, 0, h * EDGE_TOP, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP), PorterDuff.Mode.DST_IN));
                c.drawRect(0, 0, w, h * EDGE_TOP, band);
                band.setShader(new ComposeShader(blurShader,
                        new LinearGradient(0, h * (1f - EDGE_BOTTOM), 0, h, 0x00FFFFFF, 0xFFFFFFFF, Shader.TileMode.CLAMP), PorterDuff.Mode.DST_IN));
                c.drawRect(0, h * (1f - EDGE_BOTTOM), w, h, band);
            }
            p.setShader(new LinearGradient(0, 0, w * 0.78f, 0, new int[]{0xE6000000, 0x66000000, 0x00000000}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new LinearGradient(0, h, 0, h * 0.30f, new int[]{0xF2000000, 0x80000000, 0x00000000}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new LinearGradient(0, 0, 0, h * 0.24f, new int[]{0xB3000000, 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * A blurred, darkened copy: the picture scaled down to {@code width}, blurred with a Gaussian of {@code radius} pixels,
     * and stretched back up when drawn (so the blur is that radius times the stretch). 32-bit colour, so gradients do not
     * band. Uses RenderScript's blur, and the box blur if RenderScript is not available.
     */
    private Bitmap blurCopy(Bitmap src, int width, int radius, int shade) {
        try {
            int h = Math.max(8, Math.round(width * (float) src.getHeight() / src.getWidth()));
            Bitmap scaled = Bitmap.createScaledBitmap(src, width, h, true);
            Bitmap out = scaled.copy(Bitmap.Config.ARGB_8888, true);
            if (scaled != src && scaled != out) scaled.recycle();
            if (!renderScriptBlur(out, radius)) {
                int[] px = new int[width * h];
                out.getPixels(px, 0, width, 0, 0, width, h);
                BoxBlur.blur(px, width, h, Math.max(1, radius / 3), 3);
                out.setPixels(px, 0, width, 0, 0, width, h);
            }
            new Canvas(out).drawColor(shade << 24);
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** Runs on the baker thread. False when the platform has no RenderScript or it fails, so the caller falls back. */
    private boolean renderScriptBlur(Bitmap bmp, int radius) {
        if (rs == null) return false;
        try {
            Allocation in = Allocation.createFromBitmap(rs, bmp);
            Allocation out = Allocation.createTyped(rs, in.getType());
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            script.setRadius(Math.max(1, Math.min(25, radius)));
            script.setInput(in);
            script.forEach(out);
            out.copyTo(bmp);
            in.destroy();
            out.destroy();
            script.destroy();
            return true;
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "RenderScript blur unavailable, using the box blur: " + e);
            return false;
        }
    }

    private void buildDots() {
        dots.removeAllViews();
        for (int i = 0; i < titles.size(); i++) {
            View dot = new View(activity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (6 * density), (int) (6 * density));
            lp.setMarginStart(i == 0 ? 0 : (int) (6 * density));
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.feat_dot);
            dots.addView(dot);
        }
    }

    private void paintDots() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            lp.width = (int) ((i == index ? 22 : 6) * density);
            dot.setLayoutParams(lp);
            dot.setAlpha(i == index ? 1f : 0.45f);
        }
    }
}
