package dev.mmatokovic.fiskalizacija2;

import dev.mmatokovic.fiskalizacija2.internal.ProvjeraPotpisa;
import dev.mmatokovic.fiskalizacija2.internal.Xml;
import dev.mmatokovic.fiskalizacija2.internal.Xsd;
import dev.mmatokovic.fiskalizacija2.internal.Zahtjevi;
import dev.mmatokovic.fiskalizacija2.model.Greska;
import dev.mmatokovic.fiskalizacija2.model.Naplata;
import dev.mmatokovic.fiskalizacija2.model.Odbijanje;
import dev.mmatokovic.fiskalizacija2.model.Odgovor;
import dev.mmatokovic.fiskalizacija2.model.Racun;
import dev.mmatokovic.fiskalizacija2.model.VrstaERacuna;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Klijent za Fiskalizaciju 2.0 (B2B): evidentiranje eRačuna i eIzvještavanje.
 * Thread-safe; jednu instancu dijele svi pozivi.
 *
 * <pre>{@code
 * var client = FiskalizacijaClient.builder(Okruzenje.TEST, Potpisivac.izP12(p12, lozinka)).build();
 * var rezultat = client.evidentirajERacun(VrstaERacuna.IZLAZNI, UblParser.racun(ublXml));
 * }</pre>
 */
public final class FiskalizacijaClient {

    private final URI adresa;
    private final Potpisivac potpisivac;
    private final Set<TrustAnchor> sidra;
    private final List<X509Certificate> posrednici;
    private final Duration timeout;
    private final boolean xsdValidacija;
    private final Clock sat;
    private final HttpClient http;

    private FiskalizacijaClient(Builder b) {
        this.adresa = b.adresa;
        this.potpisivac = b.potpisivac;
        this.sidra = b.sidra;
        this.posrednici = b.posrednici;
        this.timeout = b.timeout;
        this.xsdValidacija = b.xsdValidacija;
        this.sat = b.sat;
        this.http = HttpClient.newBuilder().sslContext(b.ssl).connectTimeout(b.timeout).build();
    }

    /** Okruženje određuje adresu servisa i povjerljive FINA certifikate; {@code potpisivac} potpisuje zahtjeve. */
    public static Builder builder(Okruzenje okruzenje, Potpisivac potpisivac) {
        return new Builder(Objects.requireNonNull(okruzenje, "okruzenje"), Objects.requireNonNull(potpisivac, "potpisivac"));
    }

    /**
     * Šalje {@code EvidentirajERacunZahtjev} s jednim eRačunom.
     *
     * @param vrsta izlazni ili ulazni eRačun
     * @param racun podaci eRačuna, npr. iz {@link UblParser#racun(byte[])}
     * @return ishod; poslovno odbijanje je {@link Rezultat} s {@code prihvacen() == false}
     * @throws NeispravanZahtjevException ako poruka ne prolazi XSD shemu; ništa se ne šalje
     * @throws FiskalizacijaException     ako poziv ne uspije ili odgovor nije valjan fiskalizacijski odgovor
     */
    public Rezultat evidentirajERacun(VrstaERacuna vrsta, Racun racun) {
        return evidentirajERacun(vrsta, List.of(racun));
    }

    /**
     * Šalje {@code EvidentirajERacunZahtjev} s do 100 eRačuna u jednoj poruci.
     *
     * @param vrsta  izlazni ili ulazni eRačun
     * @param racuni podaci eRačuna
     * @return ishod cijele poruke; {@link Greska#redniBrojZapisa()} kaže na koji se račun greška odnosi
     * @see #evidentirajERacun(VrstaERacuna, Racun)
     */
    public Rezultat evidentirajERacun(VrstaERacuna vrsta, List<Racun> racuni) {
        return posalji(Zahtjevi.evidentirajERacun(vrsta, racuni, sat), "EvidentirajERacunOdgovor");
    }

    /**
     * Šalje {@code EvidentirajIsporukuZaKojuNijeIzdanERacunZahtjev} za jedan račun.
     *
     * @param racun podaci računa
     * @return ishod
     * @see #evidentirajERacun(VrstaERacuna, Racun)
     */
    public Rezultat evidentirajIsporukuZaKojuNijeIzdanERacun(Racun racun) {
        return evidentirajIsporukuZaKojuNijeIzdanERacun(List.of(racun));
    }

