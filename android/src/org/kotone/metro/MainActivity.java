package org.kotone.metro;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Metro para Android — host nativo de la MISMA interfaz web del escritorio (ui/). Aquí sólo va lo que el
 * navegador no puede: red sin CORS, ubicación, márgenes del sistema y el vigía en segundo plano. Kotone 2026-10-31.
 */
public class MainActivity extends Activity {
    private static final int REQ_LOC = 3;
    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private int pendingLoc = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0E1C42"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "MetroAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !r.getUrl().toString().startsWith("file:///android_asset/");
            }
            // los márgenes del sistema llegan antes de que exista la página: se re-aplican al terminar de cargar
            @Override public void onPageFinished(WebView v, String url) { v.requestApplyInsets(); }
        });
        web.setOnApplyWindowInsetsListener((v, ins) -> {
            float d = getResources().getDisplayMetrics().density;
            int top = 0, bot = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = i.top; bot = i.bottom;
            }
            js("document.documentElement.style.setProperty('--sat','" + Math.round(top / d) + "px');"
                    + "document.documentElement.style.setProperty('--sab','" + Math.round(bot / d) + "px');");
            return ins;
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/ui/index.html");
        Vigia.programar(this);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBack);
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && !getSharedPreferences("vigia", MODE_PRIVATE).getBoolean("notif_pedido", false)) {
                getSharedPreferences("vigia", MODE_PRIVATE).edit().putBoolean("notif_pedido", true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4);
            }
        }
        // prueba manual: adb shell am start -S -n org.kotone.metro/.MainActivity --ez probar_vigia true
        // prueba del enlace con la bici: --es probar_empuje "texto"
        String pe = getIntent().getStringExtra("probar_empuje");
        if (pe != null) Vigia.avisar(this, "Prueba de enlace", pe, 0);
        if (getIntent().getBooleanExtra("probar_vigia", false))
            pool.execute(() -> { try { Vigia.revisar(this, true); } catch (Exception ignored) { } });
    }

    private void onBack() {
        web.evaluateJavascript("window.metroAtras&&window.metroAtras()", r -> { if (!"true".equals(r)) finish(); });
    }
    @SuppressWarnings("deprecation") @Override public void onBackPressed() { onBack(); }

    @Override protected void onResume() { super.onResume(); if (web != null) js("window.metroResume&&window.metroResume()"); }
    @Override protected void onDestroy() { pool.shutdownNow(); if (web != null) web.destroy(); super.onDestroy(); }

    private void js(String code) { ui.post(() -> { if (web != null) web.evaluateJavascript(code, null); }); }

    /** Android 5: permiso concedido al instalar; desde Android 6, en tiempo de ejecución. */
    private boolean ubicPermitida() {
        return android.os.Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @android.annotation.TargetApi(23)
    private void doLocate(int id) {
        Location best = null;
        if (ubicPermitida()) {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            for (String p : new String[]{"fused", "network", "passive", "gps"}) {
                try { Location l = lm.getLastKnownLocation(p); if (l != null && (best == null || l.getTime() > best.getTime())) best = l; }
                catch (Exception ignored) { }
            }
        }
        js(best == null ? "window.__metroLoc(" + id + ",null,null)" : "window.__metroLoc(" + id + "," + best.getLatitude() + "," + best.getLongitude() + ")");
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        if (req == REQ_LOC && pendingLoc >= 0) { int id = pendingLoc; pendingLoc = -1; doLocate(id); }
    }

    private class Bridge {
        @JavascriptInterface public void get(final int id, final String url) {
            pool.execute(() -> {
                String body = url.startsWith("https://www.metro.cl/") || url.startsWith("https://api.open-meteo.com/") ? Vigia.get(url) : null;
                js("window.__metroHttp(" + id + "," + (body != null) + "," + JSONObject.quote(body == null ? "" : body) + ")");
            });
        }
        /** Tiempo y alertas desde Clima (proveedor con permiso de firma) → window.__metroHttp(id, ok, json). */
        @JavascriptInterface public void clima(final int id, final double lat, final double lon) {
            pool.execute(() -> {
                String j = null;
                try {
                    android.os.Bundle x = new android.os.Bundle(); x.putDouble("lat", lat); x.putDouble("lon", lon);
                    android.os.Bundle r = getContentResolver().call(android.net.Uri.parse("content://org.kotone.clima.alertas"), "alertas", null, x);
                    if (r != null) j = r.getString("json");
                } catch (Exception ignored) { }
                js("window.__metroHttp(" + id + "," + (j != null) + "," + JSONObject.quote(j == null ? "" : j) + ")");
            });
        }
        /** «Ir en bici»: abre ADO O260T navegando (offline) a la estación. false si la app de la bici no está. */
        @JavascriptInterface public boolean bici(double lat, double lon, String nombre) {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(" + android.net.Uri.encode(nombre) + ")"))
                    .setPackage("com.bicipro.app").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(getPackageManager()) == null) return false;
            ui.post(() -> startActivity(i));
            return true;
        }
        @JavascriptInterface public void locate(final int id) {
            ui.post(() -> {
                if (ubicPermitida()) doLocate(id);
                else if (android.os.Build.VERSION.SDK_INT >= 23) { pendingLoc = id; requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC); }
            });
        }
    }
}
