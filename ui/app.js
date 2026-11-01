// Metro Chile — interfaz única (escritorio GTK4/WebKit + Android WebView). Kotone 2026-10-31 (v2.1).
// Datos OFICIALES de metro.cl: estadoRedDetalle.php (estado en vivo) + estaciones.php (coordenadas, ruta expresa,
// servicios). Enlaces: Clima (tiempo/alertas, vía el host) y ADO O260T («Ir en bici», navegación offline).
"use strict";
(function () {
  const API_ESTADO = "https://www.metro.cl/api/estadoRedDetalle.php";
  const API_ESTACIONES = "https://www.metro.cl/api/estaciones.php";
  const REFRESCO_MS = 30000;
  const LINEAS = ["l1", "l2", "l3", "l4", "l4a", "l5", "l6"];
  const COLOR = { l1: "#E2231A", l2: "#FFB81C", l3: "#8B5A2B", l4: "#0055A5", l4a: "#3EA8DE", l5: "#009A44", l6: "#8E3C97" };
  const TXT_BADGE = { l2: "#1c1c1e" };
  const nombreLinea = k => "Línea " + k.slice(1).toUpperCase();
  const $ = id => document.getElementById(id);
  const esc = s => String(s == null ? "" : s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const fold = s => String(s || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
  const limpio = n => String(n || "").replace(/\s+L\d+A?$/i, "");

  // ---------------------------------------------------------------- puente con el host
  // gtk = escritorio Linux (WebKitGTK) · win = Windows (WebView2): mismo protocolo de mensajes JSON
  const HOST = window.MetroAndroid ? "android" : (window.webkit && webkit.messageHandlers && webkit.messageHandlers.bridge) ? "gtk"
             : (window.chrome && chrome.webview) ? "win" : "web";
  let seq = 0; const espera = {};
  window.__metroHttp = (id, ok, body) => { const f = espera[id]; delete espera[id]; if (f) f(ok ? body : null); };
  window.__metroLoc = (id, lat, lon) => { const f = espera[id]; delete espera[id]; if (f) f(lat == null ? null : { lat, lon }); };
  const postGtk = o => HOST === "win" ? chrome.webview.postMessage(JSON.stringify(o)) : webkit.messageHandlers.bridge.postMessage(JSON.stringify(o));
  function get(url) {
    return new Promise(res => {
      const id = ++seq; espera[id] = res;
      if (HOST === "android") MetroAndroid.get(id, url);
      else if ((HOST === "gtk" || HOST === "win")) postGtk({ action: "get", id, url });
      else fetch(url).then(r => r.ok ? r.text() : null).then(t => window.__metroHttp(id, t != null, t)).catch(() => window.__metroHttp(id, false, ""));
    });
  }
  function ubicar() {
    return new Promise(res => {
      const id = ++seq; espera[id] = res;
      if (HOST === "android") MetroAndroid.locate(id);
      else if ((HOST === "gtk" || HOST === "win")) postGtk({ action: "locate", id });
      else if (navigator.geolocation) navigator.geolocation.getCurrentPosition(p => window.__metroLoc(id, p.coords.latitude, p.coords.longitude), () => window.__metroLoc(id, null, null), { timeout: 8000 });
      else window.__metroLoc(id, null, null);
      setTimeout(() => window.__metroLoc(id, null, null), 12000);
    });
  }
  function avisar(titulo, texto) { if ((HOST === "gtk" || HOST === "win")) postGtk({ action: "notify", titulo, texto }); }
  const aviso = msg => { const t = $("aviso"); t.textContent = msg; t.classList.add("ver"); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove("ver"), 3600); };
  const cache = {
    get(k) { try { return JSON.parse(localStorage.getItem("metro." + k)); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem("metro." + k, JSON.stringify(v)); } catch (e) { } },
  };

  // ---------------------------------------------------------------- modelo
  let ESTADO = null, INFO = {}, POS = null, ultimo = 0, vista = "lineas", CLIMA = null;
  const FAV = new Set(cache.get("favoritas") || []);
  const OCULTAS = new Set();
  const clave = (linea, cod) => linea + ":" + String(cod || "").toUpperCase();

  async function cargarEstaciones() {
    let st = cache.get("estaciones");
    if (!st || Date.now() - (st.ts || 0) > 7 * 86400000) {
      const t = await get(API_ESTACIONES);
      try { const j = JSON.parse(t); if (j && j.estaciones) { st = { ts: Date.now(), lista: j.estaciones }; cache.set("estaciones", st); } } catch (e) { }
    }
    INFO = {};
    ((st && st.lista) || []).forEach(e => {
      const c = String(e.coordenadas || "").split(",").map(Number);
      INFO[clave("l" + e.linea, e.codigo)] = { lat: c[0], lon: c[1], ruta: e.ruta || "", bicimetro: e.bicimetro === "1", intermodal: e.intermodal === "1",
        tren: e.tren === "1", buses: e.buses === "1", aeropuerto: e.aeropuerto === "1", bibliometro: e.bibliometro === "1" };
    });
  }
  function problemas(e) {
    const out = [];
    LINEAS.forEach(k => {
      const L = e[k]; if (!L) return;
      if (L.estado !== "1") out.push({ linea: k, cod: "", txt: L.mensaje_app || L.mensaje || "Con problemas" });
      (L.estaciones || []).forEach(s => { if (s.estado !== "1") out.push({ linea: k, cod: s.codigo, nombre: s.nombre, txt: s.descripcion_app || s.descripcion || "Con problemas" }); });
    });
    return out;
  }
  // estaciones agrupadas por NOMBRE (una combinación = una estación con varias líneas)
  function estaciones() {
    const m = new Map();
    LINEAS.forEach(k => (ESTADO && ESTADO[k] ? ESTADO[k].estaciones : []).forEach(s => {
      const n = limpio(s.nombre), i = INFO[clave(k, s.codigo)] || {};
      if (!m.has(n)) m.set(n, { nombre: n, lineas: [], lat: i.lat, lon: i.lon, info: {} });
      const o = m.get(n); o.lineas.push({ k, s, i });
      if (!isFinite(o.lat)) { o.lat = i.lat; o.lon = i.lon; }
      Object.keys(i).forEach(a => { if (i[a] === true) o.info[a] = true; });
    }));
    return [...m.values()];
  }

  async function actualizar() {
    const b = $("btn-refresh"); b.classList.add("spin");
    if (!Object.keys(INFO).length) await cargarEstaciones();
    const t = await get(API_ESTADO);
    b.classList.remove("spin");
    let e = null; try { e = JSON.parse(t); } catch (x) { }
    if (e && e.l1) { ESTADO = e; ultimo = Date.now(); cache.set("estado", { ts: ultimo, e }); detectarNuevos(e); }
    else if (!ESTADO) { const c = cache.get("estado"); if (c) { ESTADO = c.e; ultimo = c.ts; } }
    pintar(!(e && e.l1));
  }
  let base = null;
  function detectarNuevos(e) {
    const ps = problemas(e), ks = new Set(ps.map(p => clave(p.linea, p.cod) + "|" + p.txt));
    if (base) ps.forEach(p => { if (!base.has(clave(p.linea, p.cod) + "|" + p.txt)) avisar(`Metro: ${nombreLinea(p.linea)}`, p.cod ? `${p.nombre}: ${p.txt}` : p.txt); });
    base = ks;
  }

  // ---------------------------------------------------------------- iconos
  const S = p => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${p}</svg>`;
  const ICON = {
    ok: S('<circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.7 2.7L16.5 9.5"/>'), mal: S('<path d="M10.3 3.9L2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4M12 17h.01"/>'),
    off: S('<path d="M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0M12 19h.01"/><path d="M3 3l18 18"/>'),
    chev: '<svg class="chev" viewBox="0 0 8 13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1.5 1.5L6.5 6.5L1.5 11.5"/></svg>',
    pin: S('<path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/>'), reloj: S('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'),
    bici: S('<circle cx="6" cy="16" r="3.5"/><circle cx="18" cy="16" r="3.5"/><path d="M6 16l3.5-6 3 6 3.5-6M9.5 10h6M15 7h2"/>'), bus: S('<rect x="5" y="3" width="14" height="15" rx="3"/><path d="M5 11h14M8 21v-3M16 21v-3"/>'),
    tren: S('<rect x="6" y="3" width="12" height="13" rx="3"/><path d="M6 10h12M8 20l2-4M16 20l-2-4"/>'), avion: S('<path d="M10 14l-7 2v-2l7-5V4a2 2 0 0 1 4 0v5l7 5v2l-7-2v4l2 2h-8l2-2z"/>'),
    libro: S('<path d="M4 5a2 2 0 0 1 2-2h12v16H6a2 2 0 0 0-2 2z"/><path d="M4 19V5"/>'),
    estrella: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"/></svg>',
    nube: S('<path d="M7 18h10a4 4 0 0 0 .6-8 6 6 0 0 0-11.6 1.5A3.3 3.3 0 0 0 7 18z"/>'),
  };
  const hm = ts => { const d = new Date(ts); return d.getHours() + ":" + String(d.getMinutes()).padStart(2, "0"); };
  const badge = (k, sm) => `<span class="badge${sm ? " sm" : ""}" style="background:${COLOR[k]};color:${TXT_BADGE[k] || "#fff"}">${k.slice(1).toUpperCase()}</span>`;
  const dist = (a, b, c, d) => { const k = Math.PI / 180, x = (d - b) * k * Math.cos((a + c) / 2 * k), y = (c - a) * k; return Math.sqrt(x * x + y * y) * 6371000; };
  const fmtDist = m => m < 1000 ? Math.round(m / 10) * 10 + " m" : (m / 1000).toFixed(1).replace(".", ",") + " km";

  // ---------------------------------------------------------------- horario habitual (sin festivos)
  function horario() {
    const d = new Date(), dia = d.getDay(), min = d.getHours() * 60 + d.getMinutes();
    const tramo = x => x === 0 ? [7 * 60 + 30, 22 * 60 + 30] : x === 6 ? [6 * 60 + 30, 23 * 60] : [6 * 60, 23 * 60];
    const H = tramo(dia), f = m => Math.floor(m / 60) + ":" + String(m % 60).padStart(2, "0");
    const abierto = min >= H[0] && min < H[1];
    const abre = min < H[0] ? H[0] : tramo((dia + 1) % 7)[0];
    return { abierto, txt: abierto ? `Cierra a las ${f(H[1])}` : `Abre a las ${f(abre)}`, pronto: abierto && H[1] - min <= 45 };
  }

  // ---------------------------------------------------------------- pintura
  function pintar(sinRed) {
    $("sub").textContent = !ESTADO ? (sinRed ? "Sin conexión" : "Cargando…") : (sinRed ? `Sin conexión · último dato ${hm(ultimo)}` : `Actualizado ${hm(ultimo)}`);
    const h = $("hero");
    if (!ESTADO) {
      h.className = "hero off"; h.innerHTML = `<div class="ic">${ICON.off}</div><div><div class="t">Sin datos todavía</div><div class="s">Revisa la conexión a internet.</div></div>`;
      $("v-lineas").innerHTML = ""; return;
    }
    const ps = problemas(ESTADO), lineasMal = [...new Set(ps.map(p => p.linea))];
    const franja = `<div class="franja">${LINEAS.filter(k => ESTADO[k]).map(k => `<i class="${lineasMal.includes(k) ? "mal" : ""}" style="background:${COLOR[k]}" title="${nombreLinea(k)}"></i>`).join("")}</div>`;
    if (!ps.length) h.className = "hero ok", h.innerHTML = `<div class="ic">${ICON.ok}</div><div style="flex:1"><div class="t">Toda la red opera con normalidad</div><div class="s">7 líneas · 143 estaciones habilitadas</div>${franja}</div>`;
    else {
      const est = ps.filter(p => p.cod).length;
      h.className = "hero mal";
      h.innerHTML = `<div class="ic">${ICON.mal}</div><div style="flex:1"><div class="t">Problemas en ${lineasMal.map(k => "L" + k.slice(1).toUpperCase()).join(", ")}</div>`
        + `<div class="s">${est ? est + (est === 1 ? " estación afectada" : " estaciones afectadas") : esc(ps[0].txt)}</div>${franja}</div>`;
    }
    const ho = horario();
    $("horario").innerHTML = `<div class="th">${ICON.reloj}Horario</div><div class="tv"><span class="estado-dot" style="background:${ho.abierto ? "#30D158" : "#FF453A"}"></span>${ho.abierto ? "Abierto" : "Cerrado"}</div>`
      + `<div class="ts${ho.pronto ? " mal" : ""}">${ho.txt}</div>`;
    pintarTiempo();
    $("v-lineas").innerHTML = `<div class="th">Estado por línea</div>` + LINEAS.filter(k => ESTADO[k]).map(k => {
      const L = ESTADO[k], mal = (L.estaciones || []).filter(s => s.estado !== "1").length, ok = L.estado === "1" && !mal;
      const exp = L.estaciones.some(s => { const i = INFO[clave(k, s.codigo)]; return i && (i.ruta === "roja" || i.ruta === "verde"); });
      const d = ok ? `${L.estaciones.length} estaciones${exp ? " · ruta expresa" : ""}` : (mal ? `${mal} ${mal === 1 ? "estación con problemas" : "estaciones con problemas"}` : esc(L.mensaje_app || "Con problemas"));
      return `<div class="row" data-l="${k}">${badge(k)}<div class="tx"><div class="n">${nombreLinea(k)}</div><div class="d${ok ? "" : " mal"}">${d}</div></div>`
        + `<span class="pill ${ok ? "ok" : "mal"}">${ok ? "Operativa" : "Con problemas"}</span>${ICON.chev}</div>`;
    }).join("");
    document.querySelectorAll("#v-lineas .row").forEach(r => r.onclick = () => abrirLinea(r.dataset.l));
    pintarFavoritas(); pintarCerca();
    if (vista === "plano" || window.innerWidth >= 900) pintarPlano();
  }

  // tiempo: Android pide a Clima (proveedor con alertas); si no, Open-Meteo vía el host
  async function cargarTiempo() {
    const lat = POS ? POS.lat : -33.4489, lon = POS ? POS.lon : -70.6693;
    CLIMA = null;
    if (HOST === "android" && MetroAndroid.clima) {
      CLIMA = await new Promise(res => { const id = ++seq; espera[id] = t => { try { res(JSON.parse(t)); } catch (e) { res(null); } }; MetroAndroid.clima(id, lat, lon); });
    }
    // máx/mín del día (para ambos sistemas: la misma tarjeta en Linux y Android)
    const t = await get(`https://api.open-meteo.com/v1/forecast?latitude=${lat.toFixed(4)}&longitude=${lon.toFixed(4)}&current=temperature_2m,weather_code,wind_gusts_10m&hourly=precipitation_probability,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=America/Santiago`);
    let om = null; try { om = JSON.parse(t); } catch (e) { }
    if (om && om.current) {
      const c = om.current, d = om.daily || {};
      if (!CLIMA || !CLIMA.ok) {
        // sin Clima (escritorio o app no instalada): los mismos avisos básicos, calculados aquí
        const hh = new Date().getHours(), pp = (om.hourly.precipitation_probability || []).slice(hh + 1, hh + 4), al = [];
        const iMax = pp.indexOf(Math.max(...pp));
        if (c.weather_code >= 95) al.push({ id: "tormenta", nivel: "danger", titulo: "Tormenta eléctrica" });
        else if (c.weather_code >= 51) al.push({ id: "lluvia", nivel: "warn", titulo: "Lloviendo ahora" });
        else if (pp[iMax] >= 60) al.push({ id: "lluvia_pronto", nivel: "warn", titulo: `Lluvia hacia las ${hh + 1 + iMax}:00 (${pp[iMax]}%)` });
        if (c.wind_gusts_10m >= 45) al.push({ id: "viento", nivel: "warn", titulo: `Ráfagas de ${Math.round(c.wind_gusts_10m)} km/h` });
        if (c.temperature_2m >= 31) al.push({ id: "calor", nivel: "warn", titulo: `Calor: ${Math.round(c.temperature_2m)}°` });
        if (c.temperature_2m <= 2) al.push({ id: "helada", nivel: "warn", titulo: `Helada: ${Math.round(c.temperature_2m)}°` });
        CLIMA = { ok: true, temp: Math.round(c.temperature_2m), codigo: c.weather_code, alertas: al };
      }
      CLIMA.max = d.temperature_2m_max ? Math.round(d.temperature_2m_max[0]) : null;
      CLIMA.min = d.temperature_2m_min ? Math.round(d.temperature_2m_min[0]) : null;
    }
    pintarTiempo();
  }
  const TXT_CODIGO = c => c >= 95 ? "Tormenta" : (c >= 71 && c <= 77) || c === 85 || c === 86 ? "Nieve" : c >= 51 ? "Lluvia" : c >= 45 ? "Niebla" : c >= 2 ? "Nublado" : "Despejado";
  // aviso más relevante del momento (cualquier tipo: viento, calor, lluvia, niebla…), sin consejos de bici
  const aviso1 = () => { const a = (CLIMA && CLIMA.alertas || []).filter(x => x.titulo && x.id !== "noche"); return a.find(x => x.nivel === "danger") || a.find(x => x.nivel === "warn") || null; };
  function pintarTiempo() {
    const box = $("tiempo");
    if (!CLIMA || !CLIMA.ok) { box.hidden = true; return; }
    box.hidden = false;
    box.innerHTML = `<div class="th">${ICON.nube}Tiempo</div><div class="tv">${CLIMA.temp}° · ${esc(CLIMA.texto || TXT_CODIGO(CLIMA.codigo))}</div>`
      + `<div class="ts${aviso1() ? " mal" : ""}">${esc(aviso1() ? aviso1().titulo : CLIMA.max != null ? `Máx ${CLIMA.max}° · Mín ${CLIMA.min}°` : `Sensación ${CLIMA.sensacion ?? CLIMA.temp}°`)}</div>`;
  }

  // ---------------------------------------------------------------- favoritas y cercanas
  function filaEstacion(e, extra) {
    const mal = e.lineas.find(o => o.s.estado !== "1");
    const est = mal ? esc(mal.s.descripcion_app || "Con problemas") : "Habilitada";
    return `<div class="row" data-est="${esc(e.nombre)}"><div style="display:flex;gap:3px;flex:0 0 auto">${e.lineas.map(o => badge(o.k, true)).join("")}</div>`
      + `<div class="tx"><div class="n">${esc(e.nombre)}</div><div class="d${mal ? " mal" : ""}">${extra ? extra + " · " : ""}${est}${e.info.bicimetro ? " · Bicimetro" : ""}</div></div>${ICON.chev}</div>`;
  }
  function pintarFavoritas() {
    const box = $("favoritas"), L = estaciones().filter(e => FAV.has(e.nombre));
    if (!L.length) { box.hidden = true; return; }
    box.hidden = false;
    box.innerHTML = `<div class="th">${ICON.estrella.replace("<svg", '<svg style="color:#FFD60A"')}Mis estaciones</div>`
      + L.map(e => filaEstacion(e, POS && isFinite(e.lat) ? fmtDist(dist(POS.lat, POS.lon, e.lat, e.lon)) : "")).join("");
    box.querySelectorAll(".row").forEach(r => r.onclick = () => abrirEstacion(r.dataset.est));
  }
  function pintarCerca() {
    const box = $("cerca");
    if (!POS || !ESTADO) { box.hidden = true; return; }
    const L = estaciones().filter(e => isFinite(e.lat)).map(e => ({ e, m: dist(POS.lat, POS.lon, e.lat, e.lon) })).sort((a, b) => a.m - b.m).slice(0, 3);
    if (!L.length || L[0].m > 8000) { box.hidden = true; return; }
    box.hidden = false;
    box.innerHTML = `<div class="th">${ICON.pin}Cerca de ti</div>` + L.map(o => filaEstacion(o.e, fmtDist(o.m))).join("");
    box.querySelectorAll(".row").forEach(r => r.onclick = () => abrirEstacion(r.dataset.est));
  }

  // ---------------------------------------------------------------- búsqueda
  $("q").addEventListener("input", () => {
    const q = fold($("q").value.trim()), box = $("sugerencias");
    if (!q || !ESTADO) { box.hidden = true; return; }
    const L = estaciones().filter(e => fold(e.nombre).includes(q)).sort((a, b) => fold(a.nombre).indexOf(q) - fold(b.nombre).indexOf(q)).slice(0, 8);
    box.innerHTML = L.length ? L.map(e => filaEstacion(e, "")).join("") : `<div class="err">Sin estaciones con «${esc($("q").value)}»</div>`;
    box.hidden = false;
    box.querySelectorAll(".row").forEach(r => r.onclick = () => { box.hidden = true; $("q").value = ""; $("q").blur(); abrirEstacion(r.dataset.est); });
  });
  document.addEventListener("click", e => { if (!e.target.closest(".buscar")) $("sugerencias").hidden = true; });

  // ---------------------------------------------------------------- hojas
  const btnX = `<button class="round x" id="sh-x" aria-label="Cerrar">${S('<path d="M6 6l12 12M18 6L6 18"/>')}</button>`;
  function abrirHoja(html) {
    $("sheet").innerHTML = `<div class="grab"></div>` + html;
    $("sheet-bg").hidden = false; $("sheet").classList.add("show"); $("sheet").setAttribute("aria-hidden", "false"); $("sheet").scrollTop = 0;
    const x = $("sh-x"); if (x) x.onclick = cerrarHoja;
  }
  function cerrarHoja() { $("sheet").classList.remove("show"); $("sheet").setAttribute("aria-hidden", "true"); $("sheet-bg").hidden = true; }
  window.metroAtras = () => {
    if ($("sheet").classList.contains("show")) { cerrarHoja(); return true; }
    if (!$("sugerencias").hidden) { $("sugerencias").hidden = true; return true; }
    return false;
  };
  $("sheet-bg").onclick = cerrarHoja;

  function abrirEstacion(nombre) {
    const e = estaciones().find(x => x.nombre === nombre); if (!e) return;
    const mal = e.lineas.filter(o => o.s.estado !== "1");
    const m = POS && isFinite(e.lat) ? dist(POS.lat, POS.lon, e.lat, e.lon) : null;
    const srv = [e.info.bicimetro && [ICON.bici, "Bicimetro"], (e.info.intermodal || e.info.buses) && [ICON.bus, "Buses"], e.info.tren && [ICON.tren, "Tren"],
      e.info.aeropuerto && [ICON.avion, "Aeropuerto"], e.info.bibliometro && [ICON.libro, "Bibliometro"]].filter(Boolean);
    const esFav = FAV.has(e.nombre);
    abrirHoja(`<div class="sh-top"><div style="display:flex;gap:4px">${e.lineas.map(o => badge(o.k)).join("")}</div><div><div class="t">${esc(e.nombre)}</div>`
      + `<div class="s">${mal.length ? "Con problemas" : "Habilitada"}${m != null ? " · a " + fmtDist(m) : ""}</div></div>${btnX}</div>`
      + mal.map(o => `<div class="aviso">${nombreLinea(o.k)}: ${esc(o.s.descripcion_app || o.s.descripcion)}</div>`).join("")
      + `<div class="acciones"><button class="prim" id="a-bici">${ICON.bici}Ir en bici</button>`
      + `<button id="a-fav">${ICON.estrella.replace("<svg", `<svg style="color:${esFav ? "#FFD60A" : "currentColor"}"`)}${esFav ? "Quitar de favoritas" : "Favorita"}</button></div>`
      + (srv.length ? `<div class="th">Servicios</div><div class="serv">${srv.map(([i, t]) => `<span>${i}${t}</span>`).join("")}</div>` : "")
      + `<div class="th">Líneas</div><div class="lineas-est">${e.lineas.map(o => {
        const ruta = o.i.ruta === "roja" ? '<span class="chip roja">Ruta roja</span>' : o.i.ruta === "verde" ? '<span class="chip verde">Ruta verde</span>' : o.i.ruta === "comun" ? '<span class="chip comun">Común</span>' : "";
        return `<div class="row" data-l="${o.k}" data-c="${esc(o.s.codigo)}">${badge(o.k)}<div class="tx"><div class="n">${nombreLinea(o.k)}</div>`
          + `<div class="d${o.s.estado !== "1" ? " mal" : ""}">${o.s.estado !== "1" ? esc(o.s.descripcion_app) : "Operativa"} ${ruta}</div></div>${ICON.chev}</div>`;
      }).join("")}</div>`);
    $("a-bici").onclick = () => irEnBici(e);
    $("a-fav").onclick = () => { esFav ? FAV.delete(e.nombre) : FAV.add(e.nombre); cache.set("favoritas", [...FAV]); pintarFavoritas(); abrirEstacion(e.nombre); };
    document.querySelectorAll("#sheet .lineas-est .row").forEach(r => r.onclick = () => abrirLinea(r.dataset.l, r.dataset.c));
  }
  function irEnBici(e) {
    if (!isFinite(e.lat)) return;
    if (HOST === "android" && MetroAndroid.bici) { if (!MetroAndroid.bici(e.lat, e.lon, "Metro " + e.nombre)) aviso("Instala ADO O260T para navegar en bici"); }
    else if ((HOST === "gtk" || HOST === "win")) postGtk({ action: "abrir", url: `https://www.openstreetmap.org/directions?engine=fossgis_osrm_bike&route=%3B${e.lat}%2C${e.lon}` });
    else window.open(`https://www.openstreetmap.org/?mlat=${e.lat}&mlon=${e.lon}#map=17/${e.lat}/${e.lon}`);
  }

  let filtro = "todas";
  function abrirLinea(k, codFoco) {
    const L = ESTADO && ESTADO[k]; if (!L) return;
    const tieneExpresa = L.estaciones.some(s => { const i = INFO[clave(k, s.codigo)]; return i && (i.ruta === "roja" || i.ruta === "verde"); });
    const mal = L.estaciones.filter(s => s.estado !== "1").length;
    const pintarHoja = () => {
      const filas = L.estaciones.filter(s => { if (filtro === "todas") return true; const i = INFO[clave(k, s.codigo)] || {}; return i.ruta === filtro || i.ruta === "comun" || !i.ruta; }).map(s => {
        const i = INFO[clave(k, s.codigo)] || {}, comb = !!s.combinacion, bad = s.estado !== "1", nom = limpio(s.nombre);
        const combs = comb ? s.combinacion.split(/[,\s]+/).filter(Boolean).map(c => { const kk = "l" + c.replace(/^L/i, "").toLowerCase(); return COLOR[kk] ? badge(kk, true) : ""; }).join("") : "";
        const ruta = i.ruta === "roja" ? '<span class="chip roja">Ruta roja</span>' : i.ruta === "verde" ? '<span class="chip verde">Ruta verde</span>' : i.ruta === "comun" && tieneExpresa ? '<span class="chip comun">Común</span>' : "";
        const srv = [i.bicimetro ? ICON.bici : "", i.intermodal || i.buses ? ICON.bus : "", i.tren ? ICON.tren : "", i.aeropuerto ? ICON.avion : ""].join("");
        return `<div class="est-row${comb ? " comb" : ""}${bad ? " mal" : ""}" id="e-${esc(s.codigo)}" data-est="${esc(nom)}"><div class="riel"><div class="nodo"></div></div><div class="tx">`
          + `<div class="n">${esc(nom)}${FAV.has(nom) ? ' <span style="color:#FFD60A">★</span>' : ""}</div><div class="d${bad ? " mal" : ""}">${bad ? esc(s.descripcion_app || s.descripcion) : ""}${combs}${ruta}${srv ? `<span class="srv">${srv}</span>` : ""}</div></div></div>`;
      }).join("");
      abrirHoja(`<div class="sh-top">${badge(k)}<div><div class="t">${nombreLinea(k)}</div><div class="s">${L.estaciones.length} estaciones · ${mal ? mal + " con problemas" : "operativa"}</div></div>${btnX}</div>`
        + (L.estado !== "1" || L.mensaje ? `<div class="aviso">${esc(L.mensaje || L.mensaje_app)}</div>` : "")
        + (tieneExpresa ? `<div class="filtro">${["todas", "roja", "verde"].map(f => `<button data-f="${f}" class="${filtro === f ? "on" : ""}">${f === "todas" ? "Todas" : "Ruta " + f}</button>`).join("")}</div>` : "")
        + `<div class="rail" style="--lc:${COLOR[k]}">${filas}</div>`);
      document.querySelectorAll("#sheet .filtro button").forEach(b => b.onclick = () => { filtro = b.dataset.f; pintarHoja(); });
      document.querySelectorAll("#sheet .est-row").forEach(r => r.onclick = () => abrirEstacion(r.dataset.est));
    };
    filtro = "todas"; pintarHoja();
    if (codFoco) setTimeout(() => { const el = $("e-" + codFoco); if (el) el.scrollIntoView({ block: "center" }); }, 350);
  }

  // ---------------------------------------------------------------- plano geográfico (coordenadas oficiales)
  let VB = null, VB0 = null;
  function pintarChips() {
    $("chips-lineas").innerHTML = LINEAS.map(k => `<button data-l="${k}" class="${OCULTAS.has(k) ? "off" : ""}" style="background:${COLOR[k]};color:${TXT_BADGE[k] || "#fff"}">${k.slice(1).toUpperCase()}</button>`).join("");
    document.querySelectorAll("#chips-lineas button").forEach(b => b.onclick = () => { OCULTAS.has(b.dataset.l) ? OCULTAS.delete(b.dataset.l) : OCULTAS.add(b.dataset.l); pintarChips(); pintarPlano(); });
  }
  function pintarPlano() {
    const svg = $("plano"); if (!ESTADO) return;
    const pts = [];
    LINEAS.forEach(k => (ESTADO[k] ? ESTADO[k].estaciones : []).forEach(s => { const i = INFO[clave(k, s.codigo)]; if (i && isFinite(i.lat)) pts.push(i); }));
    if (!pts.length) { svg.innerHTML = ""; return; }
    const lat0 = pts.reduce((a, p) => a + p.lat, 0) / pts.length, kx = Math.cos(lat0 * Math.PI / 180) * 1000, ky = 1000;
    const P = i => [i.lon * kx, -i.lat * ky];
    const xs = pts.map(p => P(p)[0]), ys = pts.map(p => P(p)[1]);
    const pad = 12, x0 = Math.min(...xs) - pad, y0 = Math.min(...ys) - pad, w = Math.max(...xs) - x0 + pad, hh = Math.max(...ys) - y0 + pad;
    VB0 = [x0, y0, w, hh]; if (!VB) VB = VB0.slice();
    let vias = "", tramos = "", nodos = "", auras = "", lbls = "", yo = "";
    const vistos = new Set();
    LINEAS.forEach(k => {
      const L = ESTADO[k]; if (!L || OCULTAS.has(k)) return;
      const sq = L.estaciones.map(s => ({ s, i: INFO[clave(k, s.codigo)] })).filter(o => o.i && isFinite(o.i.lat));
      vias += `<polyline class="via" stroke="${COLOR[k]}" points="${sq.map(o => P(o.i).map(v => v.toFixed(1)).join(",")).join(" ")}"/>`;
      for (let j = 1; j < sq.length; j++) if (sq[j].s.estado !== "1" && sq[j - 1].s.estado !== "1")
        tramos += `<line class="tramo-mal" x1="${P(sq[j - 1].i)[0]}" y1="${P(sq[j - 1].i)[1]}" x2="${P(sq[j].i)[0]}" y2="${P(sq[j].i)[1]}"/>`;
      sq.forEach((o, j) => {
        const [x, y] = P(o.i), mal = o.s.estado !== "1", comb = !!o.s.combinacion, nom = limpio(o.s.nombre);
        if (mal) auras += `<circle class="aura" cx="${x}" cy="${y}" r="7"/>`;
        nodos += `<circle class="est${comb ? " comb" : ""}${mal ? " mal" : ""}" cx="${x}" cy="${y}" r="3" data-est="${esc(nom)}"><title>${esc(nom)}</title></circle>`;
        if (vistos.has(nom)) return; vistos.add(nom);
        const nivel = (mal || FAV.has(nom) || j === 0 || j === sq.length - 1) ? "l0" : comb ? "l1" : "l2";
        lbls += `<text class="lbl ${nivel}" x="${x}" y="${y}">${esc(nom)}</text>`;
      });
    });
    if (POS) { const [x, y] = P({ lat: POS.lat, lon: POS.lon }); yo = `<circle class="yo-halo" cx="${x}" cy="${y}" r="9"/><circle class="yo" cx="${x}" cy="${y}" r="5"/>`; }
    svg.setAttribute("viewBox", VB.join(" "));
    svg.innerHTML = vias + tramos + auras + nodos + yo + lbls;
    escala();
    svg.querySelectorAll(".est").forEach(c => c.addEventListener("click", ev => { ev.stopPropagation(); abrirEstacion(c.dataset.est); }));
    if (!$("plano-wrap").querySelector(".zoom")) {
      $("plano-wrap").insertAdjacentHTML("beforeend", '<div class="zoom"><button data-z="0.7">+</button><button data-z="1.43">−</button><button data-z="0">⤢</button></div>');
      $("plano-wrap").querySelectorAll(".zoom button").forEach(b => b.onclick = () => {
        const z = +b.dataset.z; if (!z) { VB = VB0.slice(); $("plano").setAttribute("viewBox", VB.join(" ")); escala(); } else zoom(z, VB[0] + VB[2] / 2, VB[1] + VB[3] / 2);
      });
    }
  }
  function zoom(f, cx, cy) {
    const nw = Math.min(VB0[2] * 1.2, Math.max(VB0[2] / 14, VB[2] * f)), r = nw / VB[2];
    VB = [cx - (cx - VB[0]) * r, cy - (cy - VB[1]) * r, nw, VB[3] * r];
    $("plano").setAttribute("viewBox", VB.join(" ")); escala();
  }
  function escala() {
    const svg = $("plano"), w = svg.clientWidth || 380, h = svg.clientHeight || 360; if (!VB) return;
    // el SVG se ajusta por el lado que manda (preserveAspectRatio «meet»): unidades por píxel = el mayor de ambos
    const u = Math.max(VB[2] / w, VB[3] / h), z = VB0[2] / VB[2];
    svg.style.setProperty("--u", u.toFixed(4)); svg.dataset.z = z > 4 ? "2" : z > 1.8 ? "1" : "0";
  }
  window.addEventListener("resize", () => { if (window.innerWidth >= 900) pintarPlano(); else escala(); });
  (function () {
    const wrap = $("plano-wrap"), pts = new Map(); let d0 = 0;
    const aSvg = (x, y) => { const r = wrap.getBoundingClientRect(); return [VB[0] + (x - r.left) / r.width * VB[2], VB[1] + (y - r.top) / r.height * VB[3]]; };
    wrap.addEventListener("pointerdown", e => { if (e.target.closest(".zoom")) return; pts.set(e.pointerId, [e.clientX, e.clientY]); });
    wrap.addEventListener("pointermove", e => {
      if (!pts.has(e.pointerId) || !VB) return;
      const prev = pts.get(e.pointerId); pts.set(e.pointerId, [e.clientX, e.clientY]);
      if (pts.size === 1) { const r = wrap.getBoundingClientRect(); VB[0] -= (e.clientX - prev[0]) / r.width * VB[2]; VB[1] -= (e.clientY - prev[1]) / r.height * VB[3]; $("plano").setAttribute("viewBox", VB.join(" ")); }
      else if (pts.size === 2) { const [a, b] = [...pts.values()], d = Math.hypot(a[0] - b[0], a[1] - b[1]); if (d0) { const c = aSvg((a[0] + b[0]) / 2, (a[1] + b[1]) / 2); zoom(d0 / d, c[0], c[1]); } d0 = d; }
    });
    const fin = e => { pts.delete(e.pointerId); if (pts.size < 2) d0 = 0; };
    wrap.addEventListener("pointerup", fin); wrap.addEventListener("pointercancel", fin); wrap.addEventListener("pointerleave", fin);
    wrap.addEventListener("wheel", e => { e.preventDefault(); const c = aSvg(e.clientX, e.clientY); zoom(e.deltaY > 0 ? 1.18 : 0.85, c[0], c[1]); }, { passive: false });
  })();

  // ---------------------------------------------------------------- arranque
  document.querySelectorAll(".seg button").forEach(b => b.onclick = () => {
    document.querySelectorAll(".seg button").forEach(x => x.classList.toggle("on", x === b));
    vista = b.dataset.v; $("v-lineas").hidden = vista !== "lineas"; $("col-b").classList.toggle("ver", vista === "plano");
    if (vista === "plano") { pintarPlano(); $("col-b").scrollIntoView({ block: "start" }); }
  });
  $("btn-refresh").onclick = () => actualizar();
  window.metroResume = () => { if (Date.now() - ultimo > REFRESCO_MS) actualizar(); };
  window.addEventListener("load", async () => {
    pintarChips();
    const c = cache.get("estado"); if (c) { ESTADO = c.e; ultimo = c.ts; }
    await cargarEstaciones(); pintar(false);
    actualizar();
    ubicar().then(p => { if (p) { POS = p; pintarCerca(); pintarFavoritas(); pintarPlano(); } cargarTiempo(); });
    setInterval(() => { if (!document.hidden) actualizar(); }, REFRESCO_MS);
    setInterval(() => { if (!document.hidden) cargarTiempo(); }, 15 * 60000);
  });
})();
