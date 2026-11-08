# Cambios / Changelog

## 2.2.0 (2026-11-08)
- Corrección: la tarjeta «Toda la red opera con normalidad» traía fijo «7 líneas · 143 estaciones»; ahora el conteo sale de los datos recibidos.
- Corrección: el horario del servicio y el aviso de lluvia por hora usaban la hora del teléfono; ahora usan la hora de Santiago.
- Sin conexión: el «último dato» indica el día cuando no es de hoy (antes sólo mostraba la hora y un dato de ayer parecía reciente).
- Se evitan actualizaciones simultáneas (botón + temporizador) que podían pisarse.
- Accesibilidad: filas de línea y estación operables con teclado y lector de pantalla (Enter/Espacio, Escape cierra la hoja), nombre accesible en el buscador, foco visible, objetivos táctiles de 44 px, respeto de «reducir movimiento».
- Android: el vigía ignora respuestas incompletas de metro.cl (antes podían marcar como resueltos los problemas vigentes y repetir los avisos); si el sistema cierra la vista web por falta de memoria, la pantalla se recrea en vez de cerrar la app.
- Pruebas: `node tests/ui.test.js` (conteo, problemas, sin red, caché de ayer); se ejecutan en CI.
- Versiones unificadas en 2.2.0 (Android versionCode 3, escritorio, Windows, Apple y paquetes).

## Primera versión pública / First public release
- Android 5.0+, Linux (RPM, DEB, AppImage, Flatpak, Snap), Windows, macOS/iOS. Fuentes y paquetes en las *releases*.
