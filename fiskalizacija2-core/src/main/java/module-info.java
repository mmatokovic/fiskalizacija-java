/**
 * Klijent za Fiskalizaciju 2.0 (B2B). Javni API su paketi {@code dev.mmatokovic.fiskalizacija2} i
 * {@code dev.mmatokovic.fiskalizacija2.model}; {@code dev.mmatokovic.fiskalizacija2.internal} nije dio API-ja i mijenja se bez najave.
 */
@SuppressWarnings("module")
module dev.mmatokovic.fiskalizacija2 {
    requires java.net.http;
    requires transitive java.xml;
    requires java.xml.crypto;

    exports dev.mmatokovic.fiskalizacija2;
    exports dev.mmatokovic.fiskalizacija2.model;
}
