package dev.mmatokovic.fiskalizacija.eracun;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ERacunValidatorTest {

    // Službeni primjer stariji od pravila 1.0.4, koja traže KPD klasifikaciju na svakoj stavci.
    private static final String BEZ_KPD = "Racun_za_leasing_PDV_PPMV_aktivacija.xml";

    @Test
    void sluzbeniPrimjeriSuIspravni() throws IOException {
        try (var s = Files.list(putanja("/primjeri/eracuni"))) {
            for (var p : s.sorted().toList()) {
                if (p.getFileName().toString().equals(BEZ_KPD)) {
                    continue;
                }
                var r = ERacunValidator.validiraj(uskladi(Files.readString(p)).getBytes(StandardCharsets.UTF_8));
                assertTrue(r.ispravan(), p.getFileName() + ": " + r.greske());
            }
        }
    }

    @Test
    void primjerBezKpdKlasifikacijeKrsiHrBr25() {
        var r = ERacunValidator.validiraj(primjer(BEZ_KPD).getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of("HR-BR-25"), r.greske().stream().map(Nalaz::pravilo).toList());
    }

    @Test
    void greskaNavodiPraviloILokaciju() {
        String xml = primjer("eRacun_PDV25.xml").replace("<cbc:ID>1-P1-1</cbc:ID>", "<cbc:ID></cbc:ID>");

        var r = ERacunValidator.validiraj(xml.getBytes(StandardCharsets.UTF_8));

        assertFalse(r.ispravan());
        assertTrue(r.greske().stream().allMatch(n -> n.poruka() != null && !n.poruka().isBlank()), r.greske().toString());
        assertTrue(r.greske().stream().anyMatch(n -> n.pravilo() != null && n.lokacija() != null), r.greske().toString());
    }

    @Test
    void eRacunUStandardBusinessDocumentOmotnici() {
        String ubl = primjer("eRacun_PDV25.xml").replaceFirst("<\\?xml[^>]*\\?>", "");
        String sbd = "<StandardBusinessDocument xmlns=\"http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader\">"
                + "<StandardBusinessDocumentHeader/>" + ubl + "</StandardBusinessDocument>";

        assertTrue(ERacunValidator.validiraj(sbd.getBytes(StandardCharsets.UTF_8)).ispravan());
    }

    @Test
    void dokumentKojiNijeERacun() {
        assertEquals(1, ERacunValidator.validiraj("nije xml".getBytes(StandardCharsets.UTF_8)).greske().size());
        assertEquals(List.of("Dokument ne sadrži UBL Invoice ni CreditNote"),
                ERacunValidator.validiraj("<a/>".getBytes(StandardCharsets.UTF_8)).greske().stream().map(Nalaz::poruka).toList());
    }

    private static String primjer(String ime) {
        try {
            return uskladi(Files.readString(putanja("/primjeri/eracuni/" + ime)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Službeni primjeri datirani su 2025., a HR-BR-40 traži datum izdavanja od 2026., i koriste OIB 12345678901 s
     * neispravnom kontrolnom znamenkom (HR-BR-9, HR-BR-53).
     */
    private static String uskladi(String xml) {
        return xml.replace(">2025-", ">2026-").replace("12345678901", "12345678903");
    }

    private static Path putanja(String resurs) {
        try {
            return Path.of(Objects.requireNonNull(ERacunValidatorTest.class.getResource(resurs), resurs).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
