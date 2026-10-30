package dev.mmatokovic.fiskalizacija2;

import java.net.URI;

/** Adrese servisa iste su za eFiskalizaciju i eIzvještavanje; servis razlikuje poruke po elementu u tijelu. */
public enum Okruzenje {
    /** Testno okruženje; vjeruje i FINA demo certifikatima. */
    TEST(URI.create("https://cis.porezna-uprava.hr:8511/FiskalizacijaService")),
    PRODUKCIJA(URI.create("https://cis.porezna-uprava.hr:8509/FiskalizacijaService"));

    private final URI adresa;

    Okruzenje(URI adresa) {
        this.adresa = adresa;
    }

    public URI adresa() {
        return adresa;
    }
}
