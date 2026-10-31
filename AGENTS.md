* Brevity is good. This applies to code, comments, commit messages and PR descriptions.
* Assume that the maintainers and readers of the code are experienced Java developers:
   * Don't use comments to explain the obvious.
   * Use self-explanatory names.
* `fiskalizacija-core` depends on the JDK only. Keep it that way.
* The public API is `dev.mmatokovic.fiskalizacija` (shared by both), `dev.mmatokovic.fiskalizacija.b2b` (Fiskalizacija 2.0) and `dev.mmatokovic.fiskalizacija.b2c` (Fiskalizacija 1.0). Everything else goes in `dev.mmatokovic.fiskalizacija.internal`, split by concern: `xml`, `potpis`, `http`, `b2b`, `b2c`. Never make something public that isn't needed outside the module.
* Work from the official sources only, not from other implementations: porezna.gov.hr (technical specifications, XSD schemas, examples), web.dzs.hr (KPD classification), ec.europa.eu (EN 16931, eInvoicing) and narodne-novine.nn.hr (Zakon o fiskalizaciji, pravilnici).
* Domain names are Croatian and follow the official XSD schemas and specification (`Racun`, `evidentirajERacun`). Don't translate them, and use standard Croatian (`obvezan`, not `obavezan`).
* Write javadoc only where it adds information the signature doesn't already give.
* Look for an existing helper (`internal.xml.Xml`, `internal.http.Postavke`, `UblParser.Citac`) before writing a new one.
* Never weaken the response signature check: the signature must cover the element the client reads.
* Check the OIB check digit when reading UBL (`UblParser`); before sending, the XSD only checks the format.
* In tests, prefer the official Porezna uprava examples in `src/test/resources/primjeri`. Name integration tests `*IT` without a `Test` prefix.
* Keep `README.md` and `README-HR.md` in sync, and record user-visible changes in `CHANGELOG.md`.
* Commit messages are a single short line in English.
* Trunk-based: commit small changes directly to `main`, and only after `./mvnw clean verify` passes locally. Releases are tags `vX.Y.Z` on `main` (see CONTRIBUTING.md); the version is `${revision}` and never committed.
* Use `./mvnw verify` when you're done.
