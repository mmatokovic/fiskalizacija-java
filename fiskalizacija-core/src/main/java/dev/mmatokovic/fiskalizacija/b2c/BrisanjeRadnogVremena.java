package dev.mmatokovic.fiskalizacija.b2c;

import java.time.LocalDate;
import java.util.List;

/**
 * Koja buduća radna vremena obrisati.
 *
 * @param redovno datumi od kojih vrijede redovna radna vremena koja se brišu
 * @param iznimke datumi iznimaka koje se brišu
 */
public record BrisanjeRadnogVremena(List<LocalDate> redovno, List<LocalDate> iznimke) {

    public BrisanjeRadnogVremena {
        redovno = redovno == null ? List.of() : List.copyOf(redovno);
        iznimke = iznimke == null ? List.of() : List.copyOf(iznimke);
    }
}
