package dev.mmatokovic.fiskalizacija2.eracun;

import java.math.BigDecimal;

/**
 * PDV jedne kategorije i stope (BG-23).
 *
 * @param kategorija kategorija PDV-a (BT-118)
 * @param stopa      stopa u postotku (BT-119), ili {@code null} za {@link KategorijaPdv#O}
 * @param osnovica   zbroj neto iznosa stavki te kategorije i stope (BT-116)
 * @param iznos      PDV na osnovicu, zaokružen na cent (BT-117)
 */
public record PdvPoStopi(KategorijaPdv kategorija, BigDecimal stopa, BigDecimal osnovica, BigDecimal iznos) {
}
