package dev.mmatokovic.fiskalizacija;

/** Ishod provjere potpisa odgovora; library ne odlučuje umjesto korisnika što s njim napraviti. */
public enum PotpisOdgovora {
    /** Potpis je ispravan, a certifikat potpisnika vodi do jednog od povjerljivih FINA CA certifikata. */
    ISPRAVAN,
    /** Potpis ne odgovara sadržaju ili se ne može provjeriti. */
    NEISPRAVAN,
    /** Potpis je kriptografski ispravan, ali certifikat potpisnika ne vodi do povjerljivog CA. */
    NEPOUZDAN_CERTIFIKAT,
    /** Odgovor nema potpis. */
    NEDOSTAJE
}
