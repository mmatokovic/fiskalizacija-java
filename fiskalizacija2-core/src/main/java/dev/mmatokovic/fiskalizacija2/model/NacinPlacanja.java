package dev.mmatokovic.fiskalizacija2.model;

/** Način plaćanja u poruci {@code EvidentirajNaplatu}. */
public enum NacinPlacanja {
    /** Transakcijski račun. */
    T,
    /** Obračunsko plaćanje. */
    O,
    /** Ostalo. */
    Z
}
