package com.splitzblr.driver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.ValueCallback;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String HOME = "https://splitzblr.com/drivers/";
    private WebView web;
    private boolean tracking = false;
    private boolean asked = false;
    private ValueCallback<Uri[]> fileCb;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " SplitzblrDriverApp/1.0");
        web.addJavascriptInterface(new Bridge(), "SplitzblrApp");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, hasLocation(), false);
            }
            // internet calls with the rider: allow the page to use the microphone once the phone permission is given
            @Override public void onPermissionRequest(final android.webkit.PermissionRequest req) {
                runOnUiThread(new Runnable() { public void run() {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        req.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        pendingReq = req;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                    }
                }});
            }
            // lets the registration page pick photos and documents
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try { startActivityForResult(params.createIntent(), 77); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String h = u.getHost() == null ? "" : u.getHost();
                if ("https".equals(u.getScheme()) && (h.equals("splitzblr.com") || h.endsWith(".splitzblr.com") || h.equals("script.google.com"))) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    v.loadDataWithBaseURL(null,
                        "<body style='font-family:sans-serif;background:#0b1426;color:#f4e4b0;text-align:center;padding:40px 20px'>"
                        + "<h2>No internet</h2><p>Check your connection and try again.</p>"
                        + "<button style='font-size:18px;padding:12px 28px;border-radius:10px;border:0;background:#d4af37' onclick=\"location.href='" + HOME + "'\">Try again</button></body>",
                        "text/html", "utf-8", null);
                }
            }
        });
        if (b == null) web.loadUrl(HOME); else web.restoreState(b);
        askPermissions();
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPermissions() {
        if (asked) return;
        asked = true;
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> p = new ArrayList<>();
        if (!hasLocation()) p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 1);
        else askBackground();
    }

    private void askBackground() {
        // "Allow all the time" has to be asked on its own on Android 11+
        if (Build.VERSION.SDK_INT >= 29 && hasLocation()
            && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 2);
        }
    }

    private android.webkit.PermissionRequest pendingReq;
    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 3) {
            if (pendingReq != null) {
                if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) pendingReq.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                else pendingReq.deny();
                pendingReq = null;
            }
            return;
        }
        if (code == 1) { web.reload(); askBackground(); }
    }

    @Override protected void onActivityResult(int code, int res, Intent data) {
        super.onActivityResult(code, res, data);
        if (code == 77 && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        }
    }

    private void setTracking(boolean on) {
        if (on == tracking) return;
        Intent i = new Intent(this, TrackService.class);
        if (on) {
            if (!hasLocation()) return;
            tracking = true;
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } else {
            tracking = false;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            stopService(i);
        }
    }

    private void goOnline(String phone, String pin, String api) {
        getSharedPreferences(AlertService.PREFS, MODE_PRIVATE).edit().putBoolean("online", true).putString("phone", phone).putString("pin", pin).putString("api", api).apply();
        AlertService.start(this);
        Push.fetchAndRegister(this);
        askAlertSetup();
    }

    private void goOffline() {
        getSharedPreferences(AlertService.PREFS, MODE_PRIVATE).edit().putBoolean("online", false).putString("pin", "").apply();
        stopService(new Intent(this, AlertService.class));
    }

    // Once: let the alerts reach the lock screen and keep running while the phone sleeps.
    private void askAlertSetup() {
        android.content.SharedPreferences sp = getSharedPreferences(AlertService.PREFS, MODE_PRIVATE);
        try {
            if (Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this) && !sp.getBoolean("askedOvl", false)) {
                sp.edit().putBoolean("askedOvl", true).apply();
                android.widget.Toast.makeText(this, "Turn on \"Allow display over other apps\" so rides pop up on your screen", android.widget.Toast.LENGTH_LONG).show();
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                return;
            }
            if (Build.VERSION.SDK_INT >= 34) {
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (!nm.canUseFullScreenIntent() && !sp.getBoolean("askedFsi", false)) {
                    sp.edit().putBoolean("askedFsi", true).apply();
                    startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                    return;
                }
            }
            if (Build.VERSION.SDK_INT >= 23 && !sp.getBoolean("askedBat", false)) {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    sp.edit().putBoolean("askedBat", true).apply();
                    startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                }
            }
        } catch (Exception e) { }
    }

    private class Bridge {
        @JavascriptInterface public void online(final String phone, final String pin, final String api) {
            runOnUiThread(new Runnable() { public void run() { goOnline(phone, pin, api); } });
        }
        @JavascriptInterface public void offline() {
            runOnUiThread(new Runnable() { public void run() { goOffline(); } });
        }
        @JavascriptInterface public void tracking(final boolean on) {
            runOnUiThread(new Runnable() { public void run() { setTracking(on); } });
        }
    }

    // Keep the page (and its GPS) running when the app is in the background during a trip.
    @Override protected void onPause() { super.onPause(); AlertService.appVisible = false; if (!tracking) web.onPause(); }
    @Override protected void onResume() { super.onResume(); AlertService.appVisible = true; AlertService.clearAlerts(this); OverlayAlert.hide(); if (getSharedPreferences(AlertService.PREFS, MODE_PRIVATE).getBoolean("online", false)) askAlertSetup(); web.onResume(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); }
    @Override protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); web.saveState(b); }
    @Override protected void onDestroy() { if (tracking) stopService(new Intent(this, TrackService.class)); super.onDestroy(); }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK && web.canGoBack()) { web.goBack(); return true; }
        return super.onKeyDown(k, e);
    }
}
