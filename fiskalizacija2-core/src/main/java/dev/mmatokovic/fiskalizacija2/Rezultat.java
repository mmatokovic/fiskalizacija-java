package dev.mmatokovic.fiskalizacija2;

import dev.mmatokovic.fiskalizacija2.model.Greska;
import dev.mmatokovic.fiskalizacija2.model.Odgovor;

import java.time.Instant;
import java.util.Objects;

/**
 * Ishod jednog poziva. Sirovi SOAP zahtjev i odgovor čuvaju se namjerno: trebaju za arhivu i za analizu odbijenih poruka.
 *
 * @param idZahtjeva      id poslanog zahtjeva, isti kao {@code odgovor.idZahtjeva()} kad servis odgovori
 * @param odgovor         odgovor Porezne uprave
 * @param potpisOdgovora  rezultat provjere potpisa odgovora
 * @param httpStatus      HTTP status odgovora
 * @param soapZahtjev     potpisani zahtjev točno onako kako je poslan
 * @param soapOdgovor     odgovor točno onako kako je primljen
 * @param primljeno       kad je odgovor primljen; za kasniju provjeru potpisa iz arhive
 *                        ({@link FiskalizacijaClient#provjeriOdgovor(String, Instant)})
 */
public record Rezultat(
        String idZahtjeva,
        Odgovor odgovor,
        PotpisOdgovora potpisOdgovora,
        int httpStatus,
        String soapZahtjev,
        String soapOdgovor,
        Instant primljeno) {

    public Rezultat {
        Objects.requireNonNull(odgovor, "odgovor");
    }

    /** Prihvaćen bez greške. Potpis odgovora provjerite zasebno, kroz {@link #potpisOdgovora()}. */
    public boolean prihvacen() {
        return odgovor.prihvacenZahtjev() && odgovor.greska() == null;
    }

    public Greska greska() {
        return odgovor.greska();
    }

    /** Bez sirovih SOAP poruka, koje sadrže OIB-e, iznose i certifikat. */
    @Override
    public String toString() {
        return "Rezultat[idZahtjeva=" + idZahtjeva + ", prihvacen=" + prihvacen() + ", greska=" + greska()
                + ", potpisOdgovora=" + potpisOdgovora + ", httpStatus=" + httpStatus + ", primljeno=" + primljeno + "]";
    }
}
