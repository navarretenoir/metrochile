/* Metro Chile — host de escritorio (GTK4 + WebKitGTK 6) de la interfaz única ui/ (la misma de Android).
 * Reemplaza la ventana Cairo de metro.c (que queda como comando de consola `metro`). El host sólo pone lo
 * que la página no puede: red sin CORS (libcurl en hilo), avisos de escritorio (GNotification) y la ventana.
 * Puente: window.webkit.messageHandlers.bridge.postMessage(JSON {action:get|locate|notify}). Kotone 2026-10-31. */
#include <gtk/gtk.h>
#include <webkit/webkit.h>
#include <json-glib/json-glib.h>
#include <curl/curl.h>
#include <string.h>
#include <time.h>
#include <glib/gstdio.h>

#ifndef METRO_ROOT
#define METRO_ROOT "."
#endif
#define APP_ID "cl.kotone.MetroChile"

static GtkApplication *APPL;
static WebKitWebView *WEB;

static gchar *js_string (const char *s) {
    JsonNode *n = json_node_new (JSON_NODE_VALUE);
    json_node_set_string (n, s ? s : "");
    gchar *o = json_to_string (n, FALSE);
    json_node_free (n);
    return o;
}
static void run_js (const char *js) { webkit_web_view_evaluate_javascript (WEB, js, -1, NULL, NULL, NULL, NULL, NULL); }

/* ------------------------------------------------------------ red (sólo metro.cl) en hilo */
typedef struct { gint id; gchar *url; GString *body; gboolean ok; } Req;
static size_t on_write (char *p, size_t s, size_t n, void *u) {
    GString *b = u;
    if (b->len + s * n > (4u << 20)) return 0;            /* tope 4 MB */
    g_string_append_len (b, p, s * n);
    return s * n;
}
static gboolean deliver (gpointer d) {
    Req *r = d;
    gchar *body = js_string (r->ok ? r->body->str : "");
    gchar *js = g_strdup_printf ("window.__metroHttp(%d,%s,%s)", r->id, r->ok ? "true" : "false", body);
    run_js (js);
    g_free (js); g_free (body); g_free (r->url); g_string_free (r->body, TRUE); g_free (r);
    return G_SOURCE_REMOVE;
}
static gpointer fetch (gpointer d) {
    Req *r = d;
    CURL *c = curl_easy_init ();
    long code = 0;
    if (c) {
        curl_easy_setopt (c, CURLOPT_URL, r->url);
        curl_easy_setopt (c, CURLOPT_WRITEFUNCTION, on_write);
        curl_easy_setopt (c, CURLOPT_WRITEDATA, r->body);
        curl_easy_setopt (c, CURLOPT_TIMEOUT, 15L);
        curl_easy_setopt (c, CURLOPT_CONNECTTIMEOUT, 8L);
        curl_easy_setopt (c, CURLOPT_NOSIGNAL, 1L);
        curl_easy_setopt (c, CURLOPT_ACCEPT_ENCODING, "");
        curl_easy_setopt (c, CURLOPT_FOLLOWLOCATION, 1L);
        curl_easy_setopt (c, CURLOPT_USERAGENT, "MetroKotone/2.0 (Linux)");
        r->ok = curl_easy_perform (c) == CURLE_OK;
        curl_easy_getinfo (c, CURLINFO_RESPONSE_CODE, &code);
        if (code >= 400) r->ok = FALSE;
        curl_easy_cleanup (c);
    }
    g_idle_add (deliver, r);
    return NULL;
}

static const char *jstr (JsonObject *o, const char *k) {
    JsonNode *n = json_object_get_member (o, k);
    return (n && JSON_NODE_HOLDS_VALUE (n) && json_node_get_value_type (n) == G_TYPE_STRING) ? json_node_get_string (n) : "";
}

