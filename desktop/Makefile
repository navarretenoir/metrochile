# Metro Chile — host de escritorio GTK4 + WebKitGTK 6 de la interfaz ui/ (compartida con Android).
ROOT := $(abspath $(CURDIR)/..)
PKGS := gtk4 webkitgtk-6.0 json-glib-1.0 libcurl
CFLAGS := -O2 -Wall -Wextra -std=c11 $(shell pkg-config --cflags $(PKGS)) -DMETRO_ROOT='"$(ROOT)"' $(EXTRA_CFLAGS)
LIBS := $(shell pkg-config --libs $(PKGS))
metro-app: metro-app.c
	$(CC) $(CFLAGS) -o $@ $< $(LIBS)
clean:
	rm -f metro-app
