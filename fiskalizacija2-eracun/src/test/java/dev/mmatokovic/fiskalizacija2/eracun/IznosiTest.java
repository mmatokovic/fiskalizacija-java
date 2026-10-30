package dev.mmatokovic.fiskalizacija2.eracun;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IznosiTest {

    @Test
    void jednaStavkaKaoUSluzbenomPrimjeru() {
        var r = Iznosi.izracunaj(List.of(StavkaRacuna.sPdv("Usluga", d("1.000"), "H87", d("100.000000"), d("25"), "62.01.11")), null);

        assertEquals(d("100.00"), r.zbrojNeto());
        assertEquals(d("100.00"), r.iznosBezPdv());
        assertEquals(d("25.00"), r.pdv());
        assertEquals(d("125.00"), r.iznosSPdv());
        assertEquals(d("0.00"), r.placeniIznos());
        assertEquals(d("125.00"), r.zaPlacanje());
    }

    @Test
    void pdvSeRacunaNaZbrojOsnovicaANePoStavkama() {
        var stavka = StavkaRacuna.sPdv("Vijak", d("1"), "H87", d("0.03"), d("25"), "25.94.11");

        var r = Iznosi.izracunaj(List.of(stavka, stavka, stavka), null);

        assertEquals(d("0.09"), r.pdvPoStopama().get(0).osnovica());
        assertEquals(d("0.02"), r.pdv());
        assertEquals(d("0.11"), r.iznosSPdv());
    }

    @Test
    void viseStopaIKategorija() {
        var r = Iznosi.izracunaj(List.of(
                StavkaRacuna.sPdv("A", d("2"), "H87", d("10.00"), d("25"), "kpd"),
                StavkaRacuna.sPdv("B", d("1"), "H87", d("8.40"), d("13"), "kpd"),
                StavkaRacuna.sPdv("C", d("3"), "H87", d("1.99"), d("25.00"), "kpd"),
                StavkaRacuna.sPdv("D", d("1"), "H87", d("4.00"), d("5"), "kpd"),
                new StavkaRacuna("E", d("1"), "H87", d("50.00"), null, KategorijaPdv.E, null, "kpd")), null);

        assertEquals(List.of(
                new PdvPoStopi(KategorijaPdv.S, d("25"), d("25.97"), d("6.49")),
                new PdvPoStopi(KategorijaPdv.S, d("13"), d("8.40"), d("1.09")),
                new PdvPoStopi(KategorijaPdv.S, d("5"), d("4.00"), d("0.20")),
                new PdvPoStopi(KategorijaPdv.E, BigDecimal.ZERO, d("50.00"), d("0.00"))), r.pdvPoStopama());
        assertEquals(d("88.37"), r.zbrojNeto());
        assertEquals(d("7.78"), r.pdv());
        assertEquals(d("96.15"), r.iznosSPdv());
    }

    @Test
    void cijenaZaOsnovnuKolicinuIZaokruzivanjePolovicePremaGore() {
        var stavka = new StavkaRacuna("Kabel", d("3"), "MTR", d("10.01"), d("2"), KategorijaPdv.S, d("25"), "kpd");

        assertEquals(d("15.02"), stavka.neto());
    }

    @Test
    void placeniIznosSeOduzimaOdIznosaZaPlacanje() {
        var r = Iznosi.izracunaj(List.of(StavkaRacuna.sPdv("Usluga", d("1"), "H87", d("100"), d("25"), "kpd")), d("50"));

        assertEquals(d("50.00"), r.placeniIznos());
        assertEquals(d("75.00"), r.zaPlacanje());
    }

    @Test
    void izvanSustavaPdvaNemaStopuNiPdv() {
        var r = Iznosi.izracunaj(List.of(new StavkaRacuna("Usluga", d("1"), "H87", d("100"), null, KategorijaPdv.O, null, "kpd")), null);

        assertEquals(List.of(new PdvPoStopi(KategorijaPdv.O, null, d("100.00"), d("0.00"))), r.pdvPoStopama());
        assertEquals(d("100.00"), r.zaPlacanje());
    }

    @Test
    void povratSNegativnomKolicinom() {
        var r = Iznosi.izracunaj(List.of(StavkaRacuna.sPdv("Povrat", d("-2"), "H87", d("10"), d("25"), "kpd")), null);

        assertEquals(d("-20.00"), r.zbrojNeto());
        assertEquals(d("-5.00"), r.pdv());
    }

    @Test
    void neispravneStavke() {
        assertThrows(IllegalArgumentException.class,
                () -> new StavkaRacuna("A", d("1"), "H87", d("1"), null, KategorijaPdv.S, null, "kpd"));
        assertThrows(IllegalArgumentException.class,
                () -> new StavkaRacuna("A", d("1"), "H87", d("1"), null, KategorijaPdv.E, d("25"), "kpd"));
        assertThrows(IllegalArgumentException.class,
                () -> new StavkaRacuna("A", d("1"), "H87", d("1"), null, KategorijaPdv.O, d("0"), "kpd"));
        assertThrows(IllegalArgumentException.class,
                () -> StavkaRacuna.sPdv("A", d("1"), "H87", d("-1"), d("25"), "kpd"));
        assertThrows(IllegalArgumentException.class,
                () -> new StavkaRacuna("A", d("1"), "H87", d("1"), BigDecimal.ZERO, KategorijaPdv.S, d("25"), "kpd"));
        assertThrows(IllegalArgumentException.class, () -> Iznosi.izracunaj(List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> Iznosi.izracunaj(List.of(StavkaRacuna.sPdv("A", d("1"), "H87", d("1"), d("25"), "kpd")), d("0.001")));
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }
}
