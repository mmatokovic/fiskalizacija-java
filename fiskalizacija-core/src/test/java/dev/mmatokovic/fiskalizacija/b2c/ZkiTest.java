package dev.mmatokovic.fiskalizacija.b2c;

import dev.mmatokovic.fiskalizacija.NeispravanZahtjevException;
import dev.mmatokovic.fiskalizacija.Primjeri;
import dev.mmatokovic.fiskalizacija.internal.potpis.PristupKljucu;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZkiTest {

    private final dev.mmatokovic.fiskalizacija.Potpisivac potpisivac = Primjeri.potpisivac();

    /** Pseudokod iz poglavlja 12.1: OIB + "dd.MM.gggg HH:mm:ss" + broj + poslovni prostor + naplatni uređaj + iznos s točkom. */
    @Test
    void racunaSePremaPseudokoduIzSpecifikacije() throws Exception {
        var datVrijeme = LocalDateTime.of(2026, 3, 2, 8, 5, 9);
        var brRac = new BrojRacuna("12345", "blag001", "11245");

        String zki = Zki.izracunaj(potpisivac, "00169331406", datVrijeme, brRac, new BigDecimal("1245.56"));

        var potpis = Signature.getInstance("SHA256withRSA");
        potpis.initSign(PristupKljucu.kljuc(potpisivac));
        potpis.update("0016933140602.03.2026 08:05:0912345blag001112451245.56".getBytes(StandardCharsets.UTF_8));
        assertThat(zki)
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(potpis.sign())))
                .matches("[a-f0-9]{32}");
    }

    @Test
    void iznosBezDecimalaSePiseSDvijeDecimale() {
        var datVrijeme = LocalDateTime.of(2026, 3, 2, 8, 5, 9);
        var brRac = new BrojRacuna("1", "P1", "1");

        assertThat(Zki.izracunaj(potpisivac, "12345678903", datVrijeme, brRac, new BigDecimal("47")))
                .isEqualTo(Zki.izracunaj(potpisivac, "12345678903", datVrijeme, brRac, new BigDecimal("47.00")));
    }

    @Test
    void iznosSViseOdDvijeDecimaleJeGreska() {
        assertThatThrownBy(() -> Zki.izracunaj(potpisivac, "12345678903", LocalDateTime.of(2026, 3, 2, 8, 5, 9),
                new BrojRacuna("1", "P1", "1"), new BigDecimal("1.005")))
                .isInstanceOf(NeispravanZahtjevException.class);
    }
}
