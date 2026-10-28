package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import java.time.LocalTime;
import java.util.Random;

/**
 * The Aurora screensaver: three large soft colour fields drift and breathe over near-black, in colours that follow
 * the time of day, with a big thin clock, the date and the weather bottom left. The fields are views with a
 * hardware layer, so animating them only moves a texture: no per-frame redraw, no images, no downloads. The whole
 * clock block wanders a little every minute so no pixel stays lit. Any key wakes the TV back to where it was.
 */
public class AuroraDream extends DreamService {
    private static final long DRIFT_MIN_MS = 45_000L, DRIFT_MAX_MS = 80_000L;
    private static final long CLOCK_DRIFT_MS = 90_000L, PALETTE_CHECK_MS = 5 * 60_000L;
    private static final int COLOUR_STEPS = 20;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final ArgbEvaluator mix = new ArgbEvaluator();
    private View[] fields;
    private GradientDrawable[] glows;
    private int[] colours = new int[3];
    private float intensity = 1f;
    private View content, root;
    private TextView weather;
    private boolean running;
    private boolean weatherOn, fahrenheit, weatherIp;
    private String weatherCity = "";
    private double weatherLat, weatherLon;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_aurora);
        root = findViewById(R.id.auroraRoot);
        content = findViewById(R.id.auroraContent);
        weather = findViewById(R.id.auroraWeather);
        fields = new View[]{findViewById(R.id.fieldA), findViewById(R.id.fieldB), findViewById(R.id.fieldC)};
        glows = new GradientDrawable[3];
        int hour = LocalTime.now().getHour();
        int[] palette = AuroraRules.palette(hour);
        intensity = AuroraRules.intensity(hour);
        for (int i = 0; i < 3; i++) {
            glows[i] = new GradientDrawable();
            glows[i].setGradientType(GradientDrawable.RADIAL_GRADIENT);
            fields[i].setBackground(glows[i]);
            fields[i].setLayerType(View.LAYER_TYPE_HARDWARE, null);
            colours[i] = palette[i];
            paint(i, colours[i]);
        }
        try {
            Config cfg = Config.load(this);
            weatherOn = cfg.weather;
            fahrenheit = cfg.weatherFahrenheit;
            weatherIp = cfg.weatherIp;
            weatherCity = cfg.weatherCity;
            weatherLat = cfg.weatherLat;
            weatherLon = cfg.weatherLon;
        } catch (RuntimeException e) {
            weatherOn = false;
        }
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            for (int i = 0; i < 3; i++) glows[i].setGradientRadius(fields[i].getWidth() / 2f);
        });
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        running = true;
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(2500).start();
        root.post(() -> {
            for (int i = 0; i < 3; i++) {
                fields[i].setPivotX(fields[i].getWidth() / 2f);
                fields[i].setPivotY(fields[i].getHeight() / 2f);
                // start spread across the screen, then drift
                fields[i].setTranslationX(AuroraRules.between(-0.1f, 0.8f, random.nextFloat()) * root.getWidth());
                fields[i].setTranslationY(AuroraRules.between(-0.2f, 0.6f, random.nextFloat()) * root.getHeight());
                fields[i].setAlpha(0f);
                fields[i].animate().alpha(intensity).setDuration(4000).start();
                drift(i);
            }
            driftClock();
        });
        handler.postDelayed(this::tickPalette, PALETTE_CHECK_MS);
        updateWeather();
    }

    @Override
    public void onDreamingStopped() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (fields != null) for (View f : fields) f.animate().cancel();
        if (content != null) content.animate().cancel();
        super.onDreamingStopped();
    }

    /** One field glides to a new spot and size over 45 to 80 s, then picks another: slow enough to read as light. */
    private void drift(int i) {
        if (!running) return;
        View f = fields[i];
        float scale = AuroraRules.between(1.5f, 2.6f, random.nextFloat());
        float x = AuroraRules.between(-0.2f, 0.85f, random.nextFloat()) * root.getWidth() - f.getWidth() / 2f * 0.2f;
        float y = AuroraRules.between(-0.3f, 0.7f, random.nextFloat()) * root.getHeight() - f.getHeight() / 2f * 0.2f;
        long ms = (long) AuroraRules.between(DRIFT_MIN_MS, DRIFT_MAX_MS, random.nextFloat());
        f.animate().translationX(x).translationY(y).scaleX(scale).scaleY(scale)
                .setDuration(ms).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> drift(i)).start();
    }

    /** The clock block wanders within a margin, so the same pixels are not lit for hours. */
    private void driftClock() {
        if (!running) return;
        float dx = AuroraRules.between(-40f, 120f, random.nextFloat()) * getResources().getDisplayMetrics().density;
        float dy = AuroraRules.between(-90f, 24f, random.nextFloat()) * getResources().getDisplayMetrics().density;
        content.animate().translationX(dx).translationY(dy).setDuration(CLOCK_DRIFT_MS)
                .setInterpolator(new LinearInterpolator()).withEndAction(this::driftClock).start();
    }

    private void paint(int i, int colour) {
        int c = colour & 0x00FFFFFF;
        glows[i].setColors(new int[]{0xB0000000 | c, 0x40000000 | c, 0x00000000 | c});
    }

    /** Every few minutes: refresh the weather and, if the hour moved to another mood, fade to its colours. */
    private void tickPalette() {
        if (!running) return;
        int hour = LocalTime.now().getHour();
        final int[] to = AuroraRules.palette(hour);
        final float target = AuroraRules.intensity(hour);
        final int[] from = colours.clone();
        if (from[0] != to[0] || from[1] != to[1] || from[2] != to[2]) {
            for (int step = 1; step <= COLOUR_STEPS; step++) {
                final float f = (float) step / COLOUR_STEPS;
                handler.postDelayed(() -> {
                    for (int i = 0; i < 3; i++) {
                        colours[i] = (int) mix.evaluate(f, from[i], to[i]);
                        paint(i, colours[i]);
                    }
                }, step * 1500L);
            }
        }
        if (target != intensity) {
            intensity = target;
            for (View f : fields) f.animate().alpha(intensity).setDuration(20_000).start();
        }
        updateWeather();
        handler.postDelayed(this::tickPalette, PALETTE_CHECK_MS);
    }

    private void updateWeather() {
        if (!weatherOn) return;
        showWeather();
        WeatherStore.refreshIfDue(this, weatherIp, weatherLat, weatherLon, this::showWeather);
    }

    private void showWeather() {
        WeatherRules.Reading r = WeatherStore.reading();
        if (r == null || !running) return;
        WeatherRules.Place p = WeatherStore.place();
        String city = p != null && !p.city.isEmpty() ? p.city : weatherCity;
        weather.setText(WeatherRules.line(city, r, fahrenheit));
        weather.setVisibility(View.VISIBLE);
    }
}
