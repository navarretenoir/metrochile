Name:           metro-chile
Version:        2.1.1
Release:        1%{?dist}
Summary:        Estado en vivo de la red del Metro de Santiago (app no oficial)
License:        GPL-3.0-or-later
URL:            https://github.com/navarretenoir/metrochile
Source0:        https://github.com/navarretenoir/metrochile/archive/refs/tags/v%{version}.tar.gz#/%{name}-%{version}.tar.gz
BuildRequires:  gcc make pkgconfig(gtk4) pkgconfig(webkitgtk-6.0) pkgconfig(json-glib-1.0) pkgconfig(libcurl)
BuildRequires:  desktop-file-utils
BuildRequires:  libappstream-glib

%description
Live status of the lines and stations of the Santiago Metro, network map and nearest station, with the
local weather. Unofficial app, not affiliated with Metro S.A.; data is public from metro.cl.
Interface in Spanish.

%prep
%autosetup -n metrochile-%{version}

%build
make -C desktop ROOT=%{_datadir}/metro-chile EXTRA_CFLAGS="%{optflags} %{build_ldflags}"

%install
D=%{buildroot}
install -Dm755 desktop/metro-app "%{buildroot}%{_prefix}/bin/metro-chile"
install -d "%{buildroot}%{_prefix}/share/metro-chile"; cp -r ui "%{buildroot}%{_prefix}/share/metro-chile/"
install -Dm644 linux/io.github.navarretenoir.MetroChile.desktop "%{buildroot}%{_prefix}/share/applications/io.github.navarretenoir.MetroChile.desktop"
install -Dm644 linux/io.github.navarretenoir.MetroChile.metainfo.xml "%{buildroot}%{_prefix}/share/metainfo/io.github.navarretenoir.MetroChile.metainfo.xml"
install -Dm644 linux/io.github.navarretenoir.MetroChile.svg "%{buildroot}%{_prefix}/share/icons/hicolor/scalable/apps/io.github.navarretenoir.MetroChile.svg"

%check
desktop-file-validate %{buildroot}%{_datadir}/applications/io.github.navarretenoir.MetroChile.desktop
appstream-util validate-relax --nonet %{buildroot}%{_datadir}/metainfo/io.github.navarretenoir.MetroChile.metainfo.xml || :

%files
%license LICENSE
%doc README.md
%{_bindir}/*
%{_datadir}/metro-chile
%{_datadir}/applications/io.github.navarretenoir.MetroChile.desktop
%{_datadir}/metainfo/io.github.navarretenoir.MetroChile.metainfo.xml
%{_datadir}/icons/hicolor/scalable/apps/io.github.navarretenoir.MetroChile.svg


%changelog
* Sun Nov 01 2026 Javiera Navarrete <navarrete.noir@gmail.com> - 2.1.1-1
- Paquete inicial desde el código fuente.
