package dev.mmatokovic.fiskalizacija.internal;

import dev.mmatokovic.fiskalizacija.NeispravanZahtjevException;
import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.Primjeri;
import dev.mmatokovic.fiskalizacija.UblParser;
import dev.mmatokovic.fiskalizacija.model.NacinPlacanja;
import dev.mmatokovic.fiskalizacija.model.Naplata;
import dev.mmatokovic.fiskalizacija.model.Odbijanje;
import dev.mmatokovic.fiskalizacija.model.VrstaERacuna;
import dev.mmatokovic.fiskalizacija.model.VrstaRazlogaOdbijanja;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ZahtjeviTest {

    private final Potpisivac potpisivac = Primjeri.potpisivac();

    @Test
    void evidentirajERacunZaSvePrimjereProlaziSluzbenuShemu() {
        var neuspjeli = new ArrayList<String>();
        for (var p : Primjeri.eRacuni()) {
            var z = Zahtjevi.evidentirajERacun(VrstaERacuna.IZLAZNI, List.of(UblParser.racun(Primjeri.eRacun(p))), Primjeri.SAT);
            potpisivac.potpisi(z.element(), z.id());
            var greske = Xsd.greske(z.element());
            if (!greske.isEmpty()) {
                neuspjeli.add(p.getFileName() + ": " + greske);
            }
        }
        if (!neuspjeli.isEmpty()) {
            fail(String.join("\n", neuspjeli));
        }
    }

    @Test
    void isporukaBezERacunaKoristiImenaElemenataIzEIzvjestavanja() {
        var racun = UblParser.racun(Primjeri.eRacun("Finalni_racun_predujam.xml"));
        var z = Zahtjevi.evidentirajIsporukuZaKojuNijeIzdanERacun(List.of(racun), Primjeri.SAT);
        potpisivac.potpisi(z.element(), z.id());

        assertEquals(List.of(), Xsd.greske(z.element()));
        assertEquals("IR", tekst(z.element(), "vrstaRacuna"));
        assertEquals(1, z.element().getElementsByTagNameNS(Zahtjevi.NS_EIZV, "PrethodniRacun").getLength());
        assertEquals(1, z.element().getElementsByTagNameNS(Zahtjevi.NS_EIZV, "StavkaRacuna").getLength());
        assertEquals("EUR", tekst(z.element(), "valutaRacuna"));
    }

    @Test
    void naplataOdgovaraSluzbenomPrimjeruIProlaziShemu() {
        var z = Zahtjevi.evidentirajNaplatu(List.of(new Naplata("TEST", LocalDate.of(2026, 8, 8), "12345678901", "11111111119",
                LocalDate.of(2026, 8, 10), new BigDecimal("1000"), NacinPlacanja.T)), Primjeri.SAT);
        potpisivac.potpisi(z.element(), z.id());

        assertEquals(List.of(), Xsd.greske(z.element()));
        assertEquals("1000.00", tekst(z.element(), "naplaceniIznos"));
        assertEquals("T", tekst(z.element(), "nacinPlacanja"));
        assertEquals(List.of("brojDokumenta", "datumIzdavanja", "oibPorezniBrojIzdavatelja", "oibPorezniBrojPrimatelja",
                "datumNaplate", "naplaceniIznos", "nacinPlacanja"), djeca(z.element(), "Naplata"));
    }

    @Test
    void odbijanjeOdgovaraSluzbenomPrimjeruIProlaziShemu() {
        var z = Zahtjevi.evidentirajOdbijanje(List.of(new Odbijanje("TEST", LocalDate.of(2026, 8, 8), "12345678901",
                "11111111119", LocalDate.of(2026, 8, 10), VrstaRazlogaOdbijanja.N, "Neispravan iznos na računu")), Primjeri.SAT);
        potpisivac.potpisi(z.element(), z.id());

        assertEquals(List.of(), Xsd.greske(z.element()));
        assertEquals(List.of("brojDokumenta", "datumIzdavanja", "oibPorezniBrojIzdavatelja", "oibPorezniBrojPrimatelja",
                "datumOdbijanja", "vrstaRazlogaOdbijanja", "razlogOdbijanja"), djeca(z.element(), "Odbijanje"));
    }

    @Test
    void datumVrijemeSlanjaJeUZagrebuBezObziraNaZonuSata() {
        var sat = Clock.fixed(Instant.parse("2026-07-02T09:30:15.005Z"), ZoneOffset.UTC);

        var z = Zahtjevi.evidentirajNaplatu(List.of(naplata(BigDecimal.ONE)), sat);

        assertEquals("2026-07-02T11:30:15.0050", tekst(z.element(), "datumVrijemeSlanja"));
    }

    @Test
    void datumVrijemeSlanjaImaCetiriDecimaleULokalnomVremenu() {
        var sat = Clock.fixed(Instant.parse("2026-03-02T09:30:15.005Z"), ZoneId.of("Europe/Zagreb"));

        var z = Zahtjevi.evidentirajNaplatu(List.of(naplata(new BigDecimal("1.00"))), sat);

        assertEquals("2026-03-02T10:30:15.0050", tekst(z.element(), "datumVrijemeSlanja"));
    }

    @Test
    void idZahtjevaJeUuidUImenskomProstoruPoruke() {
        var z = Zahtjevi.evidentirajNaplatu(List.of(naplata(BigDecimal.ONE)), Primjeri.SAT);

        assertEquals(z.id(), z.element().getAttributeNS(Zahtjevi.NS_EIZV, "id"));
        assertTrue(z.id().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
    }

    @Test
    void iznosSViseOdDvijeDecimaleSeNeZaokruzujeTiho() {
        assertThrows(NeispravanZahtjevException.class,
                () -> Zahtjevi.evidentirajNaplatu(List.of(naplata(new BigDecimal("10.005"))), Primjeri.SAT));
    }

    @Test
    void praznaOpcionalnaPoljaSeIzostavljaju() {
        var racun = UblParser.racun(Primjeri.eRacun("eRacun_PDV25.xml"));

        var z = Zahtjevi.evidentirajERacun(VrstaERacuna.ULAZNI, List.of(racun), Primjeri.SAT);

        assertEquals(0, z.element().getElementsByTagNameNS(Zahtjevi.NS_EFIS, "referencaNaUgovor").getLength());
        assertEquals("U", tekst(z.element(), "vrstaERacuna"));
    }

    @Test
    void shemaOdbijaNeispravanOib() {
        var z = Zahtjevi.evidentirajNaplatu(List.of(new Naplata("TEST", LocalDate.of(2026, 8, 8), "1234567890", "11111111119",
                LocalDate.of(2026, 8, 10), BigDecimal.TEN, NacinPlacanja.T)), Primjeri.SAT);
        potpisivac.potpisi(z.element(), z.id());

        var greske = Xsd.greske(z.element());

        assertFalse(greske.isEmpty());
        assertTrue(greske.get(0).contains("1234567890"), greske.toString());
    }

    @Test
    void shemaOdbijaNepotpisanZahtjev() {
        var z = Zahtjevi.evidentirajNaplatu(List.of(naplata(BigDecimal.ONE)), Primjeri.SAT);

        assertFalse(Xsd.greske(z.element()).isEmpty());
    }

    private static Naplata naplata(BigDecimal iznos) {
        return new Naplata("1-P1-1", LocalDate.of(2026, 3, 1), "12345678901", "11111111119", LocalDate.of(2026, 3, 2), iznos,
                NacinPlacanja.T);
    }

    private static String tekst(Element el, String lokalnoIme) {
        return el.getElementsByTagNameNS("*", lokalnoIme).item(0).getTextContent();
    }

    private static List<String> djeca(Element el, String lokalnoIme) {
        var roditelj = (Element) el.getElementsByTagNameNS("*", lokalnoIme).item(0);
        var imena = new ArrayList<String>();
        for (var n = roditelj.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                imena.add(e.getLocalName());
            }
        }
        return imena;
    }
}
