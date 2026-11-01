/*
 * Anfitrión WebView2 en C puro (Win32 + COM) para las apps web de Javiera en Windows 10/11
 * (y 7/8.1 con el runtime WebView2 instalado). El mismo archivo sirve a Metro Chile y a Tu Mundo Pet;
 * cada app lo configura con su config.h. Sin .NET ni dependencias: sólo WebView2Loader.dll (de Microsoft) al lado.
 * Desarrollado por Javiera Navarrete.
 */
#define COBJMACROS
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>
#include <shlobj.h>
#include <winhttp.h>
#include <stdio.h>
#include <wchar.h>
#include "WebView2.h"
#include "config.h"

#define WM_RESPUESTA (WM_APP + 7)

static const IID IID_WV2_3 = {0xA0D6DF20, 0x3B92, 0x416D, {0xAA, 0x0C, 0x43, 0x7A, 0x9C, 0x72, 0x78, 0x57}};

static HWND g_win;
static ICoreWebView2Controller *g_ctrl;
static ICoreWebView2 *g_web;
static WCHAR g_dirApp[MAX_PATH];

typedef HRESULT (STDAPICALLTYPE *PFN_CREAR)(PCWSTR, PCWSTR, ICoreWebView2EnvironmentOptions *,
                                             ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler *);

/* ------------------------------------------------------------------ objetos COM mínimos para los callbacks */
typedef struct { void *lpVtbl; } Manejador;
static HRESULT STDMETHODCALLTYPE qi(void *t, REFIID r, void **o) { (void)r; *o = t; return S_OK; }
static ULONG STDMETHODCALLTYPE addref(void *t) { (void)t; return 1; }
static ULONG STDMETHODCALLTYPE release(void *t) { (void)t; return 1; }

static BOOL es_fuera(const WCHAR *uri) {
    int i;
    if (!uri) return FALSE;
    if (!wcsncmp(uri, L"mailto:", 7) || !wcsncmp(uri, L"tel:", 4) || !wcsncmp(uri, L"whatsapp:", 9)) return TRUE;
    if (wcsncmp(uri, L"http", 4)) return FALSE;
    for (i = 0; APP_FUERA[i]; i++) if (wcsstr(uri, APP_FUERA[i])) return TRUE;
    return FALSE;
}
static void abrir_fuera(const WCHAR *uri) { ShellExecuteW(g_win, L"open", uri, NULL, NULL, SW_SHOWNORMAL); }

/* navegación: lo externo (WhatsApp, redes, mapas) al navegador del sistema; el resto dentro */
static HRESULT STDMETHODCALLTYPE nav_invoke(void *t, ICoreWebView2 *s, ICoreWebView2NavigationStartingEventArgs *a) {
    LPWSTR uri = NULL;
    (void)t; (void)s;
    if (SUCCEEDED(ICoreWebView2NavigationStartingEventArgs_get_Uri(a, &uri)) && es_fuera(uri)) {
        ICoreWebView2NavigationStartingEventArgs_put_Cancel(a, TRUE);
        abrir_fuera(uri);
    }
    CoTaskMemFree(uri);
    return S_OK;
}
static struct { void *q, *a, *r, *i; } vt_nav = {qi, addref, release, nav_invoke};
static Manejador m_nav = {&vt_nav};

/* ventanas nuevas (pasarelas de pago, enlaces target=_blank): en la misma vista o fuera */
static HRESULT STDMETHODCALLTYPE nueva_invoke(void *t, ICoreWebView2 *s, ICoreWebView2NewWindowRequestedEventArgs *a) {
    LPWSTR uri = NULL;
    (void)t;
    if (SUCCEEDED(ICoreWebView2NewWindowRequestedEventArgs_get_Uri(a, &uri))) {
        ICoreWebView2NewWindowRequestedEventArgs_put_Handled(a, TRUE);
        if (es_fuera(uri) || !APP_VENTANAS_DENTRO) abrir_fuera(uri); else ICoreWebView2_Navigate(s, uri);
    }
    CoTaskMemFree(uri);
    return S_OK;
}
static struct { void *q, *a, *r, *i; } vt_nueva = {qi, addref, release, nueva_invoke};
static Manejador m_nueva = {&vt_nueva};

