package dev.mmatokovic.fiskalizacija.internal.b2c;

import dev.mmatokovic.fiskalizacija.b2c.BrisanjeRadnogVremena;
import dev.mmatokovic.fiskalizacija.b2c.Iznimka;
import dev.mmatokovic.fiskalizacija.b2c.NacinPlacanja;
import dev.mmatokovic.fiskalizacija.b2c.Naknada;
import dev.mmatokovic.fiskalizacija.b2c.Napojnica;
import dev.mmatokovic.fiskalizacija.b2c.Porez;
import dev.mmatokovic.fiskalizacija.b2c.PorezOstalo;
import dev.mmatokovic.fiskalizacija.b2c.Poslovnica;
import dev.mmatokovic.fiskalizacija.b2c.Racun;
import dev.mmatokovic.fiskalizacija.b2c.RadnoVrijeme;
import dev.mmatokovic.fiskalizacija.b2c.Redovno;
import dev.mmatokovic.fiskalizacija.b2c.VrstaRadnogVremena;
import dev.mmatokovic.fiskalizacija.internal.http.SoapPoziv;
import dev.mmatokovic.fiskalizacija.internal.xml.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Gradi SOAP poruke za fiskalizaciju u krajnjoj potrošnji (shema f73), bez potpisa. Redoslijed elemenata prati
 * {@code FiskalizacijaSchema.xsd}.
 */
public final class B2cZahtjevi {

    public static final String NS_F73 = "http://www.apis-it.hr/fin/2012/types/f73";
    public static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final ZoneId ZAGREB = ZoneId.of("Europe/Zagreb");
    private static final DateTimeFormatter DATUM_VRIJEME = DateTimeFormatter.ofPattern("dd.MM.yyyy'T'HH:mm:ss");

    private B2cZahtjevi() {
    }

    /** Nepotpisani zahtjev: {@code id} je vrijednost atributa {@code Id}, {@code idPoruke} UUID iz zaglavlja. */
    public record Zahtjev(Document dokument, Element element, String id, String idPoruke) {
    }

    public static Zahtjev racun(Racun racun, String zastKod, Clock sat) {
        var z = novi("RacunZahtjev", sat);
        racun(z.element, racun, zastKod);
        return z;
    }

    public static Zahtjev provjera(Racun racun, String zastKod, Clock sat) {
        var z = novi("ProvjeraZahtjev", sat);
        racun(z.element, racun, zastKod);
        return z;
    }

    public static Zahtjev promijeniNacPlac(Racun racun, String zastKod, NacinPlacanja promijenjeni, Clock sat) {
        var z = novi("PromijeniNacPlacZahtjev", sat);
        tekst(racun(z.element, racun, zastKod), "PromijenjeniNacinPlac", promijenjeni);
        return z;
    }

    /** {@code oibPrimatelja} {@code null} briše OIB primatelja s računa. */
    public static Zahtjev promijeniPodatkeRacuna(Racun racun, String zastKod, NacinPlacanja nacinPlac, String oibPrimatelja,
            Clock sat) {
        var z = novi("PromijeniPodatkeRacunaZahtjev", sat);
        var el = racun(z.element, racun, zastKod);
        tekst(el, "PromijenjeniNacinPlac", nacinPlac);
        tekst(el, "PromijenjeniOibPrimateljaRacuna", oibPrimatelja == null ? "" : oibPrimatelja);
        return z;
    }

    public static Zahtjev napojnica(Racun racun, String zastKod, Napojnica napojnica, Clock sat) {
        var z = novi("NapojnicaZahtjev", sat);
        var el = Xml.dodaj(racun(z.element, racun, zastKod), NS_F73, "tns:Napojnica");
        tekst(el, "iznosNapojnice", Xml.iznos(napojnica.iznosNapojnice()));
        tekst(el, "nacinPlacanjaNapojnice", napojnica.nacinPlacanjaNapojnice());
        return z;
    }

    public static Zahtjev prijaviRadnoVrijeme(String oib, String oznPosPr, RadnoVrijeme radnoVrijeme, String oibOper, Clock sat) {
        var z = novi("PrijaviRadnoVrijemeZahtjev", sat);
        var rv = Xml.dodaj(poslovniProstor(z.element, oib, oznPosPr), NS_F73, "tns:RadnoVrijeme");
        radnoVrijeme.redovno().forEach(r -> redovno(Xml.dodaj(rv, NS_F73, "tns:Redovno"), r));
        radnoVrijeme.iznimke().forEach(i -> iznimka(Xml.dodaj(rv, NS_F73, "tns:Iznimke"), i));
        tekst(z.element, "OibOper", oibOper);
        return z;
    }

