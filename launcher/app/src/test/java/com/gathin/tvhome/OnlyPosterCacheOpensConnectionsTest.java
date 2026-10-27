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

/** The launcher holds INTERNET for posters only: no class but PosterCache may open a connection. */
public class OnlyPosterCacheOpensConnectionsTest {
    private static final String[] NEEDLES = {"openConnection(", "HttpURLConnection", "HttpsURLConnection",
        "new Socket(", "SocketFactory", "OkHttp", "DefaultHttpClient"};

    static void scan(File f, List<String> offenders) throws IOException {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File kid : kids) scan(kid, offenders);
            return;
        }
        if (!f.getName().endsWith(".java") || f.getName().equals("PosterCache.java")) return;
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        for (String needle : NEEDLES) if (text.contains(needle)) offenders.add(f.getName() + ": " + needle);
    }

    @Test
    public void noOtherSourceOpensAConnection() throws IOException {
        File src = new File("src/main/java");
        assertTrue("run from the app module", src.isDirectory());
        List<String> offenders = new ArrayList<>();
        scan(src, offenders);
        assertEquals("only PosterCache may open connections: " + offenders, 0, offenders.size());
    }

    /** Control arm: the scan must fire on a file that does open a connection. */
    @Test
    public void theScanFindsAConnection() throws IOException {
        File dir = Files.createTempDirectory("scan").toFile();
        File bad = new File(dir, "Sneaky.java");
        Files.write(bad.toPath(), "class Sneaky { void f() throws Exception { new java.net.URL(\"https://x\").openConnection(); } }"
                .getBytes(StandardCharsets.UTF_8));
        List<String> offenders = new ArrayList<>();
        scan(dir, offenders);
        assertEquals(1, offenders.size());
        assertTrue(offenders.get(0).startsWith("Sneaky.java"));
        bad.delete();
        dir.delete();
    }
}
