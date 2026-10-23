package dev.mmatokovic.fiskalizacija2;

import dev.mmatokovic.fiskalizacija2.model.KlasifikacijaArtikla;
import dev.mmatokovic.fiskalizacija2.model.PrethodniRacun;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class UblParserTest {

    @Test
    void sviSluzbeniPrimjeriSeCitajuBezGresaka() {
        var neuspjeli = new ArrayList<String>();
        for (var p : Primjeri.eRacuni()) {
            var greske = new ArrayList<String>();
            UblParser.racun(Primjeri.eRacun(p), greske);
            if (!greske.isEmpty()) {
                neuspjeli.add(p.getFileName() + ": " + greske);
            }
        }
        assertEquals(20, Primjeri.eRacuni().size());
        if (!neuspjeli.isEmpty()) {
            fail(String.join("\n", neuspjeli));
        }
    }

    @Test
    void finalniRacunZaPredujamMapiraSvaPolja() {
        var r = UblParser.racun(Primjeri.eRacun("Finalni_racun_predujam.xml"));

        assertEquals("25-1-1", r.brojDokumenta());
        assertEquals(LocalDate.of(2026, 12, 1), r.datumIzdavanja());
        assertEquals("380", r.vrstaDokumenta());
        assertEquals("EUR", r.valuta());
        assertEquals(LocalDate.of(2026, 12, 31), r.datumDospijecaPlacanja());
        assertEquals("P11", r.vrstaPoslovnogProcesa());
        assertEquals(LocalDate.of(2026, 9, 25), r.datumIsporuke());
        assertEquals(java.util.List.of(new PrethodniRacun("18-1-1", LocalDate.of(2026, 11, 1))), r.prethodniRacuni());
        assertEquals("TVRTKA A d.o.o.", r.izdavatelj().ime());
        assertEquals(Primjeri.OIB, r.izdavatelj().oibPorezniBroj());
        assertEquals(Primjeri.OIB, r.izdavatelj().oibOperatera());
        assertEquals("Tvrtka B d.o.o.", r.primatelj().ime());
        assertEquals("11111111119", r.primatelj().oibPorezniBroj());
        assertEquals("HRXXXXXXXXXXXXXXXX", r.prijenosiSredstava().get(0).identifikatorRacunaZaPlacanje());

        var u = r.ukupanIznos();
        assertEquals(new BigDecimal("1200.00"), u.neto());
        assertEquals(new BigDecimal("1200.00"), u.iznosBezPdv());
        assertEquals(new BigDecimal("300.00"), u.pdv());
        assertEquals(new BigDecimal("1500.00"), u.iznosSPdv());
        assertEquals(new BigDecimal("500.00"), u.placeniIznos());
        assertEquals(new BigDecimal("1000.00"), u.iznosKojiDospijevaZaPlacanje());

        var s = r.stavke().get(0);
        assertEquals("Proizvod", s.artiklNaziv());
        assertEquals("HR:PDV25", s.artiklHrOznakaKategorijaPdv());
        assertEquals(new KlasifikacijaArtikla("62.20.20", "CG", null), s.klasifikacije().get(0));
        assertFalse(r.indikatorKopije());
    }

    @Test
    void odobrenjeSeCitaKaoCreditNote() {
        var r = UblParser.racun(Primjeri.eRacun("Odobrenje.xml"));

        assertEquals("381", r.vrstaDokumenta());
        assertFalse(r.stavke().isEmpty());
        assertNotNull(r.stavke().get(0).kolicina());
    }

    @Test
    void hrRascLambaPdvaImaPrednostNadStandardnom() {
        var r = UblParser.racun(Primjeri.eRacun("eRacun_PDV_NEOP_PP_trosak.xml"));

        assertTrue(r.raspodjelaPdv().stream().allMatch(rp -> rp.hrOznakaKategorijaPdv() != null),
                "raspodjela mora doći iz HRFISK20Data proširenja: " + r.raspodjelaPdv());
    }

    @Test
    void racunOmotanUStandardBusinessDocumentSeCita() {
        String ubl = new String(Primjeri.eRacun("eRacun_PDV25.xml"), StandardCharsets.UTF_8).replaceFirst("<\\?xml[^?]*\\?>", "");
        String sbd = "<StandardBusinessDocument xmlns=\"http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader\">"
                + "<StandardBusinessDocumentHeader><HeaderVersion>1.0</HeaderVersion></StandardBusinessDocumentHeader>"
                + ubl + "</StandardBusinessDocument>";

        var r = UblParser.racun(sbd.getBytes(StandardCharsets.UTF_8));

        assertEquals(UblParser.racun(Primjeri.eRacun("eRacun_PDV25.xml")), r);
    }

    @Test
    void oibSeUzimaIzEndpointIdKadCompanyIdNemaHrPrefiks() {
        String xml = new String(Primjeri.eRacun("eRacun_PDV25.xml"), StandardCharsets.UTF_8)
                .replaceAll("<cbc:CompanyID>HR(\\d{11})</cbc:CompanyID>", "<cbc:CompanyID>$1</cbc:CompanyID>");

        var r = UblParser.racun(xml.getBytes(StandardCharsets.UTF_8));

        assertEquals(Primjeri.OIB, r.izdavatelj().oibPorezniBroj());
        assertEquals("11111111119", r.primatelj().oibPorezniBroj());
    }

    @Test
    void strogoCitanjeJavljaSveGreskeOdjednom() {
        String xml = new String(Primjeri.eRacun("eRacun_PDV25.xml"), StandardCharsets.UTF_8)
                .replace("<cbc:ProfileID>P1</cbc:ProfileID>", "")
                .replaceFirst("<cbc:ID>1-P1-1</cbc:ID>", "");

        var e = assertThrows(UblException.class, () -> UblParser.racun(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, e.greske().size(), e.greske().toString());
        assertTrue(e.greske().stream().anyMatch(g -> g.startsWith("Nedostaje BT-1 ")));
        assertTrue(e.greske().stream().anyMatch(g -> g.startsWith("Nedostaje BT-23 ")));
    }

    @Test
    void tolerantnoCitanjeVracaDjelomicanRacun() {
        String xml = new String(Primjeri.eRacun("eRacun_PDV25.xml"), StandardCharsets.UTF_8)
                .replace("<cbc:ProfileID>P1</cbc:ProfileID>", "");
        var greske = new ArrayList<String>();

        var r = UblParser.racun(xml.getBytes(StandardCharsets.UTF_8), greske);

        assertEquals(1, greske.size(), greske.toString());
        assertNull(r.vrstaPoslovnogProcesa());
        assertEquals("1-P1-1", r.brojDokumenta());
    }

    @Test
    void dokumentBezRacunaJeGreska() {
        var e = assertThrows(UblException.class, () -> UblParser.racun("<a/>".getBytes(StandardCharsets.UTF_8)));

        assertTrue(e.getMessage().contains("Invoice"));
    }

    @Test
    void doctypeSeOdbija() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>";

        assertThrows(UblException.class, () -> UblParser.racun(xxe.getBytes(StandardCharsets.UTF_8)));
    }
}
