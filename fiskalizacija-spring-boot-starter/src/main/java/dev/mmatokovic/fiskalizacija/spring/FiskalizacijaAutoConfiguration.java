package dev.mmatokovic.fiskalizacija.spring;

import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.b2b.B2bClient;
import dev.mmatokovic.fiskalizacija.b2c.B2cClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Stvara {@link Potpisivac}, {@link B2bClient} i {@link B2cClient} kad je postavljeno {@code fiskalizacija.okruzenje}.
 * Svaki od njih zamjenjuje vlastiti bean istog tipa.
 */
@AutoConfiguration
@ConditionalOnProperty("fiskalizacija.okruzenje")
@EnableConfigurationProperties(FiskalizacijaProperties.class)
public class FiskalizacijaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Potpisivac potpisivac(FiskalizacijaProperties properties) {
        var certifikat = Objects.requireNonNull(properties.certifikat(),
                "Postavite fiskalizacija.certifikat ili definirajte Potpisivac bean");
        var lozinka = Objects.requireNonNull(properties.lozinka(), "Postavite fiskalizacija.lozinka");
        try (var in = certifikat.getInputStream()) {
            return Potpisivac.izP12(in, lozinka.toCharArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Ne mogu pročitati " + certifikat, e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    B2bClient b2bClient(FiskalizacijaProperties properties, Potpisivac potpisivac,
                        ObjectProvider<B2bClientCustomizer> customizers) {
        var builder = B2bClient.builder(properties.okruzenje(), potpisivac).xsdValidacija(properties.xsdValidacija());
        var klijent = properties.b2b();
        if (klijent.adresa() != null) {
            builder.adresa(klijent.adresa());
        }
        if (klijent.timeout() != null) {
            builder.timeout(klijent.timeout());
        }
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    B2cClient b2cClient(FiskalizacijaProperties properties, Potpisivac potpisivac,
                        ObjectProvider<B2cClientCustomizer> customizers) {
        var builder = B2cClient.builder(properties.okruzenje(), potpisivac).xsdValidacija(properties.xsdValidacija());
        var klijent = properties.b2c();
        if (klijent.adresa() != null) {
            builder.adresa(klijent.adresa());
        }
        if (klijent.timeout() != null) {
            builder.timeout(klijent.timeout());
        }
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder.build();
    }
}
