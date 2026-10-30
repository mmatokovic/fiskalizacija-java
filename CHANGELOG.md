# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Before 1.0.0 the public API may change between minor versions.

## [Unreleased]

### Added
- Fiscalization messages: `EvidentirajERacun`, `EvidentirajNaplatu`, `EvidentirajOdbijanje`,
  `EvidentirajIsporukuZaKojuNijeIzdanERacun`.
- Reading UBL 2.1 eRačun (HR CIUS), XAdES-B request signing, response signature check up to the FINA CA,
  XSD validation before sending.
- JPMS module `dev.mmatokovic.fiskalizacija2`; the `dev.mmatokovic.fiskalizacija2.internal` package is not exported.
- `Oib.ispravan` checks an OIB's check digit (ISO 7064, MOD 11,10). `UblParser` reports an invalid OIB of the seller,
  buyer or operator as an error.
- Integration tests against the Porezna uprava test environment (`PoreznaTestnoOkruzenjeIT`), skipped unless a demo
  certificate and OIB are provided.
- JaCoCo test coverage report.
- `FiskalizacijaClient.provjeriOdgovor` checks the signature of an archived response, with the certificate chain
  validated as of the time the response was received (`Rezultat.primljeno()`).
- `pripremi*` methods return a signed, validated `PripremljeniZahtjev` without sending it; `posalji` sends it and
  `obradiOdgovor` processes a response received through your own HTTP client.
- `Builder.proxy` for a forward proxy and `Builder.potpisnikOdgovora` to choose which certificate may sign responses.
- `datumVrijemeSlanja` is always written in Europe/Zagreb time. A SOAP Fault's `faultstring` is part of the
  `FiskalizacijaException` message. `Rezultat.toString()` leaves out the raw SOAP messages.
- Only KPD (`CG`) item classifications are read from UBL, VAT total BT-110 is taken from the tax total in the document
  currency, and a missing exemption reason in the HR extension falls back to the standard VAT breakdown.

### Security
- The response signature must be a child of the response element and must cover that element, and the response must
  contain exactly one response element. This prevents XML signature wrapping, where an unsigned forged response
  is read next to a validly signed one.
- Signature verification only accepts same-document references, a fixed set of algorithms and transforms, and RSA
  keys of at least 2048 bits.
- A response without `idZahtjeva`, or with a different one, is rejected with `FiskalizacijaException`.
- By default only a certificate whose subject contains the Ministry of Finance OIB may sign responses; a valid FINA
  chain alone is not enough, since every taxpayer has a FINA certificate.
- Responses larger than 10 MB are rejected, and at most four transforms per reference are accepted.
- A referenced id must be unique in the whole document, so a copy of the signed element hidden inside the signature
  (for example in `ds:Object`) can no longer validate a forged response. At most five references are accepted.
- A malformed `redniBrojZapisa` in a response is reported as `FiskalizacijaException`.
