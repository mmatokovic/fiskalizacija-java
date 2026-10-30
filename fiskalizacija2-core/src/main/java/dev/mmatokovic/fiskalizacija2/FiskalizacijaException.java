package dev.mmatokovic.fiskalizacija2;

import java.io.Serial;

/**
 * Zahtjev nije obrađen ili odgovor nije valjan: mrežna greška, istek vremena, SOAP Fault, odgovor bez
 * fiskalizacijskog odgovora ili s više njih, odnosno odgovor za drugi zahtjev.
 * Poslovno odbijanje od Porezne uprave nije iznimka nego {@link Rezultat} s {@code prihvacen() == false}.
 */
public class FiskalizacijaException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int httpStatus;
    private final String soapOdgovor;

    public FiskalizacijaException(String poruka, int httpStatus, String soapOdgovor, Throwable uzrok) {
        super(poruka, uzrok);
        this.httpStatus = httpStatus;
        this.soapOdgovor = soapOdgovor;
    }

    /** HTTP status, ili 0 ako odgovor nije primljen. */
    public int httpStatus() {
        return httpStatus;
    }

    /** Sirovi odgovor servisa, ili {@code null} ako odgovor nije primljen. */
    public String soapOdgovor() {
        return soapOdgovor;
    }
}
