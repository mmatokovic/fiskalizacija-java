package dev.mmatokovic.fiskalizacija2.internal;

import dev.mmatokovic.fiskalizacija2.model.DokumentPopust;
import dev.mmatokovic.fiskalizacija2.model.DokumentTrosak;
import dev.mmatokovic.fiskalizacija2.model.KlasifikacijaArtikla;
import dev.mmatokovic.fiskalizacija2.model.Naplata;
import dev.mmatokovic.fiskalizacija2.model.Odbijanje;
import dev.mmatokovic.fiskalizacija2.model.PrethodniRacun;
import dev.mmatokovic.fiskalizacija2.model.PrijenosSredstava;
import dev.mmatokovic.fiskalizacija2.model.Racun;
import dev.mmatokovic.fiskalizacija2.model.RaspodjelaPdv;
import dev.mmatokovic.fiskalizacija2.model.Stavka;
import dev.mmatokovic.fiskalizacija2.model.VrstaERacuna;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Gradi SOAP poruke za Fiskalizaciju 2.0, bez potpisa. Redoslijed elemenata prati službene XSD sheme. */
public final class Zahtjevi {

    public static final String NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String NS_EFIS = "http://www.porezna-uprava.gov.hr/fin/2024/types/eFiskalizacija";
    public static final String NS_EIZV = "http://www.porezna-uprava.gov.hr/fin/2024/types/eIzvjestavanje";

    private static final DateTimeFormatter DATUM_VRIJEME_SLANJA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSS");

    private Zahtjevi() {
    }

    /** Nepotpisani zahtjev: dokument je cijela SOAP omotnica, {@code element} je tijelo koje se potpisuje. */
    public record Zahtjev(Document dokument, Element element, String id) {
    }

    public static Zahtjev evidentirajERacun(VrstaERacuna vrsta, List<Racun> racuni, Clock sat) {
        var z = novi(NS_EFIS, "efis", "EvidentirajERacunZahtjev");
        var zaglavlje = dodaj(z.element, NS_EFIS, "efis:Zaglavlje");
        tekst(zaglavlje, "efis:datumVrijemeSlanja", LocalDateTime.now(sat).format(DATUM_VRIJEME_SLANJA));
        tekst(zaglavlje, "efis:vrstaERacuna", vrsta.oznaka());
        racuni.forEach(r -> racun(z.element, "efis", r, true));
        return z;
    }

    public static Zahtjev evidentirajIsporukuZaKojuNijeIzdanERacun(List<Racun> racuni, Clock sat) {
        var z = novi(NS_EIZV, "eizv", "EvidentirajIsporukuZaKojuNijeIzdanERacunZahtjev");
        var zaglavlje = dodaj(z.element, NS_EIZV, "eizv:Zaglavlje");
        tekst(zaglavlje, "eizv:datumVrijemeSlanja", LocalDateTime.now(sat).format(DATUM_VRIJEME_SLANJA));
        tekst(zaglavlje, "eizv:vrstaRacuna", "IR");
        racuni.forEach(r -> racun(z.element, "eizv", r, false));
        return z;
    }

    public static Zahtjev evidentirajNaplatu(List<Naplata> naplate, Clock sat) {
        var z = novi(NS_EIZV, "eizv", "EvidentirajNaplatuZahtjev");
        var zaglavlje = dodaj(z.element, NS_EIZV, "eizv:Zaglavlje");
        tekst(zaglavlje, "eizv:datumVrijemeSlanja", LocalDateTime.now(sat).format(DATUM_VRIJEME_SLANJA));
        for (var n : naplate) {
            var el = dodaj(z.element, NS_EIZV, "eizv:Naplata");
            tekst(el, "eizv:brojDokumenta", n.brojDokumenta());
            tekst(el, "eizv:datumIzdavanja", n.datumIzdavanja());
            tekst(el, "eizv:oibPorezniBrojIzdavatelja", n.oibPorezniBrojIzdavatelja());
            tekst(el, "eizv:oibPorezniBrojPrimatelja", n.oibPorezniBrojPrimatelja());
            tekst(el, "eizv:datumNaplate", n.datumNaplate());
            tekst(el, "eizv:naplaceniIznos", iznos(n.naplaceniIznos()));
            tekst(el, "eizv:nacinPlacanja", n.nacinPlacanja());
        }
        return z;
    }

