// Pruebas de la interfaz (ui/app.js) sin navegador: DOM mínimo falso + datos de ejemplo (assets/sample.json).
// Uso: node tests/ui.test.js
"use strict";
const fs = require("fs"), vm = require("vm"), path = require("path"), assert = require("assert");
const RAIZ = path.join(__dirname, "..");
const SRC = fs.readFileSync(path.join(RAIZ, "ui/app.js"), "utf8");
const MUESTRA = fs.readFileSync(path.join(RAIZ, "assets/sample.json"), "utf8");

class El {
  constructor(id) { this.id = id; this.innerHTML = ""; this.textContent = ""; this.hidden = false; this.style = { setProperty() {} }; this.dataset = {};
    this.attrs = {}; this.listeners = {}; const self = this; this.className = "";
    this.classList = { add() {}, remove() {}, toggle() {}, contains() { return false; } }; this.value = ""; this.scrollTop = 0; this.clientWidth = 380; this.clientHeight = 360; }
  addEventListener(t, f) { (this.listeners[t] = this.listeners[t] || []).push(f); }
  setAttribute(k, v) { this.attrs[k] = v; } getAttribute(k) { return this.attrs[k]; }
  querySelectorAll() { return []; } querySelector() { return null; } closest() { return null; } insertAdjacentHTML() {} focus() {} blur() {}
  getBoundingClientRect() { return { left: 0, top: 0, width: 380, height: 360 }; }
}

function entorno({ estado, conRed = true }) {
  const els = {}, cargas = [], almacen = {};
  const document = { hidden: false, getElementById: id => els[id] || (els[id] = new El(id)), querySelectorAll: () => [], addEventListener() {} };
  const window = { innerWidth: 400, addEventListener: (t, f) => { if (t === "load") cargas.push(f); } };
  const ctx = {
    window, document, console, setTimeout, clearTimeout, setInterval() {}, Intl, Date, Math, JSON, Promise, Set, Map, Number, String, isFinite,
    localStorage: { getItem: k => (k in almacen ? almacen[k] : null), setItem: (k, v) => { almacen[k] = v; } },
    fetch: url => Promise.resolve(conRed && /estadoRedDetalle/.test(url) ? { ok: true, text: () => Promise.resolve(estado) } : { ok: false, text: () => Promise.resolve("") }),
    navigator: {},
  };
  ctx.window.window = window; vm.createContext(ctx); vm.runInContext(SRC, ctx);
  return { els, almacen, cargar: () => Promise.all(cargas.map(f => f())), ctx };
}
const esperar = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  // 1. red normal con datos de ejemplo: el conteo sale de los datos, no de un número fijo
  const menos = JSON.parse(MUESTRA); menos.l6.estaciones.pop();     // red con una estación menos que la muestra
  let e = entorno({ estado: JSON.stringify(menos) });
  e.cargar(); await esperar(250);
  const hero = e.els.hero.innerHTML;
  const total = Object.values(menos).reduce((n, l) => n + l.estaciones.length, 0);
  assert.ok(hero.includes("opera con normalidad"), "con red y sin problemas debe decir que opera con normalidad");
  assert.ok(hero.includes(`${total} estaciones habilitadas`), "el total de estaciones debe salir de los datos (" + total + ")");
  assert.ok(/Actualizado \d+:\d\d/.test(e.els.sub.textContent), "etiqueta de actualización: " + e.els.sub.textContent);

  // 2. con una estación con problemas el conteo cambia a aviso y se indica la línea
  const mal = JSON.parse(MUESTRA); mal.l1.estaciones[0].estado = "0"; mal.l1.estaciones[0].descripcion_app = "Cerrada";
  e = entorno({ estado: JSON.stringify(mal) });
  e.cargar(); await esperar(250);
  assert.ok(/Problemas en L1/.test(e.els.hero.innerHTML), e.els.hero.innerHTML.slice(0, 160));
  assert.ok(/1 estación afectada/.test(e.els.hero.innerHTML));

  // 3. sin red y sin caché: mensaje claro, sin excepciones
  e = entorno({ estado: "", conRed: false });
  e.cargar(); await esperar(250);
  assert.ok(/Sin conexión/.test(e.els.sub.textContent));
  assert.ok(/Sin datos todavía/.test(e.els.hero.innerHTML));

  // 4. sin red pero con caché de ayer: se muestra y se avisa que es de ayer (no sólo la hora)
  const ayer = Date.now() - 26 * 3600e3;
  e = entorno({ estado: "", conRed: false });
  e.almacen["metro.estado"] = JSON.stringify({ ts: ayer, e: JSON.parse(MUESTRA) });
  e.cargar(); await esperar(250);
  assert.ok(/Sin conexión · último dato (ayer|\d+\/\d+) /.test(e.els.sub.textContent), e.els.sub.textContent);

  // 5. accesibilidad estática del HTML
  const html = fs.readFileSync(path.join(RAIZ, "ui/index.html"), "utf8");
  assert.ok(!/user-scalable\s*=\s*no|maximum-scale/i.test(html), "no debe bloquear el zoom");
  assert.ok(/id="q"[^>]*aria-label=/.test(html), "buscador con nombre accesible");
  console.log("metro ui: 5 grupos de pruebas OK");
})().catch(err => { console.error(err); process.exit(1); });
