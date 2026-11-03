package dev.mmatokovic.fiskalizacija.spring;

import dev.mmatokovic.fiskalizacija.Okruzenje;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

/**
 * Postavke s prefiksom {@code fiskalizacija}.
 *
 * @param okruzenje      TEST ili PRODUKCIJA; bez njega se ništa ne konfigurira
 * @param certifikat     PKCS#12 aplikacijskog certifikata: putanja na disku, npr. /etc/fina/app.p12, ili
 *                       classpath:app.p12; nije potreban uz vlastiti Potpisivac bean
 * @param lozinka        lozinka PKCS#12 datoteke i ključa
 * @param xsdValidacija  provjera poruke XSD shemom prije slanja
 * @param b2b            Fiskalizacija 2.0
 * @param b2c            Fiskalizacija 1.0
 */
@ConfigurationProperties("fiskalizacija")
public record FiskalizacijaProperties(
        Okruzenje okruzenje,
        String certifikat,
        String lozinka,
        @DefaultValue("true") Boolean xsdValidacija,
        @DefaultValue Klijent b2b,
        @DefaultValue Klijent b2c) {

    /**
     * @param adresa  umjesto adrese okruženja, npr. za gateway ispred Porezne uprave
     * @param timeout najdulje čekanje na spajanje i na odgovor, npr. 30s; broj bez jedinice su sekunde; zadano
     *                30 sekundi
     */
    public record Klijent(String adresa, @DurationUnit(ChronoUnit.SECONDS) Duration timeout) {
    }

    @Override
    public String toString() {
        return "FiskalizacijaProperties[okruzenje=" + okruzenje + ", certifikat=" + certifikat
                + ", xsdValidacija=" + xsdValidacija + ", b2b=" + b2b + ", b2c=" + b2c + "]";
    }
}
