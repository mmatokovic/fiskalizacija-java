package dev.mmatokovic.fiskalizacija.b2c;

import dev.mmatokovic.fiskalizacija.Oib;
import dev.mmatokovic.fiskalizacija.Okruzenje;
import dev.mmatokovic.fiskalizacija.PotpisOdgovora;
import dev.mmatokovic.fiskalizacija.Potpisivac;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pravi pozivi na testno okruženje fiskalizacije u krajnjoj potrošnji. Pokreću se samo uz varijable okoline
 * {@code FISK_P12}, {@code FISK_P12_LOZINKA} i {@code FISK_OIB}, kao {@code B2bTestnoOkruzenjeIT}. Računi i radno
 * vrijeme trebaju još {@code FISK_OZN_POS_PR}, poslovni prostor otvoren na OIB demo certifikata.
 *
 * <pre>{@code ./mvnw verify -Dit.test=B2cTestnoOkruzenjeIT}</pre>
 */
@EnabledIfEnvironmentVariable(named = "FISK_P12", matches = ".+")
class B2cTestnoOkruzenjeIT {

    private static B2cClient client;
    private static String oib;
    private static String oznPosPr;

    @BeforeAll
    static void klijent() {
        oib = System.getenv("FISK_OIB");
        assertThat(Oib.ispravan(oib)).as("FISK_OIB mora biti ispravan OIB").isTrue();
        oznPosPr = System.getenv("FISK_OZN_POS_PR");
        var potpisivac = Potpisivac.izP12(Path.of(System.getenv("FISK_P12")), System.getenv("FISK_P12_LOZINKA").toCharArray());
        client = B2cClient.builder(Okruzenje.TEST, potpisivac).build();
    }

    @Test
    void echo() {
        assertThat(client.echo("fiskalizacija-java")).isEqualTo("fiskalizacija-java");
    }

    @Test
    void racunPaPromjenaPodatakaPaNapojnica() {
        assumeTrue(oznPosPr != null, "FISK_OZN_POS_PR nije postavljen");
        var racun = racun();

        var r = client.racun(racun);
        prihvacen(r);
        assertThat(r.jir()).isNotNull();
        prihvacen(client.promijeniPodatkeRacuna(racun, NacinPlacanja.K, null));
        prihvacen(client.napojnica(racun, new Napojnica(new BigDecimal("1.00"), NacinPlacanja.G)));
    }

    @Test
    void provjeraRacuna() {
        assumeTrue(oznPosPr != null, "FISK_OZN_POS_PR nije postavljen");

        assertThat(client.provjera(racun()).potpisOdgovora()).isEqualTo(PotpisOdgovora.ISPRAVAN);
    }

    @Test
    void dohvatRadnogVremena() {
        assumeTrue(oznPosPr != null, "FISK_OZN_POS_PR nije postavljen");

        var r = client.dohvatiRadnoVrijeme(oib, oznPosPr, VrstaRadnogVremena.SVE, oib);

        assertThat(r.potpisOdgovora()).as(r::soapOdgovor).isEqualTo(PotpisOdgovora.ISPRAVAN);
    }

    private static Racun racun() {
        String broj = String.valueOf(System.currentTimeMillis() % 1_000_000_000L);
        return Racun.builder(oib, LocalDateTime.now(ZoneId.of("Europe/Zagreb")).truncatedTo(ChronoUnit.SECONDS),
                        new BrojRacuna(broj, oznPosPr, "1"), new BigDecimal("12.50"), NacinPlacanja.G, oib)
                .pdv(List.of(new Porez(new BigDecimal("25.00"), new BigDecimal("10.00"), new BigDecimal("2.50"))))
                .build();
    }

    private static void prihvacen(Rezultat r) {
        assertThat(r.prihvacen()).as(() -> r + "\n" + r.soapOdgovor()).isTrue();
    }
}
