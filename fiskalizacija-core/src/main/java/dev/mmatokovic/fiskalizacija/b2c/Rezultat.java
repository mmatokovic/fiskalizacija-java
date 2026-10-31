package dev.mmatokovic.fiskalizacija.b2c;

import dev.mmatokovic.fiskalizacija.PotpisOdgovora;

import java.time.Instant;
import java.util.List;

/**
 * Ishod jednog poziva. Sirovi SOAP zahtjev i odgovor čuvaju se namjerno: trebaju za arhivu i za analizu odbijenih poruka.
 * Polja koja se ne odnose na pozvanu operaciju su {@code null} ili prazna.
 *
 * @param idPoruke       id poslane poruke; Sustav za fiskalizaciju ga vraća u odgovoru
 * @param jir            jedinstveni identifikator računa iz {@link B2cClient#racun}, ili {@code null}
 * @param zastKod        ZKI računa na koji se poruka odnosi; ispisuje se na račun i kad JIR-a nema
 * @param poruka         potvrda kod promjena, napojnice i radnog vremena, ili {@code null}
 * @param radnoVrijeme   radno vrijeme iz {@link B2cClient#dohvatiRadnoVrijeme}, ili {@code null}
 * @param poslovnice     ishod po poslovnom prostoru iz {@link B2cClient#prijaviRadnoVrijemeZaPoslovnice}
 * @param greske         greške iz odgovora; kod provjere računa i rezultati provjere ({@code vXXX})
 * @param potpisOdgovora rezultat provjere potpisa odgovora
 * @param httpStatus     HTTP status odgovora
 * @param soapZahtjev    potpisani zahtjev točno onako kako je poslan
 * @param soapOdgovor    odgovor točno onako kako je primljen
 * @param primljeno      kad je odgovor primljen
 */
public record Rezultat(
        String idPoruke,
        String jir,
        String zastKod,
        PorukaOdgovora poruka,
        RadnoVrijeme radnoVrijeme,
        List<PoslovnicaOdgovor> poslovnice,
        List<Greska> greske,
        PotpisOdgovora potpisOdgovora,
        int httpStatus,
        String soapZahtjev,
        String soapOdgovor,
        Instant primljeno) {

    public Rezultat {
        poslovnice = List.copyOf(poslovnice);
        greske = List.copyOf(greske);
    }

    /** Odgovor nema grešaka, ima JIR, potvrdu ili radno vrijeme, a potpis mu je {@link PotpisOdgovora#ISPRAVAN}. */
    public boolean prihvacen() {
        return prihvacenBezProvjerePotpisa() && potpisOdgovora == PotpisOdgovora.ISPRAVAN;
    }

    /**
     * Što piše u odgovoru, bez obzira na potpis. Ako je ovo {@code true}, a {@link #prihvacen()} {@code false},
     * provjerite {@link #potpisOdgovora()} i postavke potpisnika ({@link B2cClient.Builder#potpisnikOdgovora}).
     * Kod {@link B2cClient#prijaviRadnoVrijemeZaPoslovnice} ishod svake poslovnice je u {@link #poslovnice()}.
     */
    public boolean prihvacenBezProvjerePotpisa() {
        return greske.isEmpty() && (jir != null || poruka != null || radnoVrijeme != null || !poslovnice.isEmpty());
    }

    /** Bez sirovih SOAP poruka, koje sadrže OIB-e, iznose i certifikat. */
    @Override
    public String toString() {
        return "Rezultat[idPoruke=" + idPoruke + ", jir=" + jir + ", zastKod=" + zastKod + ", poruka=" + poruka
                + ", greske=" + greske + ", potpisOdgovora=" + potpisOdgovora + ", httpStatus=" + httpStatus
                + ", primljeno=" + primljeno + "]";
    }
}
