package cl.kotone.metro;

import java.io.*;
import java.util.Hashtable;
import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

/**
 * Metro Chile para Symbian (Java ME · MIDP 2.0 / CLDC 1.1): estado de las líneas y estaciones en vivo.
 * Pensado para los últimos Symbian (Nokia Belle / Belle FP2: 808 PureView, 701, 700, 603, E6, N8…).
 * metro.cl sólo acepta TLS 1.2 (Symbian trae TLS 1.0): la conexión va por TLS 1.2 propio (Bouncy Castle ligero,
 * licencia MIT) sobre un socket. Sin conexión muestra la red guardada.
 * Desarrollado por Javiera Navarrete.
 */
public class Metro extends MIDlet implements CommandListener {
    static final String HOST = "www.metro.cl", RUTA = "/api/estadoRedDetalle.php";
    Display disp;
    Red red;
    final Command cActualizar = new Command("Actualizar", Command.SCREEN, 1);
    final Command cAcerca = new Command("Acerca de", Command.HELP, 3);
    final Command cSalir = new Command("Salir", Command.EXIT, 9);

    protected void startApp() {
        if (disp != null) return;
        disp = Display.getDisplay(this);
        red = new Red(this);
        red.addCommand(cActualizar); red.addCommand(cAcerca); red.addCommand(cSalir);
        red.setCommandListener(this);
        disp.setCurrent(red);
        String g = leerRS();
        try { red.datos((Hashtable) Json.parse(g != null ? g : base()), g != null ? Red.GUARDADO : Red.BASE); } catch (Throwable t) { }
        actualizar();
    }
    protected void pauseApp() { }
    protected void destroyApp(boolean u) { if (red != null) red.viva = false; }

    public void commandAction(Command c, Displayable d) {
        if (c == cSalir) { destroyApp(true); notifyDestroyed(); }
        else if (c == cActualizar) actualizar();
        else if (c == cAcerca) acerca();
    }

    void acerca() {
        Alert a = new Alert("Metro Chile", "Metro Chile 1.0 para Symbian\n\nDesarrollado por Javiera Navarrete.\n\n"
                + "Estado de la red desde metro.cl (dato público). App no oficial: no está afiliada a Metro S.A.\n"
                + "TLS 1.2: Bouncy Castle (MIT).", null, AlertType.INFO);
        a.setTimeout(Alert.FOREVER);
        disp.setCurrent(a, red);
    }

    String base() throws IOException { return new String(leer(getClass().getResourceAsStream("/red.json")), "UTF-8"); }

    void actualizar() {
        red.cargando(true);
        new Thread() { public void run() {
            SocketConnection sc = null;
            try {
                sc = (SocketConnection) Connector.open("socket://" + HOST + ":443");
                String r = Tls12.get(sc.openInputStream(), sc.openOutputStream(), HOST, RUTA);
                int ini = r.indexOf("\r\n\r\n");
                if (!r.startsWith("HTTP/1.") || r.indexOf(" 200") > 12 || ini < 0) throw new IOException("respuesta inesperada");
                String cuerpo = r.substring(ini + 4);
                red.datos((Hashtable) Json.parse(cuerpo), Red.VIVO);
                guardarRS(cuerpo);
            } catch (Throwable t) {
                red.error("Sin conexión con metro.cl");
            } finally {
                try { if (sc != null) sc.close(); } catch (Exception e) { }
                red.cargando(false);
            }
        }}.start();
    }

    static byte[] leer(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[2048]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close(); return b.toByteArray();
    }
    static String leerRS() {
        try {
            RecordStore rs = RecordStore.openRecordStore("metro-estado", true);
            String r = rs.getNumRecords() > 0 ? new String(rs.getRecord(1), "UTF-8") : null;
            rs.closeRecordStore(); return r;
        } catch (Exception e) { return null; }
    }
    static void guardarRS(String v) {
        try {
            RecordStore rs = RecordStore.openRecordStore("metro-estado", true);
            byte[] b = v.getBytes("UTF-8");
            if (rs.getNumRecords() > 0) rs.setRecord(1, b, 0, b.length); else rs.addRecord(b, 0, b.length);
            rs.closeRecordStore();
        } catch (Exception e) { }
    }
}