    /**
     * Šalje {@code EvidentirajIsporukuZaKojuNijeIzdanERacunZahtjev} za više računa u jednoj poruci.
     *
     * @param racuni podaci računa
     * @return ishod cijele poruke
     * @see #evidentirajERacun(VrstaERacuna, Racun)
     */
    public Rezultat evidentirajIsporukuZaKojuNijeIzdanERacun(List<Racun> racuni) {
        return posalji(Zahtjevi.evidentirajIsporukuZaKojuNijeIzdanERacun(racuni, sat), "EvidentirajIsporukuZaKojuNijeIzdanERacunOdgovor");
    }

    /**
     * Šalje {@code EvidentirajNaplatuZahtjev} za jednu naplatu.
     *
     * @param naplata naplata računa
     * @return ishod
     * @see #evidentirajERacun(VrstaERacuna, Racun)
     */
    public Rezultat evidentirajNaplatu(Naplata naplata) {
        return evidentirajNaplatu(List.of(naplata));
    }

    /**
     * Šalje {@code EvidentirajNaplatuZahtjev} za više naplata u jednoj poruci.
     *
     * @param naplate naplate računa
     * @return ishod cijele poruke
     * @see #evidentirajERacun(VrstaERacuna, Racun)
     */
    public Rezultat evidentirajNaplatu(List<Naplata> naplate) {
        return posalji(Zahtjevi.evidentirajNaplatu(naplate, sat), "EvidentirajNaplatuOdgovor");
    }

    /**
     * Šalje {@code EvidentirajOdbijanjeZahtjev} za jedan odbijeni eRačun.
     *
     * @param odbijanje odbijanje eRačuna
     * @return ishod
     * @see #evidentirajERacun(VrstaERacuna, Racun)
     */
    public Rezultat evidentirajOdbijanje(Odbijanje odbijanje) {
        return evidentirajOdbijanje(List.of(odbijanje));
    }

    /**
     * Šalje {@code EvidentirajOdbijanjeZahtjev} za više odbijenih eRačuna u jednoj poruci.
     *
     * @param odbijanja odbijanja eRačuna
     * @return ishod cijele poruke
     * @see #evidentirajERacun(VrstaERacuna, Racun)
     */
    public Rezultat evidentirajOdbijanje(List<Odbijanje> odbijanja) {
        return posalji(Zahtjevi.evidentirajOdbijanje(odbijanja, sat), "EvidentirajOdbijanjeOdgovor");
    }

