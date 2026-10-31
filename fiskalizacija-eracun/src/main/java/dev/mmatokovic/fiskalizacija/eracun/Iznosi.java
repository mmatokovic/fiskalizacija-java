package dev.mmatokovic.fiskalizacija.eracun;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Iznosi računa izračunati po pravilima EN 16931: PDV se računa na zbroj osnovica po kategoriji i stopi (BR-CO-17),
 * a ne zbrajanjem PDV-a po stavkama. Zaokružuje se na cent, polovica prema gore.
 *
 * @param pdvPoStopama raščlamba PDV-a (BG-23), redom kojim se kategorije i stope prvi put pojavljuju u stavkama
 * @param zbrojNeto    zbroj neto iznosa stavki (BT-106)
 * @param iznosBezPdv  iznos bez PDV-a (BT-109)
 * @param pdv          ukupan PDV (BT-110)
 * @param iznosSPdv    iznos s PDV-om (BT-112)
 * @param placeniIznos već plaćeni iznos (BT-113)
 * @param zaPlacanje   iznos za plaćanje (BT-115)
 */
public record Iznosi(
        List<PdvPoStopi> pdvPoStopama,
        BigDecimal zbrojNeto,
        BigDecimal iznosBezPdv,
        BigDecimal pdv,
        BigDecimal iznosSPdv,
        BigDecimal placeniIznos,
        BigDecimal zaPlacanje) {

    private static final BigDecimal STO = BigDecimal.valueOf(100);

    public Iznosi {
        pdvPoStopama = List.copyOf(pdvPoStopama);
    }

    /**
     * @param stavke       stavke računa, barem jedna
     * @param placeniIznos već plaćeni iznos, npr. predujam, ili {@code null}
     */
    public static Iznosi izracunaj(List<StavkaRacuna> stavke, BigDecimal placeniIznos) {
        if (stavke.isEmpty()) {
            throw new IllegalArgumentException("Račun mora imati barem jednu stavku");
        }
        var osnovice = new LinkedHashMap<Kljuc, BigDecimal>();
        var stope = new LinkedHashMap<Kljuc, BigDecimal>();
        var zbrojNeto = BigDecimal.ZERO.setScale(2);
        for (var s : stavke) {
            Objects.requireNonNull(s, "stavka");
            var neto = s.neto();
            zbrojNeto = zbrojNeto.add(neto);
            var kljuc = new Kljuc(s.kategorija(), s.stopa() == null ? null : s.stopa().stripTrailingZeros());
            osnovice.merge(kljuc, neto, BigDecimal::add);
            stope.putIfAbsent(kljuc, s.stopa());
        }

        var pdvPoStopama = new ArrayList<PdvPoStopi>();
        var pdv = BigDecimal.ZERO.setScale(2);
        for (var e : osnovice.entrySet()) {
            var stopa = stope.get(e.getKey());
            var iznos = stopa == null
                    ? BigDecimal.ZERO.setScale(2)
                    : e.getValue().multiply(stopa).divide(STO, 2, RoundingMode.HALF_UP);
            pdvPoStopama.add(new PdvPoStopi(e.getKey().kategorija(), stopa, e.getValue(), iznos));
            pdv = pdv.add(iznos);
        }

        var placeno = placeniIznos == null ? BigDecimal.ZERO.setScale(2) : centi(placeniIznos);
        var sPdv = zbrojNeto.add(pdv);
        return new Iznosi(pdvPoStopama, zbrojNeto, zbrojNeto, pdv, sPdv, placeno, sPdv.subtract(placeno));
    }

    private static BigDecimal centi(BigDecimal iznos) {
        try {
            return iznos.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Iznos smije imati najviše dvije decimale: " + iznos.toPlainString(), e);
        }
    }

    private record Kljuc(KategorijaPdv kategorija, BigDecimal stopa) {
    }
}
