package dev.mmatokovic.fiskalizacija.internal.b2b;

import dev.mmatokovic.fiskalizacija.b2b.DokumentPopust;
import dev.mmatokovic.fiskalizacija.b2b.DokumentTrosak;
import dev.mmatokovic.fiskalizacija.b2b.DokumentUkupanIznos;
import dev.mmatokovic.fiskalizacija.b2b.Izdavatelj;
import dev.mmatokovic.fiskalizacija.b2b.KlasifikacijaArtikla;
import dev.mmatokovic.fiskalizacija.b2b.Naplata;
import dev.mmatokovic.fiskalizacija.b2b.Odbijanje;
import dev.mmatokovic.fiskalizacija.b2b.PrethodniRacun;
import dev.mmatokovic.fiskalizacija.b2b.PrijenosSredstava;
import dev.mmatokovic.fiskalizacija.b2b.Primatelj;
import dev.mmatokovic.fiskalizacija.b2b.Racun;
import dev.mmatokovic.fiskalizacija.b2b.RaspodjelaPdv;
import dev.mmatokovic.fiskalizacija.b2b.Stavka;
import dev.mmatokovic.fiskalizacija.b2b.VrstaERacuna;
import dev.mmatokovic.fiskalizacija.internal.http.SoapPoziv;
import dev.mmatokovic.fiskalizacija.internal.xml.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Gradi SOAP poruke za Fiskalizaciju 2.0, bez potpisa. Redoslijed elemenata prati službene XSD sheme. */
public final class Zahtjevi {

    public static final String NS_EFIS = "http://www.porezna-uprava.gov.hr/fin/2024/types/eFiskalizacija";
    public static final String NS_EIZV = "http://www.porezna-uprava.gov.hr/fin/2024/types/eIzvjestavanje";

    private static final ZoneId ZAGREB = ZoneId.of("Europe/Zagreb");
    private static final DateTimeFormatter DATUM_VRIJEME_SLANJA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSS");

    private Zahtjevi() {
    }

    /** Nepotpisani zahtjev: dokument je cijela SOAP omotnica, {@code element} je tijelo koje se potpisuje. */
    public record Zahtjev(Document dokument, Element element, String id) {
    }

    public static Zahtjev evidentirajERacun(VrstaERacuna vrsta, List<Racun> racuni, Clock sat) {
        var z = novi(NS_EFIS, "efis", "EvidentirajERacunZahtjev");
        var zaglavlje = Xml.dodaj(z.element, NS_EFIS, "efis:Zaglavlje");
        tekst(zaglavlje, "efis:datumVrijemeSlanja", vrijemeSlanja(sat));
        tekst(zaglavlje, "efis:vrstaERacuna", vrsta.oznaka());
        racuni.forEach(r -> racun(z.element, "efis", r, true));
        return z;
    }

    public static Zahtjev evidentirajIsporukuZaKojuNijeIzdanERacun(List<Racun> racuni, Clock sat) {
        var z = novi(NS_EIZV, "eizv", "EvidentirajIsporukuZaKojuNijeIzdanERacunZahtjev");
        var zaglavlje = Xml.dodaj(z.element, NS_EIZV, "eizv:Zaglavlje");
        tekst(zaglavlje, "eizv:datumVrijemeSlanja", vrijemeSlanja(sat));
        tekst(zaglavlje, "eizv:vrstaRacuna", "IR");
        racuni.forEach(r -> racun(z.element, "eizv", r, false));
        return z;
    }

