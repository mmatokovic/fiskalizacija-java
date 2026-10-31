package dev.mmatokovic.fiskalizacija.internal.potpis;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Atributi subjekta certifikata. OIB se traži samo tamo gdje ga FINA upisuje: u organizationIdentifier
 * ({@code HR02994650199} kod {@code fiskalcistest}) ili u serialNumber ({@code VATHR-18683136487.341} kod Porezne),
 * a ne bilo gdje u tekstu subjekta, koji vlasnik certifikata dijelom bira sam.
 */
public final class Subjekt {

    private static final Map<String, String> IMENA = Map.of("2.5.4.97", "organizationIdentifier", "2.5.4.5", "serialNumber");

    private Subjekt() {
    }

    public record Atribut(String tip, String vrijednost) {
    }

    public static String ime(X509Certificate c) {
        return c.getSubjectX500Principal().getName(X500Principal.RFC2253, IMENA);
    }

    public static List<Atribut> atributi(X509Certificate c) {
        var atributi = new ArrayList<Atribut>();
        var dio = new StringBuilder();
        String ime = ime(c);
        for (int i = 0; i <= ime.length(); i++) {
            char z = i < ime.length() ? ime.charAt(i) : ',';
            if (z == '\\' && i + 1 < ime.length()) {
                dio.append(z).append(ime.charAt(++i));
            } else if (z == ',' || z == '+') {
                int jednako = dio.indexOf("=");
                if (jednako > 0) {
                    atributi.add(new Atribut(dio.substring(0, jednako), dio.substring(jednako + 1)));
                }
                dio.setLength(0);
            } else {
                dio.append(z);
            }
        }
        return atributi;
    }

    public static boolean imaOib(X509Certificate c, String oib) {
        var serijski = Pattern.compile("VATHR-" + Pattern.quote(oib) + "(\\.\\d+)?");
        return atributi(c).stream().anyMatch(a ->
                "organizationIdentifier".equals(a.tip()) && (a.vrijednost().equals("HR" + oib) || a.vrijednost().equals("VATHR-" + oib))
                        || "serialNumber".equals(a.tip()) && serijski.matcher(a.vrijednost()).matches());
    }

    public static Predicate<X509Certificate> sadrziOib(String oib) {
        return c -> imaOib(c, oib);
    }

    /** CN je točno {@code cn}, a OIB je {@code oib}. */
    public static Predicate<X509Certificate> potpisnik(String cn, String oib) {
        return c -> imaOib(c, oib) && atributi(c).stream().anyMatch(a -> "CN".equals(a.tip()) && cn.equals(a.vrijednost()));
    }
}
