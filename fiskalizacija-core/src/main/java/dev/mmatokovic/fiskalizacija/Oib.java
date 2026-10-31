package dev.mmatokovic.fiskalizacija;

/**
 * Provjera OIB-a: 11 znamenki, od kojih je zadnja kontrolna (ISO 7064, MOD 11,10).
 *
 * <p>Library je primjenjuje pri čitanju UBL-a, gdje zna da je vrijednost OIB ({@code CompanyID} s prefiksom HR,
 * {@code EndpointID} sa schemeID 9934, operater). Pri slanju se provjerava samo format, kroz XSD.
 */
public final class Oib {

    private Oib() {
    }

    /**
     * Provjerava format i kontrolnu znamenku.
     *
     * @param oib vrijednost koja se provjerava
     * @return je li vrijednost ispravan OIB
     */
    public static boolean ispravan(String oib) {
        if (oib == null || !oib.matches("\\d{11}")) {
            return false;
        }
        int ostatak = 10;
        for (int i = 0; i < 10; i++) {
            ostatak = (ostatak + (oib.charAt(i) - '0')) % 10;
            if (ostatak == 0) {
                ostatak = 10;
            }
            ostatak = (ostatak * 2) % 11;
        }
        return (11 - ostatak) % 10 == oib.charAt(10) - '0';
    }
}
