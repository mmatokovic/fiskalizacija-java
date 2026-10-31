package dev.mmatokovic.fiskalizacija.b2c;

import java.util.List;

/** Ishod za jedan poslovni prostor iz {@link B2cClient#prijaviRadnoVrijemeZaPoslovnice}: potvrda ili greške. */
public record PoslovnicaOdgovor(String oznPosPr, PorukaOdgovora poruka, List<Greska> greske) {

    public PoslovnicaOdgovor {
        greske = List.copyOf(greske);
    }
}