    private Rezultat posalji(Zahtjevi.Zahtjev zahtjev, String odgovorElement) {
        potpisivac.potpisi(zahtjev.element(), zahtjev.id());
        if (xsdValidacija) {
            var greske = Xsd.greske(zahtjev.element());
            if (!greske.isEmpty()) {
                throw new NeispravanZahtjevException(greske);
            }
        }
        String soap = Xml.serijaliziraj(zahtjev.dokument());

        HttpResponse<String> odgovor;
        try {
            odgovor = http.send(HttpRequest.newBuilder(adresa)
                            .timeout(timeout)
                            .header("Content-Type", "application/xml; charset=utf-8")
                            .header("SOAPAction", "\"\"")
                            .POST(HttpRequest.BodyPublishers.ofString(soap, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new FiskalizacijaException("Slanje na " + adresa + " nije uspjelo: " + e.getMessage(), 0, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FiskalizacijaException("Slanje prekinuto", 0, null, e);
        }

        int status = odgovor.statusCode();
        String tijelo = odgovor.body();
        Document dokument;
        try {
            dokument = Xml.parsiraj(tijelo);
        } catch (Exception e) {
            throw new FiskalizacijaException("Odgovor nije XML (HTTP " + status + ")", status, tijelo, e);
        }
        var el = dokument.getElementsByTagNameNS("*", odgovorElement);
        if (el.getLength() != 1) {
            throw new FiskalizacijaException("Odgovor mora sadržavati točno jedan " + odgovorElement + ", a sadrži "
                    + el.getLength() + " (HTTP " + status + ")", status, tijelo, null);
        }
        var odgovorEl = (Element) el.item(0);
        var o = odgovor(odgovorEl);
        if (o == null) {
            throw new FiskalizacijaException(odgovorElement + " ne sadrži element Odgovor (HTTP " + status + ")", status, tijelo, null);
        }
        if (o.idZahtjeva() != null && !o.idZahtjeva().equals(zahtjev.id())) {
            throw new FiskalizacijaException("Odgovor je za zahtjev " + o.idZahtjeva() + ", a poslan je " + zahtjev.id()
                    + " (HTTP " + status + ")", status, tijelo, null);
        }
        return new Rezultat(zahtjev.id(), o, ProvjeraPotpisa.provjeri(odgovorEl, sidra, posrednici), status, soap, tijelo);
    }

    /**
     * Provjerava potpis spremljenog odgovora, npr. iz arhive kod poreznog nadzora, s povjerljivim certifikatima
     * ovog klijenta. Potpis mora pokrivati jedini element u SOAP tijelu.
     *
     * @param soapOdgovor odgovor točno onako kako je primljen ({@link Rezultat#soapOdgovor()})
     * @param primljen    kad je odgovor primljen; lanac certifikata provjerava se na taj trenutak, jer certifikat
     *                    Porezne do provjere iz arhive može isteći
     * @throws IllegalArgumentException ako odgovor nije XML
     */
    public PotpisOdgovora provjeriOdgovor(String soapOdgovor, Instant primljen) {
        Objects.requireNonNull(primljen, "primljen");
        Document dokument;
        try {
            dokument = Xml.parsiraj(soapOdgovor);
        } catch (Exception e) {
            throw new IllegalArgumentException("Odgovor nije XML: " + e.getMessage(), e);
        }
        var tijela = dokument.getElementsByTagNameNS(Zahtjevi.NS_SOAP, "Body");
        if (tijela.getLength() != 1) {
            return PotpisOdgovora.NEISPRAVAN;
        }
        Element odgovorEl = null;
        for (var n = tijela.item(0).getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                if (odgovorEl != null) {
                    return PotpisOdgovora.NEISPRAVAN;
                }
                odgovorEl = e;
            }
        }
        if (odgovorEl == null) {
            return PotpisOdgovora.NEISPRAVAN;
        }
        return ProvjeraPotpisa.provjeri(odgovorEl, sidra, posrednici, primljen);
    }

    static Odgovor odgovor(Element odgovorElement) {
        var o = dijete(odgovorElement, "Odgovor");
        if (o == null) {
            return null;
        }
        var g = dijete(o, "greska");
        Greska greska = null;
        if (g != null) {
            String redniBroj = tekst(g, "redniBrojZapisa");
            greska = new Greska(tekst(g, "sifra"), redniBroj == null ? null : Integer.valueOf(redniBroj), tekst(g, "opis"));
        }
        return new Odgovor(tekst(o, "idZahtjeva"), "true".equals(tekst(o, "prihvacenZahtjev")), greska);
    }

    private static Element dijete(Element roditelj, String lokalnoIme) {
        for (var n = roditelj.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && lokalnoIme.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static String tekst(Element roditelj, String lokalnoIme) {
        var e = dijete(roditelj, lokalnoIme);
        return e == null ? null : e.getTextContent().trim();
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

        private Builder(Okruzenje okruzenje, Potpisivac potpisivac) {
            this.potpisivac = potpisivac;
            this.adresa = okruzenje.adresa();
            this.sidra = FinaCertifikati.sidra(okruzenje);
            this.posrednici = FinaCertifikati.posrednici(okruzenje);
            this.ssl = FinaCertifikati.sslContext(okruzenje);
        }

        /** Za proxy ili gateway ispred servisa Porezne uprave. */
        public Builder adresa(URI adresa) {
            this.adresa = Objects.requireNonNull(adresa, "adresa");
            return this;
        }

        /** Najdulje čekanje na spajanje i na odgovor; zadano 30 sekundi. */
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

        /** Sat u zoni Europe/Zagreb za {@code datumVrijemeSlanja}; korisno u testovima. */
        public Builder sat(Clock sat) {
            this.sat = Objects.requireNonNull(sat, "sat");
            return this;
        }

        public FiskalizacijaClient build() {
            return new FiskalizacijaClient(this);
        }
    }
}
