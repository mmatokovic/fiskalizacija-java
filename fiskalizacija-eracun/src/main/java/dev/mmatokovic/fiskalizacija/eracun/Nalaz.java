package dev.mmatokovic.fiskalizacija.eracun;

/**
 * Jedan nalaz validacije.
 *
 * @param razina   greška čini eRačun neispravnim, upozorenje ne
 * @param pravilo  oznaka pravila, npr. {@code BR-CO-10} (EN 16931) ili {@code HR-BR-12} (HR CIUS), ili {@code null} za XSD
 * @param poruka   opis
 * @param lokacija XPath ili redak u dokumentu, ili {@code null}
 */
public record Nalaz(Razina razina, String pravilo, String poruka, String lokacija) {

    public enum Razina {
        GRESKA,
        UPOZORENJE
    }
}
