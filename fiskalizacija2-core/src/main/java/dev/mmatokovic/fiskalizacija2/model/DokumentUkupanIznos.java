package dev.mmatokovic.fiskalizacija2.model;

import java.math.BigDecimal;

/**
 * Ukupni iznosi dokumenta (BG-22).
 *
 * @param neto                         zbroj neto iznosa stavki (BT-106)
 * @param popust                       zbroj popusta na razini dokumenta (BT-107), ili {@code null}
 * @param trosak                       zbroj troškova na razini dokumenta (BT-108), ili {@code null}
 * @param iznosBezPdv                  ukupan iznos bez PDV-a (BT-109)
 * @param pdv                          ukupan PDV (BT-110); nula ako ga eRačun nema, jer je u poruci obvezan
 * @param iznosSPdv                    ukupan iznos s PDV-om (BT-112)
 * @param placeniIznos                 već plaćeni iznos (BT-113), ili {@code null}
 * @param iznosKojiDospijevaZaPlacanje iznos za plaćanje (BT-115)
 */
public record DokumentUkupanIznos(
        BigDecimal neto,
        BigDecimal popust,
        BigDecimal trosak,
        BigDecimal iznosBezPdv,
        BigDecimal pdv,
        BigDecimal iznosSPdv,
        BigDecimal placeniIznos,
        BigDecimal iznosKojiDospijevaZaPlacanje) {
}
