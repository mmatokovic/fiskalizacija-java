package dev.mmatokovic.fiskalizacija2.model;

import java.math.BigDecimal;

/**
 * Popust na razini dokumenta (BG-20).
 *
 * @param iznosPopust         iznos popusta (BT-92)
 * @param kategorijaPdv       kategorija PDV-a popusta (BT-95)
 * @param stopaPdv            stopa PDV-a popusta (BT-96), ili {@code null}
 * @param tekstRazlogaPopusta razlog popusta tekstom (BT-97), ili {@code null}
 * @param razlogPopusta       šifra razloga popusta (BT-98), ili {@code null}
 */
public record DokumentPopust(
        BigDecimal iznosPopust,
        String kategorijaPdv,
        BigDecimal stopaPdv,
        String tekstRazlogaPopusta,
        String razlogPopusta) {
}
