/**
 * Klijent za Fiskalizaciju 2.0 (B2B) i fiskalizaciju računa u krajnjoj potrošnji (B2C). Javni API su paketi
 * {@code dev.mmatokovic.fiskalizacija}, {@code dev.mmatokovic.fiskalizacija.b2b} i {@code dev.mmatokovic.fiskalizacija.b2c};
 * {@code dev.mmatokovic.fiskalizacija.internal} nije dio API-ja i mijenja se bez najave.
 */
@SuppressWarnings("module")
module dev.mmatokovic.fiskalizacija {
    requires java.net.http;
    requires transitive java.xml;
    requires java.xml.crypto;

    exports dev.mmatokovic.fiskalizacija;
    exports dev.mmatokovic.fiskalizacija.b2b;
    exports dev.mmatokovic.fiskalizacija.b2c;
}
