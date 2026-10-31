package dev.mmatokovic.fiskalizacija.b2b;

import dev.mmatokovic.fiskalizacija.PotpisOdgovora;

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
 *                        ({@link B2bClient#provjeriOdgovor(String, Instant)})
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

    /** Porezna je prihvatila zahtjev bez greške, a potpis njezinog odgovora je {@link PotpisOdgovora#ISPRAVAN}. */
    public boolean prihvacen() {
        return prihvacenBezProvjerePotpisa() && potpisOdgovora == PotpisOdgovora.ISPRAVAN;
    }

    /**
     * Što piše u odgovoru, bez obzira na potpis. Ako je ovo {@code true}, a {@link #prihvacen()} {@code false}, zahtjev
     * nemojte slati ponovno: Porezna ga je vjerojatno evidentirala, ali potpis odgovora nije potvrđen, pa provjerite
     * {@link #potpisOdgovora()} i postavke potpisnika ({@link B2bClient.Builder#potpisnikOdgovora}).
     */
    public boolean prihvacenBezProvjerePotpisa() {
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
