package dev.mmatokovic.fiskalizacija.primjeri;

import dev.mmatokovic.fiskalizacija.FiskalizacijaClient;
import dev.mmatokovic.fiskalizacija.Okruzenje;
import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.Rezultat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Klijent za testno okruženje Porezne uprave iz varijabli okoline {@code FISK_P12}, {@code FISK_P12_LOZINKA} i
 * {@code FISK_OIB}, kao u {@code PoreznaTestnoOkruzenjeIT}.
 */
final class TestnoOkruzenje {

    private static final String OIB_PRIMJERA = "12345678903";

    private TestnoOkruzenje() {
    }

    static FiskalizacijaClient klijent() {
        var potpisivac = Potpisivac.izP12(Path.of(varijabla("FISK_P12")), varijabla("FISK_P12_LOZINKA").toCharArray());
        return FiskalizacijaClient.builder(Okruzenje.TEST, potpisivac).build();
    }

    static String oib() {
        return varijabla("FISK_OIB");
    }

    /** eRačun iz datoteke ili, bez nje, {@code primjer-eracun.xml} s OIB-om iz {@code FISK_OIB}, novim brojem i današnjim datumom. */
    static byte[] eRacun(String[] args) throws IOException {
        if (args.length > 0) {
            return Files.readAllBytes(Path.of(args[0]));
        }
        LocalDate danas = LocalDate.now(ZoneId.of("Europe/Zagreb"));
        String broj = (System.currentTimeMillis() % 1_000_000_000L) + "-P1-1";
        return primjer()
                .replace(OIB_PRIMJERA, oib())
                .replace("<cbc:ID>1-P1-1</cbc:ID>", "<cbc:ID>" + broj + "</cbc:ID>")
                .replaceFirst("<cbc:IssueDate>[^<]+", "<cbc:IssueDate>" + danas)
                .replaceFirst("<cbc:DueDate>[^<]+", "<cbc:DueDate>" + danas.plusDays(30))
                .getBytes(StandardCharsets.UTF_8);
    }

    static String primjer() {
        try (var in = TestnoOkruzenje.class.getResourceAsStream("/primjer-eracun.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void ispisi(String poruka, Rezultat r) {
        if (r.prihvacen()) {
            System.out.println(poruka + ": prihvaćeno, idZahtjeva " + r.idZahtjeva());
        } else if (r.prihvacenBezProvjerePotpisa()) {
            System.out.println(poruka + ": prihvaćeno, ali potpis odgovora je " + r.potpisOdgovora());
        } else {
            System.out.println(poruka + ": odbijeno, " + r.greska().sifra() + " " + r.greska().opis());
        }
    }

    private static String varijabla(String ime) {
        String vrijednost = System.getenv(ime);
        if (vrijednost == null || vrijednost.isBlank()) {
            throw new IllegalStateException("Postavite varijable okoline FISK_P12, FISK_P12_LOZINKA i FISK_OIB (nedostaje " + ime + ")");
        }
        return vrijednost;
    }
}
