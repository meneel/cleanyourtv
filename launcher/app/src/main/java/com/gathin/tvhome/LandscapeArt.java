package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/** Draws the landscape Home picture: a sky for the hour, sun or moon (stars at night) and four layered mountain ridges. */
final class LandscapeArt {
    private LandscapeArt() {
    }

    /** {@code seed} (the day) moves the mountains and the stars, so the scene changes from one day to the next. */
    static Bitmap render(int w, int h, int hour, long seed) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int mood = LandscapeRules.mood(hour);
        int[] sky = LandscapeRules.sky(mood);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.66f, sky, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Random rnd = new Random(seed);
        if (LandscapeRules.stars(mood)) {
            for (int i = 0; i < 150; i++) {
                p.setColor(0xFFFFFFFF);
                p.setAlpha(60 + rnd.nextInt(170));
                c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.52f, 0.6f + rnd.nextFloat() * 1.3f, p);
            }
        }
        // The sun or moon: high by day and night, low at dawn and dusk, with a soft glow.
        float bx = w * (0.24f + 0.52f * rnd.nextFloat());
        float by = h * (mood == LandscapeRules.DAWN || mood == LandscapeRules.DUSK ? 0.50f : 0.20f);
        int body = LandscapeRules.bodyColor(mood);
        p.setShader(new RadialGradient(bx, by, h * 0.34f, new int[]{(body & 0x00FFFFFF) | 0x73000000, (body & 0x00FFFFFF) | 0x1F000000, body & 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(bx, by, h * 0.34f, p);
        p.setShader(null);
        p.setColor(body);
        c.drawCircle(bx, by, h * 0.042f, p);
        // Four ridges, far to near; haze between them so the far ones recede.
        int[] ridges = LandscapeRules.ridges(mood);
        for (int k = 0; k < 4; k++) {
            float base = h * (0.58f + 0.075f * k), amp = h * (0.135f - 0.02f * k);
            Path path = new Path();
            path.moveTo(0, h);
            int steps = 72;
            for (int s = 0; s <= steps; s++) {
                float x = (float) s / steps;
                path.lineTo(x * w, base - amp * LandscapeRules.ridge(x, k, seed));
            }
            path.lineTo(w, h);
            path.close();
            int top = ridges[k], bottom = darken(ridges[k], 0.55f);
            p.setShader(new LinearGradient(0, base - amp, 0, h, top, bottom, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            p.setShader(null);
            if (k < 3) {
                int haze = sky[2] & 0x00FFFFFF;
                p.setShader(new LinearGradient(0, base - amp * 0.4f, 0, base + h * 0.07f, haze | 0x4D000000, haze, Shader.TileMode.CLAMP));
                c.drawRect(0, base - amp * 0.4f, w, base + h * 0.07f, p);
                p.setShader(null);
            }
        }
        // Dark edges: the bottom for the dock, the top for the header.
        p.setShader(new LinearGradient(0, h * 0.60f, 0, h, 0x00000000, 0xD9000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.60f, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.24f, 0x80000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.24f, p);
        return bmp;
    }

    private static int darken(int color, float by) {
        int r = (int) (((color >> 16) & 0xFF) * by), g = (int) (((color >> 8) & 0xFF) * by), b = (int) ((color & 0xFF) * by);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
