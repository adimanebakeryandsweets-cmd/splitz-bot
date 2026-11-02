package com.splitzblr.customer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    static final String HOME = "https://splitzblr.com/";
    WebView web;
    String pendingOrigin;
    GeolocationPermissions.Callback pendingCb;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0a1f40"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0a1f40"));
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { cb.invoke(origin, true, false); return; }
                pendingOrigin = origin; pendingCb = cb;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sc = u.getScheme() == null ? "" : u.getScheme(), host = u.getHost() == null ? "" : u.getHost();
                if (("https".equals(sc) || "http".equals(sc)) && (host.equals("splitzblr.com") || host.endsWith(".splitzblr.com"))) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }   // WhatsApp, phone dialer, Google Maps, UPI
                return true;
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame())
                    v.loadData("<body style='background:#0a1f40;color:#f4f1e6;font-family:sans-serif;text-align:center;padding:40px'><h3>No connection</h3><p>Check your internet, then reopen the app.</p></body>", "text/html", "utf-8");
            }
        });
        Uri d = getIntent() == null ? null : getIntent().getData();
        web.loadUrl(d != null && "splitzblr.com".equals(d.getHost()) ? d.toString() : HOME);
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] g) {
        if (code == 7 && pendingCb != null) {
            boolean ok = g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED;
            pendingCb.invoke(pendingOrigin, ok, false); pendingCb = null;
        }
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        Uri d = i.getData();
        if (d != null && "splitzblr.com".equals(d.getHost())) web.loadUrl(d.toString());
    }

    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
