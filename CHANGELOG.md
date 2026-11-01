# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Before 1.0.0 the public API may change between minor versions.

## [Unreleased]

### Added
- Fiscalization messages: `EvidentirajERacun`, `EvidentirajNaplatu`, `EvidentirajOdbijanje`,
  `EvidentirajIsporukuZaKojuNijeIzdanERacun`.
- Reading UBL 2.1 eRačun (HR CIUS), XAdES-B request signing, response signature check up to the FINA CA,
  XSD validation before sending.
- JPMS module `dev.mmatokovic.fiskalizacija` with the shared classes in `dev.mmatokovic.fiskalizacija`, Fiskalizacija 2.0
  in `dev.mmatokovic.fiskalizacija.b2b` (`B2bClient`) and 1.0 in `dev.mmatokovic.fiskalizacija.b2c` (`B2cClient`); the
  `dev.mmatokovic.fiskalizacija.internal` packages are not exported.
- `Oib.ispravan` checks an OIB's check digit (ISO 7064, MOD 11,10). `UblParser` reports an invalid OIB of the seller,
  buyer or operator as an error.
- Integration tests against the Porezna uprava test environment (`B2bTestnoOkruzenjeIT`), skipped unless a demo
  certificate and OIB are provided.
- JaCoCo test coverage report.
- `B2bClient.provjeriOdgovor` checks the signature of an archived response, with the certificate chain
  validated as of the time the response was received (`Rezultat.primljeno()`).
- `pripremi*` methods return a signed, validated `PripremljeniZahtjev` without sending it; `posalji` sends it and
  `obradiOdgovor` processes a response received through your own HTTP client.
- `fiskalizacija-eracun` module (not published yet): `ERacunValidator` checks an eRačun against the UBL XSD, EN 16931
  and the HR CIUS rules. `Iznosi.izracunaj` computes line, VAT breakdown and document totals from `StavkaRacuna`
  lines by the EN 16931 rules (VAT per category and rate on the summed base, rounded half up to the cent).
- `Builder.proxy` for a forward proxy and `Builder.potpisnikOdgovora` to choose which certificate may sign responses.
- Fiskalizacija 1.0 (B2C) in the `dev.mmatokovic.fiskalizacija.b2c` package, following technical specification v2.7:
  `B2cClient` fiscalizes invoices (`racun`, returns the JIR), changes payment data (`promijeniPodatkeRacuna`,
  `promijeniNacPlac`), reports tips (`napojnica`), manages business premises working hours (`prijaviRadnoVrijeme`,
  `obrisiRadnoVrijeme`, `dohvatiRadnoVrijeme`, `prijaviRadnoVrijemeZaPoslovnice`) and supports `provjera` and `echo`.
  Requests are signed with RSA-SHA256 and validated against the f73 schema; responses must be signed by
  `fiskalcis`/`fiskalcistest`. `Zki` computes the ZKI and `QrKod` the link for the QR code on the invoice.
- `fiskalizacija-spring-boot-starter`: Spring Boot 4 auto-configuration of `Potpisivac`, `B2bClient` and `B2cClient`
  from `fiskalizacija.*` properties, with `B2bClientCustomizer` and `B2cClientCustomizer`.
- The default response signer check accepts the OIB only in the subject's organizationIdentifier or serialNumber,
  where FINA puts it, and no longer anywhere in the subject text.
- XAdES signing is no longer public API (`Potpisivac.potpisi` removed); its signing time follows the client's `sat`.
- `Zki.izracunaj` throws `NeispravanZahtjevException` for an amount with more than two decimals, as the clients do.
- Fiskalizacija 2.0 follows its technical specification: test address `cistest.apis-it.hr:8509/FiskalizacijaServiceEprod`,
  `Content-Type: text/xml`, XAdES with `IssuerSerialV2` and `DataObjectFormat` as in its examples, and no item
  classification on credit notes (381) and prepayment invoices (386). `Okruzenje` gives both addresses
  (`adresaB2b()`, `adresaB2c()`) and the response signer.
- `datumVrijemeSlanja` is always written in Europe/Zagreb time. A SOAP Fault's `faultstring` is part of the
  `FiskalizacijaException` message. `Rezultat.toString()` leaves out the raw SOAP messages.
- Only KPD (`CG`) item classifications are read from UBL, VAT total BT-110 is taken from the tax total in the document
  currency, and a missing exemption reason in the HR extension falls back to the standard VAT breakdown.

### Security
- The response signature must be a child of the response element and must cover that element, and the response must
  contain exactly one response element. This prevents XML signature wrapping, where an unsigned forged response
  is read next to a validly signed one.
- Signature verification only accepts same-document references, a fixed set of algorithms and transforms, RSA
  keys of at least 2048 bits and EC keys of at least 256 bits.
- A response without `idZahtjeva` is rejected with `FiskalizacijaException`. Porezna generates `idZahtjeva`, so it is
  not compared with the request's `id`.
- By default only APIS IT's application certificate `fiskalcis` (test: `fiskalcistest`) may sign responses, as the
  specifications say; a valid FINA chain alone is not enough, since every taxpayer has a FINA certificate.
- Responses larger than 10 MB are rejected, and at most four transforms per reference are accepted.
- `Rezultat.prihvacen()` is `true` only when the response signature is `ISPRAVAN`; `prihvacenBezProvjerePotpisa()`
  tells what the response says regardless of the signature.
- A referenced id must be unique in the whole document, so a copy of the signed element hidden inside the signature
  (for example in `ds:Object`) can no longer validate a forged response. At most five references are accepted.
- A malformed `redniBrojZapisa` in a response is reported as `FiskalizacijaException`.
