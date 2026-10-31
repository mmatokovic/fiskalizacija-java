package dev.mmatokovic.fiskalizacija.b2b;

/** Vrsta razloga odbijanja u poruci {@code EvidentirajOdbijanje}. */
public enum VrstaRazlogaOdbijanja {
    /** Neusklađenost podataka koji ne utječu na obračun poreza. */
    N,
    /** Neusklađenost podataka koji utječu na obračun poreza. */
    U,
    /** Ostalo. */
    O
}
