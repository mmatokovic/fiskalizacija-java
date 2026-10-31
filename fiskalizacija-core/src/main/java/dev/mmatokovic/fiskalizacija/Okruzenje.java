package dev.mmatokovic.fiskalizacija;

import java.net.URI;

/**
 * Adrese servisa i potpisnik odgovora po okruženju, prema tehničkim specifikacijama Fiskalizacije 2.0 (pogl. 9 i 10)
 * i 1.0 v2.7 (pogl. 5 i 6.1). Odgovore u oba sustava potpisuje aplikacijski certifikat APIS IT-a.
 */
public enum Okruzenje {
    /** Testno okruženje; vjeruje i FINA demo certifikatima. */
    TEST(URI.create("https://cistest.apis-it.hr:8509/FiskalizacijaServiceEprod"),
            URI.create("https://cistest.apis-it.hr:8449/FiskalizacijaServiceTest"),
            "fiskalcistest"),
    PRODUKCIJA(URI.create("https://cis.porezna-uprava.hr:8509/FiskalizacijaService"),
            URI.create("https://cis.porezna-uprava.hr:8449/FiskalizacijaService"),
            "fiskalcis");

    /** OIB APIS IT-a, čiji certifikati {@code fiskalcis} i {@code fiskalcistest} potpisuju odgovore. */
    public static final String OIB_APIS_IT = "02994650199";

    private final URI adresaB2b;
    private final URI adresaB2c;
    private final String potpisnikOdgovora;

    Okruzenje(URI adresaB2b, URI adresaB2c, String potpisnikOdgovora) {
        this.adresaB2b = adresaB2b;
        this.adresaB2c = adresaB2c;
        this.potpisnikOdgovora = potpisnikOdgovora;
    }

    /** Fiskalizacija 2.0: ista adresa za eFiskalizaciju i eIzvještavanje; servis razlikuje poruke po elementu u tijelu. */
    public URI adresaB2b() {
        return adresaB2b;
    }

    /** Fiskalizacija 1.0, računi u krajnjoj potrošnji. */
    public URI adresaB2c() {
        return adresaB2c;
    }

    /** CN aplikacijskog certifikata koji potpisuje odgovore. */
    public String potpisnikOdgovora() {
        return potpisnikOdgovora;
    }
}
