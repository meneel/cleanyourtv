package com.gathin.tvhome;

/**
 * A separable box blur on ARGB pixels (three passes approximate a Gaussian). The fallback for the platform's RenderScript
 * blur, and plain Java so it is unit-tested.
 */
final class BoxBlur {
    private BoxBlur() {
    }

    /** Blurs {@code px} (w*h ARGB ints) in place: {@code passes} box blurs of {@code radius} pixels in each direction. */
    static void blur(int[] px, int w, int h, int radius, int passes) {
        if (radius < 1 || w < 1 || h < 1 || px.length < w * h) return;
        int[] tmp = new int[px.length];
        for (int p = 0; p < passes; p++) {
            line(px, tmp, w, h, radius, true);
            line(tmp, px, w, h, radius, false);
        }
    }

    /** One direction: each output pixel is the average of the 2r+1 pixels around it, edges clamped. */
    private static void line(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int outer = horizontal ? h : w, inner = horizontal ? w : h, step = horizontal ? 1 : w, jump = horizontal ? w : 1;
        int window = 2 * r + 1;
        for (int o = 0; o < outer; o++) {
            int base = o * jump;
            long a = 0, rr = 0, g = 0, b = 0;
            for (int k = -r; k <= r; k++) {
                int c = src[base + Math.min(inner - 1, Math.max(0, k)) * step];
                a += c >>> 24; rr += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF;
            }
            for (int i = 0; i < inner; i++) {
                dst[base + i * step] = (int) (a / window) << 24 | (int) (rr / window) << 16 | (int) (g / window) << 8 | (int) (b / window);
                int add = src[base + Math.min(inner - 1, i + r + 1) * step], drop = src[base + Math.max(0, i - r) * step];
                a += (add >>> 24) - (drop >>> 24);
                rr += ((add >> 16) & 0xFF) - ((drop >> 16) & 0xFF);
                g += ((add >> 8) & 0xFF) - ((drop >> 8) & 0xFF);
                b += (add & 0xFF) - (drop & 0xFF);
            }
        }
    }
}
