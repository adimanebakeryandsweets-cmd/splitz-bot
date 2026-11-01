package com.splitzblr.driver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Runs while the driver is logged in. Checks for new rides and rings a full-screen alert even when the app is closed. */
public class AlertService extends Service {
    static volatile boolean appVisible = false;
    static final String PREFS = "sbd";
    private static final String CH_RIDE = "rides_v2", CH_STAY = "online";
    private static final long POLL_MS = 7000, REPEAT_MS = 13000, SHOW_MS = 10000;

    private HandlerThread ht;
    private Handler h;
    private LocationManager lm;
    static volatile Location lastLoc;
    private static final Map<String, Long> muted = new HashMap<>();
    private volatile Location loc;
    private PowerManager.WakeLock wl;
    private final Map<String, Long> lastAlert = new HashMap<>();
    private boolean running = false;

    private final LocationListener ll = new LocationListener() {
        @Override public void onLocationChanged(Location l) { loc = l; lastLoc = l; }
        @Override public void onStatusChanged(String p, int s, Bundle b) { }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
    };

    static void start(Context c) {
        Intent i = new Intent(c, AlertService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH_STAY, "Online for rides", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel ch = new NotificationChannel(CH_RIDE, "New ride alerts", NotificationManager.IMPORTANCE_HIGH);
        Uri tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ch.setSound(tone, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 500});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);
    }

    static void clearAlerts(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 23) {
            for (android.service.notification.StatusBarNotification n : nm.getActiveNotifications()) { if (n.getId() != 2) nm.cancel(n.getId()); }
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!sp.getBoolean("online", false)) { stopSelf(); return START_NOT_STICKY; }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        channels(this);
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_STAY) : new Notification.Builder(this);
        Notification n = b.setContentTitle("Splitzblr: you are online")
                .setContentText("New ride alerts will ring here. Tap to open.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).setContentIntent(pi).build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(2, n);
        } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        if (!running) {
            running = true;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "splitzblr:alerts");
            wl.acquire(12 * 60 * 60 * 1000L);
            ht = new HandlerThread("sbd-alerts");
            ht.start();
            h = new Handler(ht.getLooper());
            startLocation();
            h.post(poll);
        }
        return START_STICKY;
    }

    private void startLocation() {
        try {
            lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 15000, 25, ll, ht.getLooper());
                        Location last = lm.getLastKnownLocation(p);
                        if (last != null && (loc == null || last.getTime() > loc.getTime())) { loc = last; lastLoc = last; }
                    }
                } catch (Exception e) { }
            }
        } catch (Exception e) { }
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            try { if (wl != null && !wl.isHeld()) wl.acquire(12 * 60 * 60 * 1000L); check(); } catch (Exception e) { }
            if (running && h != null) h.postDelayed(this, POLL_MS);
        }
    };

    private void check() throws Exception {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!sp.getBoolean("online", false)) { stopSelf(); return; }
        String phone = sp.getString("phone", ""), pin = sp.getString("pin", ""), api = sp.getString("api", "");
        Location l = loc;
        if (phone.isEmpty() || api.isEmpty() || l == null) return;
        String url = api + "?action=sync&phone=" + URLEncoder.encode(phone, "UTF-8") + "&pin=" + URLEncoder.encode(pin, "UTF-8")
                + "&lat=" + String.format(java.util.Locale.US, "%.5f", l.getLatitude()) + "&lng=" + String.format(java.util.Locale.US, "%.5f", l.getLongitude());
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setInstanceFollowRedirects(true);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) { String line; while ((line = r.readLine()) != null) sb.append(line); }
        JSONObject j = new JSONObject(sb.toString());
        if (!j.optBoolean("ok")) {
            if ("login".equals(j.optString("error"))) sp.edit().putBoolean("online", false).apply();
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        JSONArray rides = j.optJSONArray("rides");
        Set<String> open = new HashSet<>();
        long now = System.currentTimeMillis();
        if (rides != null && !j.optBoolean("busy")) {
            for (int i = 0; i < rides.length(); i++) {
                JSONObject r = rides.getJSONObject(i);
                String id = r.optString("id");
                open.add(id);
                Long last = lastAlert.get(id);
                if (appVisible) continue;                                   // the app itself is ringing
                if (last != null && now - last < REPEAT_MS) continue;       // each alert shows for 10 s, then comes back
                lastAlert.put(id, now);
                alert(nm, r);
            }
        }
        for (String id : new HashSet<>(lastAlert.keySet())) {               // a ride that was taken or expired stops ringing
            if (!open.contains(id)) { lastAlert.remove(id); nm.cancel(id.hashCode()); OverlayAlert.hideIf(id); }
        }
    }

    private void alert(NotificationManager nm, JSONObject r) {
        deliver(this, r.optString("id"), r.optString("earn", ""), r.optString("cat", "Ride"), r.optString("pickup", ""),
                details(r.optString("distanceKm", ""), r.optString("pickMin", ""), r.optString("drop", ""), r.optString("tripKm", ""), r.optString("tripEst", "")));
    }

    static void mute(String id) { synchronized (muted) { muted.put(id, System.currentTimeMillis()); } }

    /** Unlocked phone: pop-up over every app. Locked or screen off: full-screen notification over the lock screen. */
    static final String SEP = "\u0001";
    static String clean(String s) { return s == null || "null".equals(s) ? "" : s; }
    /** pickup km, pickup minutes, drop, trip km, trip minutes — packed for the pop-up and the notification */
    static String details(String pk, String pm, String drop, String tk, String tm) {
        return clean(pk) + SEP + clean(pm) + SEP + clean(drop) + SEP + clean(tk) + SEP + clean(tm);
    }
    static String[] parts(String more) {
        String[] p = (more == null ? "" : more).split(SEP, -1), o = new String[]{"", "", "", "", ""};
        for (int i = 0; i < 5 && i < p.length; i++) o[i] = p[i];
        return o;
    }
    static String plain(String pick, String more) {
        String[] p = parts(more);
        StringBuilder s = new StringBuilder();
        if (!pick.isEmpty()) s.append("Pickup: ").append(pick).append(p[0].isEmpty() ? "" : " · " + p[0] + " km").append(p[1].isEmpty() ? "" : " (" + p[1] + " min)");
        if (!p[2].isEmpty()) s.append(s.length() > 0 ? "\n" : "").append("Drop: ").append(p[2]).append(p[3].isEmpty() ? "" : " · " + p[3] + " km").append(p[4].isEmpty() ? "" : " (~" + p[4] + " min)");
        return s.toString();
    }

    static void deliver(Context c, String id, String earn, String cat, String pick, String more) {
        synchronized (muted) { Long m = muted.get(id); if (m != null && System.currentTimeMillis() - m < 10 * 60000L) return; }
        boolean locked = false;
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager) c.getSystemService(KEYGUARD_SERVICE);
            PowerManager pm = (PowerManager) c.getSystemService(POWER_SERVICE);
            locked = km.isKeyguardLocked() || !pm.isInteractive();
        } catch (Exception e) { }
        if (!locked && OverlayAlert.canShow(c)) OverlayAlert.show(c, id, earn, cat, pick, more);
        else showRide(c, id, earn, cat, pick, more);
    }

    static void showRide(Context c, String id, String earn, String cat, String pick, String more) {
        channels(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        String title = "New ride" + (earn.isEmpty() ? "" : " · Rs " + earn);
        String text = cat + "\n" + plain(pick, more);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH_RIDE) : new Notification.Builder(c);
        b.setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setTimeoutAfter(SHOW_MS);
        if (Build.VERSION.SDK_INT < 26) {
            b.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)).setVibrate(new long[]{0, 500, 250, 500});
        }
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT;                              // keeps ringing until it times out or is opened
        nm.notify(id.hashCode(), n);
    }

    @Override public void onDestroy() {
        running = false;
        try { if (lm != null) lm.removeUpdates(ll); } catch (Exception e) { }
        if (ht != null) ht.quitSafely();
        if (wl != null && wl.isHeld()) wl.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
