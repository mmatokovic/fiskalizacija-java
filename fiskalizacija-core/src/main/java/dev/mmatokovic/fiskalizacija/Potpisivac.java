package dev.mmatokovic.fiskalizacija;

import dev.mmatokovic.fiskalizacija.internal.potpis.PristupKljucu;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Objects;

/**
 * Privatni ključ i certifikat kojima klijenti potpisuju zahtjeve: Fiskalizacija 2.0 XAdES-B potpisom, 1.0 XMLDSig potpisom
 * i ZKI. Ključ se ne izlaže kroz javni API.
 */
public final class Potpisivac {

    static {
        PristupKljucu.postavi(p -> p.kljuc);
    }

    private final PrivateKey kljuc;
    private final X509Certificate certifikat;

    /** Za ključ iz HSM-a ili vlastitog keystorea; RSA ili EC. */
    public Potpisivac(PrivateKey kljuc, X509Certificate certifikat) {
        this.kljuc = Objects.requireNonNull(kljuc, "kljuc");
        this.certifikat = Objects.requireNonNull(certifikat, "certifikat");
    }

    /**
     * Učitava prvi ključ iz PKCS#12 datoteke kakvu izdaje FINA.
     *
     * @param datoteka PKCS#12 datoteka
     * @param lozinka  lozinka datoteke i ključa
     * @return potpisivač
     * @throws IllegalArgumentException ako se datoteka ne može pročitati ili ne sadrži privatni ključ
     */
    public static Potpisivac izP12(Path datoteka, char[] lozinka) {
        try (var in = Files.newInputStream(datoteka)) {
            return izP12(in, lozinka);
        } catch (IOException e) {
            throw new IllegalArgumentException("Ne mogu pročitati " + datoteka, e);
        }
    }

    /**
     * Učitava prvi ključ iz PKCS#12 sadržaja; tok se ne zatvara.
     *
     * @param in      PKCS#12 sadržaj
     * @param lozinka lozinka sadržaja i ključa
     * @return potpisivač
     * @throws IllegalArgumentException ako sadržaj nije ispravan PKCS#12 ili ne sadrži privatni ključ
     */
    public static Potpisivac izP12(InputStream in, char[] lozinka) {
        try {
            var ks = KeyStore.getInstance("PKCS12");
            ks.load(in, lozinka);
            for (String alias : Collections.list(ks.aliases())) {
                if (ks.isKeyEntry(alias)) {
                    return new Potpisivac((PrivateKey) ks.getKey(alias, lozinka), (X509Certificate) ks.getCertificate(alias));
                }
            }
            throw new IllegalArgumentException("PKCS#12 ne sadrži privatni ključ");
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Ne mogu učitati PKCS#12: " + e.getMessage(), e);
        }
    }

    public X509Certificate certifikat() {
        return certifikat;
    }

}
