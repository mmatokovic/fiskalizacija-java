# fiskalizacija2-java

[![build](https://github.com/mmatokovic/fiskalizacija2-java/actions/workflows/build.yml/badge.svg)](https://github.com/mmatokovic/fiskalizacija2-java/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Java 17+](https://img.shields.io/badge/Java-17%2B-orange)

[README in English](README.md)

Java klijent za **Fiskalizaciju 2.0 (B2B)**: evidentiranje eRačuna i eIzvještavanje prema Poreznoj upravi.

- Sve četiri poruke: `EvidentirajERacun` (izlazni i ulazni), `EvidentirajNaplatu`, `EvidentirajOdbijanje`, `EvidentirajIsporukuZaKojuNijeIzdanERacun`
- Čitanje UBL 2.1 eRačuna (HR CIUS, Invoice i CreditNote, i unutar StandardBusinessDocument omotnice)
- XAdES-B potpis zahtjeva i provjera potpisa odgovora, uključujući lanac do FINA CA
- Validacija prema službenim XSD shemama prije slanja, OIB s kontrolnom znamenkom pri čitanju UBL-a
- **Bez vanjskih ovisnosti**: samo JDK 17+ (`java.xml`, `java.xml.crypto`, `java.net.http`)

> Status: 0.1.0-SNAPSHOT. Testirano na službenim primjerima Porezne uprave, **još nije testirano protiv
> testnog okruženja Porezne uprave** (vidi [Otvorena pitanja](#otvorena-pitanja)). Testovi za testno okruženje
> postoje, vidi [Testno okruženje](#testno-okruženje).

## Instalacija

Još nije objavljeno na Maven Centralu. Do tada: `./mvnw install`, pa u projektu:

```xml
<dependency>
    <groupId>dev.mmatokovic.fiskalizacija2</groupId>
    <artifactId>fiskalizacija2-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Za JPMS projekte: `requires dev.mmatokovic.fiskalizacija2;`

## Primjer

```java
var potpisivac = Potpisivac.izP12(Path.of("aplikacijski-certifikat.p12"), lozinka);
var client = FiskalizacijaClient.builder(Okruzenje.TEST, potpisivac).build();

Racun racun = UblParser.racun(Files.readAllBytes(Path.of("eracun.xml")));
Rezultat r = client.evidentirajERacun(VrstaERacuna.IZLAZNI, racun);

if (r.prihvacen()) {
    arhiva.spremi(r.idZahtjeva(), r.soapZahtjev(), r.soapOdgovor());
} else {
    log.warn("Porezna je odbila zahtjev: {} {}", r.greska().sifra(), r.greska().opis());
}
```

Naplata i odbijanje ne trebaju UBL, dovoljni su identifikatori računa:

```java
client.evidentirajNaplatu(new Naplata("1-P1-1", LocalDate.of(2026, 3, 1), "12345678903", "11111111119",
        LocalDate.of(2026, 3, 15), new BigDecimal("125.00"), NacinPlacanja.T));
```

## Ishodi poziva

| Situacija | Što se dogodi |
|---|---|
| Porezna prihvati zahtjev | `Rezultat` s `prihvacen() == true` |
| Porezna odbije zahtjev (poslovna greška, npr. `S009`) | `Rezultat` s `prihvacen() == false` i `greska()` |
| Zahtjev ne prolazi XSD shemu | `NeispravanZahtjevException`, ništa se ne šalje |
| UBL nema obvezna polja | `UblException` sa **svim** pronađenim greškama |
| Mreža, timeout, SOAP Fault, odgovor koji nije XML | `FiskalizacijaException` s HTTP statusom i sirovim odgovorom |
| Odgovor za drugi `idZahtjeva` ili s više elemenata odgovora | `FiskalizacijaException` (mogući napad ili greška posrednika) |

`Rezultat` uvijek sadrži sirovi potpisani zahtjev i odgovor (`soapZahtjev`, `soapOdgovor`), za arhivu i za
analizu odbijenih poruka. Provjera potpisa odgovora vraća se kao `PotpisOdgovora`
(`ISPRAVAN`, `NEISPRAVAN`, `NEPOUZDAN_CERTIFIKAT`, `NEDOSTAJE`); library ne odlučuje umjesto vas što s tim napraviti.

Library ne drži stanje ni bazu. Za pouzdano slanje koristite outbox obrazac: spremite račun i poruku u istoj
transakciji, pošaljite, spremite odgovor, a neuspjela slanja ponavljajte u zakonskim rokovima.

## Provjera spremljenog odgovora

Potpis odgovora može se ponovno provjeriti i kasnije, npr. kod poreznog nadzora, iz spremljenog `soapOdgovor`.
Proslijedite trenutak primitka odgovora: lanac certifikata provjerava se na taj trenutak, jer je certifikat
Porezne u međuvremenu možda istekao.

```java
PotpisOdgovora potpis = client.provjeriOdgovor(arhiva.soapOdgovor(), arhiva.primljeno());
```

## Ulazni eRačuni

Za račune koje ne kontrolirate postoji tolerantno čitanje: greške se skupljaju, a polja koja se nisu mogla
pročitati ostaju `null`.

```java
var greske = new ArrayList<String>();
Racun racun = UblParser.racun(xml, greske);
```

## Otvorena pitanja

Ove odluke treba potvrditi na testnom okruženju:

1. Ista adresa servisa za eFiskalizaciju i eIzvještavanje (`:8511` test, `:8509` produkcija).
2. Referenca potpisa je `URI="#<id>"` s enveloped transformacijom; službeni primjeri koriste `URI=""` s XPath Filter 2.0.
3. `Content-Type: application/xml; charset=utf-8` umjesto uobičajenog `text/xml` za SOAP 1.1.
4. Objavljeni primjeri odgovora potpisani su RSA-SHA1, a JDK-ov `secureValidation` SHA-1 odbija i ne može se
   dopustiti samo za jedan kontekst. Zato je isključen, a njegova bitna pravila provjeravaju se eksplicitno: samo
   reference unutar dokumenta, popis dopuštenih algoritama i transformacija, RSA ključ od najmanje 2048 bita.
5. Provjera potpisa odgovora prihvaća bilo koji certifikat izdan pod FINA CA. Treba vidjeti kojim certifikatom
   Porezna stvarno potpisuje odgovore i dodati provjeru subjekta.

## Što još nije napravljeno

- Spring Boot 4 starter (`fiskalizacija2-spring-boot-starter`)
- `OvlastenjaFiskalizacije` (dohvat ovlaštenja)
- Fiskalizacija 1.0 (B2C, CIS), uključujući ZKI
- Objava na Maven Central

## Moduli i API

| Artefakt | Sadržaj |
|---|---|
| `fiskalizacija2-core` | fiskalizacijske poruke i eIzvještavanje, čitanje UBL-a, potpis; samo JDK |

`fiskalizacija2-core` je JPMS modul `dev.mmatokovic.fiskalizacija2`. Javni API su paketi `dev.mmatokovic.fiskalizacija2` i
`dev.mmatokovic.fiskalizacija2.model`; paket `dev.mmatokovic.fiskalizacija2.internal` modul ne izvozi i mijenja se bez najave.
Do verzije 1.0.0 javni API se može mijenjati između minor verzija (vidi [CHANGELOG.md](CHANGELOG.md)).

## Build

```bash
./mvnw verify        # Windows: mvnw.cmd verify
```

Testovi rade bez mreže i bez pravog certifikata: koriste službene primjere Porezne uprave
(`src/test/resources/primjeri`) i samopotpisani testni certifikat. Službeni primjeri eRačuna datirani su 2025.,
a shema fiskalizacijske poruke dopušta datum izdavanja tek od 01.01.2026., pa ih testovi pomiču u 2026.

Izvještaj o pokrivenosti testovima je u `fiskalizacija2-core/target/site/jacoco/index.html`.

## Testno okruženje

`PoreznaTestnoOkruzenjeIT` šalje stvarne poruke na testno okruženje Porezne uprave: izlazni eRačun pa naplatu,
ulazni eRačun pa odbijanje, i isporuku bez eRačuna. Treba demo aplikacijski certifikat (FINA Demo CA) i OIB na
koji je izdan; bez njih se testovi preskaču.

```bash
FISK_P12=/putanja/demo.p12 FISK_P12_LOZINKA=... FISK_OIB=... ./mvnw verify -Dit.test=PoreznaTestnoOkruzenjeIT
```

```powershell
$env:FISK_P12='C:\putanja\demo.p12'; $env:FISK_P12_LOZINKA='...'; $env:FISK_OIB='...'; .\mvnw.cmd verify "-Dit.test=PoreznaTestnoOkruzenjeIT"
```

Za doprinos vidi [CONTRIBUTING.md](CONTRIBUTING.md), a za prijavu ranjivosti [SECURITY.md](SECURITY.md) (oboje na engleskom).

## Licencija

MIT, vidi [LICENSE](LICENSE).
