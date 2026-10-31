package dev.mmatokovic.fiskalizacija.b2c;

import dev.mmatokovic.fiskalizacija.FinaCertifikati;
import dev.mmatokovic.fiskalizacija.FiskalizacijaException;
import dev.mmatokovic.fiskalizacija.NeispravanZahtjevException;
import dev.mmatokovic.fiskalizacija.Okruzenje;
import dev.mmatokovic.fiskalizacija.PotpisOdgovora;
import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.internal.B2cOdgovori;
import dev.mmatokovic.fiskalizacija.internal.B2cZahtjevi;
import dev.mmatokovic.fiskalizacija.internal.PristupKljucu;
import dev.mmatokovic.fiskalizacija.internal.ProvjeraPotpisa;
import dev.mmatokovic.fiskalizacija.internal.SoapPoziv;
import dev.mmatokovic.fiskalizacija.internal.Subjekt;
import dev.mmatokovic.fiskalizacija.internal.Xml;
import dev.mmatokovic.fiskalizacija.internal.XmlDsig;
import dev.mmatokovic.fiskalizacija.internal.Xsd;
import dev.mmatokovic.fiskalizacija.internal.Zahtjevi;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.net.ssl.SSLContext;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Klijent za fiskalizaciju računa u krajnjoj potrošnji (B2C) prema tehničkoj specifikaciji v2.7.
 * Thread-safe; jednu instancu dijele svi pozivi.
 *
 * <p>Greška Sustava za fiskalizaciju je {@link B2cRezultat} s {@code prihvacen() == false}. Iznimke:
 * {@link NeispravanZahtjevException} kad poruka nije valjana (XSD, iznos s više od dvije decimale) i ništa se ne šalje,
 * {@link FiskalizacijaException} kad slanje ne uspije ili odgovor nije valjan, {@link IllegalStateException} kad
 * potpisivanje ne uspije (ključ mora biti RSA). Račun bez JIR-a izdaje se kupcu sa ZKI-jem i naknadno šalje s
 * {@code nakDost(true)}.
 *
 * <pre>{@code
 * var client = B2cClient.builder(Okruzenje.TEST, Potpisivac.izP12(p12, lozinka)).build();
 * var rezultat = client.racun(racun);
 * }</pre>
 */
public final class B2cClient {

    /** OIB APIS IT-a, čiji certifikati {@code fiskalcis} i {@code fiskalcistest} potpisuju odgovore. */
    public static final String OIB_APIS_IT = "02994650199";

    private static final String SOAP_ACTION = "http://e-porezna.porezna-uprava.hr/fiskalizacija/2012/services/FiskalizacijaService/";
    private static final String CONTENT_TYPE = "text/xml; charset=utf-8";

    private final URI adresa;
    private final Potpisivac potpisivac;
    private final Set<TrustAnchor> sidra;
    private final List<X509Certificate> posrednici;
    private final Duration timeout;
    private final boolean xsdValidacija;
    private final Clock sat;
    private final Predicate<X509Certificate> potpisnikOdgovora;
    private final HttpClient http;

    private B2cClient(Builder b) {
        this.adresa = b.adresa;
        this.potpisivac = b.potpisivac;
        this.sidra = b.sidra;
        this.posrednici = b.posrednici;
        this.timeout = b.timeout;
        this.xsdValidacija = b.xsdValidacija;
        this.sat = b.sat;
        this.potpisnikOdgovora = b.potpisnikOdgovora;
        var http = HttpClient.newBuilder().sslContext(b.ssl).connectTimeout(b.timeout);
        if (b.proxy != null) {
            http.proxy(b.proxy);
        }
        this.http = http.build();
    }

    /** Okruženje određuje adresu servisa i povjerljive FINA certifikate; {@code potpisivac} potpisuje zahtjeve. */
    public static Builder builder(Okruzenje okruzenje, Potpisivac potpisivac) {
        return new Builder(Objects.requireNonNull(okruzenje, "okruzenje"), Objects.requireNonNull(potpisivac, "potpisivac"));
    }

    /**
     * Fiskalizira račun ({@code RacunZahtjev}). Ako račun nema ZKI, klijent ga izračuna ({@link Zki}).
     *
     * @return ishod s JIR-om ili greškama
     * @throws NeispravanZahtjevException ako poruka ne prolazi XSD shemu; ništa se ne šalje
     * @throws FiskalizacijaException     ako poziv ne uspije ili odgovor nije valjan
     */
    public B2cRezultat racun(Racun racun) {
        String zastKod = zastKod(racun);
        return posalji(B2cZahtjevi.racun(racun, zastKod, sat), zastKod, "racuni", "RacunOdgovor");
    }