#if APP_PUENTE
/* ------------------------------------------------------------------ puente con la interfaz (ui/app.js, HOST «win») */
typedef struct { int id; WCHAR url[1024]; char *cuerpo; BOOL ok; } Pedido;

static WCHAR *json_cadena(const char *utf8) {      /* UTF-8 → literal JSON entre comillas (UTF-16) */
    int n = MultiByteToWideChar(CP_UTF8, 0, utf8, -1, NULL, 0), i, k = 0;
    WCHAR *w = (WCHAR *)malloc(n * sizeof(WCHAR)), *o = (WCHAR *)malloc((n * 6 + 3) * sizeof(WCHAR));
    MultiByteToWideChar(CP_UTF8, 0, utf8, -1, w, n);
    o[k++] = L'"';
    for (i = 0; w[i]; i++) {
        WCHAR c = w[i];
        if (c == L'"' || c == L'\\') { o[k++] = L'\\'; o[k++] = c; }
        else if (c < 0x20 || c == 0x2028 || c == 0x2029) k += swprintf(o + k, 7, L"\\u%04x", c);
        else o[k++] = c;
    }
    o[k++] = L'"'; o[k] = 0;
    free(w);
    return o;
}
static DWORD WINAPI hilo_get(LPVOID p) {
    Pedido *r = (Pedido *)p;
    URL_COMPONENTS uc;
    WCHAR host[256], ruta[900];
    HINTERNET s = NULL, c = NULL, q = NULL;
    DWORD cap = 0, n = 0, l;
    char *b = NULL;
    ZeroMemory(&uc, sizeof uc); uc.dwStructSize = sizeof uc;
    uc.lpszHostName = host; uc.dwHostNameLength = 256; uc.lpszUrlPath = ruta; uc.dwUrlPathLength = 900;
    if (WinHttpCrackUrl(r->url, 0, 0, &uc)) {
        s = WinHttpOpen(L"" APP_AGENTE, WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, NULL, NULL, 0);
        if (s) {
            DWORD tls = 0x00000800 /* TLS 1.2 */ | 0x00002000 /* TLS 1.3 */;
            WinHttpSetOption(s, WINHTTP_OPTION_SECURE_PROTOCOLS, &tls, sizeof tls);   /* Windows 7/8 no lo activan solos */
            c = WinHttpConnect(s, host, uc.nPort, 0);
        }
        if (c) q = WinHttpOpenRequest(c, L"GET", ruta, NULL, NULL, NULL, uc.nScheme == INTERNET_SCHEME_HTTPS ? WINHTTP_FLAG_SECURE : 0);
        if (q && WinHttpSendRequest(q, NULL, 0, NULL, 0, 0, 0) && WinHttpReceiveResponse(q, NULL)) {
            DWORD cod = 0, t = sizeof cod;
            WinHttpQueryHeaders(q, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER, NULL, &cod, &t, NULL);
            for (;;) {
                if (n + 16385 > cap) { char *nb = (char *)realloc(b, cap = cap ? cap * 2 : 65536); if (!nb) break; b = nb; }
                if (!WinHttpReadData(q, b + n, 16384, &l) || l == 0) break;
                n += l;
            }
            r->ok = cod < 400 && b != NULL;
        }
    }
    if (b) b[n] = 0;
    r->cuerpo = b ? b : _strdup("");
    if (q) WinHttpCloseHandle(q);
    if (c) WinHttpCloseHandle(c);
    if (s) WinHttpCloseHandle(s);
    PostMessageW(g_win, WM_RESPUESTA, 0, (LPARAM)r);
    return 0;
}
static int permitido(const WCHAR *u) {
    int i;
    for (i = 0; APP_PERMITIDOS[i]; i++) if (!wcsncmp(u, APP_PERMITIDOS[i], wcslen(APP_PERMITIDOS[i]))) return 1;
    return 0;
}
/* mensajes simples: {"action":"get","id":3,"url":"https://…"} — sin librería JSON: se buscan los campos */
static const WCHAR *campo(const WCHAR *j, const WCHAR *k, WCHAR *o, int max) {
    WCHAR pat[32]; const WCHAR *p; int n = 0;
    swprintf(pat, 32, L"\"%ls\"", k);
    p = wcsstr(j, pat); o[0] = 0;
    if (!p) return o;
    p += wcslen(pat);
    while (*p == L' ' || *p == L':') p++;
    if (*p == L'"') { p++; while (*p && *p != L'"' && n < max - 1) { if (*p == L'\\' && p[1]) p++; o[n++] = *p++; } }
    else while (*p && *p != L',' && *p != L'}' && n < max - 1) o[n++] = *p++;
    o[n] = 0;
    return o;
}
static HRESULT STDMETHODCALLTYPE msg_invoke(void *t, ICoreWebView2 *s, ICoreWebView2WebMessageReceivedEventArgs *a) {
    LPWSTR m = NULL;
    WCHAR acc[32], id[16], url[1024], js[160];
    (void)t; (void)s;
    if (FAILED(ICoreWebView2WebMessageReceivedEventArgs_TryGetWebMessageAsString(a, &m)) || !m) return S_OK;
    campo(m, L"action", acc, 32); campo(m, L"id", id, 16); campo(m, L"url", url, 1024);
    if (!wcscmp(acc, L"get") && permitido(url)) {
        Pedido *r = (Pedido *)calloc(1, sizeof(Pedido));
        HANDLE h;
        r->id = _wtoi(id); wcsncpy(r->url, url, 1023);
        h = CreateThread(NULL, 0, hilo_get, r, 0, NULL);
        if (h) CloseHandle(h);
    } else if (!wcscmp(acc, L"get")) {
        swprintf(js, 160, L"window.__metroHttp(%d,false,\"\")", _wtoi(id));
        ICoreWebView2_ExecuteScript(g_web, js, NULL);
    } else if (!wcscmp(acc, L"locate")) {
        swprintf(js, 160, L"window.__metroLoc(%d,null,null)", _wtoi(id));     /* sin GPS en el PC: «Cerca de ti» se oculta */
        ICoreWebView2_ExecuteScript(g_web, js, NULL);
    } else if (!wcscmp(acc, L"abrir") && !wcsncmp(url, L"https://www.openstreetmap.org/", 30)) {
        abrir_fuera(url);
    }
    CoTaskMemFree(m);
    return S_OK;
}
static struct { void *q, *a, *r, *i; } vt_msg = {qi, addref, release, msg_invoke};
static Manejador m_msg = {&vt_msg};
#endif

