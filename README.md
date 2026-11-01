# Metro Chile

Estado **en vivo** de las líneas y estaciones del Metro de Santiago, plano de la red, estación más cercana
y clima del lugar. Avisa cuando aparece un problema nuevo en la red. Sin publicidad, sin rastreadores, sin cuentas.

> **App no oficial.** No está afiliada a Metro S.A. ni la representa. Los datos vienen del sitio público
> de Metro de Santiago (metro.cl) y de Open-Meteo.

**Desarrollado por Javiera Navarrete** · Licencia [GPL-3.0-or-later](LICENSE)

## Plataformas

| Carpeta | Plataforma | Cómo se compila |
|---|---|---|
| `android/` | Android 5.0 → 16 | Android Studio o `./gradlew assembleRelease` (sin dependencias externas) |
| `desktop/` + `linux/` | Linux (GTK 4 + WebKitGTK 6) | `make -C desktop`; paquetes RPM/DEB/AppImage/Flatpak/Snap con `herramientas/linux-pkg/empaquetar.sh . todo` |
| `windows/` | Windows 10/11 (y 7/8.1 con WebView2) | `herramientas/winhost/construir.sh windows` (MinGW-w64 + SDK de WebView2 + NSIS); proyecto de Visual Studio en `visualstudio/` |
| `apple/` | macOS (Intel y Apple Silicon) e iOS/iPadOS | `xcodegen generate` dentro de `apple/` (también lo compila el flujo de GitHub Actions) |
| `symbian/` | Symbian / Java ME (MIDP 2.0, CLDC 1.1) | `symbian/build.sh`: incluye TLS 1.2 propio porque metro.cl no acepta los protocolos antiguos de Symbian |

`ui/` es la interfaz compartida (HTML/CSS/JS) y `assets/` los datos de estaciones y el ícono.

## Créditos

- Estado de la red: sitio público de Metro de Santiago. Clima: [Open-Meteo](https://open-meteo.com) (CC BY 4.0).
- Tipografía Inter (SIL Open Font License). TLS de Symbian: Bouncy Castle (licencia MIT, ver `symbian/res/LICENCIAS.txt`).
