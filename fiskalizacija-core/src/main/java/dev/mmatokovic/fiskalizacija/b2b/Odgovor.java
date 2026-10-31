package dev.mmatokovic.fiskalizacija.b2b;

/**
 * Odgovor Porezne uprave; {@code greska} je {@code null} kad je zahtjev prihvaćen.
 *
 * @param idZahtjeva       identifikator zahtjeva koji generira Sustav za fiskalizaciju; nije {@code id} poslanog zahtjeva
 * @param prihvacenZahtjev je li zahtjev prihvaćen
 * @param greska           greška, ili {@code null}
 */
public record Odgovor(String idZahtjeva, boolean prihvacenZahtjev, Greska greska) {
}
