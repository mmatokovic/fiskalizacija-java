package dev.mmatokovic.fiskalizacija.b2c;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Račun u krajnjoj potrošnji (tehnička specifikacija v2.7, poglavlje 2.1.1). Iznosi i stope imaju najviše dvije
 * decimale; inače se zahtjev ne šalje. Liste nikad nisu {@code null}.
 *
 * @param oib                 OIB obveznika fiskalizacije
 * @param uSustPdv            je li obveznik u sustavu PDV-a
 * @param datVrijeme          datum i vrijeme izdavanja po zagrebačkom vremenu
 * @param oznSlijed           razina slijednosti numeriranja
 * @param brRac               broj računa
 * @param pdv                 PDV po stopama
 * @param pnp                 porez na potrošnju po stopama
 * @param ostaliPor           ostali porezi
 * @param iznosOslobPdv       iznos oslobođen PDV-a, ili {@code null}
 * @param iznosMarza          iznos koji se oporezuje po posebnom postupku oporezivanja marže, ili {@code null}
 * @param iznosNePodlOpor     iznos koji ne podliježe oporezivanju, ili {@code null}
 * @param naknade             naknade, npr. povratna naknada
 * @param iznosUkupno         ukupni iznos računa
 * @param nacinPlac           način plaćanja
 * @param oibOper             OIB operatera na naplatnom uređaju
 * @param zastKod             zaštitni kod izdavatelja, ili {@code null} da ga klijent izračuna ({@link Zki})
 * @param nakDost             dostavlja li se račun naknadno, npr. nakon prekida veze
 * @param paragonBrRac        broj paragon bloka, ili {@code null}
 * @param specNamj            specifična namjena, ili {@code null}
 * @param oibPrimateljaRacuna OIB kupca, obvezan od 2026. za B2B račune plaćene gotovinom ili karticom, ili {@code null}
 */
public record Racun(
        String oib,
        boolean uSustPdv,
        LocalDateTime datVrijeme,
        OznakaSlijednosti oznSlijed,
        BrojRacuna brRac,
        List<Porez> pdv,
        List<Porez> pnp,
        List<PorezOstalo> ostaliPor,
        BigDecimal iznosOslobPdv,
        BigDecimal iznosMarza,
        BigDecimal iznosNePodlOpor,
        List<Naknada> naknade,
        BigDecimal iznosUkupno,
        NacinPlacanja nacinPlac,
        String oibOper,
        String zastKod,
        boolean nakDost,
        String paragonBrRac,
        String specNamj,
        String oibPrimateljaRacuna) {

    public Racun {
        Objects.requireNonNull(oib, "oib");
        Objects.requireNonNull(datVrijeme, "datVrijeme");
        Objects.requireNonNull(oznSlijed, "oznSlijed");
        Objects.requireNonNull(brRac, "brRac");
        Objects.requireNonNull(iznosUkupno, "iznosUkupno");
        Objects.requireNonNull(nacinPlac, "nacinPlac");
        Objects.requireNonNull(oibOper, "oibOper");
        pdv = pdv == null ? List.of() : List.copyOf(pdv);
        pnp = pnp == null ? List.of() : List.copyOf(pnp);
        ostaliPor = ostaliPor == null ? List.of() : List.copyOf(ostaliPor);
        naknade = naknade == null ? List.of() : List.copyOf(naknade);
    }

    /** Obvezni podaci; ostali su prazni dok se ne postave. */
    public static Builder builder(String oib, LocalDateTime datVrijeme, BrojRacuna brRac, BigDecimal iznosUkupno,
            NacinPlacanja nacinPlac, String oibOper) {
        return new Builder(oib, datVrijeme, brRac, iznosUkupno, nacinPlac, oibOper);
    }

    public static final class Builder {

        private final String oib;
        private final LocalDateTime datVrijeme;
        private final BrojRacuna brRac;
        private final BigDecimal iznosUkupno;
        private final NacinPlacanja nacinPlac;
        private final String oibOper;
        private boolean uSustPdv = true;
        private OznakaSlijednosti oznSlijed = OznakaSlijednosti.P;
        private List<Porez> pdv = List.of();
        private List<Porez> pnp = List.of();
        private List<PorezOstalo> ostaliPor = List.of();
        private BigDecimal iznosOslobPdv;
        private BigDecimal iznosMarza;
        private BigDecimal iznosNePodlOpor;
        private List<Naknada> naknade = List.of();
        private String zastKod;
        private boolean nakDost;
        private String paragonBrRac;
        private String specNamj;
        private String oibPrimateljaRacuna;

        private Builder(String oib, LocalDateTime datVrijeme, BrojRacuna brRac, BigDecimal iznosUkupno,
                NacinPlacanja nacinPlac, String oibOper) {
            this.oib = oib;
            this.datVrijeme = datVrijeme;
            this.brRac = brRac;
            this.iznosUkupno = iznosUkupno;
            this.nacinPlac = nacinPlac;
            this.oibOper = oibOper;
        }

        /** Zadano {@code true}. */
        public Builder uSustPdv(boolean uSustPdv) {
            this.uSustPdv = uSustPdv;
            return this;
        }

        /** Zadano {@link OznakaSlijednosti#P}. */
        public Builder oznSlijed(OznakaSlijednosti oznSlijed) {
            this.oznSlijed = oznSlijed;
            return this;
        }

        public Builder pdv(List<Porez> pdv) {
            this.pdv = pdv;
            return this;
        }

        public Builder pnp(List<Porez> pnp) {
            this.pnp = pnp;
            return this;
        }

        public Builder ostaliPor(List<PorezOstalo> ostaliPor) {
            this.ostaliPor = ostaliPor;
            return this;
        }

        public Builder iznosOslobPdv(BigDecimal iznosOslobPdv) {
            this.iznosOslobPdv = iznosOslobPdv;
            return this;
        }

        public Builder iznosMarza(BigDecimal iznosMarza) {
            this.iznosMarza = iznosMarza;
            return this;
        }

        public Builder iznosNePodlOpor(BigDecimal iznosNePodlOpor) {
            this.iznosNePodlOpor = iznosNePodlOpor;
            return this;
        }

        public Builder naknade(List<Naknada> naknade) {
            this.naknade = naknade;
            return this;
        }

        /** ZKI izračunat unaprijed, npr. da se ispiše na račun prije slanja; inače ga računa klijent. */
        public Builder zastKod(String zastKod) {
            this.zastKod = zastKod;
            return this;
        }

        public Builder nakDost(boolean nakDost) {
            this.nakDost = nakDost;
            return this;
        }

        public Builder paragonBrRac(String paragonBrRac) {
            this.paragonBrRac = paragonBrRac;
            return this;
        }

        public Builder specNamj(String specNamj) {
            this.specNamj = specNamj;
            return this;
        }

        public Builder oibPrimateljaRacuna(String oibPrimateljaRacuna) {
            this.oibPrimateljaRacuna = oibPrimateljaRacuna;
            return this;
        }

        public Racun build() {
            return new Racun(oib, uSustPdv, datVrijeme, oznSlijed, brRac, pdv, pnp, ostaliPor, iznosOslobPdv, iznosMarza,
                    iznosNePodlOpor, naknade, iznosUkupno, nacinPlac, oibOper, zastKod, nakDost, paragonBrRac, specNamj,
                    oibPrimateljaRacuna);
        }
    }
}
