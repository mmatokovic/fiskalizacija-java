package dev.mmatokovic.fiskalizacija.b2c;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Sadržaj QR koda na računu (tehnička specifikacija v2.7, poglavlje 2.7): poveznica na provjeru računa s JIR-om ili
 * ZKI-jem, vremenom izdavanja i iznosom u centima. Sliku QR koda iscrtava vaš ispis računa.
 */
public final class QrKod {

    private static final String ADRESA = "https://porezna.gov.hr/rn";
    private static final DateTimeFormatter DATUM_VRIJEME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private QrKod() {
    }

    /** Za fiskalizirani račun. */
    public static String poveznica(String jir, LocalDateTime datVrijeme, BigDecimal iznosUkupno) {
        return poveznica("jir", jir, datVrijeme, iznosUkupno);
    }

    /** Za račun bez JIR-a, npr. kad Sustav za fiskalizaciju nije bio dostupan. */
    public static String poveznicaZki(String zastKod, LocalDateTime datVrijeme, BigDecimal iznosUkupno) {
        return poveznica("zki", zastKod, datVrijeme, iznosUkupno);
    }

    /** Za {@link B2cRezultat}: s JIR-om ako ga ima, inače sa ZKI-jem. */
    public static String poveznica(B2cRezultat rezultat, Racun racun) {
        return rezultat.jir() != null
                ? poveznica(rezultat.jir(), racun.datVrijeme(), racun.iznosUkupno())
                : poveznicaZki(rezultat.zastKod(), racun.datVrijeme(), racun.iznosUkupno());
    }

    private static String poveznica(String parametar, String vrijednost, LocalDateTime datVrijeme, BigDecimal iznos) {
        Objects.requireNonNull(vrijednost, parametar);
        String centi;
        try {
            centi = iznos.movePointRight(2).setScale(0).toPlainString();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Iznos smije imati najviše dvije decimale: " + iznos.toPlainString(), e);
        }
        if (centi.length() > 10) {
            throw new IllegalArgumentException("Iznos u centima smije imati najviše 10 znakova: " + centi);
        }
        return ADRESA + "?" + parametar + "=" + vrijednost + "&datv=" + datVrijeme.format(DATUM_VRIJEME) + "&izn=" + centi;
    }
}
