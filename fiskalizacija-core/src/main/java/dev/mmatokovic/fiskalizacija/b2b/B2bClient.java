package dev.mmatokovic.fiskalizacija.b2b;

import dev.mmatokovic.fiskalizacija.FinaCertifikati;
import dev.mmatokovic.fiskalizacija.FiskalizacijaException;
import dev.mmatokovic.fiskalizacija.NeispravanZahtjevException;
import dev.mmatokovic.fiskalizacija.Okruzenje;
import dev.mmatokovic.fiskalizacija.PotpisOdgovora;
import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.internal.b2b.Zahtjevi;
import dev.mmatokovic.fiskalizacija.internal.http.Postavke;
import dev.mmatokovic.fiskalizacija.internal.http.SoapPoziv;
import dev.mmatokovic.fiskalizacija.internal.potpis.Subjekt;
import dev.mmatokovic.fiskalizacija.internal.xml.Xml;
import dev.mmatokovic.fiskalizacija.internal.xml.Xsd;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Klijent za Fiskalizaciju 2.0 (B2B): evidentiranje eRačuna i eIzvještavanje.
 * Thread-safe; jednu instancu dijele svi pozivi.
 *
 * <p>Poslovno odbijanje Porezne je {@link Rezultat} s {@code prihvacen() == false}. Iznimke: {@link NeispravanZahtjevException}
 * kad poruka nije valjana (XSD, iznos s više od dvije decimale) i ništa se ne šalje, {@link FiskalizacijaException} kad
 * slanje ne uspije ili odgovor nije valjan, {@link IllegalStateException} kad potpisivanje ne uspije (npr. ključ koji nije
 * RSA ni EC) i {@link NullPointerException} za {@code null} argumente.
 *
 * <pre>{@code
 * var client = B2bClient.builder(Okruzenje.TEST, Potpisivac.izP12(p12, lozinka)).build();
 * var rezultat = client.evidentirajERacun(VrstaERacuna.IZLAZNI, UblParser.racun(ublXml));
 * }</pre>
 */
public final class B2bClient {

    /** OIB Ministarstva financija, kojem pripada Porezna uprava; nalazi se u certifikatima njezinih servisa. */
    public static final String OIB_MINISTARSTVA_FINANCIJA = "18683136487";

    private final Postavke postavke;
    private final Potpisivac potpisivac;
    private final HttpClient http;

    private B2bClient(Builder b) {
        this.postavke = b.postavke;
        this.potpisivac = b.potpisivac;
        this.http = b.postavke.httpClient();
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
        return posalji(pripremiERacun(vrsta, racuni));
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
        return posalji(pripremiIsporukuZaKojuNijeIzdanERacun(racuni));
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
        return posalji(pripremiNaplatu(naplate));
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
        return posalji(pripremiOdbijanje(odbijanja));
    }

    /**
     * Potpisuje i validira {@code EvidentirajERacunZahtjev} bez slanja, npr. za slanje vlastitim HTTP klijentom.
     *
     * @throws NeispravanZahtjevException ako poruka ne prolazi XSD shemu
     */
    public PripremljeniZahtjev pripremiERacun(VrstaERacuna vrsta, List<Racun> racuni) {
        return pripremi(Zahtjevi.evidentirajERacun(vrsta, racuni, postavke.sat()), "EvidentirajERacunOdgovor");
    }

    /** @see #pripremiERacun(VrstaERacuna, List) */
    public PripremljeniZahtjev pripremiIsporukuZaKojuNijeIzdanERacun(List<Racun> racuni) {
        return pripremi(Zahtjevi.evidentirajIsporukuZaKojuNijeIzdanERacun(racuni, postavke.sat()), "EvidentirajIsporukuZaKojuNijeIzdanERacunOdgovor");
    }

    /** @see #pripremiERacun(VrstaERacuna, List) */
    public PripremljeniZahtjev pripremiNaplatu(List<Naplata> naplate) {
        return pripremi(Zahtjevi.evidentirajNaplatu(naplate, postavke.sat()), "EvidentirajNaplatuOdgovor");
    }

    /** @see #pripremiERacun(VrstaERacuna, List) */
    public PripremljeniZahtjev pripremiOdbijanje(List<Odbijanje> odbijanja) {
        return pripremi(Zahtjevi.evidentirajOdbijanje(odbijanja, postavke.sat()), "EvidentirajOdbijanjeOdgovor");
    }