/* ------------------------------------------------------------------ creación del WebView */
static HRESULT STDMETHODCALLTYPE ctrl_invoke(void *t, HRESULT e, ICoreWebView2Controller *ctrl) {
    RECT r;
    EventRegistrationToken tok;
    ICoreWebView2Settings *st = NULL;
    (void)t;
    if (FAILED(e) || !ctrl) { MessageBoxW(g_win, L"No se pudo iniciar el motor web (WebView2).", APP_NOMBRE, MB_ICONERROR); return S_OK; }
    g_ctrl = ctrl; ICoreWebView2Controller_AddRef(ctrl);
    ICoreWebView2Controller_get_CoreWebView2(ctrl, &g_web);
    GetClientRect(g_win, &r);
    ICoreWebView2Controller_put_Bounds(ctrl, r);
    if (SUCCEEDED(ICoreWebView2_get_Settings(g_web, &st))) {
        ICoreWebView2Settings_put_IsStatusBarEnabled(st, FALSE);
        ICoreWebView2Settings_put_AreDevToolsEnabled(st, FALSE);
        ICoreWebView2Settings_put_IsZoomControlEnabled(st, TRUE);
        ICoreWebView2Settings_put_AreDefaultContextMenusEnabled(st, APP_MENU_CONTEXTUAL);
        ICoreWebView2Settings_Release(st);
    }
    ICoreWebView2_add_NavigationStarting(g_web, (ICoreWebView2NavigationStartingEventHandler *)&m_nav, &tok);
    ICoreWebView2_add_NewWindowRequested(g_web, (ICoreWebView2NewWindowRequestedEventHandler *)&m_nueva, &tok);
#if APP_PUENTE
    ICoreWebView2_add_WebMessageReceived(g_web, (ICoreWebView2WebMessageReceivedEventHandler *)&m_msg, &tok);
#endif
#ifdef APP_HOST_VIRTUAL
    {   /* la interfaz instalada junto al .exe se sirve como https://<host>/ (origen seguro, sin file://) */
        ICoreWebView2_3 *w3 = NULL;
        WCHAR dir[MAX_PATH];
        swprintf(dir, MAX_PATH, L"%ls\\%ls", g_dirApp, APP_CARPETA_UI);
        if (SUCCEEDED(ICoreWebView2_QueryInterface(g_web, &IID_WV2_3, (void **)&w3)) && w3) {
            ICoreWebView2_3_SetVirtualHostNameToFolderMapping(w3, APP_HOST_VIRTUAL, dir, COREWEBVIEW2_HOST_RESOURCE_ACCESS_KIND_DENY_CORS);
            ICoreWebView2_3_Release(w3);
        }
    }
#endif
    ICoreWebView2_Navigate(g_web, APP_INICIO);
    return S_OK;
}
static struct { void *q, *a, *r, *i; } vt_ctrl = {qi, addref, release, ctrl_invoke};
static Manejador m_ctrl = {&vt_ctrl};

