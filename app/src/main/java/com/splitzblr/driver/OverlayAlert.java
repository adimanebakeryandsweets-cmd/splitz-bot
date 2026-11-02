package com.splitzblr.driver;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/** A ride pop-up drawn over every other app (and the home screen) with a ringtone, like a taxi-app driver alert. */
final class OverlayAlert {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long SHOW_MS = 10000;
    private static View view;
    private static String curId = "";
    private static Ringtone ring;
    private static Vibrator vib;
    private static final Runnable AUTO = new Runnable() { public void run() { hide(); } };

    static boolean canShow(Context c) { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c); }

    static void show(final Context c, final String id, final String earn, final String cat, final String pick, final String more) {
        final Context app = c.getApplicationContext();
        MAIN.post(new Runnable() { public void run() { build(app, id, earn, cat, pick, more); } });
    }

    static void hideIf(final String id) {
        MAIN.post(new Runnable() { public void run() { if (view != null && id.equals(curId)) hide(); } });
    }

    private static View infoRow(Context c, String km, String text, boolean hi) {
        LinearLayout r = new LinearLayout(c); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2); rl.topMargin = dp(c, 8); r.setLayoutParams(rl);
        if (hi) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor("#1B3A6B")); g.setCornerRadius(dp(c, 12)); g.setStroke(dp(c, 2), Color.parseColor("#F1D77A")); r.setBackground(g); }
        TextView a = new TextView(c); a.setText(km.isEmpty() ? "" : km + " KM"); a.setTextColor(Color.parseColor(hi ? "#F1D77A" : "#FFFFFF")); a.setTextSize(hi ? 24 : 20); a.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(a, new LinearLayout.LayoutParams(dp(c, 96), -2));
        TextView b = new TextView(c); b.setText(text); b.setTextColor(hi ? Color.WHITE : Color.parseColor("#B9D6EA")); b.setTextSize(hi ? 18 : 16); if (hi) b.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    private static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private static void build(final Context c, final String id, String earn, String cat, String pick, String more) {
        if (view != null) { if (id.equals(curId)) return; hide(); }
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0B1F40")); bg.setCornerRadius(dp(c, 20)); bg.setStroke(dp(c, 3), Color.parseColor("#D4AF37"));
        box.setBackground(bg);

        String[] p = AlertService.parts(more);
        TextView t1 = new TextView(c); t1.setText("NEW RIDE REQUEST"); t1.setTextColor(Color.parseColor("#F1D77A")); t1.setTextSize(13); t1.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t1);
        TextView t2 = new TextView(c); t2.setText(earn.isEmpty() ? cat : "Rs " + earn); t2.setTextColor(Color.parseColor("#0B1426")); t2.setTextSize(36); t2.setTypeface(Typeface.DEFAULT_BOLD);
        t2.setPadding(dp(c, 16), dp(c, 2), dp(c, 16), dp(c, 2));
        GradientDrawable pb = new GradientDrawable(); pb.setColor(Color.parseColor("#F1D77A")); pb.setCornerRadius(dp(c, 12)); t2.setBackground(pb);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2); pp.topMargin = dp(c, 6); box.addView(t2, pp);
        if (!earn.isEmpty()) {
            TextView tc = new TextView(c); tc.setText(cat);
            if (cat.startsWith("Outstation") || cat.contains(" stops")) {            // one way / round trip and the destination state, highlighted
                tc.setTextColor(Color.parseColor("#1A1200")); tc.setTextSize(17); tc.setTypeface(Typeface.DEFAULT_BOLD);
                tc.setPadding(dp(c, 12), dp(c, 5), dp(c, 12), dp(c, 5));
                GradientDrawable cb = new GradientDrawable(); cb.setColor(Color.parseColor("#FFD54A")); cb.setCornerRadius(dp(c, 10)); tc.setBackground(cb);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2); cp.topMargin = dp(c, 6); cp.bottomMargin = dp(c, 2); box.addView(tc, cp);
            } else { tc.setTextColor(Color.parseColor("#B9D6EA")); tc.setTextSize(15); box.addView(tc); }
        }
        if (!pick.isEmpty()) box.addView(infoRow(c, p[0], "Pickup: " + pick + (p[1].isEmpty() ? "" : " (" + p[1] + " min)"), false));
        if (!p[2].isEmpty()) box.addView(infoRow(c, p[3], "Drop: " + p[2] + (p[4].isEmpty() ? "" : " (~" + p[4] + " min)"), true));

        LinearLayout row = new LinearLayout(c); row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(c, 14); row.setLayoutParams(rp);
        Button no = new Button(c); no.setText("Dismiss"); no.setAllCaps(false); no.setTextColor(Color.WHITE); no.setTextSize(16);
        GradientDrawable nb = new GradientDrawable(); nb.setColor(Color.parseColor("#33496E")); nb.setCornerRadius(dp(c, 14)); no.setBackground(nb);
        final Button yes = new Button(c); yes.setText("Accept ride"); yes.setAllCaps(false); yes.setTextColor(Color.parseColor("#0B1426")); yes.setTextSize(18); yes.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable yb = new GradientDrawable(); yb.setColor(Color.parseColor("#2BD17E")); yb.setCornerRadius(dp(c, 14)); yes.setBackground(yb);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(c, 56), 1f); lp1.rightMargin = dp(c, 8);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(c, 56), 2f);
        row.addView(no, lp1); row.addView(yes, lp2); box.addView(row);

        no.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { AlertService.mute(id); hide(); } });
        yes.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            yes.setEnabled(false); yes.setText("Accepting…");
            openApp(c);                                   // started from the tap itself, which Android always allows
            accept(c, id);
        } });

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP; lp.x = 0; lp.y = dp(c, 40);
        lp.horizontalMargin = 0.03f;
        try { wm.addView(box, lp); } catch (Exception e) { return; }
        view = box; curId = id;
        startSound(c);
        MAIN.postDelayed(AUTO, SHOW_MS);
    }

    private static void startSound(Context c) {
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ring = RingtoneManager.getRingtone(c, u);
            if (ring != null) {
                if (Build.VERSION.SDK_INT >= 21) ring.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                if (Build.VERSION.SDK_INT >= 28) ring.setLooping(true);
                ring.play();
            }
        } catch (Exception e) { }
        try {
            vib = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            long[] pat = {0, 500, 300, 500, 300};
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(pat, 0)); else vib.vibrate(pat, 0);
        } catch (Exception e) { }
    }

    static void hide() {
        MAIN.removeCallbacks(AUTO);
        try { if (ring != null) ring.stop(); } catch (Exception e) { }
        try { if (vib != null) vib.cancel(); } catch (Exception e) { }
        ring = null;
        if (view != null) {
            try { ((WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(view); } catch (Exception e) { }
            view = null;
        }
        curId = "";
    }

    private static void openApp(Context c) {
        try { c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).putExtra("accepted", true)); } catch (Exception e) { }
    }

    private static void accept(final Context c, final String id) {
        final SharedPreferences sp = c.getSharedPreferences(AlertService.PREFS, Context.MODE_PRIVATE);
        final Location l = AlertService.lastLoc;
        new Thread(new Runnable() { public void run() {
            String msg; boolean ok = false;
            try {
                String u = sp.getString("api", "") + "?action=accept&id=" + URLEncoder.encode(id, "UTF-8")
                        + "&phone=" + URLEncoder.encode(sp.getString("phone", ""), "UTF-8") + "&pin=" + URLEncoder.encode(sp.getString("pin", ""), "UTF-8")
                        + (l != null ? "&lat=" + String.format(Locale.US, "%.5f", l.getLatitude()) + "&lng=" + String.format(Locale.US, "%.5f", l.getLongitude()) : "");
                HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(15000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"))) { String line; while ((line = r.readLine()) != null) sb.append(line); }
                JSONObject j = new JSONObject(sb.toString());
                String er = j.optString("error", "");
                if (j.optBoolean("ok")) { ok = true; msg = "Ride accepted"; }
                else if ("taken".equals(er)) msg = "Another driver took this ride";
                else if ("due".equals(er)) msg = "Pay your commission dues to accept rides";
                else if ("busy".equals(er)) msg = "Finish your current ride first";
                else if ("expired".equals(er)) msg = "This ride is no longer available";
                else if ("far".equals(er)) msg = "This pickup is too far from you";
                else msg = "Could not accept this ride";
            } catch (Exception e) { msg = "No internet. Ride not accepted"; }
            final String m = msg; final boolean good = ok;
            MAIN.post(new Runnable() { public void run() {
                if (good) { openApp(c); MainActivity.refreshWeb(); }
                hide();
                Toast.makeText(c, m, Toast.LENGTH_LONG).show();
            } });
        } }).start();
    }
}
