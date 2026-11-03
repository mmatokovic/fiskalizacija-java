package dev.mmatokovic.fiskalizacija.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mmatokovic.fiskalizacija.FiskalizacijaException;
import dev.mmatokovic.fiskalizacija.NeispravanZahtjevException;
import dev.mmatokovic.fiskalizacija.Okruzenje;
import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.b2b.B2bClient;
import dev.mmatokovic.fiskalizacija.b2b.NacinPlacanja;
import dev.mmatokovic.fiskalizacija.b2b.Naplata;
import dev.mmatokovic.fiskalizacija.b2c.B2cClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class FiskalizacijaAutoConfigurationTest {

    private static final String ECHO = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
            + "<tns:EchoResponse xmlns:tns=\"http://www.apis-it.hr/fin/2012/types/f73\">proba</tns:EchoResponse>"
            + "</soap:Body></soap:Envelope>";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FiskalizacijaAutoConfiguration.class));

    private final ApplicationContextRunner konfiguriran = runner.withPropertyValues(
            "fiskalizacija.okruzenje=TEST",
            "fiskalizacija.certifikat=classpath:test-potpis.p12",
            "fiskalizacija.lozinka=test1234");

    private final MockWebServer server = new MockWebServer();

    @BeforeEach
    void pokreni() throws IOException {
        server.start(InetAddress.getLoopbackAddress(), 0);
    }

    @AfterEach
    void zaustavi() {
        server.close();
    }

    @Test
    void bezOkruzenjaNemaBeanova() {
        runner.run(ctx -> assertThat(ctx).hasNotFailed()
                .doesNotHaveBean(Potpisivac.class)
                .doesNotHaveBean(B2bClient.class)
                .doesNotHaveBean(B2cClient.class));
    }

    @Test
    void praznoOkruzenjeJeKaoDaNijePostavljeno() {
        runner.withPropertyValues("fiskalizacija.okruzenje=")
                .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(B2bClient.class));
    }

    @Test
    void sCertifikatomStvaraKlijente() {
        konfiguriran.run(ctx -> assertThat(ctx).hasNotFailed()
                .hasSingleBean(Potpisivac.class)
                .hasBean("fiskalizacijaPotpisivac")
                .hasBean("fiskalizacijaB2bClient")
                .hasBean("fiskalizacijaB2cClient"));
    }

    @Test
    void beanoviNeSmetajuIstoimenimBeanovimaAplikacije() {
        konfiguriran.withBean("b2bClient", String.class, () -> "aplikacija")
                .withBean("potpisivac", String.class, () -> "aplikacija")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(B2bClient.class));
    }

    @Test
    void certifikatBezPrefiksaJeDatotekaNaDisku(@TempDir Path dir) throws IOException {
        var p12 = dir.resolve("app.p12");
        try (var in = new ClassPathResource("test-potpis.p12").getInputStream()) {
            Files.copy(in, p12);
        }
        runner.withPropertyValues("fiskalizacija.okruzenje=TEST", "fiskalizacija.certifikat=" + p12.toAbsolutePath(),
                        "fiskalizacija.lozinka=test1234")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(Potpisivac.class));
    }

    @Test
    void vezePostavke() {
        konfiguriran.withPropertyValues("fiskalizacija.b2b.timeout=45", "fiskalizacija.b2c.timeout=500ms")
                .run(ctx -> {
                    var p = ctx.getBean(FiskalizacijaProperties.class);
                    assertThat(p.okruzenje()).isEqualTo(Okruzenje.TEST);
                    assertThat(p.xsdValidacija()).isTrue();
                    assertThat(p.b2b().timeout()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(p.b2c().timeout()).isEqualTo(Duration.ofMillis(500));
                    assertThat(p.toString()).doesNotContain("test1234");
                });
    }

    @Test
    void praznaXsdValidacijaOstajeUkljucena() {
        konfiguriran.withPropertyValues("fiskalizacija.xsd-validacija=")
                .run(ctx -> assertThat(ctx.getBean(FiskalizacijaProperties.class).xsdValidacija()).isTrue());
    }

    @Test
    void b2cKlijentKoristiAdresuIzPostavki() throws Exception {
        server.enqueue(new MockResponse.Builder().body(ECHO).build());

        konfiguriran.withPropertyValues("fiskalizacija.b2c.adresa=" + server.url("/fisk%20alizacija"))
                .run(ctx -> assertThat(ctx.getBean(B2cClient.class).echo("proba")).isEqualTo("proba"));
        assertThat(primljen().getUrl().encodedPath()).isEqualTo("/fisk%20alizacija");
    }

    @Test
    void b2bKlijentKoristiAdresuIXsdValidacijuIzPostavki() throws Exception {
        server.enqueue(new MockResponse.Builder().code(500).build());

        konfiguriran.withPropertyValues("fiskalizacija.b2b.adresa=" + server.url("/b2b"))
                .run(ctx -> assertThatThrownBy(() -> ctx.getBean(B2bClient.class).evidentirajNaplatu(neispravnaNaplata()))
                        .isInstanceOf(NeispravanZahtjevException.class));
        konfiguriran.withPropertyValues("fiskalizacija.b2b.adresa=" + server.url("/b2b"), "fiskalizacija.xsd-validacija=false")
                .run(ctx -> assertThatThrownBy(() -> ctx.getBean(B2bClient.class).evidentirajNaplatu(neispravnaNaplata()))
                        .isInstanceOf(FiskalizacijaException.class));
        assertThat(primljen().getUrl().encodedPath()).isEqualTo("/b2b");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void timeoutIzPostavkiVrijediZaKlijenta() {
        server.enqueue(new MockResponse.Builder().body(ECHO).headersDelay(5, TimeUnit.SECONDS).build());

        konfiguriran.withPropertyValues("fiskalizacija.b2c.adresa=" + server.url("/"), "fiskalizacija.b2c.timeout=200ms")
                .run(ctx -> assertThatThrownBy(() -> ctx.getBean(B2cClient.class).echo("proba"))
                        .isInstanceOf(FiskalizacijaException.class)
                        .hasMessageContaining("timed out"));
    }

    @Test
    void neispravnaAdresaZaustavljaPokretanje() {
        konfiguriran.withPropertyValues("fiskalizacija.b2b.adresa=gateway.example/b2b")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure()
                        .hasStackTraceContaining("fiskalizacija.b2b.adresa: adresa mora biti http ili https URI"));
    }

    @Test
    void vlastitiPotpisivacZamjenjujeCertifikatIzPostavki() {
        runner.withPropertyValues("fiskalizacija.okruzenje=TEST")
                .withBean(Potpisivac.class, FiskalizacijaAutoConfigurationTest::potpisivac)
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(B2bClient.class));
    }

    @Test
    void bezCertifikataIPotpisivacaPokretanjeNeUspijeva() {
        runner.withPropertyValues("fiskalizacija.okruzenje=TEST")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("fiskalizacija.certifikat"));
    }

    @Test
    void vlastitiKlijentiNeTrazeCertifikat() {
        runner.withPropertyValues("fiskalizacija.okruzenje=TEST")
                .withBean(B2bClient.class, () -> B2bClient.builder(Okruzenje.TEST, potpisivac()).build())
                .withBean(B2cClient.class, () -> B2cClient.builder(Okruzenje.TEST, potpisivac()).build())
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void b2cCustomizerSePrimjenjujeNakonPostavki() throws Exception {
        server.enqueue(new MockResponse.Builder().body(ECHO).build());

        konfiguriran.withPropertyValues("fiskalizacija.b2c.adresa=http://127.0.0.1:1/")
                .withBean(B2cClientCustomizer.class, () -> b -> b.adresa(URI.create(server.url("/customizer").toString())))
                .run(ctx -> assertThat(ctx.getBean(B2cClient.class).echo("proba")).isEqualTo("proba"));
        assertThat(primljen().getUrl().encodedPath()).isEqualTo("/customizer");
    }

    @Test
    void b2bCustomizerSePrimjenjujeNakonPostavki() throws Exception {
        server.enqueue(new MockResponse.Builder().code(500).build());

        konfiguriran.withPropertyValues("fiskalizacija.b2b.adresa=http://127.0.0.1:1/")
                .withBean(B2bClientCustomizer.class, () -> b -> b.adresa(URI.create(server.url("/customizer").toString()))
                        .xsdValidacija(false))
                .run(ctx -> assertThatThrownBy(() -> ctx.getBean(B2bClient.class).evidentirajNaplatu(neispravnaNaplata()))
                        .isInstanceOf(FiskalizacijaException.class));
        assertThat(primljen().getUrl().encodedPath()).isEqualTo("/customizer");
    }

    @Test
    void vlastitiKlijentImaPrednost() {
        var vlastiti = B2bClient.builder(Okruzenje.PRODUKCIJA, potpisivac()).build();
        konfiguriran.withBean(B2bClient.class, () -> vlastiti)
                .run(ctx -> assertThat(ctx.getBean(B2bClient.class)).isSameAs(vlastiti));
    }

    private RecordedRequest primljen() throws InterruptedException {
        var zahtjev = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(zahtjev).as("zahtjev na server").isNotNull();
        return zahtjev;
    }

    /** OIB izdavatelja ne prolazi XSD. */
    private static Naplata neispravnaNaplata() {
        return new Naplata("1-P1-1", LocalDate.of(2026, 3, 1), "1", "11111111119", LocalDate.of(2026, 3, 2),
                new BigDecimal("125.00"), NacinPlacanja.T);
    }

    private static Potpisivac potpisivac() {
        try (var in = new ClassPathResource("test-potpis.p12").getInputStream()) {
            return Potpisivac.izP12(in, "test1234".toCharArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
