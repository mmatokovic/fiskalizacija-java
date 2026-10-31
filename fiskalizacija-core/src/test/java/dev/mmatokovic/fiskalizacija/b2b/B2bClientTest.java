package dev.mmatokovic.fiskalizacija.b2b;

import dev.mmatokovic.fiskalizacija.FiskalizacijaException;
import dev.mmatokovic.fiskalizacija.NeispravanZahtjevException;
import dev.mmatokovic.fiskalizacija.Okruzenje;
import dev.mmatokovic.fiskalizacija.PotpisOdgovora;
import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.Primjeri;
import dev.mmatokovic.fiskalizacija.internal.b2b.Zahtjevi;
import dev.mmatokovic.fiskalizacija.internal.potpis.ProvjeraPotpisa;
import dev.mmatokovic.fiskalizacija.internal.potpis.Xades;
import dev.mmatokovic.fiskalizacija.internal.xml.Xml;
import dev.mmatokovic.fiskalizacija.internal.xml.Xsd;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class B2bClientTest {

    private final Potpisivac potpisivac = Primjeri.potpisivac();
    private volatile Potpisivac potpisivacOdgovora = potpisivac;
    private final List<String> primljeno = new CopyOnWriteArrayList<>();
    private final List<String> contentType = new CopyOnWriteArrayList<>();
    private final List<String> soapAction = new CopyOnWriteArrayList<>();
    private MockWebServer server;
    private volatile int status;
    /** Tijelo odgovora; {@value #ID_ZAHTJEVA} se zamjenjuje id-jem primljenog zahtjeva. */
    private volatile String odgovor;
    /** Element odgovora koji lažni servis potpiše nakon zamjene id-ja, ili {@code null} za nepotpisan odgovor. */
    private volatile String potpisati;
    private final List<String> poslano = new CopyOnWriteArrayList<>();
    private static final String ID_ZAHTJEVA = "ID_ZAHTJEVA";

    @BeforeEach
    void pokreni() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                String zahtjev = r.getBody() == null ? "" : r.getBody().utf8();
                primljeno.add(zahtjev);
                contentType.add(r.getHeaders().get("Content-Type"));
                soapAction.add(r.getHeaders().get("SOAPAction"));
                var id = Pattern.compile(":id=\"([^\"]+)\"").matcher(zahtjev);
                String o = id.find() ? odgovor.replace(ID_ZAHTJEVA, id.group(1)) : odgovor;
                if (potpisati != null) {
                    o = potpisi(o, potpisati);
                }
                poslano.add(o);
                return new MockResponse.Builder().code(status).body(o).build();
            }
        });
        server.start(InetAddress.getLoopbackAddress(), 0);
    }

    @AfterEach
    void zaustavi() {
        server.close();
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
        assertEquals(poslano.get(0), r.soapOdgovor());
    }

    @Test
    void naZicuIdePotpisanZahtjevKojiJeVracenURezultatu() throws Exception {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajERacunOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");

        var r = client().evidentirajERacun(VrstaERacuna.IZLAZNI, UblParser.racun(Primjeri.eRacun("eRacun_PDV25.xml")));

        assertEquals(1, primljeno.size());
        assertEquals(r.soapZahtjev(), primljeno.get(0));
        assertEquals("text/xml; charset=utf-8", contentType.get(0));
        assertEquals("\"\"", soapAction.get(0));
        var dokument = Xml.parsiraj(primljeno.get(0));
        var zahtjev = (org.w3c.dom.Element) dokument.getElementsByTagNameNS(Zahtjevi.NS_EFIS, "EvidentirajERacunZahtjev").item(0);
        assertEquals(r.idZahtjeva(), zahtjev.getAttributeNS(Zahtjevi.NS_EFIS, "id"));
        assertEquals(List.of(), Xsd.greske(zahtjev));
        assertEquals(PotpisOdgovora.ISPRAVAN, ProvjeraPotpisa.provjeri(zahtjev, Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void odbijenZahtjevIzSluzbenogPrimjeraNijeIznimka() {
        status = 200;
        odgovor = Primjeri.tekst("/primjeri/izvjestavanje/Neuspjesan_Odgovor_EvidentirajNaplatu.xml");

        var r = client().evidentirajNaplatu(naplata());

        assertFalse(r.prihvacen());
        assertEquals("S009", r.greska().sifra());
        assertEquals(1, r.greska().redniBrojZapisa());
        assertEquals("Ne postoji evidentirana fiskalizacija eRačuna.", r.greska().opis());
        assertEquals("22bc1e3e-feb4-47d5-95cd-2a0cfcc0bd71", r.odgovor().idZahtjeva());
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
        assertTrue(e.getMessage().contains("SOAP Fault: Greska"), e.getMessage());
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
        server.close();

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
    void idZahtjevaUOdgovoruGeneriraPorezna() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>")
                .replace(ID_ZAHTJEVA, "6c674534-1fa7-4d12-a4b3-a95bbd3fefc3");

        var r = client().evidentirajNaplatu(naplata());

        assertTrue(r.prihvacen());
        assertEquals("6c674534-1fa7-4d12-a4b3-a95bbd3fefc3", r.odgovor().idZahtjeva());
        assertNotEquals(r.idZahtjeva(), r.odgovor().idZahtjeva());
    }

    @Test
    void nepotpisanOdgovorNemaPotpisa() {
        status = 200;
        odgovor = odgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");

        var r = client().evidentirajNaplatu(naplata());

        assertEquals(PotpisOdgovora.NEDOSTAJE, r.potpisOdgovora());
        assertFalse(r.prihvacen());
        assertTrue(r.prihvacenBezProvjerePotpisa());
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
        assertThrows(NullPointerException.class, () -> B2bClient.builder(Okruzenje.TEST, null));
        var b = B2bClient.builder(Okruzenje.TEST, potpisivac);
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

    @Test
    void potpisSpremljenogOdgovoraSeMozeProvjeritiKasnije() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");
        var r = client().evidentirajNaplatu(naplata());
        var arhiva = r.soapOdgovor();

        assertEquals(Primjeri.SAT.instant(), r.primljeno());
        assertEquals(PotpisOdgovora.ISPRAVAN, client().provjeriOdgovor(arhiva, r.primljeno()));
        assertEquals(PotpisOdgovora.NEISPRAVAN, client().provjeriOdgovor(
                arhiva.replace(">true</eizv:prihvacenZahtjev>", ">false</eizv:prihvacenZahtjev>"), Instant.now()));
        assertEquals(PotpisOdgovora.NEISPRAVAN, client().provjeriOdgovor(arhiva.replace("<soapenv:Body>", "<soapenv:Body>"
                + odgovorElement("EvidentirajNaplatuOdgovor", "lazni", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>")),
                Instant.now()));
    }

    @Test
    void lanacSpremljenogOdgovoraProvjeravaSeNaTrenutakPrimitka() throws Exception {
        // Potpisnik odgovora izdan je pod testnim CA i vrijedio je samo u siječnju 2026.
        var ks = KeyStore.getInstance("PKCS12");
        try (var in = Primjeri.class.getResourceAsStream("/test-lanac.p12")) {
            ks.load(in, "test1234".toCharArray());
        }
        potpisivacOdgovora = new Potpisivac((PrivateKey) ks.getKey("potpisnik", "test1234".toCharArray()),
                (X509Certificate) ks.getCertificate("potpisnik"));
        var ca = new TrustAnchor((X509Certificate) ks.getCertificate("ca"), null);
        var c = builder().povjerljiviCertifikati(Set.of(ca), List.of()).build();
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");
        var arhiva = c.evidentirajNaplatu(naplata()).soapOdgovor();

        assertEquals(PotpisOdgovora.ISPRAVAN, c.provjeriOdgovor(arhiva, Instant.parse("2026-01-15T10:00:00Z")));
        assertEquals(PotpisOdgovora.NEPOUZDAN_CERTIFIKAT, c.provjeriOdgovor(arhiva, Instant.now()));
    }

    @Test
    void provjeraSpremljenogOdgovoraBezPotpisaIliKojiNijeXml() {
        assertEquals(PotpisOdgovora.NEDOSTAJE, client().provjeriOdgovor(
                odgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>"), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> client().provjeriOdgovor("<html>", Instant.now()));
    }

    @Test
    void pripremljeniZahtjevSeSaljeVlastitimHttpKlijentom() throws Exception {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");
        var c = client();
        var zahtjev = c.pripremiNaplatu(List.of(naplata()));

        var http = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + server.getPort() + "/FiskalizacijaService"))
                .POST(HttpRequest.BodyPublishers.ofString(zahtjev.soap())).build(), HttpResponse.BodyHandlers.ofString());
        var r = c.obradiOdgovor(zahtjev, http.statusCode(), http.body());

        assertTrue(r.prihvacen());
        assertEquals(zahtjev.idZahtjeva(), r.idZahtjeva());
        assertEquals(zahtjev.soap(), r.soapZahtjev());
        assertEquals(zahtjev.soap(), primljeno.get(0));
        assertEquals(PotpisOdgovora.ISPRAVAN, r.potpisOdgovora());
    }

    @Test
    void pripremljeniZahtjevJePotpisanIProlaziShemu() throws Exception {
        var zahtjev = client().pripremiNaplatu(List.of(naplata()));

        var el = (org.w3c.dom.Element) Xml.parsiraj(zahtjev.soap())
                .getElementsByTagNameNS(Zahtjevi.NS_EIZV, "EvidentirajNaplatuZahtjev").item(0);
        assertEquals(zahtjev.idZahtjeva(), el.getAttributeNS(Zahtjevi.NS_EIZV, "id"));
        assertEquals(List.of(), Xsd.greske(el));
        assertEquals(PotpisOdgovora.ISPRAVAN, ProvjeraPotpisa.provjeri(el, Primjeri.sidra(potpisivac), List.of()));
        assertEquals(0, primljeno.size());
    }

    @Test
    void pripremljeniZahtjevSeSaljeKlijentom() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");
        var c = client();

        assertTrue(c.posalji(c.pripremiNaplatu(List.of(naplata()))).prihvacen());
    }

    @Test
    void neispravanRedniBrojZapisaJeFiskalizacijskaIznimka() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>false</eizv:prihvacenZahtjev>"
                + "<eizv:greska><eizv:sifra>S009</eizv:sifra><eizv:redniBrojZapisa>prvi</eizv:redniBrojZapisa>"
                + "<eizv:opis>x</eizv:opis></eizv:greska>");

        var e = assertThrows(FiskalizacijaException.class, () -> client().evidentirajNaplatu(naplata()));

        assertTrue(e.getMessage().contains("redniBrojZapisa"), e.getMessage());
    }

    @Test
    void zadanoSePrihvacaSamoPotpisnikSOibomMinistarstvaFinancija() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");
        var zadani = B2bClient.builder(Okruzenje.TEST, potpisivac)
                .adresa(URI.create("http://127.0.0.1:" + server.getPort() + "/FiskalizacijaService"))
                .povjerljiviCertifikati(Primjeri.sidra(potpisivac), List.of())
                .build();

        var r = zadani.evidentirajNaplatu(naplata());

        assertEquals(PotpisOdgovora.NEPOUZDAN_CERTIFIKAT, r.potpisOdgovora());
        assertFalse(r.prihvacen());
        assertTrue(r.prihvacenBezProvjerePotpisa());
    }

    @Test
    void odgovorBezIdZahtjevaJeIznimka() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>")
                .replace("<eizv:idZahtjeva>" + ID_ZAHTJEVA + "</eizv:idZahtjeva>", "");

        var e = assertThrows(FiskalizacijaException.class, () -> client().evidentirajNaplatu(naplata()));

        assertTrue(e.getMessage().contains("idZahtjeva"), e.getMessage());
    }

    @Test
    void soapFaultSHttp200JeIznimka() {
        status = 200;
        odgovor = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>"
                + "<soapenv:Fault><faultcode>soapenv:Client</faultcode><faultstring>Neispravna poruka</faultstring></soapenv:Fault>"
                + "</soapenv:Body></soapenv:Envelope>";

        var e = assertThrows(FiskalizacijaException.class, () -> client().evidentirajNaplatu(naplata()));

        assertEquals(200, e.httpStatus());
        assertTrue(e.getMessage().contains("Neispravna poruka"), e.getMessage());
    }

    @Test
    void prihvacenZahtjevJedanJeTrue() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>1</eizv:prihvacenZahtjev>");

        assertTrue(client().evidentirajNaplatu(naplata()).prihvacen());
    }

    @Test
    void rezultatToStringNeSadrziSoapPoruke() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajNaplatuOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");

        String s = client().evidentirajNaplatu(naplata()).toString();

        assertFalse(s.contains("Envelope"), s);
        assertFalse(s.contains("11111111119"), s);
    }

    @Test
    void odbijanjeIIsporukaKrozKlijent() {
        status = 200;
        odgovor = potpisanOdgovor("EvidentirajOdbijanjeOdgovor", "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");
        assertTrue(client().evidentirajOdbijanje(new Odbijanje("1-P1-1", LocalDate.of(2026, 3, 1), "12345678903", "11111111119",
                LocalDate.of(2026, 3, 2), VrstaRazlogaOdbijanja.N, "Neispravan iznos")).prihvacen());

        odgovor = potpisanOdgovor("EvidentirajIsporukuZaKojuNijeIzdanERacunOdgovor",
                "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev>");
        assertTrue(client().evidentirajIsporukuZaKojuNijeIzdanERacun(UblParser.racun(Primjeri.eRacun("eRacun_PDV25.xml")))
                .prihvacen());
    }

    @Test
    void prevelikOdgovorJeIznimka() {
        status = 200;
        odgovor = "<a>" + "x".repeat(11 * 1024 * 1024) + "</a>";

        var e = assertThrows(FiskalizacijaException.class, () -> client().evidentirajNaplatu(naplata()));

        assertTrue(e.getMessage().contains("veći od"), e.getMessage());
    }

    private B2bClient client() {
        return builder().build();
    }

    private B2bClient.Builder builder() {
        return B2bClient.builder(Okruzenje.TEST, potpisivac)
                .adresa(URI.create("http://127.0.0.1:" + server.getPort() + "/FiskalizacijaService"))
                .povjerljiviCertifikati(Primjeri.sidra(potpisivac), List.of())
                .timeout(Duration.ofSeconds(5))
                .sat(Primjeri.SAT)
                .potpisnikOdgovora(c -> true);
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
            Xades.potpisi(el, el.getAttributeNS(Zahtjevi.NS_EIZV, "id"), potpisivacOdgovora, Clock.systemUTC());
            return Xml.serijaliziraj(doc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
