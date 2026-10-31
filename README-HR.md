# fiskalizacija-java

[![build](https://github.com/mmatokovic/fiskalizacija-java/actions/workflows/build.yml/badge.svg)](https://github.com/mmatokovic/fiskalizacija-java/actions/workflows/build.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Java 17+](https://img.shields.io/badge/Java-17%2B-orange)

[README in English](README.md)

Java klijent za fiskalizaciju prema Poreznoj upravi: **Fiskalizacija 2.0 (B2B)**, odnosno evidentiranje eRačuna i
eIzvještavanje, i **Fiskalizacija 1.0 (B2C)**, odnosno fiskalizacija računa u krajnjoj potrošnji.

- Sve četiri poruke: `EvidentirajERacun` (izlazni i ulazni), `EvidentirajNaplatu`, `EvidentirajOdbijanje`, `EvidentirajIsporukuZaKojuNijeIzdanERacun`
- Čitanje UBL 2.1 eRačuna (HR CIUS, Invoice i CreditNote, i unutar StandardBusinessDocument omotnice)
- Fiskalizacija 1.0 (B2C): računi sa ZKI-jem i JIR-om, poveznica za QR kod, promjena podataka o plaćanju, napojnice i radno vrijeme poslovnog prostora (tehnička specifikacija v2.7)
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
    <groupId>dev.mmatokovic.fiskalizacija</groupId>
    <artifactId>fiskalizacija-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Za JPMS projekte: `requires dev.mmatokovic.fiskalizacija;`

## Primjer

```java
var potpisivac = Potpisivac.izP12(Path.of("aplikacijski-certifikat.p12"), lozinka);
var client = FiskalizacijaClient.builder(Okruzenje.TEST, potpisivac).build();

Racun racun = UblParser.racun(Files.readAllBytes(Path.of("eracun.xml")));
Rezultat r = client.evidentirajERacun(VrstaERacuna.IZLAZNI, racun);

if (r.prihvacen()) {
    arhiva.spremi(r.idZahtjeva(), r.soapZahtjev(), r.soapOdgovor(), r.primljeno());
} else if (r.prihvacenBezProvjerePotpisa()) {
    log.error("Prihvaćeno, ali potpis odgovora je {}; ne šaljite ponovno", r.potpisOdgovora());
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
| Porezna prihvati zahtjev i potpis odgovora je ispravan | `Rezultat` s `prihvacen() == true` |
| Porezna prihvati zahtjev, ali potpis odgovora nedostaje ili nije pouzdan | `prihvacen() == false`, `prihvacenBezProvjerePotpisa() == true`: ne šaljite ponovno, provjerite `potpisOdgovora()` |
| Porezna odbije zahtjev (poslovna greška, npr. `S009`) | `Rezultat` s `prihvacen() == false` i `greska()` |
| Zahtjev ne prolazi XSD shemu ili iznos ima više od dvije decimale | `NeispravanZahtjevException`, ništa se ne šalje |
| UBL nema obvezna polja | `UblException` sa **svim** pronađenim greškama |
| Mreža, timeout, SOAP Fault, odgovor koji nije XML | `FiskalizacijaException` s HTTP statusom i sirovim odgovorom |
| Odgovor bez `idZahtjeva`, za drugi `idZahtjeva` ili s više elemenata odgovora | `FiskalizacijaException` (mogući napad ili greška posrednika) |
| Potpisivanje ne uspije, npr. ključ koji nije RSA ni EC | `IllegalStateException` |
| `null` argument | `NullPointerException` |

`Rezultat` uvijek sadrži sirovi potpisani zahtjev i odgovor (`soapZahtjev`, `soapOdgovor`), za arhivu i za
analizu odbijenih poruka. Provjera potpisa odgovora vraća se kao `PotpisOdgovora`
(`ISPRAVAN`, `NEISPRAVAN`, `NEPOUZDAN_CERTIFIKAT`, `NEDOSTAJE`). `prihvacen()` je `true` samo uz `ISPRAVAN`.

Iza korporativnog forward proxyja postavite ga s `Builder.proxy(ProxySelector.of(...))`.

Library ne drži stanje ni bazu. Za pouzdano slanje koristite outbox obrazac: spremite račun i poruku u istoj
transakciji, pošaljite, spremite odgovor, a neuspjela slanja ponavljajte u zakonskim rokovima.

## Slanje vlastitim HTTP klijentom

Metode `pripremi*` potpisuju i validiraju zahtjev bez slanja, npr. za slanje vlastitim HTTP klijentom, proxyjem
ili kroz red poruka. Odgovor se vraća metodi `obradiOdgovor`, koja radi iste provjere kao običan poziv.

```java
PripremljeniZahtjev zahtjev = client.pripremiNaplatu(List.of(naplata));
HttpResponse<String> http = mojHttpKlijent.post(zahtjev.soap());
Rezultat r = client.obradiOdgovor(zahtjev, http.statusCode(), http.body());
```

`zahtjev.soap()` šaljite bez izmjena, inače potpis više ne vrijedi, kao HTTP `POST` s headerima
`Content-Type: application/xml; charset=utf-8` i `SOAPAction: ""`.

## Provjera spremljenog odgovora

Potpis odgovora može se ponovno provjeriti i kasnije, npr. kod poreznog nadzora, iz spremljenih `soapOdgovor` i
`primljeno` iz `Rezultat`. Lanac certifikata provjerava se na trenutak primitka odgovora, jer je certifikat Porezne
u međuvremenu možda istekao.

```java
PotpisOdgovora potpis = client.provjeriOdgovor(r.soapOdgovor(), r.primljeno());
```

## Ulazni eRačuni

Za račune koje ne kontrolirate postoji tolerantno čitanje: greške se skupljaju, a polja koja se nisu mogla
pročitati ostaju `null`.

```java
var greske = new ArrayList<String>();
Racun racun = UblParser.racun(xml, greske);
```

## Fiskalizacija 1.0 (B2C)

Računi u krajnjoj potrošnji, i B2B računi plaćeni gotovinom ili karticom, fiskaliziraju se s `B2cClient` iz paketa
`dev.mmatokovic.fiskalizacija.b2c`, prema tehničkoj specifikaciji v2.7 i shemi f73.

```java
var client = B2cClient.builder(Okruzenje.TEST, potpisivac).build();

var racun = Racun.builder("12345678903", LocalDateTime.now(ZoneId.of("Europe/Zagreb")),
                new BrojRacuna("1", "POSL1", "1"), new BigDecimal("12.50"), NacinPlacanja.K, "12345678903")
        .pdv(List.of(new Porez(new BigDecimal("25.00"), new BigDecimal("10.00"), new BigDecimal("2.50"))))
        .build();

B2cRezultat r = client.racun(racun);
String qr = QrKod.poveznica(r, racun);   // s JIR-om, ili sa ZKI-jem ako JIR-a nema
```

- Klijent izračuna ZKI (`Zki.izracunaj`) ako ga račun nema; u svakom slučaju je u `r.zastKod()`.
- Bez JIR-a kupcu se izdaje račun sa ZKI-jem, a račun se naknadno šalje s `nakDost(true)`.
- Zahtjevi se potpisuju RSA-SHA256: testno okruženje RSA-SHA1 odbija od 1. 7. 2026., a produkcija od 1. 1. 2027.
- `prihvacen()` je `true` samo bez grešaka i uz ispravan potpis odgovora, kao kod Fiskalizacije 2.0.

Podržane su sve operacije iz specifikacije:

| Metoda | Što radi |
|---|---|
| `racun` | fiskalizira račun, vraća JIR |
| `promijeniPodatkeRacuna` | mijenja način plaćanja i OIB kupca isti dan; `null` OIB ga briše |
| `promijeniNacPlac` | mijenja samo način plaćanja; specifikacija je najavljuje za ukidanje |
| `napojnica` | prijavljuje napojnicu uz fiskalizirani račun, najkasnije dva dana nakon izdavanja |
| `prijaviRadnoVrijeme`, `obrisiRadnoVrijeme`, `dohvatiRadnoVrijeme` | radno vrijeme poslovnog prostora |
| `prijaviRadnoVrijemeZaPoslovnice` | radno vrijeme za do 100 poslovnih prostora, s ishodom za svaki u `poslovnice()` |
| `provjera` | provjerava račun bez evidentiranja; samo testno okruženje |
| `echo` | provjerava je li servis dostupan |

## Otvorena pitanja

Ove odluke treba potvrditi na testnom okruženju:

1. Ista adresa servisa za eFiskalizaciju i eIzvještavanje (`:8511` test, `:8509` produkcija).
2. Referenca potpisa je `URI="#<id>"` s enveloped transformacijom; službeni primjeri koriste `URI=""` s XPath Filter 2.0.
3. `Content-Type: application/xml; charset=utf-8` umjesto uobičajenog `text/xml` za SOAP 1.1.
4. Objavljeni primjeri odgovora potpisani su RSA-SHA1, a JDK-ov `secureValidation` SHA-1 odbija i ne može se
   dopustiti samo za jedan kontekst. Zato je isključen, a njegova bitna pravila provjeravaju se eksplicitno: samo
   reference unutar dokumenta, popis dopuštenih algoritama i transformacija, RSA ključ od najmanje 2048 bita.
5. Svaki porezni obveznik ima FINA certifikat, pa ispravan lanac nije dovoljan. Zadano subjekt potpisnika odgovora
   mora sadržavati OIB Ministarstva financija (`18683136487`), kakav ima i Poreznin TLS certifikat. Ako Porezna
   odgovore potpisuje drugim certifikatom, postavite vlastitu provjeru s `Builder.potpisnikOdgovora(...)`.
6. B2C odgovori: zadano potpisnik mora biti `CN=fiskalcis` (u testu `fiskalcistest`, kao u poglavlju 5
   specifikacije) s OIB-om APIS IT-a (`02994650199`) u subjektu. OIB se čita iz atributa organizationIdentifier,
   kao u službenom certifikatu `fiskalcistest`.
7. B2C `prijaviRadnoVrijeme` šalje se sa SOAPAction od `promijeniPodatkeRacuna`, kao u službenom WSDL-u v1.10.

## Što još nije napravljeno

- Spring Boot 4 starter (`fiskalizacija-spring-boot-starter`)
- `OvlastenjaFiskalizacije` (dohvat ovlaštenja)
- Objava na Maven Central

## Moduli i API

| Artefakt | Sadržaj |
|---|---|
| `fiskalizacija-core` | poruke Fiskalizacije 2.0 i 1.0, čitanje UBL-a, potpis; samo JDK |
| `fiskalizacija-eracun` | validacija eRačuna prema UBL XSD-u, EN 16931 i HR CIUS pravilima (koristi phax ph-ubl i phive-rules); u izradi, još se ne objavljuje |
| `fiskalizacija-examples` | pokretljivi primjeri; ne objavljuje se |

`fiskalizacija-core` je JPMS modul `dev.mmatokovic.fiskalizacija`. Javni API su paketi `dev.mmatokovic.fiskalizacija`,
`dev.mmatokovic.fiskalizacija.model` i `dev.mmatokovic.fiskalizacija.b2c`; paket `dev.mmatokovic.fiskalizacija.internal` modul ne izvozi i mijenja se bez najave.
Do verzije 1.0.0 javni API se može mijenjati između minor verzija (vidi [CHANGELOG.md](CHANGELOG.md)).

## Build

```bash
./mvnw verify        # Windows: mvnw.cmd verify
```

Testovi rade bez mreže i bez pravog certifikata: koriste službene primjere Porezne uprave
(`src/test/resources/primjeri`) i samopotpisani testni certifikat. Službeni primjeri eRačuna datirani su 2025.,
a shema fiskalizacijske poruke dopušta datum izdavanja tek od 01.01.2026., pa ih testovi pomiču u 2026.

Izvještaj o pokrivenosti testovima je u `fiskalizacija-core/target/site/jacoco/index.html`.

## Testno okruženje

`PoreznaTestnoOkruzenjeIT` šalje stvarne poruke na testno okruženje Porezne uprave: izlazni eRačun pa naplatu,
ulazni eRačun pa odbijanje, i isporuku bez eRačuna. Treba demo aplikacijski certifikat (FINA Demo CA) i OIB na
koji je izdan; bez njih se testovi preskaču.

`B2cTestnoOkruzenjeIT` radi isto za Fiskalizaciju 1.0 (`-Dit.test=B2cTestnoOkruzenjeIT`). Računi i radno vrijeme trebaju
još `FISK_OZN_POS_PR`, poslovni prostor otvoren na OIB certifikata u testnom okruženju.

```bash
FISK_P12=/putanja/demo.p12 FISK_P12_LOZINKA=... FISK_OIB=... ./mvnw verify -Dit.test=PoreznaTestnoOkruzenjeIT
```

```powershell
$env:FISK_P12='C:\putanja\demo.p12'; $env:FISK_P12_LOZINKA='...'; $env:FISK_OIB='...'; .\mvnw.cmd verify "-Dit.test=PoreznaTestnoOkruzenjeIT"
```

## Primjeri

`fiskalizacija-examples` sadrži pokretljive programe u paketu `dev.mmatokovic.fiskalizacija.primjeri`:

| Klasa | Što radi |
|---|---|
| `ValidacijaERacuna` | validira eRačun prema UBL XSD-u, EN 16931 i HR CIUS-u; ne treba certifikat |
| `EvidentiranjeERacuna` | validira izlazni eRačun i fiskalizira ga na testnom okruženju |
| `EvidentiranjeNaplate` | fiskalizira izlazni eRačun pa njegovu naplatu |
| `FiskalizacijaRacuna` | fiskalizira račun Fiskalizacije 1.0 i ispisuje JIR, ZKI i poveznicu za QR kod; treba i `FISK_OZN_POS_PR` |

Svaki prima putanju do eRačuna kao neobvezan argument. Bez nje koriste `primjer-eracun.xml` s OIB-om iz
`FISK_OIB`, novim brojem računa i današnjim datumom. Primjeri fiskalizacije trebaju iste varijable kao testno
okruženje iznad.

```bash
./mvnw install -DskipTests
./mvnw -pl fiskalizacija-examples exec:java -Dexec.mainClass=dev.mmatokovic.fiskalizacija.primjeri.ValidacijaERacuna
```

Za doprinos vidi [CONTRIBUTING.md](CONTRIBUTING.md), a za prijavu ranjivosti [SECURITY.md](SECURITY.md) (oboje na engleskom).

## Licencija

Apache License 2.0, vidi [LICENSE](LICENSE).
