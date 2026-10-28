package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BoxBlurTest {
    private static int gray(int v) {
        return 0xFF000000 | v << 16 | v << 8 | v;
    }

    @Test
    public void aHardEdgeBecomesAGradientAndTheAverageIsKept() {
        int w = 40, h = 4;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = gray(x < w / 2 ? 0 : 255);
        long before = 0;
        for (int p : px) before += p & 0xFF;
        BoxBlur.blur(px, w, h, 4, 3);
        int mid = px[w / 2 - 1] & 0xFF, left = px[0] & 0xFF, right = px[w - 1] & 0xFF;
        assertTrue("the edge is now in between: " + mid, mid > 20 && mid < 235);
        assertTrue("far from the edge it is untouched", left < 5 && right > 250);
        // monotonic across the edge: no ringing
        for (int x = 1; x < w; x++) assertTrue((px[x] & 0xFF) >= (px[x - 1] & 0xFF));
        long after = 0;
        for (int p : px) after += p & 0xFF;
        assertEquals("brightness is conserved", before, after, before * 0.02);
        assertEquals(0xFF, px[5] >>> 24);   // alpha stays opaque
    }

    @Test
    public void aFlatPictureStaysFlatAndBadArgumentsAreIgnored() {
        int[] px = new int[16];
        java.util.Arrays.fill(px, gray(90));
        BoxBlur.blur(px, 4, 4, 2, 3);
        for (int p : px) assertEquals(gray(90), p);
        int[] same = px.clone();
        BoxBlur.blur(px, 4, 4, 0, 3);
        BoxBlur.blur(px, 0, 4, 2, 3);
        BoxBlur.blur(new int[3], 4, 4, 2, 3);
        assertEquals(java.util.Arrays.toString(same), java.util.Arrays.toString(px));
    }
}
