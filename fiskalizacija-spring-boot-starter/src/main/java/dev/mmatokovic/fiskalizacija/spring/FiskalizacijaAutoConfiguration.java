package dev.mmatokovic.fiskalizacija.spring;

import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.b2b.B2bClient;
import dev.mmatokovic.fiskalizacija.b2c.B2cClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.io.ApplicationResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Stvara {@link Potpisivac}, {@link B2bClient} i {@link B2cClient} kad je postavljeno {@code fiskalizacija.okruzenje}.
 * Vlastiti bean istog tipa zamjenjuje svakog od njih.
 */
@AutoConfiguration
@Conditional(FiskalizacijaAutoConfiguration.OkruzenjePostavljeno.class)
@EnableConfigurationProperties(FiskalizacijaProperties.class)
public class FiskalizacijaAutoConfiguration {

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    Potpisivac fiskalizacijaPotpisivac(FiskalizacijaProperties properties, ResourceLoader resourceLoader) {
        if (!StringUtils.hasText(properties.certifikat())) {
            throw new IllegalStateException("Postavite fiskalizacija.certifikat ili definirajte Potpisivac bean");
        }
        var lozinka = Objects.requireNonNull(properties.lozinka(), "Postavite fiskalizacija.lozinka");
        var certifikat = ApplicationResourceLoader.get(resourceLoader, true).getResource(properties.certifikat());
        try (var in = certifikat.getInputStream()) {
            return Potpisivac.izP12(in, lozinka.toCharArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Ne mogu pročitati " + certifikat, e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    B2bClient fiskalizacijaB2bClient(FiskalizacijaProperties properties, Potpisivac potpisivac,
                                     ObjectProvider<B2bClientCustomizer> customizers) {
        var builder = B2bClient.builder(properties.okruzenje(), potpisivac).xsdValidacija(properties.xsdValidacija());
        var klijent = properties.b2b();
        adresa(klijent.adresa(), "fiskalizacija.b2b.adresa", builder::adresa);
        if (klijent.timeout() != null) {
            builder.timeout(klijent.timeout());
        }
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    B2cClient fiskalizacijaB2cClient(FiskalizacijaProperties properties, Potpisivac potpisivac,
                                     ObjectProvider<B2cClientCustomizer> customizers) {
        var builder = B2cClient.builder(properties.okruzenje(), potpisivac).xsdValidacija(properties.xsdValidacija());
        var klijent = properties.b2c();
        adresa(klijent.adresa(), "fiskalizacija.b2c.adresa", builder::adresa);
        if (klijent.timeout() != null) {
            builder.timeout(klijent.timeout());
        }
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder.build();
    }

    private static void adresa(String adresa, String svojstvo, Consumer<URI> postavi) {
        if (StringUtils.hasText(adresa)) {
            try {
                postavi.accept(URI.create(adresa));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(svojstvo + ": " + e.getMessage(), e);
            }
        }
    }

    /** Kao {@code @ConditionalOnProperty}, ali prazna vrijednost, npr. {@code ${FISK_OKRUZENJE:}}, znači da nije postavljeno. */
    static class OkruzenjePostavljeno extends SpringBootCondition {

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("fiskalizacija.okruzenje"))
                    ? ConditionOutcome.match("fiskalizacija.okruzenje je postavljeno")
                    : ConditionOutcome.noMatch("fiskalizacija.okruzenje nije postavljeno");
        }
    }
}
