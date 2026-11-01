package cl.kotone.metro;

import java.io.*;
import java.util.Vector;
import org.bouncycastle.crypto.tls.*;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.asn1.*;

/** Cliente HTTPS (TLS 1.2, Bouncy Castle ligero) para Java ME: Symbian sólo trae TLS 1.0. */
public class Tls12 {
    public static String get(InputStream in, OutputStream out, final String host, String path) throws IOException {
        TlsClientProtocol p = new TlsClientProtocol(in, out, new java.security.SecureRandom());
        p.connect(new DefaultTlsClient() {
            public java.util.Hashtable getClientExtensions() throws IOException {
                java.util.Hashtable h = TlsExtensionsUtils.ensureExtensionsInitialised(super.getClientExtensions());
                TlsExtensionsUtils.addServerNameExtension(h, new ServerNameList(single(new ServerName(NameType.host_name, host))));
                return h;
            }
            public TlsAuthentication getAuthentication() { return new ServerOnlyTlsAuthentication() {
                public void notifyServerCertificate(org.bouncycastle.crypto.tls.Certificate c) throws IOException {
                    if (!Cadena.valida(c.getCertificateList(), host)) throw new TlsFatalAlert(AlertDescription.bad_certificate);
                }
            }; }
        });
        OutputStream o = p.getOutputStream();
        o.write(("GET " + path + " HTTP/1.0\r\nHost: " + host + "\r\nUser-Agent: Metro-SCL-Symbian/1.0 (Javiera Navarrete)\r\nConnection: close\r\n\r\n").getBytes());
        o.flush();
        InputStream i = p.getInputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
        try { while ((n = i.read(buf)) > 0) b.write(buf, 0, n); } catch (IOException e) { }
        p.close();
        return new String(b.toByteArray(), "UTF-8");
    }
    static Vector single(Object o) { Vector v = new Vector(); v.addElement(o); return v; }
}
