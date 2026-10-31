package dev.mmatokovic.fiskalizacija.b2c;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Redovno radno vrijeme od datuma. Raspored je točno jedno od: po dogovoru, jednokratno, dvokratno ili parni i neparni
 * tjedni; ostala polja su prazna. Liste nikad nisu {@code null}.
 *
 * @param datumDo  ili {@code null}
 * @param napomena ili {@code null}
 */
public record Redovno(
        LocalDate datumOd,
        LocalDate datumDo,
        String napomena,
        boolean poDogovoru,
        List<Jednokratno> jednokratno,
        List<Dvokratno> dvokratno,
        List<ParniNeparni> parniNeparni) {

    public Redovno {
        Objects.requireNonNull(datumOd, "datumOd");
        jednokratno = jednokratno == null ? List.of() : List.copyOf(jednokratno);
        dvokratno = dvokratno == null ? List.of() : List.copyOf(dvokratno);
        parniNeparni = parniNeparni == null ? List.of() : List.copyOf(parniNeparni);
        int rasporeda = (poDogovoru ? 1 : 0) + (jednokratno.isEmpty() ? 0 : 1) + (dvokratno.isEmpty() ? 0 : 1)
                + (parniNeparni.isEmpty() ? 0 : 1);
        if (rasporeda != 1) {
            throw new IllegalArgumentException("Redovno radno vrijeme mora imati točno jedan raspored, a ima " + rasporeda);
        }
    }

    public static Redovno poDogovoru(LocalDate datumOd, LocalDate datumDo, String napomena) {
        return new Redovno(datumOd, datumDo, napomena, true, null, null, null);
    }

    public static Redovno jednokratno(LocalDate datumOd, LocalDate datumDo, String napomena, List<Jednokratno> dani) {
        return new Redovno(datumOd, datumDo, napomena, false, dani, null, null);
    }

    public static Redovno dvokratno(LocalDate datumOd, LocalDate datumDo, String napomena, List<Dvokratno> dani) {
        return new Redovno(datumOd, datumDo, napomena, false, null, dani, null);
    }

    public static Redovno parniNeparni(LocalDate datumOd, LocalDate datumDo, String napomena, List<ParniNeparni> dani) {
        return new Redovno(datumOd, datumDo, napomena, false, null, null, dani);
    }
}
