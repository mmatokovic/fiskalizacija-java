package dev.mmatokovic.fiskalizacija;

/**
 * Potpisan zahtjev koji je prošao XSD validaciju, spreman za slanje. Šalje se s
 * {@link FiskalizacijaClient#posalji(PripremljeniZahtjev)} ili vlastitim HTTP klijentom, a odgovor se tada predaje
 * {@link FiskalizacijaClient#obradiOdgovor(PripremljeniZahtjev, int, String)}.
 */
public final class PripremljeniZahtjev {

    private final String idZahtjeva;
    private final String soap;
    private final String odgovorElement;

    PripremljeniZahtjev(String idZahtjeva, String soap, String odgovorElement) {
        this.idZahtjeva = idZahtjeva;
        this.soap = soap;
        this.odgovorElement = odgovorElement;
    }

    public String idZahtjeva() {
        return idZahtjeva;
    }

    /** Potpisana SOAP poruka; šalje se bez ikakvih izmjena, inače potpis više ne vrijedi. */
    public String soap() {
        return soap;
    }

    String odgovorElement() {
        return odgovorElement;
    }
}