    /**
     * Provjerava račun bez evidentiranja ({@code ProvjeraZahtjev}); postoji samo u testnom okruženju. Rezultati provjere
     * dolaze kao {@link B2cRezultat#greske()} sa šiframa {@code vXXX}.
     *
     * @see #racun(Racun)
     */
    public B2cRezultat provjera(Racun racun) {
        String zastKod = zastKod(racun);
        return posalji(B2cZahtjevi.provjera(racun, zastKod, sat), zastKod, "provjera", "ProvjeraOdgovor");
    }

    /**
     * Mijenja način plaćanja fiskaliziranog računa, isti dan do ponoći. Specifikacija preporučuje
     * {@link #promijeniPodatkeRacuna}, a ovu metodu najavljuje za ukidanje.
     *
     * @param racun podaci računa kako su fiskalizirani, uključujući izvorni način plaćanja
     * @see #racun(Racun)
     */
    public B2cRezultat promijeniNacPlac(Racun racun, NacinPlacanja promijenjeniNacinPlac) {
        Objects.requireNonNull(promijenjeniNacinPlac, "promijenjeniNacinPlac");
        String zastKod = zastKod(racun);
        return posalji(B2cZahtjevi.promijeniNacPlac(racun, zastKod, promijenjeniNacinPlac, sat), zastKod, "promijeniNacPlac",
                "PromijeniNacPlacOdgovor");
    }

    /**
     * Mijenja način plaćanja i OIB primatelja fiskaliziranog računa, isti dan do ponoći. Vrijedi zadnja poslana promjena,
     * pa uvijek pošaljite oba podatka.
     *
     * @param racun                 podaci računa kako su fiskalizirani
     * @param nacinPlac             način plaćanja nakon promjene
     * @param oibPrimateljaRacuna   OIB primatelja nakon promjene, ili {@code null} da se obriše
     * @see #racun(Racun)
     */
    public B2cRezultat promijeniPodatkeRacuna(Racun racun, NacinPlacanja nacinPlac, String oibPrimateljaRacuna) {
        Objects.requireNonNull(nacinPlac, "nacinPlac");
        String zastKod = zastKod(racun);
        return posalji(B2cZahtjevi.promijeniPodatkeRacuna(racun, zastKod, nacinPlac, oibPrimateljaRacuna, sat), zastKod,
                "promijeniPodatkeRacuna", "PromijeniPodatkeRacunaOdgovor");
    }

    /**
     * Prijavljuje napojnicu uz fiskalizirani račun, najkasnije dva dana nakon izdavanja.
     *
     * @param racun podaci računa kako su fiskalizirani
     * @see #racun(Racun)
     */
    public B2cRezultat napojnica(Racun racun, Napojnica napojnica) {
        Objects.requireNonNull(napojnica, "napojnica");
        String zastKod = zastKod(racun);
        return posalji(B2cZahtjevi.napojnica(racun, zastKod, napojnica, sat), zastKod, "napojnica", "NapojnicaOdgovor");
    }

    /**
     * Prijavljuje buduća radna vremena poslovnog prostora (od sutra do najviše 45 dana unaprijed).
     *
     * @param oib      OIB obveznika
     * @param oznPosPr oznaka poslovnog prostora
     * @param oibOper  OIB operatera
     * @see #racun(Racun)
     */
    public B2cRezultat prijaviRadnoVrijeme(String oib, String oznPosPr, RadnoVrijeme radnoVrijeme, String oibOper) {
        Objects.requireNonNull(radnoVrijeme, "radnoVrijeme");
        // soapAction kao u službenom WSDL-u v1.10, iako upućuje na promijeniPodatkeRacuna
        return posalji(B2cZahtjevi.prijaviRadnoVrijeme(oib, oznPosPr, radnoVrijeme, oibOper, sat), null,
                "promijeniPodatkeRacuna", "PrijaviRadnoVrijemeOdgovor");
    }

    /** Briše buduća radna vremena poslovnog prostora. */
    public B2cRezultat obrisiRadnoVrijeme(String oib, String oznPosPr, BrisanjeRadnogVremena brisanje, String oibOper) {
        Objects.requireNonNull(brisanje, "brisanje");
        return posalji(B2cZahtjevi.obrisiRadnoVrijeme(oib, oznPosPr, brisanje, oibOper, sat), null, "obrisiRadnoVrijeme",
                "ObrisiRadnoVrijemeOdgovor");
    }

    /** Dohvaća aktivna i buduća radna vremena; rezultat je u {@link B2cRezultat#radnoVrijeme()}. */
    public B2cRezultat dohvatiRadnoVrijeme(String oib, String oznPosPr, VrstaRadnogVremena vrsta, String oibOper) {
        Objects.requireNonNull(vrsta, "vrsta");
        return posalji(B2cZahtjevi.dohvatiRadnoVrijeme(oib, oznPosPr, vrsta, oibOper, sat), null, "dohvatiRadnoVrijeme",
                "DohvatiRadnoVrijemeOdgovor");
    }

