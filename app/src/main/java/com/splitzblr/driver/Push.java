package com.splitzblr.driver;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Sends this phone's push token to the Splitzblr server so new rides can be pushed to it. */
public class Push {
    static void saveToken(Context c, String token) {
        c.getSharedPreferences(AlertService.PREFS, Context.MODE_PRIVATE).edit().putString("fcm", token).apply();
        register(c);
    }

    /** Asks Firebase for the token (does nothing when the app was built without google-services.json). */
    static void fetchAndRegister(final Context c) {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new com.google.android.gms.tasks.OnCompleteListener<String>() {
                @Override public void onComplete(com.google.android.gms.tasks.Task<String> t) {
                    if (t.isSuccessful() && t.getResult() != null) saveToken(c, t.getResult());
                }
            });
        } catch (Throwable e) { }
    }

    static void register(final Context c) {
        final SharedPreferences sp = c.getSharedPreferences(AlertService.PREFS, Context.MODE_PRIVATE);
        final String phone = sp.getString("phone", ""), pin = sp.getString("pin", ""), api = sp.getString("api", ""), tok = sp.getString("fcm", "");
        if (!sp.getBoolean("online", false) || phone.isEmpty() || pin.isEmpty() || api.isEmpty() || tok.isEmpty()) return;
        new Thread(new Runnable() { public void run() {
            try {
                String u = api + "?action=pushreg&phone=" + URLEncoder.encode(phone, "UTF-8") + "&pin=" + URLEncoder.encode(pin, "UTF-8") + "&token=" + URLEncoder.encode(tok, "UTF-8");
                HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(10000);
                h.getResponseCode();
                h.disconnect();
            } catch (Exception e) { }
        } }).start();
    }
}
