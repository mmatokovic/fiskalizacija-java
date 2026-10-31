package dev.mmatokovic.fiskalizacija.b2c;

import dev.mmatokovic.fiskalizacija.FiskalizacijaException;
import dev.mmatokovic.fiskalizacija.NeispravanZahtjevException;
import dev.mmatokovic.fiskalizacija.Okruzenje;
import dev.mmatokovic.fiskalizacija.PotpisOdgovora;
import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.Primjeri;
import dev.mmatokovic.fiskalizacija.internal.b2c.B2cZahtjevi;
import dev.mmatokovic.fiskalizacija.internal.potpis.ProvjeraPotpisa;
import dev.mmatokovic.fiskalizacija.internal.potpis.XmlDsig;
import dev.mmatokovic.fiskalizacija.internal.xml.Xml;
import dev.mmatokovic.fiskalizacija.internal.xml.Xsd;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class B2cClientTest {

    private static final String JIR = "6b7749c6-56c1-4cf5-b7f7-9f29cebc9f7f";
    private static final String SOAP_ACTION = "http://e-porezna.porezna-uprava.hr/fiskalizacija/2012/services/FiskalizacijaService/";

    private final Potpisivac potpisivac = Primjeri.potpisivac();
    private final List<RecordedRequest> primljeno = new CopyOnWriteArrayList<>();
    private MockWebServer server;
    private volatile String odgovor;
    private volatile boolean potpisati = true;
    private volatile String idPoruke;

    @BeforeEach
    void pokreni() throws IOException {
        odgovor = Primjeri.tekst("/primjeri/b2c/RacunOdgovor.xml");
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                primljeno.add(r);
                String zahtjev = r.getBody() == null ? "" : r.getBody().utf8();
                var id = Pattern.compile("<tns:IdPoruke>([^<]+)</tns:IdPoruke>").matcher(zahtjev);
                String o = odgovor.replace("ID_PORUKE", idPoruke != null ? idPoruke : id.find() ? id.group(1) : "");
                return new MockResponse.Builder().code(200).body(potpisati ? potpisi(o) : o).build();
            }
        });
        server.start(InetAddress.getLoopbackAddress(), 0);
    }

    @AfterEach
    void zaustavi() {
        server.close();
    }

    @Test
    void racunIzSluzbenogPrimjeraSeSaljeIstiIDobivaJir() throws Exception {
        var rezultat = client().racun(sluzbeniPrimjer());

        assertThat(rezultat.prihvacen()).isTrue();
        assertThat(rezultat.jir()).isEqualTo(JIR);
        assertThat(rezultat.potpisOdgovora()).isEqualTo(PotpisOdgovora.ISPRAVAN);
        var ocekivano = Xml.parsiraj(Primjeri.tekst("/primjeri/b2c/RacunZahtjev.xml")).getDocumentElement();
        assertThat(jednako(element(poslano(), "Racun"), element(ocekivano, "Racun"))).as(rezultat.soapZahtjev()).isTrue();
        assertThat(primljeno.get(0).getHeaders().get("SOAPAction")).isEqualTo("\"" + SOAP_ACTION + "racuni\"");
        assertThat(primljeno.get(0).getHeaders().get("Content-Type")).startsWith("text/xml");
    }

    @Test
    void zahtjevJePotpisanPremaSpecifikacijiIProlaziShemu() throws Exception {
        var rezultat = client().racun(sluzbeniPrimjer());

        var zahtjev = element(Xml.parsiraj(rezultat.soapZahtjev()).getDocumentElement(), "RacunZahtjev");
        assertThat(Xsd.greske(zahtjev)).isEmpty();
        assertThat(ProvjeraPotpisa.provjeri(zahtjev, Primjeri.sidra(potpisivac), List.of())).isEqualTo(PotpisOdgovora.ISPRAVAN);
        assertThat(rezultat.soapZahtjev())
                .contains("Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"")
                .contains("Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\"")
                .contains("Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\"")
                .contains("X509IssuerSerial");
    }

    @Test
    void racunBezZkiDobivaIzracunatiZki() {
        var racun = osnovniRacun().build();

        var rezultat = client().racun(racun);

        assertThat(rezultat.zastKod()).isEqualTo(Zki.izracunaj(potpisivac, racun));
        assertThat(rezultat.soapZahtjev()).contains("<tns:ZastKod>" + rezultat.zastKod() + "</tns:ZastKod>");
    }

    @Test
    void greskaIzSluzbenogPrimjeraNijeIznimka() {
        odgovor = Primjeri.tekst("/primjeri/b2c/RacunOdgovorGreska.xml");

        var rezultat = client().racun(osnovniRacun().build());

        assertThat(rezultat.prihvacenBezProvjerePotpisa()).isFalse();
        assertThat(rezultat.jir()).isNull();
        assertThat(rezultat.zastKod()).isNotNull();
        assertThat(rezultat.greske()).singleElement().satisfies(g -> assertThat(g.sifra()).isEqualTo("s001"));
    }

    @Test
    void greskaBezIdPorukeSePrihvaca() {
        odgovor = Primjeri.tekst("/primjeri/b2c/RacunOdgovorGreska.xml");
        idPoruke = "";

        assertThat(client().racun(osnovniRacun().build()).greske()).hasSize(1);
    }

    @Test
    void odgovorZaDruguPorukuJeIznimka() {
        idPoruke = "733362f1-063f-11e2-892e-0800200c9a66";

        assertThatThrownBy(() -> client().racun(osnovniRacun().build()))
                .isInstanceOf(FiskalizacijaException.class)
                .hasMessageContaining("733362f1");
    }

    @Test
    void nepotpisanOdgovorNijePrihvacen() {
        potpisati = false;

        var rezultat = client().racun(osnovniRacun().build());

        assertThat(rezultat.potpisOdgovora()).isEqualTo(PotpisOdgovora.NEDOSTAJE);
        assertThat(rezultat.prihvacenBezProvjerePotpisa()).isTrue();
        assertThat(rezultat.prihvacen()).isFalse();
    }

    @Test
    void zadanoSeTestniCertifikatNePrihvacaKaoPotpisnik() {
        var client = B2cClient.builder(Okruzenje.TEST, potpisivac)
                .adresa(adresa())
                .povjerljiviCertifikati(Primjeri.sidra(potpisivac), List.of())
                .build();

        assertThat(client.racun(osnovniRacun().build()).potpisOdgovora()).isEqualTo(PotpisOdgovora.NEPOUZDAN_CERTIFIKAT);
    }

    @Test
    void iznosSViseOdDvijeDecimaleSeNeSalje() {
        var racun = osnovniRacun().pdv(List.of(new Porez(new BigDecimal("25.00"), new BigDecimal("8.001"), new BigDecimal("2.00"))))
                .build();

        assertThatThrownBy(() -> client().racun(racun)).isInstanceOf(NeispravanZahtjevException.class);
        assertThat(primljeno).isEmpty();
    }

    @Test
    void racunKojiNeProlaziShemuSeNeSalje() {
        var racun = Racun.builder("12345678903", LocalDateTime.of(2026, 3, 2, 10, 30, 15), new BrojRacuna("A1", "POSL1", "1"),
                new BigDecimal("10.00"), NacinPlacanja.G, "12345678903").build();

        assertThatThrownBy(() -> client().racun(racun))
                .isInstanceOf(NeispravanZahtjevException.class)
                .hasMessageContaining("BrOznRac");
        assertThat(primljeno).isEmpty();
    }

    @Test
    void soapFaultJeIznimka() {
        potpisati = false;
        odgovor = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><soap:Fault>"
                + "<faultcode>soap:Server</faultcode><faultstring>Interna greska</faultstring></soap:Fault></soap:Body></soap:Envelope>";

        assertThatThrownBy(() -> client().racun(osnovniRacun().build()))
                .isInstanceOf(FiskalizacijaException.class)
                .hasMessageContaining("Interna greska");
    }

    @Test
    void echoVracaTekstOdgovora() {
        potpisati = false;
        odgovor = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<tns:EchoResponse xmlns:tns=\"http://www.apis-it.hr/fin/2012/types/f73\">proba</tns:EchoResponse></soap:Body></soap:Envelope>";

        assertThat(client().echo("proba")).isEqualTo("proba");
        assertThat(primljeno.get(0).getBody().utf8()).contains("EchoRequest").doesNotContain("Signature");
    }

    @Test
    void promjenaPodatakaSaljeOboPodatkaIPrazanOibZaBrisanje() throws Exception {
        odgovor = odgovorSPorukom("PromijeniPodatkeRacunaOdgovor", "p001");
        var racun = osnovniRacun().oibPrimateljaRacuna("11111111119").build();

        var rezultat = client().promijeniPodatkeRacuna(racun, NacinPlacanja.K, null);

        assertThat(rezultat.prihvacen()).isTrue();
        assertThat(rezultat.poruka().sifraPoruke()).isEqualTo("p001");
        var poslanRacun = element(poslano(), "Racun");
        assertThat(tekst(poslanRacun, "NacinPlac")).isEqualTo("G");
        assertThat(tekst(poslanRacun, "OibPrimateljaRacuna")).isEqualTo("11111111119");
        assertThat(tekst(poslanRacun, "PromijenjeniNacinPlac")).isEqualTo("K");
        assertThat(tekst(poslanRacun, "PromijenjeniOibPrimateljaRacuna")).isEmpty();
        assertThat(primljeno.get(0).getHeaders().get("SOAPAction")).isEqualTo("\"" + SOAP_ACTION + "promijeniPodatkeRacuna\"");
    }

    @Test
    void promjenaNacinaPlacanja() throws Exception {
        odgovor = odgovorSPorukom("PromijeniNacPlacOdgovor", "p001");

        var rezultat = client().promijeniNacPlac(osnovniRacun().build(), NacinPlacanja.T);

        assertThat(rezultat.prihvacen()).isTrue();
        assertThat(tekst(element(poslano(), "Racun"), "PromijenjeniNacinPlac")).isEqualTo("T");
    }

    @Test
    void napojnicaSeSaljeUzPodatkeRacuna() throws Exception {
        odgovor = odgovorSPorukom("NapojnicaOdgovor", "p002");

        var rezultat = client().napojnica(osnovniRacun().build(), new Napojnica(new BigDecimal("2"), NacinPlacanja.K));

        assertThat(rezultat.prihvacen()).isTrue();
        assertThat(rezultat.poruka().sifraPoruke()).isEqualTo("p002");
        var napojnica = element(element(poslano(), "Racun"), "Napojnica");
        assertThat(tekst(napojnica, "iznosNapojnice")).isEqualTo("2.00");
        assertThat(tekst(napojnica, "nacinPlacanjaNapojnice")).isEqualTo("K");
    }

    @Test
    void prijavaRadnogVremenaIzSluzbenogPrimjera() throws Exception {
        odgovor = odgovorSPorukom("PrijaviRadnoVrijemeOdgovor", "p003");
        var radnoVrijeme = new RadnoVrijeme(
                List.of(Redovno.jednokratno(LocalDate.of(2025, 6, 24), null, "Standardno radno vrijeme",
                        List.of(new Jednokratno(1, "08:00", "16:00")))),
                List.of(Iznimka.jednokratno(LocalDate.of(2025, 12, 25), "10:00", "14:00")));

        var rezultat = client().prijaviRadnoVrijeme("12345678901", "POS001", radnoVrijeme, "10987654321");

        assertThat(rezultat.prihvacen()).isTrue();
        var primjer = Xml.parsiraj(Primjeri.tekst("/primjeri/b2c/PrijaviRadnoVrijemeZahtjev.xml")).getDocumentElement();
        var prostor = element(primjer, "PoslovniProstor");
        prostor.removeChild(element(prostor, "OibOper"));
        var zahtjev = element(poslano(), "PrijaviRadnoVrijemeZahtjev");
        assertThat(jednako(element(zahtjev, "PoslovniProstor"), prostor)).as(rezultat.soapZahtjev()).isTrue();
        assertThat(tekst(zahtjev, "OibOper")).isEqualTo("10987654321");
        assertThat(primljeno.get(0).getHeaders().get("SOAPAction")).isEqualTo("\"" + SOAP_ACTION + "promijeniPodatkeRacuna\"");
    }

    @Test
    void dvokratnaIznimkaIRedovnoPoDogovoruProlazeShemu() {
        odgovor = odgovorSPorukom("PrijaviRadnoVrijemeOdgovor", "p003");
        var radnoVrijeme = new RadnoVrijeme(
                List.of(Redovno.poDogovoru(LocalDate.of(2026, 3, 5), null, null),
                        Redovno.dvokratno(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 31), null,
                                List.of(new Dvokratno(1, 1, "08:00", "12:00"), new Dvokratno(1, 2, "16:00", "20:00")))),
                List.of(Iznimka.dvokratno(LocalDate.of(2026, 3, 8), "08:00", "12:00", "16:00", "24:00")));

        assertThat(client().prijaviRadnoVrijeme("12345678901", "POS001", radnoVrijeme, "10987654321").prihvacen()).isTrue();
    }

    @Test
    void brisanjeRadnogVremena() throws Exception {
        odgovor = odgovorSPorukom("ObrisiRadnoVrijemeOdgovor", "p003");

        var rezultat = client().obrisiRadnoVrijeme("12345678901", "POS001",
                new BrisanjeRadnogVremena(List.of(LocalDate.of(2026, 3, 10)), List.of(LocalDate.of(2026, 3, 8))), "10987654321");

        assertThat(rezultat.prihvacen()).isTrue();
        var brisanje = element(element(poslano(), "PoslovniProstor"), "BrisanjeRadnogVremena");
        assertThat(tekst(element(brisanje, "Redovno"), "DatumOd")).isEqualTo("10.03.2026");
        assertThat(tekst(element(brisanje, "Iznimke"), "Datum")).isEqualTo("08.03.2026");
    }

    @Test
    void dohvatRadnogVremenaIzSluzbenogPrimjera() {
        odgovor = Primjeri.tekst("/primjeri/b2c/DohvatiRadnoVrijemeOdgovor.xml");

        var rezultat = client().dohvatiRadnoVrijeme("12345678901", "POS001", VrstaRadnogVremena.SVE, "10987654321");

        assertThat(rezultat.prihvacen()).isTrue();
        assertThat(rezultat.radnoVrijeme().iznimke()).isEmpty();
        assertThat(rezultat.radnoVrijeme().redovno()).singleElement().satisfies(r -> {
            assertThat(r.datumOd()).isEqualTo(LocalDate.of(2025, 12, 8));
            assertThat(r.parniNeparni()).hasSize(6).first().isEqualTo(new ParniNeparni(1, ParNepar.P, "08:30", "12:00"));
        });
    }

    @Test
    void neispravnoRadnoVrijemeUOdgovoruJeIznimka() {
        odgovor = Primjeri.tekst("/primjeri/b2c/DohvatiRadnoVrijemeOdgovor.xml").replace("<fis:ParNepar>P</fis:ParNepar>", "");

        assertThatThrownBy(() -> client().dohvatiRadnoVrijeme("12345678901", "POS001", VrstaRadnogVremena.SVE, "10987654321"))
                .isInstanceOf(FiskalizacijaException.class)
                .hasMessageContaining("radno vrijeme");
    }

    @Test
    void dohvatSGreskomIzSluzbenogPrimjera() {
        odgovor = Primjeri.tekst("/primjeri/b2c/DohvatiRadnoVrijemeOdgovorGreska.xml");

        var rezultat = client().dohvatiRadnoVrijeme("02994650199", "0306", VrstaRadnogVremena.REDOVNO, "02994650199");

        assertThat(rezultat.prihvacenBezProvjerePotpisa()).isFalse();
        assertThat(rezultat.greske()).singleElement().satisfies(g -> assertThat(g.sifra()).isEqualTo("s110"));
        assertThat(rezultat.radnoVrijeme().redovno()).isEmpty();
    }

    @Test
    void prijavaZaVisePoslovnicaVracaIshodSvake() throws Exception {
        odgovor = Primjeri.tekst("/primjeri/b2c/PrijaviRadnoVrijemeZaPoslovniceOdgovor.xml");
        var redovno = Redovno.jednokratno(LocalDate.of(2026, 3, 5), null, null, List.of(new Jednokratno(8, "09:00", "13:00")));
        var poslovnice = new ArrayList<Poslovnica>();
        for (String p : List.of("POS01", "POS02")) {
            poslovnice.add(Poslovnica.redovno(p, redovno));
        }
        poslovnice.add(Poslovnica.iznimka("POS03", Iznimka.jednokratno(LocalDate.of(2026, 3, 8), "10:00", "12:00")));

        var rezultat = client().prijaviRadnoVrijemeZaPoslovnice("12345678901", poslovnice, "10987654321");

        assertThat(rezultat.prihvacen()).isTrue();
        assertThat(rezultat.poslovnice()).extracting(PoslovnicaOdgovor::oznPosPr).containsExactly("POS01", "POS02", "POS03");
        assertThat(rezultat.poslovnice()).allSatisfy(p -> assertThat(p.poruka().sifraPoruke()).isEqualTo("p003"));
        assertThat(element(element(element(poslano(), "PoslovniProstori"), "Poslovnica"), "Redovno")).isNotNull();
    }

    private B2cClient client() {
        return B2cClient.builder(Okruzenje.TEST, potpisivac)
                .adresa(adresa())
                .povjerljiviCertifikati(Primjeri.sidra(potpisivac), List.of())
                .timeout(Duration.ofSeconds(5))
                .sat(Primjeri.SAT)
                .potpisnikOdgovora(c -> true)
                .build();
    }

    private URI adresa() {
        return URI.create("http://127.0.0.1:" + server.getPort() + "/FiskalizacijaServiceTest");
    }

    private Element poslano() throws Exception {
        return Xml.parsiraj(primljeno.get(0).getBody().utf8()).getDocumentElement();
    }

    private static String odgovorSPorukom(String element, String sifra) {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<tns:" + element + " xmlns:tns=\"http://www.apis-it.hr/fin/2012/types/f73\" Id=\"G0x1\">"
                + "<tns:Zaglavlje><tns:IdPoruke>ID_PORUKE</tns:IdPoruke><tns:DatumVrijeme>02.03.2026T10:30:16</tns:DatumVrijeme></tns:Zaglavlje>"
                + "<tns:PorukaOdgovora><tns:SifraPoruke>" + sifra + "</tns:SifraPoruke><tns:Poruka>Uspjesno.</tns:Poruka></tns:PorukaOdgovora>"
                + "</tns:" + element + "></soap:Body></soap:Envelope>";
    }

    private static Racun.Builder osnovniRacun() {
        return Racun.builder("12345678903", LocalDateTime.of(2026, 3, 2, 10, 30, 15), new BrojRacuna("1", "POSL1", "1"),
                        new BigDecimal("12.50"), NacinPlacanja.G, "12345678903")
                .pdv(List.of(new Porez(new BigDecimal("25.00"), new BigDecimal("10.00"), new BigDecimal("2.50"))));
    }

    /** Podaci iz primjera 9.1 tehničke specifikacije. */
    private static Racun sluzbeniPrimjer() {
        var deset = new BigDecimal("10.00");
        return Racun.builder("98765432198", LocalDateTime.of(2012, 9, 1, 21, 10, 34), new BrojRacuna("123456789", "POSL1", "12"),
                        new BigDecimal("145.68"), NacinPlacanja.K, "01234567890")
                .pdv(List.of(new Porez(new BigDecimal("25.00"), deset, new BigDecimal("2.50")),
                        new Porez(new BigDecimal("10.00"), deset, new BigDecimal("1.00")),
                        new Porez(new BigDecimal("0.00"), deset, new BigDecimal("0.00"))))
                .pnp(List.of(new Porez(new BigDecimal("3.00"), deset, new BigDecimal("0.30"))))
                .ostaliPor(List.of(new PorezOstalo("Porez na luksuz", new BigDecimal("15.00"), deset, new BigDecimal("1.50"))))
                .iznosOslobPdv(new BigDecimal("12.00"))
                .iznosMarza(new BigDecimal("13.00"))
                .iznosNePodlOpor(new BigDecimal("100.00"))
                .naknade(List.of(new Naknada("Povratna naknada", new BigDecimal("1.00"))))
                .zastKod("e4d909c290d0fb1ca068ffaddf22cbd0")
                .paragonBrRac("123/458/5")
                .specNamj("Navedeno kao primjer")
                .build();
    }

    private String potpisi(String xml) {
        try {
            var doc = Xml.parsiraj(xml);
            var tijelo = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body").item(0);
            for (var n = tijelo.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element el && el.hasAttribute("Id")) {
                    XmlDsig.potpisi(el, el.getAttribute("Id"), potpisivac);
                    return Xml.serijaliziraj(doc);
                }
            }
            return xml;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Prvi potomak s lokalnim imenom u f73 namespaceu. */
    private static Element element(Element roditelj, String ime) {
        return (Element) roditelj.getElementsByTagNameNS(B2cZahtjevi.NS_F73, ime).item(0);
    }

    private static String tekst(Element roditelj, String ime) {
        var el = element(roditelj, ime);
        return el == null ? null : el.getTextContent();
    }

    /** Isti namespace, lokalna imena, redoslijed elemenata i tekst listova; prefiksi i praznine se zanemaruju. */
    private static boolean jednako(Element a, Element b) {
        if (!java.util.Objects.equals(a.getNamespaceURI(), b.getNamespaceURI()) || !a.getLocalName().equals(b.getLocalName())) {
            return false;
        }
        var djecaA = djeca(a);
        var djecaB = djeca(b);
        if (djecaA.isEmpty() && djecaB.isEmpty()) {
            return a.getTextContent().trim().equals(b.getTextContent().trim());
        }
        if (djecaA.size() != djecaB.size()) {
            return false;
        }
        for (int i = 0; i < djecaA.size(); i++) {
            if (!jednako(djecaA.get(i), djecaB.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<Element> djeca(Element el) {
        var djeca = new ArrayList<Element>();
        for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                djeca.add(e);
            }
        }
        return djeca;
    }
}
