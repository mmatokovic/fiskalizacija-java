/**
 * Klijent za Fiskalizaciju 2.0 (B2B) i fiskalizaciju računa u krajnjoj potrošnji (B2C). Javni API su paketi
 * {@code io.github.mmatokovic.fiskalizacija}, {@code io.github.mmatokovic.fiskalizacija.b2b} i {@code io.github.mmatokovic.fiskalizacija.b2c};
 * {@code io.github.mmatokovic.fiskalizacija.internal} nije dio API-ja i mijenja se bez najave.
 */
@SuppressWarnings("module")
module io.github.mmatokovic.fiskalizacija {
    requires java.net.http;
    requires transitive java.xml;
    requires java.xml.crypto;

    exports io.github.mmatokovic.fiskalizacija;
    exports io.github.mmatokovic.fiskalizacija.b2b;
    exports io.github.mmatokovic.fiskalizacija.b2c;
}