    /**
     * Prijavljuje radno vrijeme za do 100 poslovnih prostora; ishod svakog je u {@link B2cRezultat#poslovnice()}.
     */
    public B2cRezultat prijaviRadnoVrijemeZaPoslovnice(String oib, List<Poslovnica> poslovnice, String oibOper) {
        return posalji(B2cZahtjevi.prijaviRadnoVrijemeZaPoslovnice(oib, List.copyOf(poslovnice), oibOper, sat), null,
                "prijaviRadnoVrijemeZaPoslovnice", "PrijaviRadnoVrijemeZaPoslovniceOdgovor");
    }

    /**
     * Provjera dostupnosti servisa; poruka se ne potpisuje.
     *
     * @return tekst iz {@code EchoResponse}
     * @throws FiskalizacijaException ako poziv ne uspije ili odgovor nema {@code EchoResponse}
     */
    public String echo(String poruka) {
        var odgovor = SoapPoziv.posalji(http, adresa, timeout, CONTENT_TYPE, SOAP_ACTION + "echo",
                Xml.serijaliziraj(B2cZahtjevi.echo(Objects.requireNonNull(poruka, "poruka"))));
        var echo = parsiraj(odgovor.status(), odgovor.tijelo()).getElementsByTagNameNS(B2cZahtjevi.NS_F73, "EchoResponse");
        if (echo.getLength() != 1) {
            throw new FiskalizacijaException("Odgovor nema EchoResponse (HTTP " + odgovor.status() + ")",
                    odgovor.status(), odgovor.tijelo(), null);
        }
        return echo.item(0).getTextContent();
    }

    private String zastKod(Racun racun) {
        return racun.zastKod() != null ? racun.zastKod() : Zki.izracunaj(potpisivac, racun);
    }

    private B2cRezultat posalji(B2cZahtjevi.Zahtjev zahtjev, String zastKod, String operacija, String odgovorElement) {
        XmlDsig.potpisi(zahtjev.element(), zahtjev.id(), PristupKljucu.kljuc(potpisivac), potpisivac.certifikat());
        if (xsdValidacija) {
            var greske = Xsd.greske(zahtjev.element());
            if (!greske.isEmpty()) {
                throw new NeispravanZahtjevException(greske);
            }
        }
        String soap = Xml.serijaliziraj(zahtjev.dokument());
        var odgovor = SoapPoziv.posalji(http, adresa, timeout, CONTENT_TYPE, SOAP_ACTION + operacija, soap);
        Instant primljeno = sat.instant();
        int status = odgovor.status();
        String tijelo = odgovor.tijelo();

        var el = parsiraj(status, tijelo).getElementsByTagNameNS(B2cZahtjevi.NS_F73, odgovorElement);
        if (el.getLength() != 1) {
            throw new FiskalizacijaException("Odgovor mora sadržavati točno jedan " + odgovorElement + ", a sadrži "
                    + el.getLength() + " (HTTP " + status + ")", status, tijelo, null);
        }
        var odgovorEl = (Element) el.item(0);
        var greske = B2cOdgovori.greske(odgovorEl);
        String jir = B2cOdgovori.tekst(odgovorEl, "Jir");
        var zaglavlje = B2cOdgovori.dijete(odgovorEl, "Zaglavlje");
        String idPoruke = zaglavlje == null ? null : B2cOdgovori.tekst(zaglavlje, "IdPoruke");
        boolean bezIdaUzGresku = (idPoruke == null || idPoruke.isEmpty()) && jir == null && !greske.isEmpty();
        if (!bezIdaUzGresku && !zahtjev.idPoruke().equals(idPoruke)) {
            throw new FiskalizacijaException("Odgovor je za poruku " + idPoruke + ", a poslana je " + zahtjev.idPoruke()
                    + " (HTTP " + status + ")", status, tijelo, null);
        }
        RadnoVrijeme radnoVrijeme;
        try {
            radnoVrijeme = B2cOdgovori.radnoVrijeme(odgovorEl);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new FiskalizacijaException("Neispravno radno vrijeme u odgovoru: " + e.getMessage() + " (HTTP " + status + ")",
                    status, tijelo, e);
        }
        PotpisOdgovora potpis = ProvjeraPotpisa.provjeri(odgovorEl, sidra, posrednici, null, potpisnikOdgovora);
        return new B2cRezultat(zahtjev.idPoruke(), jir, zastKod, B2cOdgovori.poruka(odgovorEl), radnoVrijeme,
                B2cOdgovori.poslovnice(odgovorEl), greske, potpis, status, soap, tijelo, primljeno);
    }

