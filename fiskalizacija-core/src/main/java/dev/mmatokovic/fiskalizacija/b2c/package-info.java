/**
 * Fiskalizacija računa u krajnjoj potrošnji (B2C, "Fiskalizacija 1.0") prema tehničkoj specifikaciji v2.7:
 * {@link dev.mmatokovic.fiskalizacija.b2c.B2cClient} šalje račune i dobiva JIR, {@link dev.mmatokovic.fiskalizacija.b2c.Zki}
 * računa zaštitni kod izdavatelja, a {@link dev.mmatokovic.fiskalizacija.b2c.QrKod} poveznicu za QR kod na računu.
 * Imena polja prate službenu XSD shemu ({@code FiskalizacijaSchema.xsd}).
 */
package dev.mmatokovic.fiskalizacija.b2c;
