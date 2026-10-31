package dev.mmatokovic.fiskalizacija.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Stavka računa (BG-25).
 *
 * @param kolicina                             količina (BT-129)
 * @param jedinicaMjere                        jedinica mjere (BT-130)
 * @param neto                                 neto iznos stavke (BT-131)
 * @param artiklNetoCijena                     neto cijena artikla (BT-146)
 * @param artiklBrutoCijena                    bruto cijena artikla (BT-148), ili {@code null}
 * @param artiklOsnovnaKolicina                osnovna količina za cijenu (BT-149), ili {@code null}
 * @param artiklJedinicaMjereZaOsnovnuKolicinu jedinica mjere osnovne količine (BT-150), ili {@code null}
 * @param artiklKategorijaPdv                  kategorija PDV-a stavke (BT-151)
 * @param artiklStopaPdv                       stopa PDV-a stavke (BT-152), ili {@code null}
 * @param artiklNaziv                          naziv artikla (BT-153)
 * @param artiklOpis                           opis artikla (BT-154), ili {@code null}
 * @param artiklHrOznakaKategorijaPdv          HR oznaka kategorije PDV-a stavke, ili {@code null}
 * @param klasifikacije                        klasifikacije artikla, npr. KPD (BT-158)
 */
public record Stavka(
        BigDecimal kolicina,
        String jedinicaMjere,
        BigDecimal neto,
        BigDecimal artiklNetoCijena,
        BigDecimal artiklBrutoCijena,
        BigDecimal artiklOsnovnaKolicina,
        String artiklJedinicaMjereZaOsnovnuKolicinu,
        String artiklKategorijaPdv,
        BigDecimal artiklStopaPdv,
        String artiklNaziv,
        String artiklOpis,
        String artiklHrOznakaKategorijaPdv,
        List<KlasifikacijaArtikla> klasifikacije) {

    public Stavka {
        klasifikacije = List.copyOf(Objects.requireNonNull(klasifikacije, "klasifikacije"));
    }
}
