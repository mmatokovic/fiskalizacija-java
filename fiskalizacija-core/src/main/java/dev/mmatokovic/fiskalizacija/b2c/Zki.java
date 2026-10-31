package dev.mmatokovic.fiskalizacija.b2c;

import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.internal.PristupKljucu;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Zaštitni kod izdavatelja (tehnička specifikacija v2.7, poglavlje 12): MD5 sažetak RSA-SHA256 potpisa nad
 * OIB-om, datumom i vremenom izdavanja, brojem računa i ukupnim iznosom. Potpis je deterministički, pa isti račun
 * uvijek ima isti ZKI.
 */
public final class Zki {

    private static final DateTimeFormatter DATUM_VRIJEME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private Zki() {
    }

    /**
     * @throws IllegalArgumentException ako iznos ima više od dvije decimale
     * @throws IllegalStateException    ako potpisivanje ne uspije, npr. ključ koji nije RSA
     */
    public static String izracunaj(Potpisivac potpisivac, Racun racun) {
        return izracunaj(potpisivac, racun.oib(), racun.datVrijeme(), racun.brRac(), racun.iznosUkupno());
    }

    /** @see #izracunaj(Potpisivac, Racun) */
    public static String izracunaj(Potpisivac potpisivac, String oib, LocalDateTime datVrijeme, BrojRacuna brRac,
            BigDecimal iznosUkupno) {
        String podaci = oib + datVrijeme.format(DATUM_VRIJEME) + brRac.brOznRac() + brRac.oznPosPr() + brRac.oznNapUr()
                + iznos(iznosUkupno);
        try {
            var potpis = Signature.getInstance("SHA256withRSA");
            potpis.initSign(PristupKljucu.kljuc(potpisivac));
            potpis.update(podaci.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(potpis.sign()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Izračun ZKI-ja nije uspio: " + e.getMessage(), e);
        }
    }

    private static String iznos(BigDecimal iznos) {
        try {
            return iznos.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Iznos smije imati najviše dvije decimale: " + iznos.toPlainString(), e);
        }
    }
}
