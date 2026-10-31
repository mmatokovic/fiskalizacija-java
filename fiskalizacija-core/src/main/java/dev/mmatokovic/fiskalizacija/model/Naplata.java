package dev.mmatokovic.fiskalizacija.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Naplata računa za poruku {@code EvidentirajNaplatu}.
 *
 * @param brojDokumenta             broj naplaćenog računa
 * @param datumIzdavanja            datum izdavanja računa
 * @param oibPorezniBrojIzdavatelja OIB izdavatelja računa
 * @param oibPorezniBrojPrimatelja  OIB primatelja računa
 * @param datumNaplate              datum naplate
 * @param naplaceniIznos            naplaćeni iznos, najviše dvije decimale
 * @param nacinPlacanja             način plaćanja
 */
public record Naplata(
        String brojDokumenta,
        LocalDate datumIzdavanja,
        String oibPorezniBrojIzdavatelja,
        String oibPorezniBrojPrimatelja,
        LocalDate datumNaplate,
        BigDecimal naplaceniIznos,
        NacinPlacanja nacinPlacanja) {
}
