package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AuroraRulesTest {
    @Test
    public void everyHourHasThreeDistinctColours() {
        for (int h = 0; h < 24; h++) {
            int[] p = AuroraRules.palette(h);
            assertEquals(3, p.length);
            assertNotEquals(p[0], p[1]);
            assertNotEquals(p[1], p[2]);
            assertEquals("opaque", 0xFF000000, p[0] & 0xFF000000);
        }
    }

    @Test
    public void paletteChangesWithTheDayAndIsDimmerAtNight() {
        assertNotEquals(AuroraRules.palette(2)[0], AuroraRules.palette(12)[0]);
        assertNotEquals(AuroraRules.palette(7)[0], AuroraRules.palette(19)[0]);
        assertEquals(0.35f, AuroraRules.intensity(1), 0f);
        assertEquals(0.6f, AuroraRules.intensity(22), 0f);
        assertEquals(1f, AuroraRules.intensity(12), 0f);
        for (int h = 0; h < 24; h++) assertTrue(AuroraRules.intensity(h) >= 0.35f && AuroraRules.intensity(h) <= 1f);
    }

    @Test
    public void betweenStaysInsideItsBounds() {
        assertEquals(10f, AuroraRules.between(10, 20, -3f), 0f);
        assertEquals(20f, AuroraRules.between(10, 20, 9f), 0f);
        assertEquals(15f, AuroraRules.between(10, 20, 0.5f), 0f);
    }
}
