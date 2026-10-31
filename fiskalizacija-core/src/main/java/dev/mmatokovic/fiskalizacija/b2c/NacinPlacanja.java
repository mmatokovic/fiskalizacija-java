package dev.mmatokovic.fiskalizacija.b2c;

/** Način plaćanja računa; plaćanje čekom ukinuto je 1. 9. 2025. */
public enum NacinPlacanja {
    /** Gotovina. */
    G,
    /** Kartice. */
    K,
    /** Transakcijski račun. */
    T,
    /** Ostalo. */
    O
}
