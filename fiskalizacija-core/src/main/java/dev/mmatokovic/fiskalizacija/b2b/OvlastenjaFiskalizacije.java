package dev.mmatokovic.fiskalizacija.b2b;

import dev.mmatokovic.fiskalizacija.PotpisOdgovora;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Ishod {@code OvlastenjaFiskalizacijeZahtjev} (specifikacija 2.0, pogl. 6).
 *
 * @param idZahtjeva                      vrijednost atributa {@code id} poslanog zahtjeva
 * @param ovlasteniOibiBezProvjerePotpisa {@code ovlasteniOib} iz odgovora, bez obzira na potpis; neuspješan odgovor
 *                                        iz primjera specifikacije ima prazan {@code ovlasteniOib}, pa je lista prazna
 * @param potpisOdgovora                  rezultat provjere potpisa odgovora
 * @param httpStatus                      HTTP status odgovora
 * @param soapZahtjev                     potpisani zahtjev točno onako kako je poslan
 * @param soapOdgovor                     odgovor točno onako kako je primljen
 * @param primljeno                       kad je odgovor primljen
 */
public record OvlastenjaFiskalizacije(
        String idZahtjeva,
        List<String> ovlasteniOibiBezProvjerePotpisa,
        PotpisOdgovora potpisOdgovora,
        int httpStatus,
        String soapZahtjev,
        String soapOdgovor,
        Instant primljeno) {

    public OvlastenjaFiskalizacije {
        ovlasteniOibiBezProvjerePotpisa = List.copyOf(ovlasteniOibiBezProvjerePotpisa);
        Objects.requireNonNull(potpisOdgovora, "potpisOdgovora");
    }

    /** @throws IllegalStateException ako potpis odgovora nije {@link PotpisOdgovora#ISPRAVAN} */
    public List<String> ovlasteniOibi() {
        if (potpisOdgovora != PotpisOdgovora.ISPRAVAN) {
            throw new IllegalStateException("Potpis odgovora nije ispravan: " + potpisOdgovora);
        }
        return ovlasteniOibiBezProvjerePotpisa;
    }

    /** Bez sirovih SOAP poruka. */
    @Override
    public String toString() {
        return "OvlastenjaFiskalizacije[idZahtjeva=" + idZahtjeva + ", ovlasteniOibi=" + ovlasteniOibiBezProvjerePotpisa
                + ", potpisOdgovora=" + potpisOdgovora + ", httpStatus=" + httpStatus + ", primljeno=" + primljeno + "]";
    }
}