    public static Zahtjev obrisiRadnoVrijeme(String oib, String oznPosPr, BrisanjeRadnogVremena brisanje, String oibOper,
            Clock sat) {
        var z = novi("ObrisiRadnoVrijemeZahtjev", sat);
        var el = Xml.dodaj(poslovniProstor(z.element, oib, oznPosPr), NS_F73, "tns:BrisanjeRadnogVremena");
        brisanje.redovno().forEach(d -> tekst(Xml.dodaj(el, NS_F73, "tns:Redovno"), "DatumOd", d.format(DATUM)));
        brisanje.iznimke().forEach(d -> tekst(Xml.dodaj(el, NS_F73, "tns:Iznimke"), "Datum", d.format(DATUM)));
        tekst(z.element, "OibOper", oibOper);
        return z;
    }

    public static Zahtjev dohvatiRadnoVrijeme(String oib, String oznPosPr, VrstaRadnogVremena vrsta, String oibOper, Clock sat) {
        var z = novi("DohvatiRadnoVrijemeZahtjev", sat);
        tekst(z.element, "Oib", oib);
        tekst(z.element, "OznPosPr", oznPosPr);
        tekst(z.element, "VrstaRadnogVremena", vrsta);
        tekst(z.element, "OibOper", oibOper);
        return z;
    }

    public static Zahtjev prijaviRadnoVrijemeZaPoslovnice(String oib, List<Poslovnica> poslovnice, String oibOper, Clock sat) {
        var z = novi("PrijaviRadnoVrijemeZaPoslovniceZahtjev", sat);
        tekst(z.element, "Oib", oib);
        var prostori = Xml.dodaj(z.element, NS_F73, "tns:PoslovniProstori");
        for (var p : poslovnice) {
            var el = Xml.dodaj(prostori, NS_F73, "tns:Poslovnica");
            tekst(el, "OznPosPr", p.oznPosPr());
            if (p.iznimka() != null) {
                iznimka(Xml.dodaj(el, NS_F73, "tns:Iznimka"), p.iznimka());
            } else {
                redovno(Xml.dodaj(el, NS_F73, "tns:Redovno"), p.redovno());
            }
        }
        tekst(z.element, "OibOper", oibOper);
        return z;
    }

    public static Document echo(String poruka) {
        var body = SoapPoziv.novoTijelo();
        var doc = body.getOwnerDocument();
        var echo = Xml.dodaj(body, NS_F73, "tns:EchoRequest");
        echo.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:tns", NS_F73);
        echo.setTextContent(poruka);
        return doc;
    }

    private static Element racun(Element zahtjev, Racun r, String zastKod) {
        var el = Xml.dodaj(zahtjev, NS_F73, "tns:Racun");
        tekst(el, "Oib", r.oib());
        tekst(el, "USustPdv", r.uSustPdv());
        tekst(el, "DatVrijeme", r.datVrijeme().format(DATUM_VRIJEME));
        tekst(el, "OznSlijed", r.oznSlijed());
        var brRac = Xml.dodaj(el, NS_F73, "tns:BrRac");
        tekst(brRac, "BrOznRac", r.brRac().brOznRac());
        tekst(brRac, "OznPosPr", r.brRac().oznPosPr());
        tekst(brRac, "OznNapUr", r.brRac().oznNapUr());
        porezi(el, "Pdv", r.pdv());
        porezi(el, "Pnp", r.pnp());
        if (!r.ostaliPor().isEmpty()) {
            var ostali = Xml.dodaj(el, NS_F73, "tns:OstaliPor");
            for (PorezOstalo p : r.ostaliPor()) {
                var porez = Xml.dodaj(ostali, NS_F73, "tns:Porez");
                tekst(porez, "Naziv", p.naziv());
                tekst(porez, "Stopa", Xml.iznos(p.stopa()));
                tekst(porez, "Osnovica", Xml.iznos(p.osnovica()));
                tekst(porez, "Iznos", Xml.iznos(p.iznos()));
            }
        }
        tekst(el, "IznosOslobPdv", Xml.iznos(r.iznosOslobPdv()));
        tekst(el, "IznosMarza", Xml.iznos(r.iznosMarza()));
        tekst(el, "IznosNePodlOpor", Xml.iznos(r.iznosNePodlOpor()));
        if (!r.naknade().isEmpty()) {
            var naknade = Xml.dodaj(el, NS_F73, "tns:Naknade");
            for (Naknada n : r.naknade()) {
                var naknada = Xml.dodaj(naknade, NS_F73, "tns:Naknada");
                tekst(naknada, "NazivN", n.nazivN());
                tekst(naknada, "IznosN", Xml.iznos(n.iznosN()));
            }
        }
        tekst(el, "IznosUkupno", Xml.iznos(r.iznosUkupno()));
        tekst(el, "NacinPlac", r.nacinPlac());
        tekst(el, "OibOper", r.oibOper());
        tekst(el, "ZastKod", zastKod);
        tekst(el, "NakDost", r.nakDost());
        tekst(el, "ParagonBrRac", r.paragonBrRac());
        tekst(el, "SpecNamj", r.specNamj());
        tekst(el, "OibPrimateljaRacuna", r.oibPrimateljaRacuna());
        return el;
    }