static HRESULT STDMETHODCALLTYPE env_invoke(void *t, HRESULT e, ICoreWebView2Environment *env) {
    (void)t;
    if (FAILED(e) || !env) {
        if (MessageBoxW(g_win, L"No se encontró el componente Microsoft Edge WebView2 (viene con Windows 10 y 11).\n\n"
                               L"¿Abrir la página de Microsoft para instalarlo?", APP_NOMBRE, MB_YESNO | MB_ICONINFORMATION) == IDYES)
            ShellExecuteW(NULL, L"open", L"https://go.microsoft.com/fwlink/p/?LinkId=2124703", NULL, NULL, SW_SHOWNORMAL);
        PostMessageW(g_win, WM_CLOSE, 0, 0);
        return S_OK;
    }
    ICoreWebView2Environment_CreateCoreWebView2Controller(env, g_win, (ICoreWebView2CreateCoreWebView2ControllerCompletedHandler *)&m_ctrl);
    return S_OK;
}
static struct { void *q, *a, *r, *i; } vt_env = {qi, addref, release, env_invoke};
static Manejador m_env = {&vt_env};

static BOOL iniciar_webview(void) {
    WCHAR dll[MAX_PATH], datos[MAX_PATH];
    HMODULE h;
    PFN_CREAR crear;
    swprintf(dll, MAX_PATH, L"%ls\\WebView2Loader.dll", g_dirApp);
    h = LoadLibraryW(dll);
    crear = h ? (PFN_CREAR)GetProcAddress(h, "CreateCoreWebView2EnvironmentWithOptions") : NULL;
    if (!crear) return FALSE;
    /* datos (cookies, carrito, caché) en %LOCALAPPDATA%\<app>, nunca junto al programa */
    if (!SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_LOCAL_APPDATA, NULL, 0, datos))) GetTempPathW(MAX_PATH, datos);
    wcscat(datos, L"\\"); wcscat(datos, APP_CARPETA_DATOS);
    CreateDirectoryW(datos, NULL);
    return SUCCEEDED(crear(NULL, datos, NULL, (ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler *)&m_env));
}

