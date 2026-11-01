package dev.mmatokovic.fiskalizacija.spring;

import dev.mmatokovic.fiskalizacija.b2b.B2bClient;

/** Mijenja {@link B2bClient.Builder} nakon primjene {@link FiskalizacijaProperties}, npr. za proxy ili sat. */
@FunctionalInterface
public interface B2bClientCustomizer {

    void customize(B2bClient.Builder builder);
}