static void on_bridge (WebKitUserContentManager *m, JSCValue *v, gpointer u) {
    (void) m; (void) u;
    gchar *s = jsc_value_to_string (v);
    JsonParser *p = json_parser_new ();
    if (s && json_parser_load_from_data (p, s, -1, NULL) && JSON_NODE_HOLDS_OBJECT (json_parser_get_root (p))) {
        JsonObject *o = json_node_get_object (json_parser_get_root (p));
        const char *a = jstr (o, "action");
        gint id = (gint) json_object_get_int_member_with_default (o, "id", 0);
        if (!strcmp (a, "get") && (g_str_has_prefix (jstr (o, "url"), "https://www.metro.cl/")
                                   || g_str_has_prefix (jstr (o, "url"), "https://api.open-meteo.com/"))) {
            Req *r = g_new0 (Req, 1);
            r->id = id; r->url = g_strdup (jstr (o, "url")); r->body = g_string_new (NULL);
            g_thread_unref (g_thread_new ("metro-red", fetch, r));
        } else if (!strcmp (a, "locate")) {
            /* misma ubicación que Clima de escritorio (GeoClue → ~/.cache/clima/last.json, si es de < 24 h):
             * así «Cerca de ti» aparece igual que en el teléfono */
            gchar *path = g_build_filename (g_get_user_cache_dir (), "clima", "last.json", NULL), *js = NULL;
            GStatBuf st;
            JsonParser *lp = json_parser_new ();
            if (g_stat (path, &st) == 0 && time (NULL) - st.st_mtime < 86400 && json_parser_load_from_file (lp, path, NULL)
                && JSON_NODE_HOLDS_OBJECT (json_parser_get_root (lp))) {
                JsonObject *ro = json_node_get_object (json_parser_get_root (lp));
                JsonObject *lo = json_object_has_member (ro, "location") ? json_object_get_object_member (ro, "location") : NULL;
                if (lo && json_object_has_member (lo, "lat") && json_object_has_member (lo, "lon")) {
                    gchar la[32], lo2[32];
                    g_ascii_dtostr (la, sizeof la, json_object_get_double_member (lo, "lat"));
                    g_ascii_dtostr (lo2, sizeof lo2, json_object_get_double_member (lo, "lon"));
                    js = g_strdup_printf ("window.__metroLoc(%d,%s,%s)", id, la, lo2);
                }
            }
            if (!js) js = g_strdup_printf ("window.__metroLoc(%d,null,null)", id);
            run_js (js); g_free (js); g_free (path); g_object_unref (lp);
        } else if (!strcmp (a, "abrir") && g_str_has_prefix (jstr (o, "url"), "https://www.openstreetmap.org/")) {
            g_app_info_launch_default_for_uri (jstr (o, "url"), NULL, NULL);    /* «Ir en bici» en el escritorio */
        } else if (!strcmp (a, "notify")) {
            GNotification *n = g_notification_new (jstr (o, "titulo"));
            g_notification_set_body (n, jstr (o, "texto"));
            GIcon *ic = g_themed_icon_new (APP_ID);
            g_notification_set_icon (n, ic);
            g_application_send_notification (G_APPLICATION (APPL), NULL, n);
            g_object_unref (ic); g_object_unref (n);
        }
    }
    g_object_unref (p); g_free (s);
}

static gboolean on_key (GtkEventControllerKey *k, guint kv, guint kc, GdkModifierType st, gpointer w) {
    (void) k; (void) kc; (void) st;
    if (kv == GDK_KEY_F5) { run_js ("document.getElementById('btn-refresh').click()"); return TRUE; }
    if (kv == GDK_KEY_Escape) { run_js ("window.metroAtras&&window.metroAtras()"); return TRUE; }
    if (kv == GDK_KEY_F11) {
        GtkWindow *win = w;
        if (gtk_window_is_fullscreen (win)) gtk_window_unfullscreen (win); else gtk_window_fullscreen (win);
        return TRUE;
    }
    return FALSE;
}

static void activate (GtkApplication *app, gpointer u) {
    (void) u;
    GList *ws = gtk_application_get_windows (app);
    if (ws) { gtk_window_present (ws->data); return; }      /* instancia única */
    GtkWidget *win = gtk_application_window_new (app);
    gtk_window_set_title (GTK_WINDOW (win), "Metro Chile");
    gtk_window_set_default_size (GTK_WINDOW (win), 460, 860);
    gtk_window_set_icon_name (GTK_WINDOW (win), APP_ID);

    WEB = WEBKIT_WEB_VIEW (webkit_web_view_new ());
    WebKitUserContentManager *ucm = webkit_web_view_get_user_content_manager (WEB);
    webkit_user_content_manager_register_script_message_handler (ucm, "bridge", NULL);
    g_signal_connect (ucm, "script-message-received::bridge", G_CALLBACK (on_bridge), NULL);
    GdkRGBA bg = { 0.055, 0.11, 0.26, 1 };
    webkit_web_view_set_background_color (WEB, &bg);
    WebKitSettings *st = webkit_web_view_get_settings (WEB);
    webkit_settings_set_enable_developer_extras (st, FALSE);
    /* sin aceleración por hardware: en este HP el GL de WebKit dejaba la vista en blanco (igual que Clima) */
    webkit_settings_set_hardware_acceleration_policy (st, WEBKIT_HARDWARE_ACCELERATION_POLICY_NEVER);

    GtkEventController *kc = gtk_event_controller_key_new ();
    g_signal_connect (kc, "key-pressed", G_CALLBACK (on_key), win);
    gtk_widget_add_controller (win, kc);

    gtk_window_set_child (GTK_WINDOW (win), GTK_WIDGET (WEB));
    /* $METRO_DIR (AppImage/Flatpak) o la raíz compilada */
    const gchar *raiz = g_getenv ("METRO_DIR") && *g_getenv ("METRO_DIR") ? g_getenv ("METRO_DIR") : METRO_ROOT;
    gchar *idx = g_build_filename (raiz, "ui", "index.html", NULL);
    gchar *uri = g_filename_to_uri (idx, NULL, NULL);
    g_free (idx);
    webkit_web_view_load_uri (WEB, uri);
    g_free (uri);
    gtk_window_present (GTK_WINDOW (win));
}

int main (int argc, char **argv) {
    curl_global_init (CURL_GLOBAL_DEFAULT);
    APPL = gtk_application_new (APP_ID, G_APPLICATION_DEFAULT_FLAGS);
    g_signal_connect (APPL, "activate", G_CALLBACK (activate), NULL);
    int r = g_application_run (G_APPLICATION (APPL), argc, argv);
    g_object_unref (APPL);
    curl_global_cleanup ();
    return r;
}
