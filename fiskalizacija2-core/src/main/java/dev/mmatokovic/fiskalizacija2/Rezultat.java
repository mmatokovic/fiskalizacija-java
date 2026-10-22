package dev.mmatokovic.fiskalizacija2;

import dev.mmatokovic.fiskalizacija2.model.Greska;
import dev.mmatokovic.fiskalizacija2.model.Odgovor;

/**
 * Ishod jednog poziva. Sirovi SOAP zahtjev i odgovor čuvaju se namjerno: trebaju za arhivu i za analizu odbijenih poruka.
 *
 * @param idZahtjeva      id poslanog zahtjeva, isti kao {@code odgovor.idZahtjeva()} kad servis odgovori
 * @param odgovor         odgovor Porezne uprave
 * @param potpisOdgovora  rezultat provjere potpisa odgovora
 * @param httpStatus      HTTP status odgovora
 * @param soapZahtjev     potpisani zahtjev točno onako kako je poslan
 * @param soapOdgovor     odgovor točno onako kako je primljen
 */
public record Rezultat(
        String idZahtjeva,
        Odgovor odgovor,
        PotpisOdgovora potpisOdgovora,
        int httpStatus,
        String soapZahtjev,
        String soapOdgovor) {

    /**
     * Ishod provjere potpisa odgovora treba provjeriti zasebno, kroz {@link #potpisOdgovora()}.
     *
     * @return je li zahtjev prihvaćen, bez greške
     */
    public boolean prihvacen() {
        return odgovor.prihvacenZahtjev() && odgovor.greska() == null;
    }

    /**
     * Greška iz odgovora.
     *
     * @return greška iz odgovora, ili {@code null} ako je zahtjev prihvaćen
     */
    public Greska greska() {
        return odgovor.greska();
    }
}
