package dev.mmatokovic.fiskalizacija.spring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mmatokovic.fiskalizacija.Okruzenje;
import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.b2b.B2bClient;
import dev.mmatokovic.fiskalizacija.b2c.B2cClient;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class FiskalizacijaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FiskalizacijaAutoConfiguration.class));

    private final ApplicationContextRunner konfiguriran = runner.withPropertyValues(
            "fiskalizacija.okruzenje=TEST",
            "fiskalizacija.certifikat=classpath:test-potpis.p12",
            "fiskalizacija.lozinka=test1234");

    @Test
    void bezOkruzenjaNemaBeanova() {
        runner.run(ctx -> assertThat(ctx).hasNotFailed()
                .doesNotHaveBean(Potpisivac.class)
                .doesNotHaveBean(B2bClient.class)
                .doesNotHaveBean(B2cClient.class));
    }

    @Test
    void sCertifikatomStvaraKlijente() {
        konfiguriran.run(ctx -> assertThat(ctx).hasNotFailed()
                .hasSingleBean(Potpisivac.class)
                .hasSingleBean(B2bClient.class)
                .hasSingleBean(B2cClient.class));
    }

    @Test
    void vezePostavke() {
        konfiguriran.withPropertyValues(
                        "fiskalizacija.xsd-validacija=false",
                        "fiskalizacija.b2b.adresa=https://gateway.example/b2b",
                        "fiskalizacija.b2c.timeout=3s")
                .run(ctx -> {
                    var p = ctx.getBean(FiskalizacijaProperties.class);
                    assertThat(p.okruzenje()).isEqualTo(Okruzenje.TEST);
                    assertThat(p.xsdValidacija()).isFalse();
                    assertThat(p.b2b().adresa()).hasToString("https://gateway.example/b2b");
                    assertThat(p.b2c().timeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(p.toString()).doesNotContain("test1234");
                });
    }

    @Test
    void b2cKlijentKoristiAdresuIzPostavki() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder().code(200).body(
                    "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><tns:EchoResponse"
                            + " xmlns:tns=\"http://www.apis-it.hr/fin/2012/types/f73\">proba</tns:EchoResponse></soap:Body></soap:Envelope>")
                    .build());
            server.start(InetAddress.getLoopbackAddress(), 0);

            konfiguriran.withPropertyValues("fiskalizacija.b2c.adresa=" + server.url("/fiskalizacija"))
                    .run(ctx -> assertThat(ctx.getBean(B2cClient.class).echo("proba")).isEqualTo("proba"));
            assertThat(server.takeRequest().getUrl().encodedPath()).isEqualTo("/fiskalizacija");
        }
    }

    @Test
    void vlastitiPotpisivacZamjenjujeCertifikatIzPostavki() {
        runner.withPropertyValues("fiskalizacija.okruzenje=TEST")
                .withBean(Potpisivac.class, () -> potpisivac())
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(B2bClient.class));
    }

    @Test
    void bezCertifikataIPotpisivacaPokretanjeNeUspijeva() {
        runner.withPropertyValues("fiskalizacija.okruzenje=TEST")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("fiskalizacija.certifikat"));
    }

    @Test
    void customizeriSePrimjenjuju() {
        var pozivi = new AtomicInteger();
        konfiguriran
                .withBean(B2bClientCustomizer.class, () -> b -> pozivi.incrementAndGet())
                .withBean(B2cClientCustomizer.class, () -> b -> pozivi.incrementAndGet())
                .run(ctx -> {
                    ctx.getBean(B2bClient.class);
                    ctx.getBean(B2cClient.class);
                    assertThat(pozivi).hasValue(2);
                });
    }

    @Test
    void vlastitiKlijentImaPrednost() {
        var vlastiti = B2bClient.builder(Okruzenje.PRODUKCIJA, potpisivac()).build();
        konfiguriran.withBean(B2bClient.class, () -> vlastiti)
                .run(ctx -> assertThat(ctx.getBean(B2bClient.class)).isSameAs(vlastiti));
    }

    private static Potpisivac potpisivac() {
        try (var in = new ClassPathResource("test-potpis.p12").getInputStream()) {
            return Potpisivac.izP12(in, "test1234".toCharArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
