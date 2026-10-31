package dev.mmatokovic.fiskalizacija.model;

import java.time.LocalDate;

/**
 * Odbijanje primljenog eRačuna za poruku {@code EvidentirajOdbijanje}.
 *
 * @param brojDokumenta             broj odbijenog računa
 * @param datumIzdavanja            datum izdavanja računa
 * @param oibPorezniBrojIzdavatelja OIB izdavatelja računa
 * @param oibPorezniBrojPrimatelja  OIB primatelja računa
 * @param datumOdbijanja            datum odbijanja
 * @param vrstaRazlogaOdbijanja     vrsta razloga odbijanja
 * @param razlogOdbijanja           razlog odbijanja tekstom
 */
public record Odbijanje(
        String brojDokumenta,
        LocalDate datumIzdavanja,
        String oibPorezniBrojIzdavatelja,
        String oibPorezniBrojPrimatelja,
        LocalDate datumOdbijanja,
        VrstaRazlogaOdbijanja vrstaRazlogaOdbijanja,
        String razlogOdbijanja) {
}
