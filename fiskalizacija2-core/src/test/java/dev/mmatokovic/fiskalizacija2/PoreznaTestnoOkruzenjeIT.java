package dev.mmatokovic.fiskalizacija2;

import dev.mmatokovic.fiskalizacija2.model.NacinPlacanja;
import dev.mmatokovic.fiskalizacija2.model.Naplata;
import dev.mmatokovic.fiskalizacija2.model.Odbijanje;
import dev.mmatokovic.fiskalizacija2.model.Racun;
import dev.mmatokovic.fiskalizacija2.model.VrstaERacuna;
import dev.mmatokovic.fiskalizacija2.model.VrstaRazlogaOdbijanja;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pravi pozivi na testno okruženje Porezne uprave. Pokreću se samo uz varijable okoline:
 * {@code FISK_P12} (putanja do demo aplikacijskog certifikata), {@code FISK_P12_LOZINKA} i {@code FISK_OIB}
 * (OIB na koji je certifikat izdan). Bez njih se preskaču.
 *
 * <pre>{@code ./mvnw verify -Dit.test=PoreznaTestnoOkruzenjeIT}</pre>
 */
@EnabledIfEnvironmentVariable(named = "FISK_P12", matches = ".+")
class PoreznaTestnoOkruzenjeIT {

    private static final String DRUGA_STRANA = "11111111119";

    private static FiskalizacijaClient client;
    private static String oib;

    @BeforeAll
    static void klijent() {
        oib = System.getenv("FISK_OIB");
        assertTrue(Oib.ispravan(oib), "FISK_OIB mora biti ispravan OIB");
        var potpisivac = Potpisivac.izP12(Path.of(System.getenv("FISK_P12")), System.getenv("FISK_P12_LOZINKA").toCharArray());
        client = FiskalizacijaClient.builder(Okruzenje.TEST, potpisivac).build();
    }

    @Test
    void evidentirajIzlazniERacunPaNaplatu() throws Exception {
        var racun = racun(oib, DRUGA_STRANA);

        prihvacen(client.evidentirajERacun(VrstaERacuna.IZLAZNI, racun));
        Thread.sleep(1000);
        prihvacen(client.evidentirajNaplatu(new Naplata(racun.brojDokumenta(), racun.datumIzdavanja(), oib, DRUGA_STRANA,
                danas(), racun.ukupanIznos().iznosKojiDospijevaZaPlacanje(), NacinPlacanja.T)));
    }

    @Test
    void evidentirajUlazniERacunPaOdbijanje() throws Exception {
        var racun = racun(DRUGA_STRANA, oib);

        prihvacen(client.evidentirajERacun(VrstaERacuna.ULAZNI, racun));
        Thread.sleep(1000);
        prihvacen(client.evidentirajOdbijanje(new Odbijanje(racun.brojDokumenta(), racun.datumIzdavanja(), DRUGA_STRANA, oib,
                danas(), VrstaRazlogaOdbijanja.O, "Test odbijanja iz fiskalizacija2-java")));
    }

    @Test
    void evidentirajIsporukuZaKojuNijeIzdanERacun() {
        prihvacen(client.evidentirajIsporukuZaKojuNijeIzdanERacun(racun(oib, DRUGA_STRANA)));
    }

    /** Službeni primjer s jedinstvenim brojem, današnjim datumom i zadanim OIB-ima prodavatelja (i operatera) i kupca. */
    private static Racun racun(String prodavatelj, String kupac) {
        String broj = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L) + "-P1-1";
        String xml = new String(Primjeri.eRacun("eRacun_PDV25.xml"), StandardCharsets.UTF_8)
                .replaceFirst("<cbc:ID>1-P1-1</cbc:ID>", "<cbc:ID>" + broj + "</cbc:ID>")
                .replaceFirst("<cbc:IssueDate>[^<]+</cbc:IssueDate>", "<cbc:IssueDate>" + danas() + "</cbc:IssueDate>")
                .replace(Primjeri.OIB, "@PRODAVATELJ@")
                .replace(DRUGA_STRANA, kupac)
                .replace("@PRODAVATELJ@", prodavatelj);
        return UblParser.racun(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDate danas() {
        return LocalDate.now(ZoneId.of("Europe/Zagreb"));
    }

    private static void prihvacen(Rezultat r) {
        assertTrue(r.prihvacen(), () -> r.greska() + "\n" + r.soapOdgovor());
        assertEquals(PotpisOdgovora.ISPRAVAN, r.potpisOdgovora(), r::soapOdgovor);
    }
}
