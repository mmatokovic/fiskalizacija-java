package dev.mmatokovic.fiskalizacija2.eracun;

/** Kategorija PDV-a (UNTDID 5305), kakvu koristi EN 16931. */
public enum KategorijaPdv {
    /** Standardna stopa (25 %, 13 % ili 5 %). */
    S,
    /** Nulta stopa. */
    Z,
    /** Oslobođeno PDV-a; traži razlog oslobođenja. */
    E,
    /** Prijenos porezne obveze; traži razlog oslobođenja. */
    AE,
    /** Isporuka unutar EU. */
    K,
    /** Izvoz izvan EU. */
    G,
    /** Izvan sustava PDV-a; bez stope. */
    O;

    boolean imaStopu() {
        return this != O;
    }

    boolean imaPozitivnuStopu() {
        return this == S;
    }
}
