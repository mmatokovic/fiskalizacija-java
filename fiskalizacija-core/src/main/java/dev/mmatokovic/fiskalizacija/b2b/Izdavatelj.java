package dev.mmatokovic.fiskalizacija.b2b;

/**
 * Izdavatelj računa, prodavatelj (BG-4).
 *
 * @param ime            naziv prodavatelja (BT-27)
 * @param oibPorezniBroj OIB prodavatelja (BT-31 bez prefiksa HR, ili BT-34)
 * @param oibOperatera   OIB operatera koji je izdao račun (HR-BT-5)
 */
public record Izdavatelj(String ime, String oibPorezniBroj, String oibOperatera) {
}
