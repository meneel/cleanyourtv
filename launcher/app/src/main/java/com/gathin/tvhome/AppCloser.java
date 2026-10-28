package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Closes the apps that are no longer on screen, except music apps, so the 1 GB stick keeps its memory for Home and for
 * whatever plays. It asks Android to kill each launchable app's background processes (KILL_BACKGROUND_PROCESSES):
 * an app on screen, or one with a foreground service such as playing music, is not touched, and one with no process is
 * a no-op. Fire OS has no app archiving (Android 15 added it), so closing is what is available.
 */
final class AppCloser {
    private static final String TAG = "TvHomeCloser";

    private AppCloser() {
    }

    /** Asks Android to close every launchable app that is not kept; returns how many packages were asked. */
    static int closeBackgroundApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        Set<String> pkgs = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (r.activityInfo != null) pkgs.add(r.activityInfo.packageName);
            }
        }
        int asked = 0;
        StringBuilder names = new StringBuilder();
        for (String pkg : pkgs) {
            if (AppCloserRules.keep(pkg, ctx.getPackageName())) continue;
            try {
                am.killBackgroundProcesses(pkg);
                asked++;
                names.append(pkg).append(' ');
            } catch (RuntimeException e) {
                Log.w(TAG, "could not close " + pkg + ": " + e);
            }
        }
        Log.i(TAG, "asked Android to close " + asked + ": " + names);
        return asked;
    }
}