    private PripremljeniZahtjev pripremi(Zahtjevi.Zahtjev zahtjev, String odgovorElement) {
        potpisivac.potpisi(zahtjev.element(), zahtjev.id());
        if (postavke.xsdValidacija()) {
            var greske = Xsd.greske(zahtjev.element());
            if (!greske.isEmpty()) {
                throw new NeispravanZahtjevException(greske);
            }
        }
        return new PripremljeniZahtjev(zahtjev.id(), Xml.serijaliziraj(zahtjev.dokument()), odgovorElement);
    }

    /**
     * Šalje pripremljeni zahtjev.
     *
     * @throws FiskalizacijaException ako poziv ne uspije ili odgovor nije valjan fiskalizacijski odgovor
     */
    public Rezultat posalji(PripremljeniZahtjev zahtjev) {
        var odgovor = SoapPoziv.posalji(http, postavke.adresa(), postavke.timeout(), "application/xml; charset=utf-8", "", zahtjev.soap());
        return obradiOdgovor(zahtjev, odgovor.status(), odgovor.tijelo());
    }

    /**
     * Obrađuje odgovor na zahtjev poslan vlastitim HTTP klijentom, uz iste provjere kao {@link #posalji(PripremljeniZahtjev)}.
     * Pozovite je odmah po primitku, jer se tada bilježi {@link Rezultat#primljeno()}.
     *
     * @throws FiskalizacijaException ako odgovor nije valjan fiskalizacijski odgovor na taj zahtjev
     */
    public Rezultat obradiOdgovor(PripremljeniZahtjev zahtjev, int httpStatus, String soapOdgovor) {
        Instant primljeno = postavke.sat().instant();
        String odgovorElement = zahtjev.odgovorElement();
        Document dokument;
        try {
            dokument = Xml.parsiraj(soapOdgovor);
        } catch (Exception e) {
            throw new FiskalizacijaException("Odgovor nije XML (HTTP " + httpStatus + ")", httpStatus, soapOdgovor, e);
        }
        var el = dokument.getElementsByTagNameNS("*", odgovorElement);
        if (el.getLength() == 0) {
            var fault = dokument.getElementsByTagNameNS(SoapPoziv.NS_SOAP, "Fault");
            if (fault.getLength() > 0) {
                String opis = Xml.tekst((Element) fault.item(0), "faultstring");
                throw new FiskalizacijaException("SOAP Fault: " + opis + " (HTTP " + httpStatus + ")", httpStatus, soapOdgovor, null);
            }
        }
        if (el.getLength() != 1) {
            throw new FiskalizacijaException("Odgovor mora sadržavati točno jedan " + odgovorElement + ", a sadrži "
                    + el.getLength() + " (HTTP " + httpStatus + ")", httpStatus, soapOdgovor, null);
        }
        var odgovorEl = (Element) el.item(0);
        Odgovor o;
        try {
            o = odgovor(odgovorEl);
        } catch (NumberFormatException e) {
            throw new FiskalizacijaException("Neispravan redniBrojZapisa u odgovoru (HTTP " + httpStatus + ")",
                    httpStatus, soapOdgovor, e);
        }
        if (o == null) {
            throw new FiskalizacijaException(odgovorElement + " ne sadrži element Odgovor (HTTP " + httpStatus + ")",
                    httpStatus, soapOdgovor, null);
        }
        if (o.idZahtjeva() == null) {
            throw new FiskalizacijaException("Odgovor nema idZahtjeva (HTTP " + httpStatus + ")", httpStatus, soapOdgovor, null);
        }
        if (!o.idZahtjeva().equals(zahtjev.idZahtjeva())) {
            throw new FiskalizacijaException("Odgovor je za zahtjev " + o.idZahtjeva() + ", a poslan je " + zahtjev.idZahtjeva()
                    + " (HTTP " + httpStatus + ")", httpStatus, soapOdgovor, null);
        }
        return new Rezultat(zahtjev.idZahtjeva(), o,
                postavke.provjeriPotpis(odgovorEl, null), httpStatus,
                zahtjev.soap(), soapOdgovor, primljeno);
    }

