package dev.mmatokovic.fiskalizacija.model;

/** Smjer eRačuna u poruci {@code EvidentirajERacun}. */
public enum VrstaERacuna {
    /** Izdani eRačun, evidentira ga izdavatelj. */
    IZLAZNI("I"),
    /** Primljeni eRačun, evidentira ga primatelj. */
    ULAZNI("U");

    private final String oznaka;

    VrstaERacuna(String oznaka) {
        this.oznaka = oznaka;
    }

    public String oznaka() {
        return oznaka;
    }
}
