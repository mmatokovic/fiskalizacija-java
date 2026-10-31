package dev.mmatokovic.fiskalizacija.primjeri;

import dev.mmatokovic.fiskalizacija.b2c.BrojRacuna;
import dev.mmatokovic.fiskalizacija.b2c.NacinPlacanja;
import dev.mmatokovic.fiskalizacija.b2c.Porez;
import dev.mmatokovic.fiskalizacija.b2c.QrKod;
import dev.mmatokovic.fiskalizacija.b2c.Racun;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Fiskalizira račun u krajnjoj potrošnji (Fiskalizacija 1.0) na testnom okruženju i ispisuje JIR, ZKI i poveznicu
 * za QR kod. Treba i {@code FISK_OZN_POS_PR}, poslovni prostor otvoren na OIB demo certifikata.
 */
public final class FiskalizacijaRacuna {

    public static void main(String[] args) {
        String oib = TestnoOkruzenje.oib();
        var racun = Racun.builder(oib, LocalDateTime.now(ZoneId.of("Europe/Zagreb")).truncatedTo(ChronoUnit.SECONDS),
                        new BrojRacuna(String.valueOf(System.currentTimeMillis() % 1_000_000_000L), TestnoOkruzenje.oznPosPr(), "1"),
                        new BigDecimal("12.50"), NacinPlacanja.G, oib)
                .pdv(List.of(new Porez(new BigDecimal("25.00"), new BigDecimal("10.00"), new BigDecimal("2.50"))))
                .build();

        var rezultat = TestnoOkruzenje.b2cKlijent().racun(racun);

        if (rezultat.prihvacen()) {
            System.out.println("JIR: " + rezultat.jir());
        } else {
            System.out.println("Bez JIR-a, izdajte račun sa ZKI-jem i pošaljite ga naknadno: " + rezultat.greske());
        }
        System.out.println("ZKI: " + rezultat.zastKod());
        System.out.println("QR:  " + QrKod.poveznica(rezultat, racun));
    }
}
