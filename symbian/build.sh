#!/usr/bin/env bash
# Metro SCL para Symbian (Java ME) con TLS 1.2 propio (Bouncy Castle 1.60 ligero, fuentes adaptadas a CLDC en el kit).
exec ~/.local/share/kotone-herramientas/j2me/construir-midlet.sh "$(dirname "$(readlink -f "$0")")" "Metro Chile" cl.kotone.metro.Metro 2.0.0 javax.microedition.io.Connector.socket ~/.local/share/kotone-herramientas/j2me/bc-j2me-src:$HOME/Proyectos/SYMBIAN_KIT/src
