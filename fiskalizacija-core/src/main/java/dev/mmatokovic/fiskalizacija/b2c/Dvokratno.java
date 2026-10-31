package dev.mmatokovic.fiskalizacija.b2c;

import java.util.Objects;

/**
 * Jedan od dva dijela radnog vremena za dan u tjednu.
 *
 * @param danUTjednu     1 (ponedjeljak) do 7 (nedjelja), 8 za praznik i državni blagdan
 * @param dioDvokratnog  1 ili 2
 * @param radnoVrijemeOd {@code HH:mm}
 * @param radnoVrijemeDo {@code HH:mm}, najviše {@code 24:00}
 */
public record Dvokratno(int danUTjednu, int dioDvokratnog, String radnoVrijemeOd, String radnoVrijemeDo) {

    public Dvokratno {
        Objects.requireNonNull(radnoVrijemeOd, "radnoVrijemeOd");
        Objects.requireNonNull(radnoVrijemeDo, "radnoVrijemeDo");
    }
}
