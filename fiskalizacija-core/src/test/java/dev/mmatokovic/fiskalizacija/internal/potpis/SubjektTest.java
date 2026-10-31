package dev.mmatokovic.fiskalizacija.internal.potpis;

import dev.mmatokovic.fiskalizacija.b2b.B2bClient;
import dev.mmatokovic.fiskalizacija.b2c.B2cClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code fiskalcistest} je iz primjera 9.45 tehničke specifikacije v2.7, {@code cis.porezna-uprava.hr} je TLS certifikat
 * servisa, a lažni certifikat ima oba OIB-a samo u CN-u i OU-u.
 */
class SubjektTest {

    private final X509Certificate fiskalcistest = certifikat("fiskalcistest.pem");
    private final X509Certificate porezna = certifikat("cis.porezna-uprava.hr.pem");
    private final X509Certificate lazni = certifikat("oib-izvan-atributa.pem");

    @Test
    void oibIzOrganizationIdentifiera() {
        assertThat(Subjekt.sadrziOib(B2cClient.OIB_APIS_IT)).accepts(fiskalcistest);
    }

    @Test
    void oibIzSerialNumbera() {
        assertThat(Subjekt.sadrziOib(B2bClient.OIB_MINISTARSTVA_FINANCIJA)).accepts(porezna);
    }

    @Test
    void oibIzvanTihAtributaSeNePrihvaca() {
        assertThat(Subjekt.sadrziOib(B2bClient.OIB_MINISTARSTVA_FINANCIJA)).rejects(lazni);
        assertThat(Subjekt.sadrziOib(B2cClient.OIB_APIS_IT)).rejects(lazni);
        assertThat(Subjekt.potpisnik("18683136487 VATHR-18683136487", B2bClient.OIB_MINISTARSTVA_FINANCIJA)).rejects(lazni);
    }

    @Test
    void potpisnikTrebaIstiCnIOib() {
        assertThat(Subjekt.potpisnik("fiskalcistest", B2cClient.OIB_APIS_IT)).accepts(fiskalcistest);
        assertThat(Subjekt.potpisnik("fiskalcis", B2cClient.OIB_APIS_IT)).rejects(fiskalcistest);
        assertThat(Subjekt.potpisnik("fiskalcistest", B2bClient.OIB_MINISTARSTVA_FINANCIJA)).rejects(fiskalcistest);
    }

    private static X509Certificate certifikat(String ime) {
        try (var in = SubjektTest.class.getResourceAsStream("/primjeri/certifikati/" + ime)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
