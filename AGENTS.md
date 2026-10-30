# AGENTS.md

Java library for Croatian Fiskalizacija 2.0 (B2B): fiscalization messages and eIzvještavanje sent to
Porezna uprava, plus reading UBL 2.1 eRačun (HR CIUS). See README.md for the user-facing view.

## Build and test

```bash
./mvnw verify            # Windows: mvnw.cmd verify
./mvnw -pl fiskalizacija2-core test -Dtest=ZahtjeviTest
```

JDK 17+. Unit tests need no network and no real certificate. `*IT` tests (Failsafe) call the real Porezna test
environment and are skipped unless `FISK_P12`, `FISK_P12_LOZINKA` and `FISK_OIB` are set; name integration tests
`*IT` without a `Test` prefix, because Surefire also runs `Test*.java`. The build must stay free of compiler and javadoc warnings.

## Layout

```
fiskalizacija2-core/                          JDK only, no external dependencies (hard rule)
  src/main/java/module-info.java              JPMS module dev.mmatokovic.fiskalizacija2
  dev/mmatokovic/fiskalizacija2/              public API: FiskalizacijaClient, Potpisivac, UblParser, Rezultat, exceptions
  dev/mmatokovic/fiskalizacija2/model/        public API: immutable records mirroring the official XSD names
  dev/mmatokovic/fiskalizacija2/internal/     not exported: message building, XSD validation, signature check, XML helpers
  src/main/resources/.../internal/xsd/        official XSD schemas
  src/main/resources/.../ca/                  bundled FINA CA certificates
  src/test/resources/primjeri/                official Porezna uprava examples, used as test fixtures
```

Planned modules, in this order: `fiskalizacija2-eracun` (UBL HR CIUS builder, validation, ApplicationResponse),
`fiskalizacija2-spring-boot-starter`, then `fiskalizacija2-razmjena` (AMS/MPS/AS4) or `fiskalizacija2-posrednici`.
Only modules other than core may use external libraries (phax ecosystem). Add a `fiskalizacija2-bom` module once a
second artifact exists.

## Conventions

- Domain names (classes, fields, methods) are Croatian and follow the official XSD and specification terms
  (`Racun`, `Naplata`, `evidentirajERacun`). Do not translate them.
- New implementation classes go in `internal`. Anything added to an exported package is public API: keep it
  minimal and immutable where possible. Doclint runs with all checks, so every public type and member needs javadoc.
- Docs language: README.md in English, README-HR.md in Croatian (keep both in sync), javadoc in Croatian;
  CONTRIBUTING.md, SECURITY.md and CHANGELOG.md in English.
- Response signature checks must stay bound to the element the client reads (see `internal/ProvjeraPotpisa`);
  never search the whole document for a response or signature.
- OIB check digit (`Oib.ispravan`) is applied only where the value is known to be a Croatian OIB (UBL CompanyID with
  HR prefix, EndpointID 9934, operator). `oibPorezniBroj` in messages may be a foreign tax number, so never check it
  before sending.
- Amounts with more than two decimals are rejected, never silently rounded. Messages are XSD-validated before sending.
- Business rejection by Porezna is a `Rezultat` with `prihvacen() == false`, not an exception.
- Every behaviour change needs a test, preferably against an official example in `primjeri/`.
- Record user-visible changes in CHANGELOG.md under `Unreleased`.
- Sources and attributions for borrowed material go in the Credits / Zasluge section of both READMEs (and LICENSE for MIT code).

## Open questions

Not yet tested against the Porezna test environment; see "Open questions" in README.md before changing
endpoint, signature reference, Content-Type or signature-algorithm handling.
