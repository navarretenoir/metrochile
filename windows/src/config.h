/* Metro de Santiago para Windows — configuración del anfitrión WebView2 (webhost.c). Desarrollado por Javiera Navarrete. */
#define APP_NOMBRE          L"Metro de Santiago"
#define APP_CLASE           L"MetroSCLJavieraNavarrete"
#define APP_AGENTE          "Metro-SCL-Windows/2.0 (Javiera Navarrete)"
#define APP_HOST_VIRTUAL    L"metro.local"
#define APP_CARPETA_UI      L"ui"
#define APP_INICIO          L"https://metro.local/index.html"
#define APP_CARPETA_DATOS   L"MetroSCL"
#define APP_PUENTE          1
#define APP_VENTANAS_DENTRO 0
#define APP_MENU_CONTEXTUAL FALSE
#define APP_ANCHO           460
#define APP_ALTO            860
#define APP_COLOR_FONDO     RGB(0xF2, 0xF2, 0xF7)
#define APP_ACERCA          L"Metro de Santiago 2.0 para Windows\n\n" \
                            L"Estado de la red desde metro.cl (dato público). App no oficial: no está afiliada a Metro S.A.\n" \
                            L"Clima del lugar: Open-Meteo (CC BY 4.0). F5 actualiza."
static const WCHAR *APP_FUERA[] = {L"openstreetmap.org", L"google.com/maps", L"twitter.com", L"x.com/", L"facebook.com", L"instagram.com", NULL};
static const WCHAR *APP_PERMITIDOS[] = {L"https://www.metro.cl/", L"https://api.open-meteo.com/", NULL};
