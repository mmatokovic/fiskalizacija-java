package dev.mmatokovic.fiskalizacija.b2c;

import java.math.BigDecimal;
import java.util.Objects;

/** Naknada na računu, npr. povratna naknada za ambalažu. */
public record Naknada(String nazivN, BigDecimal iznosN) {

    public Naknada {
        Objects.requireNonNull(nazivN, "nazivN");
        Objects.requireNonNull(iznosN, "iznosN");
    }
}
