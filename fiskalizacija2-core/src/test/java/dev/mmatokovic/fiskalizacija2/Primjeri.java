package dev.mmatokovic.fiskalizacija2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.TrustAnchor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public final class Primjeri {

    public static final String OIB = "12345678903";

    public static final Clock SAT = Clock.fixed(Instant.parse("2026-03-02T09:30:15.123Z"), ZoneId.of("Europe/Zagreb"));

    private Primjeri() {
    }

    public static Potpisivac potpisivac() {
        try (var in = Primjeri.class.getResourceAsStream("/test-potpis.p12")) {
            return Potpisivac.izP12(in, "test1234".toCharArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Set<TrustAnchor> sidra(Potpisivac p) {
        return Set.of(new TrustAnchor(p.certifikat(), null));
    }

    public static List<Path> eRacuni() {
        try (var s = Files.list(putanja("/primjeri/eracuni"))) {
            return s.sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Službeni primjeri eRačuna datirani su 2025., a sheme fiskalizacijskih poruka dopuštaju datum izdavanja
     * tek od 01.01.2026. Datumi se zato pomiču u 2026. Primjeri koriste i OIB {@code 12345678901}, koji nema
     * ispravnu kontrolnu znamenku, pa se zamjenjuje s {@value #OIB}.
     */
    public static byte[] eRacun(String ime) {
        return eRacun(putanja("/primjeri/eracuni/" + ime));
    }

    public static byte[] eRacun(Path p) {
        return tekst(p).replace(">2025-", ">2026-").replace("12345678901", OIB).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String tekst(String resurs) {
        return tekst(putanja(resurs));
    }

    private static String tekst(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path putanja(String resurs) {
        try {
            return Path.of(Primjeri.class.getResource(resurs).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
