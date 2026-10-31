package dev.mmatokovic.fiskalizacija.b2c;

import java.math.BigDecimal;
import java.util.Objects;

/** PDV ili porez na potrošnju po jednoj stopi; iznosi i stopa s najviše dvije decimale. */
public record Porez(BigDecimal stopa, BigDecimal osnovica, BigDecimal iznos) {

    public Porez {
        Objects.requireNonNull(stopa, "stopa");
        Objects.requireNonNull(osnovica, "osnovica");
        Objects.requireNonNull(iznos, "iznos");
    }
}
