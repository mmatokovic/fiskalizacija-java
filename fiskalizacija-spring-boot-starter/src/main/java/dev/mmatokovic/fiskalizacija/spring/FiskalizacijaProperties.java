package dev.mmatokovic.fiskalizacija.spring;

import dev.mmatokovic.fiskalizacija.Okruzenje;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Postavke s prefiksom {@code fiskalizacija}.
 *
 * @param okruzenje      TEST ili PRODUKCIJA; bez njega se ništa ne konfigurira
 * @param certifikat     PKCS#12 aplikacijskog certifikata, npr. file:/etc/fina/app.p12; nije potreban uz
 *                       vlastiti Potpisivac bean
 * @param lozinka        lozinka PKCS#12 datoteke i ključa
 * @param xsdValidacija  provjera poruke XSD shemom prije slanja
 * @param b2b            Fiskalizacija 2.0
 * @param b2c            Fiskalizacija 1.0
 */
@ConfigurationProperties("fiskalizacija")
public record FiskalizacijaProperties(
        Okruzenje okruzenje,
        Resource certifikat,
        String lozinka,
        @DefaultValue("true") boolean xsdValidacija,
        @DefaultValue Klijent b2b,
        @DefaultValue Klijent b2c) {

    /**
     * @param adresa  umjesto adrese okruženja, npr. za gateway ispred Porezne uprave
     * @param timeout najdulje čekanje na spajanje i na odgovor; zadano 30 sekundi
     */
    public record Klijent(URI adresa, Duration timeout) {
    }

    @Override
    public String toString() {
        return "FiskalizacijaProperties[okruzenje=" + okruzenje + ", certifikat=" + certifikat
                + ", xsdValidacija=" + xsdValidacija + ", b2b=" + b2b + ", b2c=" + b2c + "]";
    }
}
