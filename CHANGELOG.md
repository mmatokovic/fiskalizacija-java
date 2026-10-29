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

### Security
- The response signature must be a child of the response element and must cover that element, and the response must
  contain exactly one response element. This prevents XML signature wrapping, where an unsigned forged response
  is read next to a validly signed one.
- Signature verification only accepts same-document references, a fixed set of algorithms and transforms, and RSA
  keys of at least 2048 bits.
- A response whose `idZahtjeva` differs from the sent request is rejected with `FiskalizacijaException`.
