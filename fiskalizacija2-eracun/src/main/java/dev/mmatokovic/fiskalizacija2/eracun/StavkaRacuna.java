package dev.mmatokovic.fiskalizacija2.eracun;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Stavka računa koji se izrađuje.
 *
 * @param naziv           naziv artikla (BT-153)
 * @param kolicina        količina (BT-129); negativna za povrat
 * @param jedinicaMjere   jedinica mjere, UN/ECE Rec 20, npr. {@code H87} za komad (BT-130)
 * @param cijena          neto cijena za osnovnu količinu (BT-146), ne smije biti negativna
 * @param osnovnaKolicina količina na koju se cijena odnosi (BT-149), ili {@code null} za 1
 * @param kategorija      kategorija PDV-a (BT-151)
 * @param stopa           stopa PDV-a u postotku (BT-152): pozitivna za {@link KategorijaPdv#S}, nula za ostale,
 *                        {@code null} za {@link KategorijaPdv#O}
 * @param kpd             šifra KPD klasifikacije (BT-158, shema {@code CG})
 */
public record StavkaRacuna(
        String naziv,
        BigDecimal kolicina,
        String jedinicaMjere,
        BigDecimal cijena,
        BigDecimal osnovnaKolicina,
        KategorijaPdv kategorija,
        BigDecimal stopa,
        String kpd) {

    public StavkaRacuna {
        Objects.requireNonNull(naziv, "naziv");
        Objects.requireNonNull(kolicina, "kolicina");
        Objects.requireNonNull(jedinicaMjere, "jedinicaMjere");
        Objects.requireNonNull(cijena, "cijena");
        Objects.requireNonNull(kategorija, "kategorija");
        if (cijena.signum() < 0) {
            throw new IllegalArgumentException("Cijena ne smije biti negativna: " + cijena);
        }
        if (osnovnaKolicina == null) {
            osnovnaKolicina = BigDecimal.ONE;
        } else if (osnovnaKolicina.signum() <= 0) {
            throw new IllegalArgumentException("Osnovna količina mora biti pozitivna: " + osnovnaKolicina);
        }
        if (!kategorija.imaStopu()) {
            if (stopa != null) {
                throw new IllegalArgumentException("Kategorija " + kategorija + " nema stopu PDV-a");
            }
        } else if (kategorija.imaPozitivnuStopu()) {
            if (stopa == null || stopa.signum() <= 0) {
                throw new IllegalArgumentException("Kategorija S traži pozitivnu stopu PDV-a: " + stopa);
            }
        } else if (stopa == null) {
            stopa = BigDecimal.ZERO;
        } else if (stopa.signum() != 0) {
            throw new IllegalArgumentException("Kategorija " + kategorija + " ima stopu 0, a ne " + stopa);
        }
    }

    /** Stavka po standardnoj stopi, cijena za jednu jedinicu. */
    public static StavkaRacuna sPdv(String naziv, BigDecimal kolicina, String jedinicaMjere, BigDecimal cijena,
            BigDecimal stopa, String kpd) {
        return new StavkaRacuna(naziv, kolicina, jedinicaMjere, cijena, null, KategorijaPdv.S, stopa, kpd);
    }

    /** Neto iznos stavke (BT-131): količina × cijena / osnovna količina, zaokruženo na cent. */
    public BigDecimal neto() {
        return kolicina.multiply(cijena).divide(osnovnaKolicina, 2, RoundingMode.HALF_UP);
    }
}
