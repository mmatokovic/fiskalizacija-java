package dev.mmatokovic.fiskalizacija2.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Podaci računa iz eRačuna (UBL). Ista struktura služi za {@code ERacun} (evidentiranje eRačuna)
 * i {@code Racun} (isporuka za koju nije izdan eRačun); razlikuju se samo imena elemenata u poruci.
 *
 * <p>Kad dolazi iz tolerantnog čitanja ({@code UblParser.racun(xml, greske)}), polja koja se nisu mogla
 * pročitati su {@code null}; liste nikad nisu {@code null}.
 *
 * @param brojDokumenta          broj računa (BT-1)
 * @param datumIzdavanja         datum izdavanja (BT-2)
 * @param vrstaDokumenta         vrsta dokumenta (BT-3)
 * @param valuta                 valuta računa (BT-5)
 * @param datumDospijecaPlacanja datum dospijeća plaćanja (BT-9), ili {@code null}
 * @param vrstaPoslovnogProcesa  vrsta poslovnog procesa (BT-23)
 * @param referencaNaUgovor      referenca na ugovor (BT-12), ili {@code null}
 * @param datumIsporuke          datum isporuke (BT-72), ili {@code null}
 * @param prethodniRacuni        reference na prethodne račune (BG-3)
 * @param izdavatelj             prodavatelj (BG-4)
 * @param primatelj              kupac (BG-7)
 * @param prijenosiSredstava     računi za plaćanje (BG-17)
 * @param ukupanIznos            ukupni iznosi (BG-22)
 * @param raspodjelaPdv          raščlamba PDV-a (HR-BG-2 ako postoji, inače BG-23)
 * @param popusti                popusti na razini dokumenta (BG-20)
 * @param troskovi               troškovi na razini dokumenta (BG-21)
 * @param stavke                 stavke računa (BG-25)
 * @param indikatorKopije        je li dokument kopija ({@code cbc:CopyIndicator})
 */
public record Racun(
        String brojDokumenta,
        LocalDate datumIzdavanja,
        String vrstaDokumenta,
        String valuta,
        LocalDate datumDospijecaPlacanja,
        String vrstaPoslovnogProcesa,
        String referencaNaUgovor,
        LocalDate datumIsporuke,
        List<PrethodniRacun> prethodniRacuni,
        Izdavatelj izdavatelj,
        Primatelj primatelj,
        List<PrijenosSredstava> prijenosiSredstava,
        DokumentUkupanIznos ukupanIznos,
        List<RaspodjelaPdv> raspodjelaPdv,
        List<DokumentPopust> popusti,
        List<DokumentTrosak> troskovi,
        List<Stavka> stavke,
        boolean indikatorKopije) {

    public Racun {
        prethodniRacuni = List.copyOf(Objects.requireNonNull(prethodniRacuni, "prethodniRacuni"));
        prijenosiSredstava = List.copyOf(Objects.requireNonNull(prijenosiSredstava, "prijenosiSredstava"));
        raspodjelaPdv = List.copyOf(Objects.requireNonNull(raspodjelaPdv, "raspodjelaPdv"));
        popusti = List.copyOf(Objects.requireNonNull(popusti, "popusti"));
        troskovi = List.copyOf(Objects.requireNonNull(troskovi, "troskovi"));
        stavke = List.copyOf(Objects.requireNonNull(stavke, "stavke"));
    }
}
