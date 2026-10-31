package dev.mmatokovic.fiskalizacija.b2c;

import java.util.Objects;

/**
 * Radno vrijeme u jednom dijelu za dan u tjednu.
 *
 * @param danUTjednu     1 (ponedjeljak) do 7 (nedjelja), 8 za praznik i državni blagdan
 * @param radnoVrijemeOd {@code HH:mm}
 * @param radnoVrijemeDo {@code HH:mm}, najviše {@code 24:00}
 */
public record Jednokratno(int danUTjednu, String radnoVrijemeOd, String radnoVrijemeDo) {

    public Jednokratno {
        Objects.requireNonNull(radnoVrijemeOd, "radnoVrijemeOd");
        Objects.requireNonNull(radnoVrijemeDo, "radnoVrijemeDo");
    }
}
