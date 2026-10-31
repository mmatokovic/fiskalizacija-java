package dev.mmatokovic.fiskalizacija.b2b;

import java.math.BigDecimal;

/**
 * Trošak na razini dokumenta (BG-21).
 *
 * @param iznosTrosak                iznos troška (BT-99)
 * @param kategorijaPdv              kategorija PDV-a troška (BT-102)
 * @param hrOznakaPorezneKategorije  HR oznaka porezne kategorije ({@code cac:TaxCategory/cbc:Name}), ili {@code null}
 * @param stopaPdv                   stopa PDV-a troška (BT-103), ili {@code null}
 * @param tekstRazlogaOslobodenjaPdv razlog oslobođenja od PDV-a tekstom, ili {@code null}
 * @param razlogOslobodenjaPdv       šifra razloga oslobođenja od PDV-a, ili {@code null}
 */
public record DokumentTrosak(
        BigDecimal iznosTrosak,
        String kategorijaPdv,
        String hrOznakaPorezneKategorije,
        BigDecimal stopaPdv,
        String tekstRazlogaOslobodenjaPdv,
        String razlogOslobodenjaPdv) {
}
