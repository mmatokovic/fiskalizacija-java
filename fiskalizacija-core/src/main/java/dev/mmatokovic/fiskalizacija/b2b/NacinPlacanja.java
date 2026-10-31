package dev.mmatokovic.fiskalizacija.b2b;

/** Način plaćanja u poruci {@code EvidentirajNaplatu}. */
public enum NacinPlacanja {
    /** Transakcijski račun. */
    T,
    /** Obračunsko plaćanje. */
    O,
    /** Ostalo. */
    Z
}
