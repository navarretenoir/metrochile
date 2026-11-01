#!/usr/bin/env bash
# Metro Chile para Symbian (Java ME) con TLS 1.2 propio (Bouncy Castle 1.60 ligero, fuentes adaptadas a CLDC en el kit).
exec "$(git rev-parse --show-toplevel)/herramientas"/j2me/construir-midlet.sh "$(dirname "$(readlink -f "$0")")" "Metro Chile" cl.kotone.metro.Metro 1.0.0 javax.microedition.io.Connector.socket "$(git rev-parse --show-toplevel)/herramientas"/j2me/bc-j2me-src
