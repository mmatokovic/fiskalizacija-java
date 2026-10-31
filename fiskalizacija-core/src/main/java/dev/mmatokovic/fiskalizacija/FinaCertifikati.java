package dev.mmatokovic.fiskalizacija;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FINA CA certifikati ugrađeni u library. Produkcija vjeruje samo produkcijskom lancu (Fina Root CA),
 * a test okruženje i demo lancu, jer su testni certifikati izdani iz Fina Demo CA.
 */
public final class FinaCertifikati {

    private static final List<X509Certificate> PRODUKCIJA_KORIJENI = ucitaj("fina-root-ca.pem");
    private static final List<X509Certificate> PRODUKCIJA_POSREDNICI = ucitaj("fina-rdc-2020.pem", "fina-rdc-2025.pem");
    private static final List<X509Certificate> DEMO_KORIJENI = ucitaj("fina-demo-root-ca.pem");
    private static final List<X509Certificate> DEMO_POSREDNICI = ucitaj("fina-demo-ca-2020.pem");

    private FinaCertifikati() {
    }

    /** Korijenski FINA CA certifikati kojima se vjeruje u zadanom okruženju. */
    public static Set<TrustAnchor> sidra(Okruzenje okruzenje) {
        var korijeni = new ArrayList<>(PRODUKCIJA_KORIJENI);
        if (okruzenje == Okruzenje.TEST) {
            korijeni.addAll(DEMO_KORIJENI);
        }
        return korijeni.stream().map(c -> new TrustAnchor(c, null)).collect(Collectors.toUnmodifiableSet());
    }

    /** Posrednički FINA CA certifikati za izgradnju lanca u zadanom okruženju. */
    public static List<X509Certificate> posrednici(Okruzenje okruzenje) {
        var posrednici = new ArrayList<>(PRODUKCIJA_POSREDNICI);
        if (okruzenje == Okruzenje.TEST) {
            posrednici.addAll(DEMO_POSREDNICI);
        }
        return List.copyOf(posrednici);
    }

    /** TLS kontekst koji vjeruje FINA lancu; Java default truststore FINA certifikate u pravilu nema. */
    public static SSLContext sslContext(Okruzenje okruzenje) {
        try {
            var ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            int i = 0;
            for (var sidro : sidra(okruzenje)) {
                ks.setCertificateEntry("fina-" + i++, sidro.getTrustedCert());
            }
            for (var c : posrednici(okruzenje)) {
                ks.setCertificateEntry("fina-" + i++, c);
            }
            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            var ssl = SSLContext.getInstance("TLS");
            ssl.init(null, tmf.getTrustManagers(), null);
            return ssl;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<X509Certificate> ucitaj(String... datoteke) {
        try {
            var cf = CertificateFactory.getInstance("X.509");
            var lista = new ArrayList<X509Certificate>();
            for (String d : datoteke) {
                try (var in = FinaCertifikati.class.getResourceAsStream("ca/" + d)) {
                    lista.add((X509Certificate) cf.generateCertificate(in));
                }
            }
            return List.copyOf(lista);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Ne mogu učitati ugrađene FINA certifikate", e);
        }
    }
}
