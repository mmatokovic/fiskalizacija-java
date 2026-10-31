package dev.mmatokovic.fiskalizacija.b2c;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Radno vrijeme za jedan datum, drukčije od redovnog: jednokratno, ili dvokratno kad je drugi dio postavljen.
 *
 * @param drugiDioOd ili {@code null} za jednokratno
 * @param drugiDioDo ili {@code null} za jednokratno
 */
public record Iznimka(LocalDate datum, String radnoVrijemeOd, String radnoVrijemeDo, String drugiDioOd, String drugiDioDo) {

    public Iznimka {
        Objects.requireNonNull(datum, "datum");
        Objects.requireNonNull(radnoVrijemeOd, "radnoVrijemeOd");
        Objects.requireNonNull(radnoVrijemeDo, "radnoVrijemeDo");
        if ((drugiDioOd == null) != (drugiDioDo == null)) {
            throw new IllegalArgumentException("Drugi dio dvokratnog radnog vremena treba i početak i kraj");
        }
    }

    public static Iznimka jednokratno(LocalDate datum, String radnoVrijemeOd, String radnoVrijemeDo) {
        return new Iznimka(datum, radnoVrijemeOd, radnoVrijemeDo, null, null);
    }

    public static Iznimka dvokratno(LocalDate datum, String prviOd, String prviDo, String drugiOd, String drugiDo) {
        return new Iznimka(datum, prviOd, prviDo, drugiOd, drugiDo);
    }

    public boolean dvokratno() {
        return drugiDioOd != null;
    }
}
