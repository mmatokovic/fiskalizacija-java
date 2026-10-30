# Contributing

## Build

```bash
./mvnw verify        # Windows: mvnw.cmd verify
```

Only JDK 17+ is required; the wrapper downloads Maven. Unit tests need no network and no real certificate.
`PoreznaTestnoOkruzenjeIT` calls the real Porezna uprava test environment and runs only when `FISK_P12`,
`FISK_P12_LOZINKA` and `FISK_OIB` are set (see the README). Coverage report: `fiskalizacija2-core/target/site/jacoco/index.html`.

## Rules

- `fiskalizacija2-core` must have no external dependencies, only the JDK.
- The public API is `dev.mmatokovic.fiskalizacija2` and `dev.mmatokovic.fiskalizacija2.model`. Everything else goes in
  `dev.mmatokovic.fiskalizacija2.internal`, which `module-info.java` does not export.
- Write javadoc where it tells the reader something the signature does not. Doclint checks syntax and references.
- Domain names (classes, fields, methods) stay in Croatian and follow the official XSD and specification terms.
- Every behaviour change comes with a test, preferably against an official Porezna uprava example in
  `src/test/resources/primjeri`.
- Record user-visible changes in [CHANGELOG.md](CHANGELOG.md) under `Unreleased`.
- The build must pass without compiler or javadoc warnings.

## Releasing

Releases are cut from `main` with a tag; the version comes from the tag, so nothing is committed back.

1. In a pull request, rename `## [Unreleased]` in `CHANGELOG.md` to `## [X.Y.Z] - YYYY-MM-DD` and add a new empty
   `## [Unreleased]` above it. Merge it.
2. Tag the merge commit on `main` and push the tag:

   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```

3. The `release` workflow checks that the tag is on `main` and that the changelog has the version, then builds,
   signs and uploads to Maven Central and creates a draft GitHub release with that changelog section.
4. In the Central Portal (Deployments), check the validated deployment and press **Publish**. A published version
   can never be removed from Maven Central.
5. Once it is on Maven Central, publish the draft GitHub release.

The workflow needs the repository secrets `CENTRAL_USERNAME`, `CENTRAL_TOKEN` (a Central Portal user token),
`GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`. It runs in the `release` environment, where a required reviewer can be added.
