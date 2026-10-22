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

    /** HTTP status, ili 0 ako odgovor nije primljen. */
    private final int httpStatus;
    /** Sirovi odgovor, ili {@code null}. */
    private final String soapOdgovor;

    /**
     * Nova iznimka.
     *
     * @param poruka      opis greške
     * @param httpStatus  HTTP status, ili 0 ako odgovor nije primljen
     * @param soapOdgovor sirovi odgovor, ili {@code null}
     * @param uzrok       uzrok, ili {@code null}
     */
    public FiskalizacijaException(String poruka, int httpStatus, String soapOdgovor, Throwable uzrok) {
        super(poruka, uzrok);
        this.httpStatus = httpStatus;
        this.soapOdgovor = soapOdgovor;
    }

    /**
     * HTTP status odgovora.
     *
     * @return HTTP status, ili 0 ako odgovor nije primljen
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Sirovi odgovor servisa, za arhivu i analizu.
     *
     * @return sirovi odgovor servisa, ili {@code null} ako odgovor nije primljen
     */
    public String soapOdgovor() {
        return soapOdgovor;
    }
}