    public static Zahtjev evidentirajNaplatu(List<Naplata> naplate, Clock sat) {
        var z = novi(NS_EIZV, "eizv", "EvidentirajNaplatuZahtjev");
        var zaglavlje = Xml.dodaj(z.element, NS_EIZV, "eizv:Zaglavlje");
        tekst(zaglavlje, "eizv:datumVrijemeSlanja", vrijemeSlanja(sat));
        for (var n : naplate) {
            var el = Xml.dodaj(z.element, NS_EIZV, "eizv:Naplata");
            tekst(el, "eizv:brojDokumenta", n.brojDokumenta());
            tekst(el, "eizv:datumIzdavanja", n.datumIzdavanja());
            tekst(el, "eizv:oibPorezniBrojIzdavatelja", n.oibPorezniBrojIzdavatelja());
            tekst(el, "eizv:oibPorezniBrojPrimatelja", n.oibPorezniBrojPrimatelja());
            tekst(el, "eizv:datumNaplate", n.datumNaplate());
            tekst(el, "eizv:naplaceniIznos", Xml.iznos(n.naplaceniIznos()));
            tekst(el, "eizv:nacinPlacanja", n.nacinPlacanja());
        }
        return z;
    }

    public static Zahtjev evidentirajOdbijanje(List<Odbijanje> odbijanja, Clock sat) {
        var z = novi(NS_EIZV, "eizv", "EvidentirajOdbijanjeZahtjev");
        var zaglavlje = Xml.dodaj(z.element, NS_EIZV, "eizv:Zaglavlje");
        tekst(zaglavlje, "eizv:datumVrijemeSlanja", vrijemeSlanja(sat));
        for (var o : odbijanja) {
            var el = Xml.dodaj(z.element, NS_EIZV, "eizv:Odbijanje");
            tekst(el, "eizv:brojDokumenta", o.brojDokumenta());
            tekst(el, "eizv:datumIzdavanja", o.datumIzdavanja());
            tekst(el, "eizv:oibPorezniBrojIzdavatelja", o.oibPorezniBrojIzdavatelja());
            tekst(el, "eizv:oibPorezniBrojPrimatelja", o.oibPorezniBrojPrimatelja());
            tekst(el, "eizv:datumOdbijanja", o.datumOdbijanja());
            tekst(el, "eizv:vrstaRazlogaOdbijanja", o.vrstaRazlogaOdbijanja());
            tekst(el, "eizv:razlogOdbijanja", o.razlogOdbijanja());
        }
        return z;
    }

    private static void racun(Element roditelj, String p, Racun r, boolean eRacun) {
        var el = Xml.dodaj(roditelj, roditelj.getNamespaceURI(), p + (eRacun ? ":ERacun" : ":Racun"));
        tekst(el, p + ":brojDokumenta", r.brojDokumenta());
        tekst(el, p + ":datumIzdavanja", r.datumIzdavanja());
        tekst(el, p + ":vrstaDokumenta", r.vrstaDokumenta());
        tekst(el, p + (eRacun ? ":valutaERacuna" : ":valutaRacuna"), r.valuta());
        tekst(el, p + ":datumDospijecaPlacanja", r.datumDospijecaPlacanja());
        tekst(el, p + ":vrstaPoslovnogProcesa", r.vrstaPoslovnogProcesa());
        tekst(el, p + ":referencaNaUgovor", r.referencaNaUgovor());
        tekst(el, p + ":datumIsporuke", r.datumIsporuke());
        r.prethodniRacuni().forEach(pr -> prethodniRacun(el, p, pr, eRacun));
        if (r.izdavatelj() != null) {
            izdavatelj(el, p, r.izdavatelj());
        }
        if (r.primatelj() != null) {
            primatelj(el, p, r.primatelj());
        }
        r.prijenosiSredstava().forEach(ps -> prijenosSredstava(el, p, ps));
        if (r.ukupanIznos() != null) {
            ukupanIznos(el, p, r.ukupanIznos());
        }
        r.raspodjelaPdv().forEach(rp -> raspodjelaPdv(el, p, rp));
        r.popusti().forEach(dp -> popust(el, p, dp));
        r.troskovi().forEach(dt -> trosak(el, p, dt));
        r.stavke().forEach(st -> stavka(el, p, st, eRacun));
        tekst(el, p + ":indikatorKopije", r.indikatorKopije());
    }

