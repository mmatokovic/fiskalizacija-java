package dev.mmatokovic.fiskalizacija.b2c;

import java.util.Objects;

/** Radno vrijeme jednog poslovnog prostora u {@link B2cClient#prijaviRadnoVrijemeZaPoslovnice}: redovno ili iznimka. */
public record Poslovnica(String oznPosPr, Redovno redovno, Iznimka iznimka) {

    public Poslovnica {
        Objects.requireNonNull(oznPosPr, "oznPosPr");
        if ((redovno == null) == (iznimka == null)) {
            throw new IllegalArgumentException("Poslovnica ima ili redovno radno vrijeme ili iznimku");
        }
    }

    public static Poslovnica redovno(String oznPosPr, Redovno redovno) {
        return new Poslovnica(oznPosPr, redovno, null);
    }

    public static Poslovnica iznimka(String oznPosPr, Iznimka iznimka) {
        return new Poslovnica(oznPosPr, null, iznimka);
    }
}
