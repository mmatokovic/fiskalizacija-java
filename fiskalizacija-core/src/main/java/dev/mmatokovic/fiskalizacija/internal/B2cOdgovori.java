package dev.mmatokovic.fiskalizacija.internal;

import dev.mmatokovic.fiskalizacija.b2c.Dvokratno;
import dev.mmatokovic.fiskalizacija.b2c.Greska;
import dev.mmatokovic.fiskalizacija.b2c.Iznimka;
import dev.mmatokovic.fiskalizacija.b2c.Jednokratno;
import dev.mmatokovic.fiskalizacija.b2c.ParNepar;
import dev.mmatokovic.fiskalizacija.b2c.ParniNeparni;
import dev.mmatokovic.fiskalizacija.b2c.PorukaOdgovora;
import dev.mmatokovic.fiskalizacija.b2c.PoslovnicaOdgovor;
import dev.mmatokovic.fiskalizacija.b2c.RadnoVrijeme;
import dev.mmatokovic.fiskalizacija.b2c.Redovno;
import org.w3c.dom.Element;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Čita odgovore fiskalizacije u krajnjoj potrošnji (shema f73). Elementi se traže po lokalnom imenu. */
public final class B2cOdgovori {

    private B2cOdgovori() {
    }

    public static List<Greska> greske(Element roditelj) {
        var greske = new ArrayList<Greska>();
        var el = dijete(roditelj, "Greske");
        if (el != null) {
            for (var g : djeca(el, "Greska")) {
                greske.add(new Greska(tekst(g, "SifraGreske"), tekst(g, "PorukaGreske")));
            }
        }
        return greske;
    }

    public static PorukaOdgovora poruka(Element roditelj) {
        var el = dijete(roditelj, "PorukaOdgovora");
        return el == null ? null : new PorukaOdgovora(tekst(el, "SifraPoruke"), tekst(el, "Poruka"));
    }

    public static List<PoslovnicaOdgovor> poslovnice(Element odgovor) {
        var el = dijete(odgovor, "PoslovniProstoriOdgovor");
        if (el == null) {
            return List.of();
        }
        return djeca(el, "PoslovnicaOdgovor").stream()
                .map(p -> new PoslovnicaOdgovor(tekst(p, "OznPosPr"), poruka(p), greske(p)))
                .toList();
    }

    /** {@code null} ako odgovor nema {@code PoslovniProstor/RadnoVrijeme}. */
    public static RadnoVrijeme radnoVrijeme(Element odgovor) {
        var prostor = dijete(odgovor, "PoslovniProstor");
        var rv = prostor == null ? null : dijete(prostor, "RadnoVrijeme");
        if (rv == null) {
            return null;
        }
        var redovno = djeca(rv, "Redovno").stream().map(B2cOdgovori::redovno).toList();
        var iznimke = djeca(rv, "Iznimke").stream().map(B2cOdgovori::iznimka).toList();
        return new RadnoVrijeme(redovno, iznimke);
    }

    private static Redovno redovno(Element r) {
        var jednokratno = djeca(r, "Jednokratno").stream()
                .map(j -> new Jednokratno(broj(j, "DanUTjednu"), tekst(j, "RadnoVrijemeOd"), tekst(j, "RadnoVrijemeDo")))
                .toList();
        var dvokratno = djeca(r, "Dvokratno").stream()
                .map(d -> new Dvokratno(broj(d, "DanUTjednu"), broj(d, "DioDvokratnog"), tekst(d, "RadnoVrijemeOd"),
                        tekst(d, "RadnoVrijemeDo")))
                .toList();
        var parniNeparni = djeca(r, "ParniNeparni").stream()
                .map(p -> new ParniNeparni(broj(p, "DanUTjednu"), ParNepar.valueOf(tekst(p, "ParNepar")),
                        tekst(p, "RadnoVrijemeOd"), tekst(p, "RadnoVrijemeDo")))
                .toList();
        return new Redovno(datum(r, "DatumOd"), datum(r, "DatumDo"), tekst(r, "Napomena"), dijete(r, "PoDogovoru") != null,
                jednokratno, dvokratno, parniNeparni);
    }

    private static Iznimka iznimka(Element i) {
        var jednokratno = dijete(i, "Jednokratno");
        if (jednokratno != null) {
            return Iznimka.jednokratno(datum(i, "Datum"), tekst(jednokratno, "RadnoVrijemeOd"), tekst(jednokratno, "RadnoVrijemeDo"));
        }
        var dijelovi = new ArrayList<>(djeca(i, "Dvokratno"));
        if (dijelovi.size() != 2) {
            throw new IllegalArgumentException("Dvokratna iznimka mora imati dva dijela, a ima " + dijelovi.size());
        }
        dijelovi.sort(Comparator.comparingInt(d -> broj(d, "DioDvokratnog")));
        return Iznimka.dvokratno(datum(i, "Datum"), tekst(dijelovi.get(0), "RadnoVrijemeOd"), tekst(dijelovi.get(0), "RadnoVrijemeDo"),
                tekst(dijelovi.get(1), "RadnoVrijemeOd"), tekst(dijelovi.get(1), "RadnoVrijemeDo"));
    }

    public static Element dijete(Element roditelj, String lokalnoIme) {
        for (var n = roditelj.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && lokalnoIme.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    public static String tekst(Element roditelj, String lokalnoIme) {
        var e = dijete(roditelj, lokalnoIme);
        return e == null ? null : e.getTextContent().trim();
    }

    private static List<Element> djeca(Element roditelj, String lokalnoIme) {
        var djeca = new ArrayList<Element>();
        for (var n = roditelj.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && lokalnoIme.equals(e.getLocalName())) {
                djeca.add(e);
            }
        }
        return djeca;
    }

    private static int broj(Element roditelj, String lokalnoIme) {
        return Integer.parseInt(tekst(roditelj, lokalnoIme));
    }

    private static LocalDate datum(Element roditelj, String lokalnoIme) {
        String s = tekst(roditelj, lokalnoIme);
        return s == null ? null : LocalDate.parse(s, B2cZahtjevi.DATUM);
    }
}
