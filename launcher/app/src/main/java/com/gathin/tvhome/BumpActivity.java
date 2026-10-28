package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;

/**
 * Takes Android's "previous app" slot. The app you just left is kept as the previous process, and on Fire OS an ordinary
 * app may not close that one (only the shell may). This activity has no window, lives in its own process, finishes at once
 * and takes that process with it, so the app you left moves down and AppCloser can close it. Together with denying the
 * apps RUN_IN_BACKGROUND (which stops their services, the other thing that holds them up) this is what makes
 * "close apps when I leave them" work. Started by MainActivity a moment after Home returns, only when close_apps is on.
 */
public final class BumpActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());   // do not leave a second process holding memory
    }
}