static void acerca(void) {
    MessageBoxW(g_win, APP_ACERCA, L"Acerca de " APP_NOMBRE, MB_OK | MB_ICONINFORMATION);
}

static LRESULT CALLBACK proc(HWND h, UINT m, WPARAM wp, LPARAM lp) {
    switch (m) {
    case WM_SIZE:
        if (g_ctrl) { RECT r; GetClientRect(h, &r); ICoreWebView2Controller_put_Bounds(g_ctrl, r); }
        return 0;
    case WM_SYSCOMMAND:
        if ((wp & 0xFFF0) == 0x0010) { acerca(); return 0; }
        break;
    case WM_KEYDOWN:
        if (wp == VK_F5 && g_web) ICoreWebView2_Reload(g_web);
        break;
#if APP_PUENTE
    case WM_RESPUESTA: {
        Pedido *r = (Pedido *)lp;
        WCHAR *lit = json_cadena(r->cuerpo);
        size_t n = wcslen(lit) + 64;
        WCHAR *js = (WCHAR *)malloc(n * sizeof(WCHAR));
        swprintf(js, n, L"window.__metroHttp(%d,%ls,%ls)", r->id, r->ok ? L"true" : L"false", lit);
        if (g_web) ICoreWebView2_ExecuteScript(g_web, js, NULL);
        free(js); free(lit); free(r->cuerpo); free(r);
        return 0;
    }
#endif
    case WM_DESTROY:
        if (g_ctrl) ICoreWebView2Controller_Close(g_ctrl);
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(h, m, wp, lp);
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR cmd, int show) {
    WNDCLASSW wc;
    MSG msg;
    HMENU sm;
    WCHAR *p;
    (void)prev; (void)cmd;
    CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    GetModuleFileNameW(NULL, g_dirApp, MAX_PATH);
    if ((p = wcsrchr(g_dirApp, L'\\'))) *p = 0;
    ZeroMemory(&wc, sizeof wc);
    wc.lpfnWndProc = proc; wc.hInstance = inst; wc.lpszClassName = APP_CLASE;
    wc.hIcon = LoadIconW(inst, MAKEINTRESOURCEW(1)); wc.hCursor = LoadCursorW(NULL, (LPCWSTR)IDC_ARROW);
    wc.hbrBackground = CreateSolidBrush(APP_COLOR_FONDO);
    RegisterClassW(&wc);
    g_win = CreateWindowW(APP_CLASE, APP_NOMBRE, WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, APP_ANCHO, APP_ALTO, NULL, NULL, inst, NULL);
    sm = GetSystemMenu(g_win, FALSE);
    AppendMenuW(sm, MF_SEPARATOR, 0, NULL);
    AppendMenuW(sm, MF_STRING, 0x0010, L"Acerca de " APP_NOMBRE L"…");
    ShowWindow(g_win, show);
    UpdateWindow(g_win);
    if (!iniciar_webview()) {
        if (MessageBoxW(g_win, L"Esta aplicación necesita el componente Microsoft Edge WebView2, que viene con Windows 10 y 11.\n\n"
                               L"¿Abrir la página de Microsoft para instalarlo?", APP_NOMBRE, MB_YESNO | MB_ICONINFORMATION) == IDYES)
            ShellExecuteW(NULL, L"open", L"https://go.microsoft.com/fwlink/p/?LinkId=2124703", NULL, NULL, SW_SHOWNORMAL);
        return 1;
    }
    while (GetMessageW(&msg, NULL, 0, 0) > 0) { TranslateMessage(&msg); DispatchMessageW(&msg); }
    CoUninitialize();
    return (int)msg.wParam;
}