    private static void porezi(Element racun, String ime, List<Porez> porezi) {
        if (porezi.isEmpty()) {
            return;
        }
        var el = Xml.dodaj(racun, NS_F73, "tns:" + ime);
        for (Porez p : porezi) {
            var porez = Xml.dodaj(el, NS_F73, "tns:Porez");
            tekst(porez, "Stopa", Xml.iznos(p.stopa()));
            tekst(porez, "Osnovica", Xml.iznos(p.osnovica()));
            tekst(porez, "Iznos", Xml.iznos(p.iznos()));
        }
    }

    private static Element poslovniProstor(Element zahtjev, String oib, String oznPosPr) {
        var el = Xml.dodaj(zahtjev, NS_F73, "tns:PoslovniProstor");
        tekst(el, "Oib", oib);
        tekst(el, "OznPosPr", oznPosPr);
        return el;
    }

    private static void redovno(Element el, Redovno r) {
        tekst(el, "DatumOd", r.datumOd().format(DATUM));
        tekst(el, "DatumDo", datum(r.datumDo()));
        tekst(el, "Napomena", r.napomena());
        if (r.poDogovoru()) {
            tekst(Xml.dodaj(el, NS_F73, "tns:PoDogovoru"), "RedovnoPoDogovoru", "DA");
        }
        for (var j : r.jednokratno()) {
            var dan = Xml.dodaj(el, NS_F73, "tns:Jednokratno");
            tekst(dan, "DanUTjednu", j.danUTjednu());
            vrijeme(dan, j.radnoVrijemeOd(), j.radnoVrijemeDo());
        }
        for (var d : r.dvokratno()) {
            var dan = Xml.dodaj(el, NS_F73, "tns:Dvokratno");
            tekst(dan, "DanUTjednu", d.danUTjednu());
            tekst(dan, "DioDvokratnog", d.dioDvokratnog());
            vrijeme(dan, d.radnoVrijemeOd(), d.radnoVrijemeDo());
        }
        for (var p : r.parniNeparni()) {
            var dan = Xml.dodaj(el, NS_F73, "tns:ParniNeparni");
            tekst(dan, "DanUTjednu", p.danUTjednu());
            tekst(dan, "ParNepar", p.parNepar());
            vrijeme(dan, p.radnoVrijemeOd(), p.radnoVrijemeDo());
        }
    }

    private static void iznimka(Element el, Iznimka i) {
        tekst(el, "Datum", i.datum().format(DATUM));
        if (i.dvokratno()) {
            var prvi = Xml.dodaj(el, NS_F73, "tns:Dvokratno");
            tekst(prvi, "DioDvokratnog", 1);
            vrijeme(prvi, i.radnoVrijemeOd(), i.radnoVrijemeDo());
            var drugi = Xml.dodaj(el, NS_F73, "tns:Dvokratno");
            tekst(drugi, "DioDvokratnog", 2);
            vrijeme(drugi, i.drugiDioOd(), i.drugiDioDo());
        } else {
            vrijeme(Xml.dodaj(el, NS_F73, "tns:Jednokratno"), i.radnoVrijemeOd(), i.radnoVrijemeDo());
        }
    }

    private static void vrijeme(Element el, String od, String doVremena) {
        tekst(el, "RadnoVrijemeOd", od);
        tekst(el, "RadnoVrijemeDo", doVremena);
    }

    private static String datum(LocalDate d) {
        return d == null ? null : d.format(DATUM);
    }

    private static Zahtjev novi(String ime, Clock sat) {
        var body = SoapPoziv.novoTijelo();
        var doc = body.getOwnerDocument();
        var zahtjev = Xml.dodaj(body, NS_F73, "tns:" + ime);
        zahtjev.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:tns", NS_F73);
        zahtjev.setAttribute("Id", ime);
        String idPoruke = UUID.randomUUID().toString();
        var zaglavlje = Xml.dodaj(zahtjev, NS_F73, "tns:Zaglavlje");
        tekst(zaglavlje, "IdPoruke", idPoruke);
        tekst(zaglavlje, "DatumVrijeme", LocalDateTime.ofInstant(sat.instant(), ZAGREB).format(DATUM_VRIJEME));
        return new Zahtjev(doc, zahtjev, ime, idPoruke);
    }


    private static void tekst(Element roditelj, String ime, Object vrijednost) {
        if (vrijednost == null) {
            return;
        }
        String s = vrijednost instanceof BigDecimal bd ? bd.toPlainString() : vrijednost.toString();
        Xml.dodaj(roditelj, NS_F73, "tns:" + ime).setTextContent(s);
    }
}
