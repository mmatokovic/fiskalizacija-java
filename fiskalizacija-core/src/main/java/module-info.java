/**
 * Klijent za Fiskalizaciju 2.0 (B2B). Javni API su paketi {@code dev.mmatokovic.fiskalizacija} i
 * {@code dev.mmatokovic.fiskalizacija.model}; {@code dev.mmatokovic.fiskalizacija.internal} nije dio API-ja i mijenja se bez najave.
 */
@SuppressWarnings("module")
module dev.mmatokovic.fiskalizacija {
    requires java.net.http;
    requires transitive java.xml;
    requires java.xml.crypto;

    exports dev.mmatokovic.fiskalizacija;
    exports dev.mmatokovic.fiskalizacija.model;
}
