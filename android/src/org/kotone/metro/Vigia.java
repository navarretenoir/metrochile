package org.kotone.metro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;

/**
 * Vigía del Metro (Kotone 2026-10-31): cada 15 min, en horario de servicio (05:30–24:00 de Santiago),
 * lee el estado OFICIAL de metro.cl y, ante un problema NUEVO en una línea o estación:
 *   · notifica en el teléfono (también con la app cerrada), y
 *   · empuja la alerta a ADO O260T (org.kotone.metro.ALERTA, permiso de firma) para que la avise en ruta.
 * No repite lo ya avisado; cuando el problema se resuelve sale de la lista y, si vuelve, se avisa de nuevo.
 */
public class Vigia extends JobService {
    static final String TAG = "MetroVigia", API = "https://www.metro.cl/api/estadoRedDetalle.php";
    static final String PKG_BICI = "com.bicipro.app", PERMISO = "org.kotone.clima.permission.ALERTAS";
    static final String CH = "metro_problemas";
    static final int JOB = 71;

    static void programar(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 24) { if (js.getPendingJob(JOB) != null) return; }
        else for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB) return;   // Android 5–6
        js.schedule(new JobInfo.Builder(JOB, new ComponentName(c, Vigia.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60_000L).setPersisted(true).build());
    }

    public static class Arranque extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) { programar(c); }
    }

    @Override public boolean onStartJob(final JobParameters p) {
        new Thread(() -> { try { revisar(this); } catch (Exception e) { Log.w(TAG, "revisión falló", e); } jobFinished(p, false); }, "metro-vigia").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters p) { return true; }

    static boolean enServicio() {
        Calendar k = Calendar.getInstance(TimeZone.getTimeZone("America/Santiago"));
        int m = k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
        return m >= 5 * 60 + 30;
    }

    static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "MetroKotone/2.0 (Android)");
            if (c.getResponseCode() >= 400) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[16384]; int n, t = 0;
                while ((n = in.read(b)) > 0) { t += n; if (t > 4 << 20) return null; o.write(b, 0, n); }
                return o.toString("UTF-8");
            }
        } catch (Exception e) { return null; } finally { if (c != null) c.disconnect(); }
    }

    static String nombreLinea(String k) { return "Línea " + k.substring(1).toUpperCase(); }

    /** Revisa el estado y avisa lo nuevo. forzar=true: avisa todo lo vigente (prueba). */
    static int revisar(Context c) throws Exception { return revisar(c, false); }
    static int revisar(Context c, boolean forzar) throws Exception {
        if (!forzar && !enServicio()) return 0;
        String t = get(API); if (t == null) return 0;
        JSONObject e = new JSONObject(t);
        SharedPreferences sp = c.getSharedPreferences("vigia", MODE_PRIVATE);
        Set<String> antes = forzar ? new HashSet<>() : new HashSet<>(sp.getStringSet("vigentes", new HashSet<>()));
        boolean primera = !forzar && !sp.contains("vigentes");
        Set<String> ahora = new HashSet<>();
        int avisos = 0;
        for (Iterator<String> it = e.keys(); it.hasNext(); ) {
            String k = it.next(); JSONObject L = e.optJSONObject(k); if (L == null) continue;
            if (!"1".equals(L.optString("estado"))) {
                String txt = L.optString("mensaje_app", L.optString("mensaje", "Con problemas"));
                String key = k + "|" + txt; ahora.add(key);
                if (!primera && !antes.contains(key)) { avisar(c, nombreLinea(k), txt, avisos++); }
            }
            JSONArray es = L.optJSONArray("estaciones");
            for (int i = 0; es != null && i < es.length(); i++) {
                JSONObject s = es.getJSONObject(i);
                if ("1".equals(s.optString("estado"))) continue;
                String txt = s.optString("descripcion_app", s.optString("descripcion", "Con problemas"));
                String key = k + ":" + s.optString("codigo") + "|" + txt; ahora.add(key);
                if (!primera && !antes.contains(key)) avisar(c, nombreLinea(k), s.optString("nombre") + ": " + txt, avisos++);
            }
        }
        if (!forzar) sp.edit().putStringSet("vigentes", ahora).putLong("revisado", System.currentTimeMillis()).apply();
        nowBar(c, ahora);
        Log.i(TAG, "revisión: " + ahora.size() + " problemas vigentes, " + avisos + " avisos nuevos" + (primera ? " (línea base)" : ""));
        return avisos;
    }

    /** Now Bar (Samsung, Live Updates de Android 16): problemas VIGENTES de la red; se retira cuando se resuelven. */
    static final int ID_NOW = 70;
    static boolean nowBarDisponible(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 36 || !"samsung".equalsIgnoreCase(android.os.Build.MANUFACTURER)) return false;
        try { return nm(c).canPostPromotedNotifications(); } catch (Throwable t) { return false; }
    }
    /** API 21+: el servicio por nombre (getSystemService(Clase) es de Android 6). */
    static NotificationManager nm(Context c) { return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE); }

    @android.annotation.TargetApi(36)            // la Now Bar sólo se publica si nowBarDisponible() (Android 16 + Samsung)
    static void nowBar(Context c, Set<String> vigentes) {
        NotificationManager nm = nm(c);
        if (android.os.Build.VERSION.SDK_INT < 36) return;
        if (vigentes.isEmpty() || !nowBarDisponible(c)) { nm.cancel(ID_NOW); return; }
        java.util.TreeSet<String> lineas = new java.util.TreeSet<>();
        String primero = null;
        for (String k : vigentes) {
            String l = k.split("[:|]")[0]; lineas.add("L" + l.substring(1).toUpperCase());
            if (primero == null) primero = k.substring(k.indexOf('|') + 1);
        }
        NotificationChannel ch = new NotificationChannel("now_bar", "Problemas en curso (Now Bar)", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setSound(null, null); ch.enableVibration(false); nm.createNotificationChannel(ch);
        String titulo = "Metro: problemas en " + String.join(", ", lineas);
        String texto = vigentes.size() == 1 ? primero : vigentes.size() + " avisos vigentes · " + primero;
        Notification.Builder nb = new Notification.Builder(c, "now_bar").setSmallIcon(R.drawable.ic_stat_metro).setOngoing(true).setOnlyAlertOnce(true)
                .setContentTitle(titulo).setContentText(texto).setStyle(new Notification.BigTextStyle().bigText(texto))
                .setShortCriticalText("🚇 " + String.join(" ", lineas)).setGroup("now-bar")
                .setContentIntent(PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        // setRequestPromotedOngoing es de Android 16 QPR (36.1): en 36.0 la llamada no existe
        if (android.os.Build.VERSION.SDK_INT_FULL >= android.os.Build.VERSION_CODES_FULL.BAKLAVA_1) nb.setRequestPromotedOngoing(true);
        Notification n = nb.build();
        try { nm.notify(ID_NOW, n); } catch (SecurityException ignored) { }
    }

    static void avisar(Context c, String linea, String texto, int n) {
        NotificationManager nm = nm(c);
        if (android.os.Build.VERSION.SDK_INT >= 26)           // canales: Android 8+
            nm.createNotificationChannel(new NotificationChannel(CH, "Problemas en la red", NotificationManager.IMPORTANCE_HIGH));
        PendingIntent abrir = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder base = android.os.Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH)
                : new Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH);
        Notification no = base.setSmallIcon(R.drawable.ic_stat_metro).setColor(0xFFE2231A)
                .setContentTitle("🚇 Metro: " + linea).setContentText(texto)
                .setStyle(new Notification.BigTextStyle().bigText(texto)).setContentIntent(abrir).setAutoCancel(true).build();
        try { nm.notify((linea + texto).hashCode(), no); } catch (SecurityException ignored) { }
        // enlace con la bici: la avisa por notificación y voz si hay ruta en curso
        try {
            c.sendBroadcast(new Intent("org.kotone.metro.ALERTA").setPackage(PKG_BICI)
                    .putExtra("titulo", linea).putExtra("texto", texto).putExtra("nivel", "warn"), PERMISO);
        } catch (Exception ignored) { }
    }
}
