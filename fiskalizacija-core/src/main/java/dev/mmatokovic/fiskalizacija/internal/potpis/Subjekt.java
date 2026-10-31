package dev.mmatokovic.fiskalizacija.internal.potpis;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Subjekt certifikata kao tekst. FINA certifikati OIB nose u atributu organizationIdentifier (2.5.4.97, npr.
 * {@code HR02994650199}), koji bi RFC 2253 bez ovog imena ispisao heksadecimalno.
 */
public final class Subjekt {

    private static final Map<String, String> IMENA = Map.of("2.5.4.97", "organizationIdentifier");
    private static final Pattern CN = Pattern.compile("(?:^|,)CN=([^,]+)");

    private Subjekt() {
    }

    public static String ime(X509Certificate c) {
        return c.getSubjectX500Principal().getName(X500Principal.RFC2253, IMENA);
    }

    public static Predicate<X509Certificate> sadrziOib(String oib) {
        return c -> ime(c).contains(oib);
    }

    /** CN je točno {@code cn}, a subjekt sadrži {@code oib}. */
    public static Predicate<X509Certificate> potpisnik(String cn, String oib) {
        return c -> {
            String ime = ime(c);
            var m = CN.matcher(ime);
            return m.find() && cn.equals(m.group(1)) && ime.contains(oib);
        };
    }
}
