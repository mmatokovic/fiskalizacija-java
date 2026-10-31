package dev.mmatokovic.fiskalizacija.b2c;

import java.math.BigDecimal;
import java.util.Objects;

/** Nagrada za dobro obavljenu uslugu, prijavljena uz već fiskalizirani račun. */
public record Napojnica(BigDecimal iznosNapojnice, NacinPlacanja nacinPlacanjaNapojnice) {

    public Napojnica {
        Objects.requireNonNull(iznosNapojnice, "iznosNapojnice");
        Objects.requireNonNull(nacinPlacanjaNapojnice, "nacinPlacanjaNapojnice");
    }
}
