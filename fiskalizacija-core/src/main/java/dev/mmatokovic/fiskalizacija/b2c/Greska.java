package dev.mmatokovic.fiskalizacija.b2c;

/**
 * Greška koju je vratio Sustav za fiskalizaciju.
 *
 * @param sifra  šifra, npr. {@code s005}, ili kod provjere računa {@code vXXX}
 * @param poruka opis
 */
public record Greska(String sifra, String poruka) {
}
