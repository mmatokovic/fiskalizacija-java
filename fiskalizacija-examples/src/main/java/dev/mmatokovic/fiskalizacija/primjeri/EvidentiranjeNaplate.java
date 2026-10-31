package dev.mmatokovic.fiskalizacija.primjeri;

import dev.mmatokovic.fiskalizacija.b2b.NacinPlacanja;
import dev.mmatokovic.fiskalizacija.b2b.Naplata;
import dev.mmatokovic.fiskalizacija.b2b.UblParser;
import dev.mmatokovic.fiskalizacija.b2b.VrstaERacuna;

import java.time.LocalDate;
import java.time.ZoneId;

/** Fiskalizira izlazni eRačun pa njegovu naplatu na testnom okruženju. Naplata treba samo podatke o računu, ne UBL. */
public final class EvidentiranjeNaplate {

    public static void main(String[] args) throws Exception {
        var klijent = TestnoOkruzenje.klijent();
        var racun = UblParser.racun(TestnoOkruzenje.eRacun(args));

        TestnoOkruzenje.ispisi("eRačun " + racun.brojDokumenta(), klijent.evidentirajERacun(VrstaERacuna.IZLAZNI, racun));

        var naplata = new Naplata(racun.brojDokumenta(), racun.datumIzdavanja(),
                racun.izdavatelj().oibPorezniBroj(), racun.primatelj().oibPorezniBroj(),
                LocalDate.now(ZoneId.of("Europe/Zagreb")), racun.ukupanIznos().iznosKojiDospijevaZaPlacanje(),
                NacinPlacanja.T);
        TestnoOkruzenje.ispisi("Naplata", klijent.evidentirajNaplatu(naplata));
    }
}
