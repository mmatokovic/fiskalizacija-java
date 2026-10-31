package dev.mmatokovic.fiskalizacija.model;

/**
 * Odgovor Porezne uprave; {@code greska} je {@code null} kad je zahtjev prihvaćen.
 *
 * @param idZahtjeva       id zahtjeva na koji se odgovor odnosi
 * @param prihvacenZahtjev je li zahtjev prihvaćen
 * @param greska           greška, ili {@code null}
 */
public record Odgovor(String idZahtjeva, boolean prihvacenZahtjev, Greska greska) {
}
