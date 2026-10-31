package dev.mmatokovic.fiskalizacija.model;

import java.math.BigDecimal;

/**
 * Raščlamba PDV-a po kategoriji (BG-23, odnosno HR-BG-2 iz HR proširenja).
 *
 * @param kategorijaPdv           kategorija PDV-a (BT-118 / HR-BT-18)
 * @param oporeziviIznos          oporezivi iznos (BT-116 / HR-BT-16)
 * @param iznosPoreza             iznos poreza (BT-117 / HR-BT-17)
 * @param stopa                   stopa PDV-a (BT-119 / HR-BT-19), ili {@code null}
 * @param razlogOslobodenja       šifra razloga oslobođenja (BT-121), ili {@code null}
 * @param tekstRazlogaOslobodenja razlog oslobođenja tekstom (BT-120), ili {@code null}
 * @param hrOznakaKategorijaPdv   HR oznaka kategorije PDV-a iz HR proširenja, ili {@code null}
 */
public record RaspodjelaPdv(
        String kategorijaPdv,
        BigDecimal oporeziviIznos,
        BigDecimal iznosPoreza,
        BigDecimal stopa,
        String razlogOslobodenja,
        String tekstRazlogaOslobodenja,
        String hrOznakaKategorijaPdv) {
}
