package org.kotone.metro;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;

/**
 * Estado del Metro para las apps hermanas (Clima y ADO O260T): content://org.kotone.metro.estado, método «estado» →
 * Bundle{json: {ok, problemas:[{linea, texto}], revisado}}. Lo que vio el vigía en su última revisión (no consulta la
 * red: responde al tiro). Permiso de FIRMA compartido; call() no lo aplica solo, se verifica aquí. Kotone 2026-10-31.
 */
public class MetroProveedor extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        if (getContext() == null || getContext().checkCallingPermission(Vigia.PERMISO) != PackageManager.PERMISSION_GRANTED) { out.putString("error", "sin permiso"); return out; }
        try {
            android.content.SharedPreferences sp = getContext().getSharedPreferences("vigia", android.content.Context.MODE_PRIVATE);
            Set<String> v = sp.getStringSet("vigentes", null);
            JSONArray p = new JSONArray();
            if (v != null) for (String k : v) p.put(new JSONObject().put("linea", "L" + k.split("[:|]")[0].substring(1).toUpperCase()).put("texto", k.substring(k.indexOf('|') + 1)));
            out.putString("json", new JSONObject().put("ok", v != null).put("problemas", p).put("revisado", sp.getLong("revisado", 0)).toString());
        } catch (Exception e) { out.putString("error", String.valueOf(e)); }
        return out;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
