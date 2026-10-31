package dev.mmatokovic.fiskalizacija.internal;

import dev.mmatokovic.fiskalizacija.FiskalizacijaException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** HTTP POST SOAP poruke s ograničenom veličinom odgovora. */
public final class SoapPoziv {

    private static final int MAKS_ODGOVOR = 10 * 1024 * 1024;

    private SoapPoziv() {
    }

    public record Odgovor(int status, String tijelo) {
    }

    /** @throws FiskalizacijaException ako slanje ne uspije ili je odgovor prevelik */
    public static Odgovor posalji(HttpClient http, URI adresa, Duration timeout, String contentType, String soapAction,
            String soap) {
        try {
            var odgovor = http.send(HttpRequest.newBuilder(adresa)
                            .timeout(timeout)
                            .header("Content-Type", contentType)
                            .header("SOAPAction", "\"" + soapAction + "\"")
                            .POST(HttpRequest.BodyPublishers.ofString(soap, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            int status = odgovor.statusCode();
            try (var in = odgovor.body()) {
                byte[] bajtovi = in.readNBytes(MAKS_ODGOVOR + 1);
                if (bajtovi.length > MAKS_ODGOVOR) {
                    throw new FiskalizacijaException("Odgovor je veći od " + MAKS_ODGOVOR + " bajtova (HTTP " + status + ")",
                            status, null, null);
                }
                return new Odgovor(status, new String(bajtovi, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new FiskalizacijaException("Slanje na " + adresa + " nije uspjelo: " + e.getMessage(), 0, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FiskalizacijaException("Slanje prekinuto", 0, null, e);
        }
    }
}