    private static void prethodniRacun(Element racun, String p, PrethodniRacun pr, boolean eRacun) {
        var el = Xml.dodaj(racun, racun.getNamespaceURI(), p + (eRacun ? ":PrethodniERacun" : ":PrethodniRacun"));
        tekst(el, p + ":brojDokumenta", pr.brojDokumenta());
        tekst(el, p + ":datumIzdavanja", pr.datumIzdavanja());
    }

    private static void izdavatelj(Element racun, String p, Izdavatelj i) {
        var el = Xml.dodaj(racun, racun.getNamespaceURI(), p + ":Izdavatelj");
        tekst(el, p + ":ime", i.ime());
        tekst(el, p + ":oibPorezniBroj", i.oibPorezniBroj());
        tekst(el, p + ":oibOperatera", i.oibOperatera());
    }

    private static void primatelj(Element racun, String p, Primatelj pr) {
        var el = Xml.dodaj(racun, racun.getNamespaceURI(), p + ":Primatelj");
        tekst(el, p + ":ime", pr.ime());
        tekst(el, p + ":oibPorezniBroj", pr.oibPorezniBroj());
    }

    private static void prijenosSredstava(Element racun, String p, PrijenosSredstava ps) {
        var el = Xml.dodaj(racun, racun.getNamespaceURI(), p + ":PrijenosSredstava");
        tekst(el, p + ":identifikatorRacunaZaPlacanje", ps.identifikatorRacunaZaPlacanje());
        tekst(el, p + ":nazivRacunaZaPlacanje", ps.nazivRacunaZaPlacanje());
        tekst(el, p + ":identifikatorPruzateljaPlatnihUsluga", ps.identifikatorPruzateljaPlatnihUsluga());
    }

    private static void ukupanIznos(Element racun, String p, DokumentUkupanIznos u) {
        var el = Xml.dodaj(racun, racun.getNamespaceURI(), p + ":DokumentUkupanIznos");
        tekst(el, p + ":neto", Xml.iznos(u.neto()));
        tekst(el, p + ":popust", Xml.iznos(u.popust()));
        tekst(el, p + ":trosak", Xml.iznos(u.trosak()));
        tekst(el, p + ":iznosBezPdv", Xml.iznos(u.iznosBezPdv()));
        tekst(el, p + ":pdv", Xml.iznos(u.pdv()));
        tekst(el, p + ":iznosSPdv", Xml.iznos(u.iznosSPdv()));
        tekst(el, p + ":placeniIznos", Xml.iznos(u.placeniIznos()));
        tekst(el, p + ":iznosKojiDospijevaZaPlacanje", Xml.iznos(u.iznosKojiDospijevaZaPlacanje()));
    }

    private static void raspodjelaPdv(Element racun, String p, RaspodjelaPdv rp) {
        var el = Xml.dodaj(racun, racun.getNamespaceURI(), p + ":RaspodjelaPdv");
        tekst(el, p + ":kategorijaPdv", rp.kategorijaPdv());
        tekst(el, p + ":oporeziviIznos", Xml.iznos(rp.oporeziviIznos()));
        tekst(el, p + ":iznosPoreza", Xml.iznos(rp.iznosPoreza()));
        tekst(el, p + ":stopa", rp.stopa());
        tekst(el, p + ":razlogOslobodenja", rp.razlogOslobodenja());
        tekst(el, p + ":tekstRazlogaOslobodenja", rp.tekstRazlogaOslobodenja());
        tekst(el, p + ":hrOznakaKategorijaPdv", rp.hrOznakaKategorijaPdv());
    }

    private static void popust(Element racun, String p, DokumentPopust dp) {
        var el = Xml.dodaj(racun, racun.getNamespaceURI(), p + ":DokumentPopust");
        tekst(el, p + ":iznosPopust", Xml.iznos(dp.iznosPopust()));
        tekst(el, p + ":kategorijaPdv", dp.kategorijaPdv());
        tekst(el, p + ":stopaPdv", dp.stopaPdv());
        tekst(el, p + ":tekstRazlogaPopusta", dp.tekstRazlogaPopusta());
        tekst(el, p + ":razlogPopusta", dp.razlogPopusta());
    }

