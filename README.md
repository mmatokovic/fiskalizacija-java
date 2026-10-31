# fiskalizacija2-java

[![build](https://github.com/mmatokovic/fiskalizacija2-java/actions/workflows/build.yml/badge.svg)](https://github.com/mmatokovic/fiskalizacija2-java/actions/workflows/build.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Java 17+](https://img.shields.io/badge/Java-17%2B-orange)

[README na hrvatskom](README-HR.md)

Java client for Croatian **Fiskalizacija 2.0 (B2B)**: fiscalization of eRačun (e-invoices) and eIzvještavanje
(e-reporting) to Porezna uprava, the Croatian Tax Administration.

- All four messages: `EvidentirajERacun` (issued and received), `EvidentirajNaplatu`, `EvidentirajOdbijanje`, `EvidentirajIsporukuZaKojuNijeIzdanERacun`
- Reads UBL 2.1 eRačun (HR CIUS, Invoice and CreditNote, also inside a StandardBusinessDocument envelope)
- XAdES-B request signing and response signature verification, including the chain up to the FINA CA
- Validation against the official XSD schemas before sending, OIB check digit when reading UBL
- **No external dependencies**: only JDK 17+ (`java.xml`, `java.xml.crypto`, `java.net.http`)

> Status: 0.1.0-SNAPSHOT. Tested against the official Porezna uprava examples, **not yet tested against the
> Porezna uprava test environment** (see [Open questions](#open-questions)). Tests for the test environment exist,
> see [Test environment](#test-environment).

The API uses the Croatian names from the official specification and XSD schemas (`Racun` = invoice,
`Naplata` = payment collection, `Odbijanje` = rejection, `Potpisivac` = signer), so it maps one to one to the
Porezna uprava documentation.

## Installation

Not yet published to Maven Central. Until then, run `./mvnw install` and add:

```xml
<dependency>
    <groupId>dev.mmatokovic.fiskalizacija2</groupId>
    <artifactId>fiskalizacija2-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

For JPMS projects: `requires dev.mmatokovic.fiskalizacija2;`

## Example

```java
var potpisivac = Potpisivac.izP12(Path.of("application-certificate.p12"), password);
var client = FiskalizacijaClient.builder(Okruzenje.TEST, potpisivac).build();

Racun racun = UblParser.racun(Files.readAllBytes(Path.of("eracun.xml")));
Rezultat r = client.evidentirajERacun(VrstaERacuna.IZLAZNI, racun);

if (r.prihvacen()) {
    archive.save(r.idZahtjeva(), r.soapZahtjev(), r.soapOdgovor(), r.primljeno());
} else if (r.prihvacenBezProvjerePotpisa()) {
    log.error("Accepted, but the response signature is {}; do not resend", r.potpisOdgovora());
} else {
    log.warn("Porezna rejected the request: {} {}", r.greska().sifra(), r.greska().opis());
}
```

Payments and rejections need no UBL, only the invoice identifiers:

```java
client.evidentirajNaplatu(new Naplata("1-P1-1", LocalDate.of(2026, 3, 1), "12345678903", "11111111119",
        LocalDate.of(2026, 3, 15), new BigDecimal("125.00"), NacinPlacanja.T));
```

## Outcomes

| Situation | What happens |
|---|---|
| Porezna accepts the request and its signature is valid | `Rezultat` with `prihvacen() == true` |
| Porezna accepts the request, but its signature is missing or not trusted | `prihvacen() == false`, `prihvacenBezProvjerePotpisa() == true`: do not resend, check `potpisOdgovora()` |
| Porezna rejects the request (business error, e.g. `S009`) | `Rezultat` with `prihvacen() == false` and `greska()` |
| The request fails the XSD schema, or an amount has more than two decimals | `NeispravanZahtjevException`, nothing is sent |
| The UBL lacks mandatory fields | `UblException` with **all** errors found |
| Network, timeout, SOAP Fault, response that is not XML | `FiskalizacijaException` with HTTP status and raw response |
| Response without `idZahtjeva`, for a different one, or with several response elements | `FiskalizacijaException` (possible attack or a broken intermediary) |
| Signing fails, e.g. a key that is neither RSA nor EC | `IllegalStateException` |
| A `null` argument | `NullPointerException` |

`Rezultat` always holds the raw signed request and response (`soapZahtjev`, `soapOdgovor`) for archiving and for
analysing rejected messages. The response signature check is returned as `PotpisOdgovora`
(`ISPRAVAN`, `NEISPRAVAN`, `NEPOUZDAN_CERTIFIKAT`, `NEDOSTAJE`). `prihvacen()` is `true` only with `ISPRAVAN`.

Behind a corporate forward proxy, set it with `Builder.proxy(ProxySelector.of(...))`.

The library keeps no state and no database. For reliable delivery use the outbox pattern: store the invoice and the
message in one transaction, send, store the response, and retry failed sends within the legal deadlines.

## Sending with your own HTTP client

`pripremi*` methods sign and validate a request without sending it, for example to send it through your own
HTTP client, proxy or queue. Hand the response back to `obradiOdgovor`, which runs the same checks as a normal call.

```java
PripremljeniZahtjev zahtjev = client.pripremiNaplatu(List.of(naplata));
HttpResponse<String> http = myHttpClient.post(zahtjev.soap());
Rezultat r = client.obradiOdgovor(zahtjev, http.statusCode(), http.body());
```

Send `zahtjev.soap()` unchanged, otherwise the signature is no longer valid, as an HTTP `POST` with the headers
`Content-Type: application/xml; charset=utf-8` and `SOAPAction: ""`.

## Checking an archived response

The response signature can be checked again later, for example during a tax audit, from the archived
`soapOdgovor` and `primljeno` of a `Rezultat`. The certificate chain is checked as of the time the response was
received, because the Porezna certificate may have expired since.

```java
PotpisOdgovora potpis = client.provjeriOdgovor(r.soapOdgovor(), r.primljeno());
```

## Received eRačun

For invoices you don't control there is lenient parsing: errors are collected and fields that could not be read
stay `null`.

```java
var greske = new ArrayList<String>();
Racun racun = UblParser.racun(xml, greske);
```

## Open questions

These choices need confirming on the test environment:

1. Same service address for eFiskalizacija and eIzvještavanje (`:8511` test, `:8509` production).
2. The signature reference is `URI="#<id>"` with the enveloped transform; the official examples use `URI=""` with XPath Filter 2.0.
3. `Content-Type: application/xml; charset=utf-8` instead of the usual `text/xml` for SOAP 1.1.
4. The published response examples are signed with RSA-SHA1, which the JDK's `secureValidation` rejects and which
   cannot be allowed for a single context. It is therefore off, and its relevant rules are checked explicitly:
   same-document references only, an allow-list of algorithms and transforms, RSA keys of at least 2048 bits.
5. Every taxpayer has a FINA certificate, so a valid chain is not enough. By default the response signer's subject
   must contain the OIB of the Ministry of Finance (`18683136487`), which Porezna's TLS certificate carries. If Porezna
   signs responses with a different certificate, set your own check with `Builder.potpisnikOdgovora(...)`.

## Not done yet

- Spring Boot 4 starter (`fiskalizacija2-spring-boot-starter`)
- `OvlastenjaFiskalizacije` (fetching authorisations)
- Fiskalizacija 1.0 (B2C, CIS), including ZKI
- Publishing to Maven Central

## Modules and API

| Artifact | Contents |
|---|---|
| `fiskalizacija2-core` | fiscalization and eIzvještavanje messages, UBL parsing, signing; JDK only |
| `fiskalizacija2-eracun` | eRačun validation against UBL XSD, EN 16931 and HR CIUS rules (uses phax ph-ubl and phive-rules); in progress, not published yet |

`fiskalizacija2-core` is the JPMS module `dev.mmatokovic.fiskalizacija2`. The public API is the
`dev.mmatokovic.fiskalizacija2` and `dev.mmatokovic.fiskalizacija2.model` packages; the module does not export
`dev.mmatokovic.fiskalizacija2.internal`, which may change without notice. Before 1.0.0 the public API may change
between minor versions (see [CHANGELOG.md](CHANGELOG.md)).

## Build

```bash
./mvnw verify        # Windows: mvnw.cmd verify
```

Tests need no network and no real certificate: they use the official Porezna uprava examples
(`src/test/resources/primjeri`) and a self-signed test certificate. The official eRačun examples are dated 2025,
while the message schema only allows issue dates from 2026-01-01, so the tests move them to 2026.

The test coverage report is in `fiskalizacija2-core/target/site/jacoco/index.html`.

## Test environment

`PoreznaTestnoOkruzenjeIT` sends real messages to the Porezna uprava test environment: an issued eRačun followed by
a payment, a received eRačun followed by a rejection, and a supply without an eRačun. It needs a demo application
certificate (FINA Demo CA) and the OIB it was issued to; without them the tests are skipped.

```bash
FISK_P12=/path/demo.p12 FISK_P12_LOZINKA=... FISK_OIB=... ./mvnw verify -Dit.test=PoreznaTestnoOkruzenjeIT
```

```powershell
$env:FISK_P12='C:\path\demo.p12'; $env:FISK_P12_LOZINKA='...'; $env:FISK_OIB='...'; .\mvnw.cmd verify "-Dit.test=PoreznaTestnoOkruzenjeIT"
```

See [CONTRIBUTING.md](CONTRIBUTING.md) to contribute and [SECURITY.md](SECURITY.md) to report a vulnerability.

## License

Apache License 2.0, see [LICENSE](LICENSE).
