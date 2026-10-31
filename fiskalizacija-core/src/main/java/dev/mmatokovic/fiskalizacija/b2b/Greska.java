package dev.mmatokovic.fiskalizacija.b2b;

/**
 * Greška koju je vratila Porezna uprava.
 *
 * @param sifra           šifra greške, npr. {@code S009}
 * @param redniBrojZapisa redni broj zapisa u poruci na koji se greška odnosi, ili {@code null}
 * @param opis            opis greške
 */
public record Greska(String sifra, Integer redniBrojZapisa, String opis) {
}
