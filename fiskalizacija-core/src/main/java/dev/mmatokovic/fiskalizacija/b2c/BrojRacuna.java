package dev.mmatokovic.fiskalizacija.b2c;

import java.util.Objects;

/**
 * Broj računa u obliku brojčana oznaka / oznaka poslovnog prostora / oznaka naplatnog uređaja.
 *
 * @param brOznRac brojčana oznaka računa, samo znamenke
 * @param oznPosPr oznaka poslovnog prostora prijavljena u ePoreznoj
 * @param oznNapUr oznaka naplatnog uređaja, samo znamenke
 */
public record BrojRacuna(String brOznRac, String oznPosPr, String oznNapUr) {

    public BrojRacuna {
        Objects.requireNonNull(brOznRac, "brOznRac");
        Objects.requireNonNull(oznPosPr, "oznPosPr");
        Objects.requireNonNull(oznNapUr, "oznNapUr");
    }
}
