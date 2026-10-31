package dev.mmatokovic.fiskalizacija.b2c;

/**
 * Potvrda uspješne obrade, npr. {@code p002} za napojnicu ili {@code p003} za radno vrijeme.
 *
 * @param sifraPoruke šifra u obliku {@code pXXX}
 * @param poruka      opis
 */
public record PorukaOdgovora(String sifraPoruke, String poruka) {
}