    /**
     * Provjerava potpis spremljenog odgovora, npr. iz arhive kod poreznog nadzora, s povjerljivim certifikatima
     * ovog klijenta. Potpis mora pokrivati jedini element u SOAP tijelu.
     *
     * @param soapOdgovor odgovor točno onako kako je primljen ({@link Rezultat#soapOdgovor()})
     * @param primljen    kad je odgovor primljen ({@link Rezultat#primljeno()}); lanac certifikata provjerava se na
     *                    taj trenutak, jer certifikat Porezne do provjere iz arhive može isteći
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
        var tijela = dokument.getElementsByTagNameNS(SoapPoziv.NS_SOAP, "Body");
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
        return postavke.provjeriPotpis(odgovorEl, primljen);
    }

    static Odgovor odgovor(Element odgovorElement) {
        var o = Xml.dijete(odgovorElement, "Odgovor");
        if (o == null) {
            return null;
        }
        var g = Xml.dijete(o, "greska");
        Greska greska = null;
        if (g != null) {
            String redniBroj = Xml.tekst(g, "redniBrojZapisa");
            greska = new Greska(Xml.tekst(g, "sifra"), redniBroj == null ? null : Integer.valueOf(redniBroj), Xml.tekst(g, "opis"));
        }
        String prihvacen = Xml.tekst(o, "prihvacenZahtjev");
        return new Odgovor(Xml.tekst(o, "idZahtjeva"), "true".equals(prihvacen) || "1".equals(prihvacen), greska);
    }

    /** Postavke klijenta; zadane vrijednosti odgovaraju odabranom {@link Okruzenje}. */
    public static final class Builder {

        private final Potpisivac potpisivac;
        private Postavke postavke;

        private Builder(Okruzenje okruzenje, Potpisivac potpisivac) {
            this.potpisivac = potpisivac;
            this.postavke = Postavke.zadane(okruzenje, okruzenje.adresa(), Subjekt.sadrziOib(OIB_MINISTARSTVA_FINANCIJA));
        }

        /** Za gateway ili reverse proxy ispred servisa Porezne uprave; za forward proxy vidi {@link #proxy(ProxySelector)}. */
        public Builder adresa(URI adresa) {
            postavke = postavke.adresa(adresa);
            return this;
        }

        /** Najdulje čekanje na spajanje i na odgovor; zadano 30 sekundi. */
        public Builder timeout(Duration timeout) {
            postavke = postavke.timeout(timeout);
            return this;
        }

        /** Uključeno po defaultu: neispravna poruka se ne šalje nego baca {@link NeispravanZahtjevException}. */
        public Builder xsdValidacija(boolean ukljuceno) {
            postavke = postavke.xsdValidacija(ukljuceno);
            return this;
        }

        /** Umjesto zadanog konteksta, koji vjeruje FINA lancu ({@link FinaCertifikati#sslContext(Okruzenje)}). */
        public Builder sslContext(SSLContext ssl) {
            postavke = postavke.ssl(ssl);
            return this;
        }

        /** Povjerljivi CA za provjeru potpisa odgovora, umjesto ugrađenih FINA certifikata. */
        public Builder povjerljiviCertifikati(Set<TrustAnchor> sidra, List<X509Certificate> posrednici) {
            postavke = postavke.povjerljiviCertifikati(sidra, posrednici);
            return this;
        }

        /**
         * Sat za {@code datumVrijemeSlanja}, koji se uvijek piše po zagrebačkom vremenu, i za {@link Rezultat#primljeno()};
         * korisno u testovima.
         */
        public Builder sat(Clock sat) {
            postavke = postavke.sat(sat);
            return this;
        }

        /** Forward proxy za HTTP vezu prema Poreznoj, npr. {@code ProxySelector.of(new InetSocketAddress("proxy", 8080))}. */
        public Builder proxy(ProxySelector proxy) {
            postavke = postavke.proxy(proxy);
            return this;
        }

        /**
         * Koji certifikat smije potpisati odgovor, uz ispravan lanac do povjerljivih CA. Zadano: subjekt sadrži
         * {@value B2bClient#OIB_MINISTARSTVA_FINANCIJA}, jer svaki porezni obveznik ima vlastiti FINA certifikat
         * kojim bi inače mogao potpisati lažni odgovor.
         */
        public Builder potpisnikOdgovora(Predicate<X509Certificate> provjera) {
            postavke = postavke.potpisnikOdgovora(provjera);
            return this;
        }

        public B2bClient build() {
            return new B2bClient(this);
        }
    }
}
