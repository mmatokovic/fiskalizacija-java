package io.github.mmatokovic.fiskalizacija.primjeri;

import io.github.mmatokovic.fiskalizacija.b2b.UblParser;
import io.github.mmatokovic.fiskalizacija.b2b.VrstaERacuna;
import io.github.mmatokovic.fiskalizacija.eracun.ERacunValidator;

/** Validira izlazni eRačun i fiskalizira ga na testnom okruženju. */
public final class EvidentiranjeERacuna {

    public static void main(String[] args) throws Exception {
        byte[] xml = TestnoOkruzenje.eRacun(args);

        var validacija = ERacunValidator.validiraj(xml);
        if (!validacija.ispravan()) {
            validacija.greske().forEach(n -> System.out.println(n.pravilo() + ": " + n.poruka()));
            return;
        }

        var racun = UblParser.racun(xml);
        var rezultat = TestnoOkruzenje.klijent().evidentirajERacun(VrstaERacuna.IZLAZNI, racun);

        TestnoOkruzenje.ispisi("eRačun " + racun.brojDokumenta(), rezultat);
    }
}
