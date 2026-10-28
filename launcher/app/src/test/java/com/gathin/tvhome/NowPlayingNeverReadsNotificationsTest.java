package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Android only lets an app read other apps' media sessions if it has an enabled notification-listener component.
 * NowPlayingListener is that component and nothing else: no source file may read or react to notifications.
 */
public class NowPlayingNeverReadsNotificationsTest {
    private static final String[] NEEDLES = {"onNotificationPosted", "onNotificationRemoved", "getActiveNotifications",
        "StatusBarNotification", "getNotification(", "cancelNotification", "NotificationListenerService."};

    static void scan(File f, List<String> offenders) throws IOException {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File kid : kids) scan(kid, offenders);
            return;
        }
        if (!f.getName().endsWith(".java")) return;
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        for (String needle : NEEDLES) if (text.contains(needle)) offenders.add(f.getName() + ": " + needle);
    }

    @Test
    public void noSourceTouchesNotifications() throws IOException {
        File src = new File("src/main/java");
        assertTrue("run from the app module", src.isDirectory());
        List<String> offenders = new ArrayList<>();
        scan(src, offenders);
        assertEquals("nothing may read notifications: " + offenders, 0, offenders.size());
    }

    /** Control arm: the scan must fire on a listener that does read them. */
    @Test
    public void theScanFindsAListenerThatReads() throws IOException {
        File dir = Files.createTempDirectory("scan2").toFile();
        File bad = new File(dir, "Nosy.java");
        Files.write(bad.toPath(), "class Nosy { void onNotificationPosted(Object sbn) { } }".getBytes(StandardCharsets.UTF_8));
        List<String> offenders = new ArrayList<>();
        scan(dir, offenders);
        assertEquals(1, offenders.size());
        bad.delete();
        dir.delete();
    }
}
