package dev.mmatokovic.fiskalizacija2;

import java.io.Serial;
import java.util.List;

/** Potpisani zahtjev ne prolazi službenu XSD shemu, pa nije poslan. */
public class NeispravanZahtjevException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Greške XSD validacije. */
    @SuppressWarnings("serial")
    private final List<String> greske;

    /**
     * Nova iznimka.
     *
     * @param greske sve pronađene greške
     */
    public NeispravanZahtjevException(List<String> greske) {
        super(String.join("; ", greske));
        this.greske = List.copyOf(greske);
    }

    /**
     * Greške XSD validacije.
     *
     * @return sve pronađene greške, nepromjenjiva lista
     */
    public List<String> greske() {
        return greske;
    }
}
