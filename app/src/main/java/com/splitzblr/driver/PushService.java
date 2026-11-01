package com.splitzblr.driver;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Receives a new-ride push, even when the app is closed, and rings the full-screen alert. */
public class PushService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) { Push.saveToken(this, token); }

    @Override public void onMessageReceived(RemoteMessage m) {
        Map<String, String> d = m.getData();
        if (d == null || !"ride".equals(d.get("type"))) return;
        if (!getSharedPreferences(AlertService.PREFS, MODE_PRIVATE).getBoolean("online", false)) return;
        try { AlertService.start(this); } catch (Exception e) { }           // brings the background checker back, which repeats the ring
        if (AlertService.appVisible) return;
        String id = d.get("id") == null ? "" : d.get("id");
        AlertService.deliver(this, id, d.get("earn") == null ? "" : d.get("earn"), d.get("cat") == null ? "Ride" : d.get("cat"), d.get("pickup") == null ? "" : d.get("pickup"));
    }
}
