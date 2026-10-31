package dev.mmatokovic.fiskalizacija.internal.potpis;

import dev.mmatokovic.fiskalizacija.Potpisivac;

import java.security.PrivateKey;
import java.util.Objects;
import java.util.function.Function;

/**
 * Privatni ključ {@link Potpisivac}a za ostale pakete modula, bez javne metode koja bi ga izložila korisnicima.
 * {@code Potpisivac} se ovdje registrira u svom statičkom inicijalizatoru.
 */
public final class PristupKljucu {

    private static volatile Function<Potpisivac, PrivateKey> pristup;

    private PristupKljucu() {
    }

    public static void postavi(Function<Potpisivac, PrivateKey> p) {
        if (pristup != null) {
            throw new IllegalStateException("Pristup ključu je već postavljen");
        }
        pristup = Objects.requireNonNull(p);
    }

    public static PrivateKey kljuc(Potpisivac potpisivac) {
        return pristup.apply(Objects.requireNonNull(potpisivac, "potpisivac"));
    }
}
