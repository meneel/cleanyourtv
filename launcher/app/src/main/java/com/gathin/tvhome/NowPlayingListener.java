package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so Android lets the Now Playing card list other apps' media sessions, which it allows for an
 * enabled notification-listener component. It overrides nothing: notifications are never read
 * (NowPlayingNeverReadsNotificationsTest). Off until enabled with
 * `adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener`.
 */
public final class NowPlayingListener extends NotificationListenerService {
}
