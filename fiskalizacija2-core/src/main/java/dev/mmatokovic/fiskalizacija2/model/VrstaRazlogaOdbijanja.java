package dev.mmatokovic.fiskalizacija2.model;

/** Vrsta razloga odbijanja u poruci {@code EvidentirajOdbijanje}. */
public enum VrstaRazlogaOdbijanja {
    /** Neusklađenost podataka koji ne utječu na obračun poreza. */
    N,
    /** Neusklađenost podataka koji utječu na obračun poreza. */
    U,
    /** Ostalo. */
    O
}
