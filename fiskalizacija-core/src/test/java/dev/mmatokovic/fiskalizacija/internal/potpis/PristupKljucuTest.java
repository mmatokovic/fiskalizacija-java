package dev.mmatokovic.fiskalizacija.internal.potpis;

import dev.mmatokovic.fiskalizacija.Primjeri;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PristupKljucuTest {

    @Test
    void samoPotpisivacPostavljaPristup() {
        var potpisivac = Primjeri.potpisivac();

        assertThatThrownBy(() -> PristupKljucu.postavi(p -> null)).isInstanceOf(IllegalStateException.class);
        assertThat(PristupKljucu.kljuc(potpisivac)).isNotNull();
    }
}
