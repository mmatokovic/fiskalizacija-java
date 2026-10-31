package dev.mmatokovic.fiskalizacija.b2c;

import java.util.Objects;

/**
 * Radno vrijeme za dan u tjednu koje se razlikuje u parnim i neparnim tjednima.
 *
 * @param danUTjednu     1 (ponedjeljak) do 7 (nedjelja), 8 za praznik i državni blagdan
 * @param radnoVrijemeOd {@code HH:mm}
 * @param radnoVrijemeDo {@code HH:mm}, najviše {@code 24:00}
 */
public record ParniNeparni(int danUTjednu, ParNepar parNepar, String radnoVrijemeOd, String radnoVrijemeDo) {

    public ParniNeparni {
        Objects.requireNonNull(parNepar, "parNepar");
        Objects.requireNonNull(radnoVrijemeOd, "radnoVrijemeOd");
        Objects.requireNonNull(radnoVrijemeDo, "radnoVrijemeDo");
    }
}
