package dev.mmatokovic.fiskalizacija2.model;

import java.time.LocalDate;

/**
 * Referenca na prethodni račun (BG-3), npr. kod storna ili konačnog računa nakon predujma.
 *
 * @param brojDokumenta  broj prethodnog računa (BT-25)
 * @param datumIzdavanja datum izdavanja prethodnog računa (BT-26)
 */
public record PrethodniRacun(String brojDokumenta, LocalDate datumIzdavanja) {
}
