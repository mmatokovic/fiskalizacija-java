package dev.mmatokovic.fiskalizacija.internal.b2c;

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
import dev.mmatokovic.fiskalizacija.internal.xml.Xml;
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
        var el = Xml.dijete(roditelj, "Greske");
        if (el != null) {
            for (var g : Xml.djeca(el, "Greska")) {
                greske.add(new Greska(Xml.tekst(g, "SifraGreske"), Xml.tekst(g, "PorukaGreske")));
            }
        }
        return greske;
    }

    public static PorukaOdgovora poruka(Element roditelj) {
        var el = Xml.dijete(roditelj, "PorukaOdgovora");
        return el == null ? null : new PorukaOdgovora(Xml.tekst(el, "SifraPoruke"), Xml.tekst(el, "Poruka"));
    }

    public static List<PoslovnicaOdgovor> poslovnice(Element odgovor) {
        var el = Xml.dijete(odgovor, "PoslovniProstoriOdgovor");
        if (el == null) {
            return List.of();
        }
        return Xml.djeca(el, "PoslovnicaOdgovor").stream()
                .map(p -> new PoslovnicaOdgovor(Xml.tekst(p, "OznPosPr"), poruka(p), greske(p)))
                .toList();
    }

    /** {@code null} ako odgovor nema {@code PoslovniProstor/RadnoVrijeme}. */
    public static RadnoVrijeme radnoVrijeme(Element odgovor) {
        var prostor = Xml.dijete(odgovor, "PoslovniProstor");
        var rv = prostor == null ? null : Xml.dijete(prostor, "RadnoVrijeme");
        if (rv == null) {
            return null;
        }
        var redovno = Xml.djeca(rv, "Redovno").stream().map(B2cOdgovori::redovno).toList();
        var iznimke = Xml.djeca(rv, "Iznimke").stream().map(B2cOdgovori::iznimka).toList();
        return new RadnoVrijeme(redovno, iznimke);
    }

    private static Redovno redovno(Element r) {
        var jednokratno = Xml.djeca(r, "Jednokratno").stream()
                .map(j -> new Jednokratno(broj(j, "DanUTjednu"), Xml.tekst(j, "RadnoVrijemeOd"), Xml.tekst(j, "RadnoVrijemeDo")))
                .toList();
        var dvokratno = Xml.djeca(r, "Dvokratno").stream()
                .map(d -> new Dvokratno(broj(d, "DanUTjednu"), broj(d, "DioDvokratnog"), Xml.tekst(d, "RadnoVrijemeOd"),
                        Xml.tekst(d, "RadnoVrijemeDo")))
                .toList();
        var parniNeparni = Xml.djeca(r, "ParniNeparni").stream()
                .map(p -> new ParniNeparni(broj(p, "DanUTjednu"), ParNepar.valueOf(Xml.tekst(p, "ParNepar")),
                        Xml.tekst(p, "RadnoVrijemeOd"), Xml.tekst(p, "RadnoVrijemeDo")))
                .toList();
        return new Redovno(datum(r, "DatumOd"), datum(r, "DatumDo"), Xml.tekst(r, "Napomena"), Xml.dijete(r, "PoDogovoru") != null,
                jednokratno, dvokratno, parniNeparni);
    }

    private static Iznimka iznimka(Element i) {
        var jednokratno = Xml.dijete(i, "Jednokratno");
        if (jednokratno != null) {
            return Iznimka.jednokratno(datum(i, "Datum"), Xml.tekst(jednokratno, "RadnoVrijemeOd"), Xml.tekst(jednokratno, "RadnoVrijemeDo"));
        }
        var dijelovi = new ArrayList<>(Xml.djeca(i, "Dvokratno"));
        if (dijelovi.size() != 2) {
            throw new IllegalArgumentException("Dvokratna iznimka mora imati dva dijela, a ima " + dijelovi.size());
        }
        dijelovi.sort(Comparator.comparingInt(d -> broj(d, "DioDvokratnog")));
        return Iznimka.dvokratno(datum(i, "Datum"), Xml.tekst(dijelovi.get(0), "RadnoVrijemeOd"), Xml.tekst(dijelovi.get(0), "RadnoVrijemeDo"),
                Xml.tekst(dijelovi.get(1), "RadnoVrijemeOd"), Xml.tekst(dijelovi.get(1), "RadnoVrijemeDo"));
    }




    private static int broj(Element roditelj, String lokalnoIme) {
        return Integer.parseInt(Xml.tekst(roditelj, lokalnoIme));
    }

    private static LocalDate datum(Element roditelj, String lokalnoIme) {
        String s = Xml.tekst(roditelj, lokalnoIme);
        return s == null ? null : LocalDate.parse(s, B2cZahtjevi.DATUM);
    }
}
