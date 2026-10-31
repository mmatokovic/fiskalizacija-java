package dev.mmatokovic.fiskalizacija.b2c;

import java.util.List;

/** Do tri redovna radna vremena i do deset iznimaka za jedan poslovni prostor; liste nikad nisu {@code null}. */
public record RadnoVrijeme(List<Redovno> redovno, List<Iznimka> iznimke) {

    public RadnoVrijeme {
        redovno = redovno == null ? List.of() : List.copyOf(redovno);
        iznimke = iznimke == null ? List.of() : List.copyOf(iznimke);
    }
}
