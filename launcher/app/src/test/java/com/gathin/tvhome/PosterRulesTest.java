package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Which poster addresses may be fetched, how they are cached and evicted. */
public class PosterRulesTest {
    @Test
    public void httpsWithAHostIsNetwork() {
        assertEquals(PosterRules.NETWORK, PosterRules.kind("https://img.example.com/p/1.jpg"));
        assertEquals(PosterRules.NETWORK, PosterRules.kind("HTTPS://Img.Example.com/p?x=1#y"));
        assertEquals(PosterRules.NETWORK, PosterRules.kind("https://img.example.com"));
    }

    @Test
    public void plainHttpAndOtherSchemesAreRefused() {
        assertEquals(PosterRules.NONE, PosterRules.kind("http://img.example.com/1.jpg"));
        assertEquals(PosterRules.NONE, PosterRules.kind("file:///sdcard/x.jpg"));
        assertEquals(PosterRules.NONE, PosterRules.kind("android.resource://app.a/1"));
        assertEquals(PosterRules.NONE, PosterRules.kind("ftp://host/x"));
        assertEquals(PosterRules.NONE, PosterRules.kind("javascript:alert(1)"));
    }

    @Test
    public void userInfoEmptyHostAndControlCharactersAreRefused() {
        assertEquals(PosterRules.NONE, PosterRules.kind("https://user:pw@host/x.jpg"));
        assertEquals(PosterRules.NONE, PosterRules.kind("https://host@evil.example/x.jpg"));
        assertEquals(PosterRules.NONE, PosterRules.kind("https:///x.jpg"));
        assertEquals(PosterRules.NONE, PosterRules.kind("https://?x"));
        assertEquals(PosterRules.NONE, PosterRules.kind("https://host/a b.jpg"));
        assertEquals(PosterRules.NONE, PosterRules.kind("https://host/a\nb.jpg"));
    }

    @Test
    public void nullEmptyAndTooLongAreRefused() {
        assertEquals(PosterRules.NONE, PosterRules.kind(null));
        assertEquals(PosterRules.NONE, PosterRules.kind(""));
        StringBuilder sb = new StringBuilder("https://host/");
        while (sb.length() <= PosterRules.MAX_URI) sb.append('a');
        assertEquals(PosterRules.NONE, PosterRules.kind(sb.toString()));
        assertEquals(PosterRules.NETWORK, PosterRules.kind(sb.substring(0, PosterRules.MAX_URI)));
    }

    @Test
    public void contentUrisAreAllowedButNeedAnAuthority() {
        assertEquals(PosterRules.CONTENT, PosterRules.kind("content://app.a.provider/posters/1"));
        assertEquals(PosterRules.NONE, PosterRules.kind("content://"));
    }

    @Test
    public void keyIsStableHexAndDiffersPerAddress() {
        String a = PosterRules.key("https://host/a.jpg");
        assertEquals(64, a.length());
        for (char c : a.toCharArray()) assertTrue("hex digit: " + c, Character.digit(c, 16) >= 0 && !Character.isUpperCase(c));
        assertEquals(a, PosterRules.key("https://host/a.jpg"));
        assertFalse(a.equals(PosterRules.key("https://host/b.jpg")));
    }

    private static List<PosterRules.Cached> files(long... sizeAndModified) {
        List<PosterRules.Cached> out = new ArrayList<>();
        for (int i = 0; i < sizeAndModified.length; i += 2) {
            out.add(new PosterRules.Cached("f" + (i / 2), sizeAndModified[i], sizeAndModified[i + 1]));
        }
        return out;
    }

    @Test
    public void nothingIsEvictedWithinTheLimits() {
        assertEquals(Collections.<String>emptyList(), PosterRules.evict(files(10, 1, 10, 2), 2, 100));
    }

    @Test
    public void oldestGoFirstWhenThereAreTooManyFiles() {
        // modified times: f0=30, f1=10, f2=20 -> oldest is f1, then f2
        assertEquals(Arrays.asList("f1"), PosterRules.evict(files(5, 30, 5, 10, 5, 20), 2, 1000));
        assertEquals(Arrays.asList("f1", "f2"), PosterRules.evict(files(5, 30, 5, 10, 5, 20), 1, 1000));
    }

    @Test
    public void oldestGoFirstWhenTheTotalSizeIsTooBig() {
        assertEquals(Arrays.asList("f1"), PosterRules.evict(files(60, 30, 50, 10, 40, 20), 10, 100));
        assertEquals(Arrays.asList("f1", "f2"), PosterRules.evict(files(60, 30, 50, 10, 40, 20), 10, 60));
    }
}
