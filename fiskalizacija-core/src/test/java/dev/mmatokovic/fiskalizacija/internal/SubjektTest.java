package dev.mmatokovic.fiskalizacija.internal;

import dev.mmatokovic.fiskalizacija.b2c.B2cClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/** Certifikat {@code fiskalcistest} iz primjera 9.45 tehničke specifikacije v2.7. */
class SubjektTest {

    private final X509Certificate fiskalcistest = certifikat();

    @Test
    void oibIzOrganizationIdentifieraSeCitaKaoTekst() {
        assertThat(Subjekt.ime(fiskalcistest)).contains("organizationIdentifier=HR02994650199");
        assertThat(Subjekt.sadrziOib(B2cClient.OIB_APIS_IT)).accepts(fiskalcistest);
    }

    @Test
    void potpisnikTrebaIstiCnIOib() {
        assertThat(Subjekt.potpisnik("fiskalcistest", B2cClient.OIB_APIS_IT)).accepts(fiskalcistest);
        assertThat(Subjekt.potpisnik("fiskalcis", B2cClient.OIB_APIS_IT)).rejects(fiskalcistest);
        assertThat(Subjekt.potpisnik("fiskalcistest", "18683136487")).rejects(fiskalcistest);
    }

    private static X509Certificate certifikat() {
        try (var in = SubjektTest.class.getResourceAsStream("/primjeri/b2c/fiskalcistest.pem")) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
