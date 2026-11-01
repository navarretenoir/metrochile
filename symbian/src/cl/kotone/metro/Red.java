package cl.kotone.metro;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.*;

/** Estado de la red: líneas con su color y estado; al tocar una línea se despliegan sus estaciones. */
public class Red extends Canvas implements Runnable {
    static final int BASE = 0, GUARDADO = 1, VIVO = 2;
    static final String[] LINEAS = {"l1", "l2", "l3", "l4", "l4a", "l5", "l6", "l7"};
    static final String[] NOMBRES = {"Línea 1", "Línea 2", "Línea 3", "Línea 4", "Línea 4A", "Línea 5", "Línea 6", "Línea 7"};
    static final int[] COLORES = {0xE1251B, 0xF9A800, 0x8B5A2B, 0x0055A4, 0x4AB3E3, 0x00A651, 0x9B4F96, 0x7B8794};

    final Metro app;
    Hashtable d;
    int origen = BASE;
    boolean cargando;
    String error;
    volatile boolean viva = true;
    int abierta = -1, sel = 0, scroll, maxScroll, pressY, baseScroll;
    boolean movio;
    int[] yLinea = new int[LINEAS.length];
    Font f, fb, ft;
    String hora = "";

    Red(Metro app) {
        this.app = app;
        setFullScreenMode(true);
        f = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
        fb = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        ft = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);
        new Thread(this).start();
    }

    void datos(Hashtable h, int o) {
        d = h; origen = o; if (o == VIVO) { error = null; hora = ahora(); }
        repaint();
    }
    void cargando(boolean c) { cargando = c; repaint(); }
    void error(String e) { error = e; repaint(); }
    public void run() { while (viva) { if (cargando && isShown()) repaint(); try { Thread.sleep(120); } catch (InterruptedException e) { } } }

    static String ahora() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int h = c.get(java.util.Calendar.HOUR_OF_DAY), m = c.get(java.util.Calendar.MINUTE);
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setColor(0xF2F2F7); g.fillRect(0, 0, w, h);
        int y = 10 - scroll;
        g.setColor(0x1C1C1E); g.setFont(ft);
        g.drawString("Metro Chile", 12, y, Graphics.TOP | Graphics.LEFT); y += ft.getHeight();
        g.setFont(f); g.setColor(0x6E6E73);
        String sub = cargando ? "Consultando metro.cl" + "...".substring(0, (int) (System.currentTimeMillis() / 300 % 4))
                : error != null ? error + (origen == GUARDADO ? " · último estado guardado" : origen == BASE ? " · red sin estado" : "")
                : origen == VIVO ? "En vivo · " + hora : origen == GUARDADO ? "Último estado guardado" : "Red sin estado en vivo";
        g.drawString(sub, 12, y, Graphics.TOP | Graphics.LEFT); y += f.getHeight() + 8;

        int alto = fb.getHeight() + f.getHeight() + 16;
        for (int i = 0; i < LINEAS.length; i++) {
            Hashtable l = d == null ? null : Json.obj(d, LINEAS[i]);
            if (l == null) { yLinea[i] = -9999; continue; }
            yLinea[i] = y + scroll;
            boolean ok = "1".equals(Json.str(l, "estado")) || origen == BASE;
            g.setColor(i == sel ? 0xE5E5EA : 0xFFFFFF);
            g.fillRoundRect(8, y, w - 16, alto, 18, 18);
            g.setColor(COLORES[i]); g.fillRoundRect(16, y + 10, 46, alto - 20, 12, 12);
            g.setColor(0xFFFFFF); g.setFont(fb);
            String cod = NOMBRES[i].substring(6);
            g.drawString(cod, 39, y + alto / 2 + fb.getBaselinePosition() / 2 - 1, Graphics.BASELINE | Graphics.HCENTER);
            g.setColor(0x1C1C1E);
            g.drawString(NOMBRES[i], 74, y + 8, Graphics.TOP | Graphics.LEFT);
            g.setFont(f);
            String msg = origen == BASE ? Json.arr(l, "estaciones").size() + " estaciones" : Json.str(l, "mensaje_app");
            if (msg.length() == 0) msg = ok ? "Línea disponible" : "Con problemas";
            g.setColor(origen == BASE ? 0x6E6E73 : ok ? 0x248A3D : 0xD70015);
            g.drawString(cortar(msg, w - 74 - 40), 74, y + 10 + fb.getHeight(), Graphics.TOP | Graphics.LEFT);
            if (origen != BASE) { g.setColor(ok ? 0x34C759 : 0xFF3B30); g.fillArc(w - 34, y + alto / 2 - 7, 14, 14, 0, 360); }
            y += alto + 6;
            if (abierta == i) y = estaciones(g, l, i, y, w);
        }
        y += 10;
        g.setColor(0x8E8E93); g.setFont(f);
        g.drawString("Toca una línea para ver sus estaciones", w / 2, y, Graphics.TOP | Graphics.HCENTER); y += f.getHeight();
        g.drawString("Desarrollado por Javiera Navarrete", w / 2, y, Graphics.TOP | Graphics.HCENTER); y += f.getHeight() + 30;
        maxScroll = Math.max(0, y + scroll - h);
        // barra inferior
        g.setColor(0xFFFFFF); g.fillRect(0, h - f.getHeight() - 10, w, f.getHeight() + 10);
        g.setColor(0x007AFF); g.setFont(fb);
        g.drawString("Actualizar", 10, h - 5, Graphics.BOTTOM | Graphics.LEFT);
        g.drawString("Salir", w - 10, h - 5, Graphics.BOTTOM | Graphics.RIGHT);
    }

    int estaciones(Graphics g, Hashtable l, int i, int y, int w) {
        Vector es = Json.arr(l, "estaciones");
        if (es == null) return y;
        int fila = f.getHeight() + 12, x = 40;
        for (int k = 0; k < es.size(); k++) {
            Hashtable e = (Hashtable) es.elementAt(k);
            // trazo de la línea con un punto por estación (como el plano)
            g.setColor(COLORES[i]);
            if (k < es.size() - 1) g.fillRect(x - 2, y + fila / 2, 5, fila);
            boolean ok = origen == BASE || "1".equals(Json.str(e, "estado"));
            g.setColor(ok ? 0xFFFFFF : 0xFF3B30); g.fillArc(x - 7, y + fila / 2 - 7, 14, 14, 0, 360);
            g.setColor(COLORES[i]); g.drawArc(x - 7, y + fila / 2 - 7, 14, 14, 0, 360); g.drawArc(x - 6, y + fila / 2 - 6, 12, 12, 0, 360);
            g.setColor(0x1C1C1E); g.setFont(f);
            String n = Json.str(e, "nombre");
            g.drawString(n, x + 18, y + fila / 2 + f.getBaselinePosition() / 2, Graphics.BASELINE | Graphics.LEFT);
            int xx = x + 24 + f.stringWidth(n);
            String comb = Json.str(e, "combinacion");
            for (int j = 0; j < LINEAS.length && comb.length() > 0; j++) {
                if (!tiene(comb, LINEAS[j])) continue;
                g.setColor(COLORES[j]); g.fillRoundRect(xx, y + 6, 30, fila - 12, 8, 8);
                g.setColor(0xFFFFFF); g.setFont(fb);
                g.drawString(NOMBRES[j].substring(6), xx + 15, y + fila / 2 + fb.getBaselinePosition() / 2 - 1, Graphics.BASELINE | Graphics.HCENTER);
                xx += 34;
            }
            if (!ok) { g.setColor(0xD70015); g.setFont(f); g.drawString(Json.str(e, "descripcion_app"), w - 12, y + fila / 2 + f.getBaselinePosition() / 2, Graphics.BASELINE | Graphics.RIGHT); }
            y += fila;
        }
        return y + 8;
    }

    /** ¿«L4, L4A» incluye exactamente la línea «l4»? (comparación por palabra, no por subcadena) */
    static boolean tiene(String comb, String linea) {
        String c = comb.toLowerCase();
        int p = 0;
        while ((p = c.indexOf(linea, p)) >= 0) {
            int fin = p + linea.length();
            boolean izq = p == 0 || !letraODigito(c.charAt(p - 1)), der = fin == c.length() || !letraODigito(c.charAt(fin));
            if (izq && der) return true;
            p = fin;
        }
        return false;
    }
    static boolean letraODigito(char ch) { return ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9'; }

    String cortar(String s, int ancho) {
        if (f.stringWidth(s) <= ancho) return s;
        while (s.length() > 1 && f.stringWidth(s + "…") > ancho) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    protected void pointerPressed(int x, int y) { pressY = y; baseScroll = scroll; movio = false; }
    protected void pointerDragged(int x, int y) {
        if (Math.abs(y - pressY) > 8) movio = true;
        scroll = Math.max(0, Math.min(maxScroll, baseScroll - (y - pressY)));
        repaint();
    }
    protected void pointerReleased(int x, int y) {
        if (movio) return;
        int h = getHeight();
        if (y > h - f.getHeight() - 10) {
            if (x < getWidth() / 3) app.actualizar(); else if (x > getWidth() * 2 / 3) app.commandAction(app.cSalir, this);
            return;
        }
        int alto = fb.getHeight() + f.getHeight() + 16;
        for (int i = 0; i < LINEAS.length; i++)
            if (yLinea[i] > -9999 && y + scroll >= yLinea[i] && y + scroll <= yLinea[i] + alto) { sel = i; abierta = abierta == i ? -1 : i; repaint(); return; }
    }
    protected void keyPressed(int k) {
        int a = getGameAction(k);
        if (a == UP) { if (sel > 0) sel--; asegurar(); }
        else if (a == DOWN) { if (sel < LINEAS.length - 1) sel++; asegurar(); }
        else if (a == FIRE || k == KEY_NUM5) abierta = abierta == sel ? -1 : sel;
        else if (k == -6 || k == KEY_STAR) app.actualizar();
        else if (k == -7) app.commandAction(app.cSalir, this);
        else if (k == KEY_NUM2) scroll = Math.max(0, scroll - 60);
        else if (k == KEY_NUM8) scroll = Math.min(maxScroll, scroll + 60);
        repaint();
    }
    void asegurar() {
        int y = yLinea[sel] - scroll, h = getHeight();
        if (y < 40) scroll = Math.max(0, yLinea[sel] - 40);
        else if (y > h - 120) scroll = Math.min(maxScroll, yLinea[sel] - h + 120);
    }
}
