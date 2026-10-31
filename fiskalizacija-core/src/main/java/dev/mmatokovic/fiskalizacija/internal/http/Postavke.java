package dev.mmatokovic.fiskalizacija.internal.http;

import dev.mmatokovic.fiskalizacija.FinaCertifikati;
import dev.mmatokovic.fiskalizacija.Okruzenje;
import dev.mmatokovic.fiskalizacija.PotpisOdgovora;
import dev.mmatokovic.fiskalizacija.internal.potpis.ProvjeraPotpisa;
import org.w3c.dom.Element;

import javax.net.ssl.SSLContext;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Zajedničke postavke klijenata za Fiskalizaciju 2.0 i 1.0; svaka promjena vraća novu kopiju. */
public record Postavke(
        URI adresa,
        Set<TrustAnchor> sidra,
        List<X509Certificate> posrednici,
        SSLContext ssl,
        Duration timeout,
        boolean xsdValidacija,
        Clock sat,
        Predicate<X509Certificate> potpisnikOdgovora,
        ProxySelector proxy) {

    public static Postavke zadane(Okruzenje okruzenje, URI adresa, Predicate<X509Certificate> potpisnikOdgovora) {
        return new Postavke(adresa, FinaCertifikati.sidra(okruzenje), FinaCertifikati.posrednici(okruzenje),
                FinaCertifikati.sslContext(okruzenje), Duration.ofSeconds(30), true, Clock.system(ZoneId.of("Europe/Zagreb")),
                potpisnikOdgovora, null);
    }

    public Postavke adresa(URI adresa) {
        return new Postavke(Objects.requireNonNull(adresa, "adresa"), sidra, posrednici, ssl, timeout, xsdValidacija, sat,
                potpisnikOdgovora, proxy);
    }

    public Postavke povjerljiviCertifikati(Set<TrustAnchor> sidra, List<X509Certificate> posrednici) {
        return new Postavke(adresa, Set.copyOf(sidra), List.copyOf(posrednici), ssl, timeout, xsdValidacija, sat,
                potpisnikOdgovora, proxy);
    }

    public Postavke ssl(SSLContext ssl) {
        return new Postavke(adresa, sidra, posrednici, Objects.requireNonNull(ssl, "ssl"), timeout, xsdValidacija, sat,
                potpisnikOdgovora, proxy);
    }

    public Postavke timeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout mora biti pozitivan: " + timeout);
        }
        return new Postavke(adresa, sidra, posrednici, ssl, timeout, xsdValidacija, sat, potpisnikOdgovora, proxy);
    }

    public Postavke xsdValidacija(boolean ukljuceno) {
        return new Postavke(adresa, sidra, posrednici, ssl, timeout, ukljuceno, sat, potpisnikOdgovora, proxy);
    }

    public Postavke sat(Clock sat) {
        return new Postavke(adresa, sidra, posrednici, ssl, timeout, xsdValidacija, Objects.requireNonNull(sat, "sat"),
                potpisnikOdgovora, proxy);
    }

    public Postavke potpisnikOdgovora(Predicate<X509Certificate> provjera) {
        return new Postavke(adresa, sidra, posrednici, ssl, timeout, xsdValidacija, sat,
                Objects.requireNonNull(provjera, "provjera"), proxy);
    }

    public Postavke proxy(ProxySelector proxy) {
        return new Postavke(adresa, sidra, posrednici, ssl, timeout, xsdValidacija, sat, potpisnikOdgovora,
                Objects.requireNonNull(proxy, "proxy"));
    }

    public HttpClient httpClient() {
        var http = HttpClient.newBuilder().sslContext(ssl).connectTimeout(timeout);
        if (proxy != null) {
            http.proxy(proxy);
        }
        return http.build();
    }

    /** @param trenutak na koji se provjerava lanac certifikata, ili {@code null} za sada */
    public PotpisOdgovora provjeriPotpis(Element odgovor, Instant trenutak) {
        return ProvjeraPotpisa.provjeri(odgovor, sidra, posrednici, trenutak, potpisnikOdgovora);
    }
}
