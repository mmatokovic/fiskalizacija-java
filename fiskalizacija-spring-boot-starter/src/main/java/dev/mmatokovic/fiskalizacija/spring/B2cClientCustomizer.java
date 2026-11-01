package dev.mmatokovic.fiskalizacija.spring;

import dev.mmatokovic.fiskalizacija.b2c.B2cClient;

/** Mijenja {@link B2cClient.Builder} nakon primjene {@link FiskalizacijaProperties}, npr. za proxy ili sat. */
@FunctionalInterface
public interface B2cClientCustomizer {

    void customize(B2cClient.Builder builder);
}
