package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class LandscapeRulesTest {
    @Test
    public void everyHourHasAMoodAndEveryMoodHasThreeSkyColoursAndFourRidges() {
        boolean[] seen = new boolean[4];
        for (int h = 0; h < 24; h++) {
            int m = LandscapeRules.mood(h);
            assertTrue(m >= 0 && m <= 3);
            seen[m] = true;
            assertEquals(3, LandscapeRules.sky(m).length);
            assertEquals(4, LandscapeRules.ridges(m).length);
        }
        for (boolean s : seen) assertTrue(s);
        assertEquals(LandscapeRules.NIGHT, LandscapeRules.mood(23));
        assertEquals(LandscapeRules.NIGHT, LandscapeRules.mood(3));
        assertEquals(LandscapeRules.DAWN, LandscapeRules.mood(6));
        assertEquals(LandscapeRules.DAY, LandscapeRules.mood(12));
        assertEquals(LandscapeRules.DUSK, LandscapeRules.mood(18));
        assertTrue(LandscapeRules.stars(LandscapeRules.NIGHT));
        assertTrue(!LandscapeRules.stars(LandscapeRules.DAY));
    }

    @Test
    public void ridgesAreDarkerTowardsTheViewerAndStayInRange() {
        for (int m = 0; m < 4; m++) {
            int[] r = LandscapeRules.ridges(m);
            for (int i = 1; i < r.length; i++) assertTrue("ridge " + i + " darker in mood " + m, (r[i] & 0xFF) + ((r[i] >> 8) & 0xFF) + ((r[i] >> 16) & 0xFF) < (r[i - 1] & 0xFF) + ((r[i - 1] >> 8) & 0xFF) + ((r[i - 1] >> 16) & 0xFF));
        }
        for (int layer = 0; layer < 4; layer++) {
            for (float x = 0; x <= 1f; x += 0.05f) {
                float v = LandscapeRules.ridge(x, layer, 12345L);
                assertTrue(v >= 0f && v <= 1f);
            }
        }
        assertNotEquals(LandscapeRules.ridge(0.3f, 1, 1L), LandscapeRules.ridge(0.3f, 1, 2L), 1e-6);   // a new seed moves the mountains
    }
}
