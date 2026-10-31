package dev.mmatokovic.fiskalizacija.eracun;

import java.util.List;

/** Svi nalazi validacije jednog eRačuna. */
public record RezultatValidacije(List<Nalaz> nalazi) {

    public RezultatValidacije {
        nalazi = List.copyOf(nalazi);
    }

    /** Nema grešaka; upozorenja su dopuštena. */
    public boolean ispravan() {
        return greske().isEmpty();
    }

    public List<Nalaz> greske() {
        return nalazi.stream().filter(n -> n.razina() == Nalaz.Razina.GRESKA).toList();
    }

    public List<Nalaz> upozorenja() {
        return nalazi.stream().filter(n -> n.razina() == Nalaz.Razina.UPOZORENJE).toList();
    }
}
