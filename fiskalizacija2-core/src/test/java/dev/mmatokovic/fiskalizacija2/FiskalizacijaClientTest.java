package dev.mmatokovic.fiskalizacija2;

import dev.mmatokovic.fiskalizacija2.internal.ProvjeraPotpisa;
import dev.mmatokovic.fiskalizacija2.internal.Xml;
import dev.mmatokovic.fiskalizacija2.internal.Xsd;
import dev.mmatokovic.fiskalizacija2.internal.Zahtjevi;
import com.sun.net.httpserver.HttpServer;
import dev.mmatokovic.fiskalizacija2.model.NacinPlacanja;
import dev.mmatokovic.fiskalizacija2.model.Naplata;
import dev.mmatokovic.fiskalizacija2.model.VrstaERacuna;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiskalizacijaClientTest {

    private final Potpisivac potpisivac = Primjeri.potpisivac();
    private final List<String> primljeno = new ArrayList<>();
    private final List<String> contentType = new ArrayList<>();
    private HttpServer server;
    private int status;
    /** Tijelo odgovora; {@value #ID_ZAHTJEVA} se zamjenjuje id-jem primljenog zahtjeva. */
    private String odgovor;
    /** Element odgovora koji lažni servis potpiše nakon zamjene id-ja, ili {@code null} za nepotpisan odgovor. */
    private String potpisati;
    private final List<String> poslano = new ArrayList<>();
    private static final String ID_ZAHTJEVA = "ID_ZAHTJEVA";

    @BeforeEach
    void pokreni() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String zahtjev = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            primljeno.add(zahtjev);
            contentType.add(ex.getRequestHeaders().getFirst("Content-Type"));
            var id = Pattern.compile(":id=\"([^\"]+)\"").matcher(zahtjev);
            String o = id.find() ? odgovor.replace(ID_ZAHTJEVA, id.group(1)) : odgovor;
            if (potpisati != null) {
                o = potpisi(o, potpisati);
            }
            poslano.add(o);
            byte[] tijelo = o.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, tijelo.length);
            ex.getResponseBody().write(tijelo);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void zaustavi() {
        server.stop(0);
    }

    @Test
    void prihvacenZahtjevSIspravnimPotpisomOdgovora() throws Exception {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");

        var r = client().evidentirajNaplatu(naplata());

        assertTrue(r.prihvacen());
        assertNull(r.greska());
        assertEquals(PotpisOdgovora.ISPRAVAN, r.potpisOdgovora());
        assertEquals(200, r.httpStatus());
        assertEquals(r.idZahtjeva(), r.odgovor().idZahtjeva());
        assertEquals(poslano.get(0), r.soapOdgovor());
    }

    @Test
    void naZicuIdePotpisanZahtjevKojiJeVracenURezultatu() throws Exception {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajERacunOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");

        var r = client().evidentirajERacun(VrstaERacuna.IZLAZNI, UblParser.racun(Primjeri.eRacun("eRacun_PDV25.xml")));

        assertEquals(1, primljeno.size());
        assertEquals(r.soapZahtjev(), primljeno.get(0));
        assertEquals("application/xml; charset=utf-8", contentType.get(0));
        var dokument = Xml.parsiraj(primljeno.get(0));
        var zahtjev = (org.w3c.dom.Element) dokument.getElementsByTagNameNS(Zahtjevi.NS_EFIS, "EvidentirajERacunZahtjev").item(0);
        assertEquals(r.idZahtjeva(), zahtjev.getAttributeNS(Zahtjevi.NS_EFIS, "id"));
        assertEquals(List.of(), Xsd.greske(zahtjev));
        assertEquals(PotpisOdgovora.ISPRAVAN, ProvjeraPotpisa.provjeri(zahtjev, Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void odbijenZahtjevIzSluzbenogPrimjeraNijeIznimka() {
        status = 200;
        odgovor = Primjeri.tekst("/primjeri/izvjestavanje/Neuspjesan_Odgovor_EvidentirajNaplatu.xml")
                .replace("22bc1e3e-feb4-47d5-95cd-2a0cfcc0bd71", ID_ZAHTJEVA);

        var r = client().evidentirajNaplatu(naplata());

        assertFalse(r.prihvacen());
        assertEquals("S009", r.greska().sifra());
        assertEquals(1, r.greska().redniBrojZapisa());
        assertEquals("Ne postoji evidentirana fiskalizacija eRačuna.", r.greska().opis());
        assertEquals(r.idZahtjeva(), r.odgovor().idZahtjeva());
        assertEquals(PotpisOdgovora.NEISPRAVAN, r.potpisOdgovora());
    }

    @Test
    void soapFaultJeIznimkaSaStatusomITijelom() {
        status = 500;
        odgovor = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>"
                + "<soapenv:Fault><faultcode>soapenv:Server</faultcode><faultstring>Greska</faultstring></soapenv:Fault>"
                + "</soapenv:Body></soapenv:Envelope>";

        var e = assertThrows(FiskalizacijaException.class, () -> client().evidentirajNaplatu(naplata()));

        assertEquals(500, e.httpStatus());
        assertEquals(odgovor, e.soapOdgovor());
        assertTrue(e.getMessage().contains("EvidentirajNaplatuOdgovor"));
    }

    @Test
    void odgovorKojiNijeXmlJeIznimka() {
        status = 502;
        odgovor = "<html><body>Bad Gateway</body>";

        var e = assertThrows(FiskalizacijaException.class, () -> client().evidentirajNaplatu(naplata()));

        assertEquals(502, e.httpStatus());
        assertEquals(odgovor, e.soapOdgovor());
    }

    @Test
    void nedostupanServisJeIznimkaBezStatusa() {
        var c = client();
        server.stop(0);

        var e = assertThrows(FiskalizacijaException.class, () -> c.evidentirajNaplatu(naplata()));

        assertEquals(0, e.httpStatus());
        assertNull(e.soapOdgovor());
    }

    @Test
    void zahtjevKojiNeProlaziShemuSeNeSalje() {
        var neispravna = new Naplata("1", LocalDate.of(2026, 3, 1), "123", "11111111119", LocalDate.of(2026, 3, 2),
                BigDecimal.ONE, NacinPlacanja.T);

        var e = assertThrows(NeispravanZahtjevException.class, () -> client().evidentirajNaplatu(neispravna));

        assertFalse(e.greske().isEmpty());
        assertEquals(0, primljeno.size());
    }

    @Test
    void nepotpisanLazniOdgovorUzPotpisaniJeIznimka() {
        status = 200;
        String odbijen = "<eizv:prihvacenZahtjev>false</eizv:prihvacenZahtjev>"
                + "<eizv:greska><eizv:sifra>S009</eizv:sifra><eizv:opis>x</eizv:opis></eizv:greska>";
        String potpisan = potpisi(odgovor("EvidentirajNaplatuOdgovor", odbijen), "EvidentirajNaplatuOdgovor");
        odgovor = potpisan.replace("<soapenv:Body>", "<soapenv:Body>"
                + odgovorElement("EvidentirajNaplatuOdgovor", "lazni", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>"));

        var e = assertThrows(FiskalizacijaException.class, () -> client().evidentirajNaplatu(naplata()));

        assertTrue(e.getMessage().contains("točno jedan"), e.getMessage());
    }

    @Test
    void odgovorZaDrugiZahtjevJeIznimka() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>")
                .replace(ID_ZAHTJEVA, "neki-drugi-zahtjev");

        var e = assertThrows(FiskalizacijaException.class, () -> client().evidentirajNaplatu(naplata()));

        assertTrue(e.getMessage().contains("neki-drugi-zahtjev"), e.getMessage());
    }

    @Test
    void nepotpisanOdgovorNemaPotpisa() {
        status = 200;
        odgovor = odgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");

        assertEquals(PotpisOdgovora.NEDOSTAJE, client().evidentirajNaplatu(naplata()).potpisOdgovora());
    }

    @Test
    void racunIzTolerantnogCitanjaBezIzdavateljaJeNeispravanZahtjevANeNpe() {
        var greske = new ArrayList<String>();
        String bezProdavatelja = new String(Primjeri.eRacun("eRacun_PDV25.xml"), StandardCharsets.UTF_8)
                .replaceAll("(?s)<cac:AccountingSupplierParty>.*</cac:AccountingSupplierParty>", "");
        var racun = UblParser.racun(bezProdavatelja.getBytes(StandardCharsets.UTF_8), greske);
        assertNull(racun.izdavatelj());

        var e = assertThrows(NeispravanZahtjevException.class, () -> client().evidentirajERacun(VrstaERacuna.IZLAZNI, racun));

        assertFalse(e.greske().isEmpty());
        assertEquals(0, primljeno.size());
    }

    @Test
    void builderOdbijaNullINepozitivanTimeout() {
        assertThrows(NullPointerException.class, () -> FiskalizacijaClient.builder(Okruzenje.TEST, null));
        var b = FiskalizacijaClient.builder(Okruzenje.TEST, potpisivac);
        assertThrows(NullPointerException.class, () -> b.adresa(null));
        assertThrows(IllegalArgumentException.class, () -> b.timeout(Duration.ZERO));
    }

    @Test
    void bezXsdValidacijeSeNeispravanZahtjevIpakSalje() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>false</eizv:prihvacenZahtjev>");
        var neispravna = new Naplata("1", LocalDate.of(2026, 3, 1), "123", "11111111119", LocalDate.of(2026, 3, 2),
                BigDecimal.ONE, NacinPlacanja.T);

        var r = builder().xsdValidacija(false).build().evidentirajNaplatu(neispravna);

        assertEquals(1, primljeno.size());
        assertFalse(r.prihvacen());
    }

    private FiskalizacijaClient client() {
        return builder().build();
    }

    private FiskalizacijaClient.Builder builder() {
        return FiskalizacijaClient.builder(Okruzenje.TEST, potpisivac)
                .adresa(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/FiskalizacijaService"))
                .povjerljiviCertifikati(Primjeri.sidra(potpisivac), List.of())
                .timeout(Duration.ofSeconds(5))
                .sat(Primjeri.SAT);
    }

    private static Naplata naplata() {
        return new Naplata("1-P1-1", LocalDate.of(2026, 3, 1), "12345678901", "11111111119", LocalDate.of(2026, 3, 2),
                new BigDecimal("125.00"), NacinPlacanja.T);
    }

    /** Odgovor u obliku kakav vraća servis; lažni servis ga potpiše testnim certifikatom kojem klijent u testu vjeruje. */
    private String potpisanOdgovor(String element, String prihvacen) {
        potpisati = element;
        return odgovor(element, prihvacen);
    }

    private static String odgovor(String element, String prihvacen) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>"
                + odgovorElement(element, UUID.randomUUID().toString(), prihvacen)
                + "</soapenv:Body></soapenv:Envelope>";
    }

    private static String odgovorElement(String element, String id, String prihvacen) {
        return "<eizv:" + element + " xmlns:eizv=\"" + Zahtjevi.NS_EIZV + "\" eizv:id=\"" + id + "\">"
                + "<eizv:datumVrijemeSlanja>2026-03-02T10:30:16+01:00</eizv:datumVrijemeSlanja>"
                + "<eizv:Odgovor><eizv:idZahtjeva>" + ID_ZAHTJEVA + "</eizv:idZahtjeva>" + prihvacen + "</eizv:Odgovor>"
                + "</eizv:" + element + ">";
    }

    private String potpisi(String xml, String element) {
        try {
            var doc = Xml.parsiraj(xml);
            var el = (org.w3c.dom.Element) doc.getElementsByTagNameNS(Zahtjevi.NS_EIZV, element).item(0);
            potpisivac.potpisi(el, el.getAttributeNS(Zahtjevi.NS_EIZV, "id"));
            return Xml.serijaliziraj(doc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
