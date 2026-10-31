package dev.mmatokovic.fiskalizacija.internal.http;

import dev.mmatokovic.fiskalizacija.FiskalizacijaException;
import dev.mmatokovic.fiskalizacija.b2b.Odgovor;
import dev.mmatokovic.fiskalizacija.internal.xml.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** SOAP omotnica i HTTP POST s ograničenom veličinom odgovora. */
public final class SoapPoziv {

    public static final String NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/";

    private static final int MAKS_ODGOVOR = 10 * 1024 * 1024;

    private SoapPoziv() {
    }

    public record Odgovor(int status, String tijelo) {
    }

    /** Nova SOAP omotnica; vraća njezin prazni {@code Body}. */
    public static Element novoTijelo() {
        Document doc;
        try {
            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            doc = dbf.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        var envelope = doc.createElementNS(NS_SOAP, "soapenv:Envelope");
        envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:soapenv", NS_SOAP);
        doc.appendChild(envelope);
        return Xml.dodaj(envelope, NS_SOAP, "soapenv:Body");
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
