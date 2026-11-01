package cl.kotone.metro;

import java.io.InputStream;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Vector;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.*;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.RSADigestSigner;

/**
 * Validación de la cadena de certificados del servidor contra raíces incluidas en la app (res/raices/*.der:
 * Amazon Root CA 1, DigiCert Global Root G2, GTS Root R1, ISRG Root X1, USERTrust RSA) — el almacén de Symbian
 * está vencido. Comprueba nombre (SAN/CN, comodín de un nivel), vigencia y firmas RSA eslabón por eslabón.
 */
final class Cadena {
    private static Vector raices;

    static boolean valida(Certificate[] cs, String host) {
        try {
            if (cs == null || cs.length == 0 || !nombre(cs[0], host)) return false;
            String hoy = ahoraUTC();
            for (int i = 0; i < cs.length; i++) if (!vigente(cs[i], hoy)) return false;
            // se recorre desde la hoja: en cuanto un eslabón ES una raíz nuestra o está firmado por una, la cadena vale
            // (así sirven también las cadenas con certificados cruzados de más al final)
            Vector rs = raices();
            for (int i = 0; i < cs.length; i++) {
                for (int j = 0; j < rs.size(); j++) {
                    Certificate r = (Certificate) rs.elementAt(j);
                    if (i > 0 && r.getSubject().equals(cs[i].getSubject()) && r.getSubjectPublicKeyInfo().equals(cs[i].getSubjectPublicKeyInfo())) return true;
                    if (r.getSubject().equals(cs[i].getIssuer()) && firma(cs[i], r)) return true;
                }
                if (i + 1 >= cs.length || !cs[i].getIssuer().equals(cs[i + 1].getSubject()) || !firma(cs[i], cs[i + 1])) return false;
            }
        } catch (Throwable t) { }
        return false;
    }

    private static Vector raices() {
        if (raices != null) return raices;
        Vector v = new Vector();
        for (int i = 0; i < 16; i++) {
            InputStream in = Cadena.class.getResourceAsStream("/raices/" + i + ".der");
            if (in == null) break;
            try { v.addElement(Certificate.getInstance(ASN1Primitive.fromByteArray(Metro.leer(in)))); } catch (Exception e) { }
        }
        return raices = v;
    }

    private static boolean nombre(Certificate c, String host) {
        host = host.toLowerCase();
        Extensions ex = c.getTBSCertificate().getExtensions();
        Extension san = ex == null ? null : ex.getExtension(Extension.subjectAlternativeName);
        if (san != null) {
            GeneralName[] ns = GeneralNames.getInstance(san.getParsedValue()).getNames();
            for (int i = 0; i < ns.length; i++)
                if (ns[i].getTagNo() == GeneralName.dNSName && coincide(DERIA5String.getInstance(ns[i].getName()).getString().toLowerCase(), host)) return true;
            return false;
        }
        String s = c.getSubject().toString().toLowerCase();
        int p = s.indexOf("cn=");
        if (p < 0) return false;
        int f = s.indexOf(',', p); return coincide(s.substring(p + 3, f < 0 ? s.length() : f), host);
    }
    private static boolean coincide(String patron, String host) {
        if (patron.equals(host)) return true;
        if (!patron.startsWith("*.")) return false;
        int p = host.indexOf('.');
        return p > 0 && host.substring(p + 1).equals(patron.substring(2));
    }

    // vigencia comparando cadenas «AAAAMMDDhhmmss» en UTC (sin java.text en CLDC)
    private static boolean vigente(Certificate c, String hoy) {
        return comparable(c.getStartDate()).compareTo(hoy) <= 0 && comparable(c.getEndDate()).compareTo(hoy) >= 0;
    }
    private static String comparable(Time t) {
        String s = t.getTime();                     // «YYYYMMDDhhmmssGMT+00:00» o similar
        return s.length() >= 14 ? s.substring(0, 14) : s;
    }
    private static String ahoraUTC() {
        Calendar k = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        return d(k.get(Calendar.YEAR), 4) + d(k.get(Calendar.MONTH) + 1, 2) + d(k.get(Calendar.DAY_OF_MONTH), 2)
                + d(k.get(Calendar.HOUR_OF_DAY), 2) + d(k.get(Calendar.MINUTE), 2) + d(k.get(Calendar.SECOND), 2);
    }
    private static String d(int n, int w) { String s = String.valueOf(n); while (s.length() < w) s = "0" + s; return s; }

    /** ¿cert está firmado por la clave pública de emisor? (sólo RSA PKCS#1 v1.5: SHA-1/256/384/512) */
    private static boolean firma(Certificate cert, Certificate emisor) throws Exception {
        String alg = cert.getSignatureAlgorithm().getAlgorithm().getId();
        Digest dg;
        if (alg.equals("1.2.840.113549.1.1.11")) dg = new SHA256Digest();
        else if (alg.equals("1.2.840.113549.1.1.12")) dg = new SHA384Digest();
        else if (alg.equals("1.2.840.113549.1.1.13")) dg = new SHA512Digest();
        else if (alg.equals("1.2.840.113549.1.1.5")) dg = new SHA1Digest();
        else return false;
        RSAPublicKey k = RSAPublicKey.getInstance(emisor.getSubjectPublicKeyInfo().parsePublicKey());
        RSADigestSigner s = new RSADigestSigner(dg);
        s.init(false, new RSAKeyParameters(false, k.getModulus(), k.getPublicExponent()));
        byte[] tbs = cert.getTBSCertificate().getEncoded(ASN1Encoding.DER);
        s.update(tbs, 0, tbs.length);
        return s.verifySignature(cert.getSignature().getBytes());
    }
}
