package dev.mmatokovic.fiskalizacija2.model;

/**
 * Račun za plaćanje (BG-17).
 *
 * @param identifikatorRacunaZaPlacanje        broj računa, npr. IBAN (BT-84)
 * @param nazivRacunaZaPlacanje                naziv računa (BT-85), ili {@code null}
 * @param identifikatorPruzateljaPlatnihUsluga identifikator pružatelja platnih usluga, npr. BIC (BT-86), ili {@code null}
 */
public record PrijenosSredstava(
        String identifikatorRacunaZaPlacanje,
        String nazivRacunaZaPlacanje,
        String identifikatorPruzateljaPlatnihUsluga) {
}
