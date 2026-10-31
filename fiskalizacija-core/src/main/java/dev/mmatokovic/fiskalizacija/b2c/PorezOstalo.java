package dev.mmatokovic.fiskalizacija.b2c;

import java.math.BigDecimal;
import java.util.Objects;

/** Ostali porez, npr. porez na luksuz. */
public record PorezOstalo(String naziv, BigDecimal stopa, BigDecimal osnovica, BigDecimal iznos) {

    public PorezOstalo {
        Objects.requireNonNull(naziv, "naziv");
        Objects.requireNonNull(stopa, "stopa");
        Objects.requireNonNull(osnovica, "osnovica");
        Objects.requireNonNull(iznos, "iznos");
    }
}
