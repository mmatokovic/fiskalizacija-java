package dev.mmatokovic.fiskalizacija.model;

/**
 * Primatelj računa, kupac (BG-7).
 *
 * @param ime            naziv kupca (BT-44)
 * @param oibPorezniBroj OIB kupca (BT-48 bez prefiksa HR, ili BT-49)
 */
public record Primatelj(String ime, String oibPorezniBroj) {
}
