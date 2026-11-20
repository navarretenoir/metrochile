#!/usr/bin/env bash
# Copia la interfaz compartida ../ui a Recursos/web (la misma de Android, Linux y Windows). Desarrollado por Javiera Navarrete.
set -euo pipefail
A=$(cd "$(dirname "$0")" && pwd); rm -rf "$A/Recursos/web"; cp -r "$A/../ui" "$A/Recursos/web"; rm -f "$A/Recursos/web/"*.bak-*
echo "✔ Recursos/web listo"
