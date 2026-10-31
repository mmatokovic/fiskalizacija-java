package dev.mmatokovic.fiskalizacija.model;

/**
 * Klasifikacija artikla, npr. KPD (BT-158).
 *
 * @param identifikatorKlasifikacije oznaka artikla u klasifikaciji (BT-158)
 * @param identifikatorSheme         oznaka sheme klasifikacije (BT-158-1)
 * @param verzijaSheme               verzija sheme (BT-158-2), ili {@code null}
 */
public record KlasifikacijaArtikla(String identifikatorKlasifikacije, String identifikatorSheme, String verzijaSheme) {
}