    public static Zahtjev evidentirajOdbijanje(List<Odbijanje> odbijanja, Clock sat) {
        var z = novi(NS_EIZV, "eizv", "EvidentirajOdbijanjeZahtjev");
        var zaglavlje = dodaj(z.element, NS_EIZV, "eizv:Zaglavlje");
        tekst(zaglavlje, "eizv:datumVrijemeSlanja", LocalDateTime.now(sat).format(DATUM_VRIJEME_SLANJA));
        for (var o : odbijanja) {
            var el = dodaj(z.element, NS_EIZV, "eizv:Odbijanje");
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
        String ns = roditelj.getNamespaceURI();
        var el = dodaj(roditelj, ns, p + (eRacun ? ":ERacun" : ":Racun"));
        tekst(el, p + ":brojDokumenta", r.brojDokumenta());
        tekst(el, p + ":datumIzdavanja", r.datumIzdavanja());
        tekst(el, p + ":vrstaDokumenta", r.vrstaDokumenta());
        tekst(el, p + (eRacun ? ":valutaERacuna" : ":valutaRacuna"), r.valuta());
        tekst(el, p + ":datumDospijecaPlacanja", r.datumDospijecaPlacanja());
        tekst(el, p + ":vrstaPoslovnogProcesa", r.vrstaPoslovnogProcesa());
        tekst(el, p + ":referencaNaUgovor", r.referencaNaUgovor());
        tekst(el, p + ":datumIsporuke", r.datumIsporuke());
        for (PrethodniRacun pr : r.prethodniRacuni()) {
            var prEl = dodaj(el, ns, p + (eRacun ? ":PrethodniERacun" : ":PrethodniRacun"));
            tekst(prEl, p + ":brojDokumenta", pr.brojDokumenta());
            tekst(prEl, p + ":datumIzdavanja", pr.datumIzdavanja());
        }

        if (r.izdavatelj() != null) {
            var izdavatelj = dodaj(el, ns, p + ":Izdavatelj");
            tekst(izdavatelj, p + ":ime", r.izdavatelj().ime());
            tekst(izdavatelj, p + ":oibPorezniBroj", r.izdavatelj().oibPorezniBroj());
            tekst(izdavatelj, p + ":oibOperatera", r.izdavatelj().oibOperatera());
        }

        if (r.primatelj() != null) {
            var primatelj = dodaj(el, ns, p + ":Primatelj");
            tekst(primatelj, p + ":ime", r.primatelj().ime());
            tekst(primatelj, p + ":oibPorezniBroj", r.primatelj().oibPorezniBroj());
        }

        for (PrijenosSredstava ps : r.prijenosiSredstava()) {
            var psEl = dodaj(el, ns, p + ":PrijenosSredstava");
            tekst(psEl, p + ":identifikatorRacunaZaPlacanje", ps.identifikatorRacunaZaPlacanje());
            tekst(psEl, p + ":nazivRacunaZaPlacanje", ps.nazivRacunaZaPlacanje());
            tekst(psEl, p + ":identifikatorPruzateljaPlatnihUsluga", ps.identifikatorPruzateljaPlatnihUsluga());
        }

        var u = r.ukupanIznos();
        if (u != null) {
            var ukupno = dodaj(el, ns, p + ":DokumentUkupanIznos");
            tekst(ukupno, p + ":neto", iznos(u.neto()));
            tekst(ukupno, p + ":popust", iznos(u.popust()));
            tekst(ukupno, p + ":trosak", iznos(u.trosak()));
            tekst(ukupno, p + ":iznosBezPdv", iznos(u.iznosBezPdv()));
            tekst(ukupno, p + ":pdv", iznos(u.pdv()));
            tekst(ukupno, p + ":iznosSPdv", iznos(u.iznosSPdv()));
            tekst(ukupno, p + ":placeniIznos", iznos(u.placeniIznos()));
            tekst(ukupno, p + ":iznosKojiDospijevaZaPlacanje", iznos(u.iznosKojiDospijevaZaPlacanje()));
        }

        for (RaspodjelaPdv rp : r.raspodjelaPdv()) {
            var rpEl = dodaj(el, ns, p + ":RaspodjelaPdv");
            tekst(rpEl, p + ":kategorijaPdv", rp.kategorijaPdv());
            tekst(rpEl, p + ":oporeziviIznos", iznos(rp.oporeziviIznos()));
            tekst(rpEl, p + ":iznosPoreza", iznos(rp.iznosPoreza()));
            tekst(rpEl, p + ":stopa", rp.stopa());
            tekst(rpEl, p + ":razlogOslobodenja", rp.razlogOslobodenja());
            tekst(rpEl, p + ":tekstRazlogaOslobodenja", rp.tekstRazlogaOslobodenja());
            tekst(rpEl, p + ":hrOznakaKategorijaPdv", rp.hrOznakaKategorijaPdv());
        }

        for (DokumentPopust dp : r.popusti()) {
            var dpEl = dodaj(el, ns, p + ":DokumentPopust");
            tekst(dpEl, p + ":iznosPopust", iznos(dp.iznosPopust()));
            tekst(dpEl, p + ":kategorijaPdv", dp.kategorijaPdv());
            tekst(dpEl, p + ":stopaPdv", dp.stopaPdv());
            tekst(dpEl, p + ":tekstRazlogaPopusta", dp.tekstRazlogaPopusta());
            tekst(dpEl, p + ":razlogPopusta", dp.razlogPopusta());
        }

        for (DokumentTrosak dt : r.troskovi()) {
            var dtEl = dodaj(el, ns, p + ":DokumentTrosak");
            tekst(dtEl, p + ":iznosTrosak", iznos(dt.iznosTrosak()));
            tekst(dtEl, p + ":kategorijaPdv", dt.kategorijaPdv());
            tekst(dtEl, p + ":hrOznakaPorezneKategorije", dt.hrOznakaPorezneKategorije());
            tekst(dtEl, p + ":stopaPdv", dt.stopaPdv());
            tekst(dtEl, p + ":tekstRazlogaOslobodenjaPdv", dt.tekstRazlogaOslobodenjaPdv());
            tekst(dtEl, p + ":razlogOslobodenjaPdv", dt.razlogOslobodenjaPdv());
        }

        for (Stavka s : r.stavke()) {
            var sEl = dodaj(el, ns, p + (eRacun ? ":StavkaERacuna" : ":StavkaRacuna"));
            tekst(sEl, p + ":kolicina", s.kolicina());
            tekst(sEl, p + ":jedinicaMjere", s.jedinicaMjere());
            tekst(sEl, p + ":neto", iznos(s.neto()));
            tekst(sEl, p + ":artiklNetoCijena", s.artiklNetoCijena());
            tekst(sEl, p + ":artiklBrutoCijena", s.artiklBrutoCijena());
            tekst(sEl, p + ":artiklOsnovnaKolicina", s.artiklOsnovnaKolicina());
            tekst(sEl, p + ":artiklJedinicaMjereZaOsnovnuKolicinu", s.artiklJedinicaMjereZaOsnovnuKolicinu());
            tekst(sEl, p + ":artiklKategorijaPdv", s.artiklKategorijaPdv());
            tekst(sEl, p + ":artiklStopaPdv", s.artiklStopaPdv());
            tekst(sEl, p + ":artiklNaziv", s.artiklNaziv());
            tekst(sEl, p + ":artiklOpis", s.artiklOpis());
            tekst(sEl, p + ":artiklHrOznakaKategorijaPdv", s.artiklHrOznakaKategorijaPdv());
            for (KlasifikacijaArtikla k : s.klasifikacije()) {
                var kEl = dodaj(sEl, ns, p + ":ArtiklIdentifikatorKlasifikacija");
                tekst(kEl, p + ":identifikatorKlasifikacije", k.identifikatorKlasifikacije());
                tekst(kEl, p + ":identifikatorSheme", k.identifikatorSheme());
                tekst(kEl, p + ":verzijaSheme", k.verzijaSheme());
            }
        }

        tekst(el, p + ":indikatorKopije", r.indikatorKopije());
    }

    private static Zahtjev novi(String ns, String prefiks, String ime) {
        Document doc;
        try {
            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            doc = dbf.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        var envelope = doc.createElementNS(NS_SOAP, "soapenv:Envelope");
        envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:soapenv", NS_SOAP);
        doc.appendChild(envelope);
        var body = dodaj(envelope, NS_SOAP, "soapenv:Body");
        var zahtjev = dodaj(body, ns, prefiks + ":" + ime);
        zahtjev.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:" + prefiks, ns);
        String id = UUID.randomUUID().toString();
        zahtjev.setAttributeNS(ns, prefiks + ":id", id);
        return new Zahtjev(doc, zahtjev, id);
    }

    private static Element dodaj(Element roditelj, String ns, String ime) {
        var el = roditelj.getOwnerDocument().createElementNS(ns, ime);
        roditelj.appendChild(el);
        return el;
    }

    private static void tekst(Element roditelj, String ime, Object vrijednost) {
        if (vrijednost == null) {
            return;
        }
        String s = vrijednost instanceof BigDecimal bd ? bd.toPlainString() : vrijednost.toString();
        dodaj(roditelj, roditelj.getNamespaceURI(), ime).setTextContent(s);
    }

    private static BigDecimal iznos(BigDecimal v) {
        if (v == null) {
            return null;
        }
        try {
            return v.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Iznos smije imati najviše dvije decimale: " + v.toPlainString(), e);
        }
    }
}
