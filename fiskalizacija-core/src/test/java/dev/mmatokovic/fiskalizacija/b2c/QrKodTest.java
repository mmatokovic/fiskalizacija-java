package dev.mmatokovic.fiskalizacija.b2c;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Primjeri iz poglavlja 2.7 tehničke specifikacije. */
class QrKodTest {

    private static final String JIR = "12345678-1234-1234-1234-123456789012";

    @Test
    void poveznicaSJirom() {
        assertThat(QrKod.poveznica(JIR, LocalDateTime.of(2020, 9, 21, 6, 30), new BigDecimal("1525.99")))
                .isEqualTo("https://porezna.gov.hr/rn?jir=12345678-1234-1234-1234-123456789012&datv=20200921_0630&izn=152599");
    }

    @Test
    void negativanIznos() {
        assertThat(QrKod.poveznica(JIR, LocalDateTime.of(2021, 1, 10, 14, 0), new BigDecimal("-105.50")))
                .isEqualTo("https://porezna.gov.hr/rn?jir=12345678-1234-1234-1234-123456789012&datv=20210110_1400&izn=-10550");
    }

    @Test
    void poveznicaSaZki() {
        assertThat(QrKod.poveznicaZki("12345678123412341234123456789012", LocalDateTime.of(2021, 2, 5, 8, 1), new BigDecimal("5.10")))
                .isEqualTo("https://porezna.gov.hr/rn?zki=12345678123412341234123456789012&datv=20210205_0801&izn=510");
        assertThat(QrKod.poveznicaZki("12345678123412341234123456789010", LocalDateTime.of(2021, 1, 1, 19, 0), new BigDecimal("47.00")))
                .isEqualTo("https://porezna.gov.hr/rn?zki=12345678123412341234123456789010&datv=20210101_1900&izn=4700");
    }

    @Test
    void iznosSViseOdDvijeDecimaleJeGreska() {
        assertThatThrownBy(() -> QrKod.poveznica(JIR, LocalDateTime.of(2021, 1, 1, 19, 0), new BigDecimal("1.005")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
