; Instalador de Metro Chile para Windows 10/11 (y 7/8.1 con WebView2) — desarrollado por Javiera Navarrete.
Unicode true
SetCompressor /SOLID lzma
!include "MUI2.nsh"
!define NOMBRE "Metro Chile"
!define EXE "MetroChile"
!define VERSION "2.0"
!define AUTORA "Javiera Navarrete"
!define CLAVE_DESINST "Software\Microsoft\Windows\CurrentVersion\Uninstall\MetroChile"
Name "${NOMBRE} ${VERSION}"
OutFile "..\dist\MetroChile-${VERSION}-instalador.exe"
InstallDir "$PROGRAMFILES\${NOMBRE}"
InstallDirRegKey HKLM "Software\${AUTORA}\${EXE}" "Carpeta"
RequestExecutionLevel admin
BrandingText "${NOMBRE} · desarrollado por ${AUTORA}"
VIProductVersion "2.0.0.0"
VIAddVersionKey /LANG=1034 "ProductName" "${NOMBRE}"
VIAddVersionKey /LANG=1034 "CompanyName" "${AUTORA}"
VIAddVersionKey /LANG=1034 "LegalCopyright" "(c) 2026 ${AUTORA}"
VIAddVersionKey /LANG=1034 "FileDescription" "Instalador de ${NOMBRE}"
VIAddVersionKey /LANG=1034 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1034 "ProductVersion" "${VERSION}"
!define MUI_ICON "..\src\app.ico"
!define MUI_UNICON "..\src\app.ico"
!define MUI_WELCOMEPAGE_TITLE "Bienvenida a ${NOMBRE}"
!define MUI_WELCOMEPAGE_TEXT "Metro Chile muestra en vivo el estado de las líneas y estaciones del Metro, el plano de la red y el clima del lugar.$\r$\n$\r$\nCaracterísticas:$\r$\n  • Estado de cada línea y estación, con avisos de cierres y demoras$\r$\n  • Plano de la red y combinaciones$\r$\n  • Estaciones favoritas$\r$\n  • Funciona con Windows 10 y 11 (y 7/8.1 con Microsoft Edge WebView2)$\r$\n$\r$\nApp no oficial: no está afiliada a Metro S.A. Los datos son públicos de metro.cl.$\r$\n$\r$\nDesarrollado por ${AUTORA}."
!define MUI_LICENSEPAGE_TEXT_TOP "Lee el acuerdo de uso antes de instalar."
!define MUI_FINISHPAGE_RUN "$INSTDIR\${EXE}.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Abrir ${NOMBRE} ahora"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "ACUERDO.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "Spanish"
Section "${NOMBRE}"
  SectionIn RO
  SetOutPath "$INSTDIR"
  File "..\dist\${EXE}.exe"
  File "..\dist\WebView2Loader.dll"
  File "ACUERDO.txt"
  File /r "..\dist\ui"
  WriteUninstaller "$INSTDIR\Desinstalar.exe"
  SetShellVarContext all
  CreateShortCut "$SMPROGRAMS\${NOMBRE}.lnk" "$INSTDIR\${EXE}.exe" "" "$INSTDIR\${EXE}.exe" 0 "" "" "Estado del Metro de Santiago en vivo"
  CreateShortCut "$DESKTOP\${NOMBRE}.lnk" "$INSTDIR\${EXE}.exe"
  WriteRegStr HKLM "Software\${AUTORA}\${EXE}" "Carpeta" "$INSTDIR"
  WriteRegStr HKLM "${CLAVE_DESINST}" "DisplayName" "${NOMBRE}"
  WriteRegStr HKLM "${CLAVE_DESINST}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKLM "${CLAVE_DESINST}" "Publisher" "${AUTORA}"
  WriteRegStr HKLM "${CLAVE_DESINST}" "DisplayIcon" "$INSTDIR\${EXE}.exe"
  WriteRegStr HKLM "${CLAVE_DESINST}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "${CLAVE_DESINST}" "UninstallString" '"$INSTDIR\Desinstalar.exe"'
  WriteRegStr HKLM "${CLAVE_DESINST}" "Comments" "Desarrollado por ${AUTORA}"
  WriteRegDWORD HKLM "${CLAVE_DESINST}" "NoModify" 1
  WriteRegDWORD HKLM "${CLAVE_DESINST}" "NoRepair" 1
SectionEnd
Section "Uninstall"
  SetShellVarContext all
  RMDir /r "$INSTDIR"
  Delete "$SMPROGRAMS\${NOMBRE}.lnk"
  Delete "$DESKTOP\${NOMBRE}.lnk"
  DeleteRegKey HKLM "${CLAVE_DESINST}"
  DeleteRegKey HKLM "Software\${AUTORA}\${EXE}"
SectionEnd