    private static void trosak(Element racun, String p, DokumentTrosak dt) {
        var el = Xml.dodaj(racun, racun.getNamespaceURI(), p + ":DokumentTrosak");
        tekst(el, p + ":iznosTrosak", Xml.iznos(dt.iznosTrosak()));
        tekst(el, p + ":kategorijaPdv", dt.kategorijaPdv());
        tekst(el, p + ":hrOznakaPorezneKategorije", dt.hrOznakaPorezneKategorije());
        tekst(el, p + ":stopaPdv", dt.stopaPdv());
        tekst(el, p + ":tekstRazlogaOslobodenjaPdv", dt.tekstRazlogaOslobodenjaPdv());
        tekst(el, p + ":razlogOslobodenjaPdv", dt.razlogOslobodenjaPdv());
    }

    private static void stavka(Element racun, String p, Stavka s, boolean eRacun) {
        var el = Xml.dodaj(racun, racun.getNamespaceURI(), p + (eRacun ? ":StavkaERacuna" : ":StavkaRacuna"));
        tekst(el, p + ":kolicina", s.kolicina());
        tekst(el, p + ":jedinicaMjere", s.jedinicaMjere());
        tekst(el, p + ":neto", Xml.iznos(s.neto()));
        tekst(el, p + ":artiklNetoCijena", s.artiklNetoCijena());
        tekst(el, p + ":artiklBrutoCijena", s.artiklBrutoCijena());
        tekst(el, p + ":artiklOsnovnaKolicina", s.artiklOsnovnaKolicina());
        tekst(el, p + ":artiklJedinicaMjereZaOsnovnuKolicinu", s.artiklJedinicaMjereZaOsnovnuKolicinu());
        tekst(el, p + ":artiklKategorijaPdv", s.artiklKategorijaPdv());
        tekst(el, p + ":artiklStopaPdv", s.artiklStopaPdv());
        tekst(el, p + ":artiklNaziv", s.artiklNaziv());
        tekst(el, p + ":artiklOpis", s.artiklOpis());
        tekst(el, p + ":artiklHrOznakaKategorijaPdv", s.artiklHrOznakaKategorijaPdv());
        for (KlasifikacijaArtikla k : s.klasifikacije()) {
            var kEl = Xml.dodaj(el, el.getNamespaceURI(), p + ":ArtiklIdentifikatorKlasifikacija");
            tekst(kEl, p + ":identifikatorKlasifikacije", k.identifikatorKlasifikacije());
            tekst(kEl, p + ":identifikatorSheme", k.identifikatorSheme());
            tekst(kEl, p + ":verzijaSheme", k.verzijaSheme());
        }
    }

    private static String vrijemeSlanja(Clock sat) {
        return LocalDateTime.ofInstant(sat.instant(), ZAGREB).format(DATUM_VRIJEME_SLANJA);
    }

    private static Zahtjev novi(String ns, String prefiks, String ime) {
        var body = SoapPoziv.novoTijelo();
        var doc = body.getOwnerDocument();
        var zahtjev = Xml.dodaj(body, ns, prefiks + ":" + ime);
        zahtjev.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:" + prefiks, ns);
        String id = UUID.randomUUID().toString();
        zahtjev.setAttributeNS(ns, prefiks + ":id", id);
        return new Zahtjev(doc, zahtjev, id);
    }


    private static void tekst(Element roditelj, String ime, Object vrijednost) {
        if (vrijednost == null) {
            return;
        }
        String s = vrijednost instanceof BigDecimal bd ? bd.toPlainString() : vrijednost.toString();
        Xml.dodaj(roditelj, roditelj.getNamespaceURI(), ime).setTextContent(s);
    }

}