    private static Document parsiraj(int status, String tijelo) {
        Document dokument;
        try {
            dokument = Xml.parsiraj(tijelo);
        } catch (Exception e) {
            throw new FiskalizacijaException("Odgovor nije XML (HTTP " + status + ")", status, tijelo, e);
        }
        var fault = dokument.getElementsByTagNameNS(Zahtjevi.NS_SOAP, "Fault");
        if (fault.getLength() > 0) {
            throw new FiskalizacijaException("SOAP Fault: " + B2cOdgovori.tekst((Element) fault.item(0), "faultstring")
                    + " (HTTP " + status + ")", status, tijelo, null);
        }
        return dokument;
    }

    /** Postavke klijenta; zadane vrijednosti odgovaraju odabranom {@link Okruzenje}. */
    public static final class Builder {

        private final Potpisivac potpisivac;
        private URI adresa;
        private Set<TrustAnchor> sidra;
        private List<X509Certificate> posrednici;
        private SSLContext ssl;
        private Duration timeout = Duration.ofSeconds(30);
        private boolean xsdValidacija = true;
        private Clock sat = Clock.system(ZoneId.of("Europe/Zagreb"));
        private Predicate<X509Certificate> potpisnikOdgovora;
        private ProxySelector proxy;

        private Builder(Okruzenje okruzenje, Potpisivac potpisivac) {
            this.potpisivac = potpisivac;
            this.adresa = okruzenje == Okruzenje.TEST
                    ? URI.create("https://cistest.apis-it.hr:8449/FiskalizacijaServiceTest")
                    : URI.create("https://cis.porezna-uprava.hr:8449/FiskalizacijaService");
            this.potpisnikOdgovora = Subjekt.potpisnik(okruzenje == Okruzenje.TEST ? "fiskalcistest" : "fiskalcis", OIB_APIS_IT);
            this.sidra = FinaCertifikati.sidra(okruzenje);
            this.posrednici = FinaCertifikati.posrednici(okruzenje);
            this.ssl = FinaCertifikati.sslContext(okruzenje);
        }

        /** Za gateway ili reverse proxy ispred servisa; za forward proxy vidi {@link #proxy(ProxySelector)}. */
        public Builder adresa(URI adresa) {
            this.adresa = Objects.requireNonNull(adresa, "adresa");
            return this;
        }

        /**
         * Najdulje čekanje na spajanje i na odgovor; zadano 30 sekundi. Račun se obrađuje u najviše 2 sekunde, a radno
         * vrijeme oko 10, pa na naplatnom uređaju postavite kraće čekanje: račun bez JIR-a izdaje se i šalje naknadno.
         */
        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout mora biti pozitivan: " + timeout);
            }
            this.timeout = timeout;
            return this;
        }

        /** Uključeno po defaultu: neispravna poruka se ne šalje nego baca {@link NeispravanZahtjevException}. */
        public Builder xsdValidacija(boolean ukljuceno) {
            this.xsdValidacija = ukljuceno;
            return this;
        }

        /** Umjesto zadanog konteksta, koji vjeruje FINA lancu ({@link FinaCertifikati#sslContext(Okruzenje)}). */
        public Builder sslContext(SSLContext ssl) {
            this.ssl = Objects.requireNonNull(ssl, "ssl");
            return this;
        }

        /** Povjerljivi CA za provjeru potpisa odgovora, umjesto ugrađenih FINA certifikata. */
        public Builder povjerljiviCertifikati(Set<TrustAnchor> sidra, List<X509Certificate> posrednici) {
            this.sidra = Set.copyOf(sidra);
            this.posrednici = List.copyOf(posrednici);
            return this;
        }

        /** Sat za vrijeme slanja u zaglavlju i za {@link B2cRezultat#primljeno()}; korisno u testovima. */
        public Builder sat(Clock sat) {
            this.sat = Objects.requireNonNull(sat, "sat");
            return this;
        }

        /** Forward proxy za HTTP vezu prema Poreznoj, npr. {@code ProxySelector.of(new InetSocketAddress("proxy", 8080))}. */
        public Builder proxy(ProxySelector proxy) {
            this.proxy = Objects.requireNonNull(proxy, "proxy");
            return this;
        }

        /**
         * Koji certifikat smije potpisati odgovor, uz ispravan lanac do povjerljivih CA. Zadano: {@code CN=fiskalcis}
         * (u testu {@code fiskalcistest}) i {@value B2cClient#OIB_APIS_IT} u subjektu, prema poglavlju 5 specifikacije.
         */
        public Builder potpisnikOdgovora(Predicate<X509Certificate> provjera) {
            this.potpisnikOdgovora = Objects.requireNonNull(provjera, "provjera");
            return this;
        }

        public B2cClient build() {
            return new B2cClient(this);
        }
    }
}
