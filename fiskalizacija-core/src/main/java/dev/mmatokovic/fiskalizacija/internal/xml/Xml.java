package dev.mmatokovic.fiskalizacija.internal.xml;

import dev.mmatokovic.fiskalizacija.NeispravanZahtjevException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Parsiranje bez DOCTYPE-a (zaštita od XXE), serijalizacija u UTF-8 i pomoćnici za DOM. */
public final class Xml {

    private Xml() {
    }

    public static Document parsiraj(String xml) throws ParserConfigurationException, SAXException, IOException {
        return parsiraj(xml.getBytes(StandardCharsets.UTF_8));
    }

    public static Document parsiraj(byte[] xml) throws ParserConfigurationException, SAXException, IOException {
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    public static String serijaliziraj(Document dokument) {
        try {
            var t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            var w = new StringWriter();
            t.transform(new DOMSource(dokument), new StreamResult(w));
            return w.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Dodaje novo dijete i vraća ga. */
    public static Element dodaj(Element roditelj, String ns, String ime) {
        var el = roditelj.getOwnerDocument().createElementNS(ns, ime);
        roditelj.appendChild(el);
        return el;
    }

    /** Prvo dijete s lokalnim imenom, ili {@code null}. */
    public static Element dijete(Element roditelj, String lokalnoIme) {
        for (var n = roditelj.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && lokalnoIme.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    public static List<Element> djeca(Element roditelj, String lokalnoIme) {
        var djeca = new ArrayList<Element>();
        for (var n = roditelj.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && lokalnoIme.equals(e.getLocalName())) {
                djeca.add(e);
            }
        }
        return djeca;
    }

    /** Tekst prvog djeteta s lokalnim imenom bez razmaka na rubovima, ili {@code null}. */
    public static String tekst(Element roditelj, String lokalnoIme) {
        var e = dijete(roditelj, lokalnoIme);
        return e == null ? null : e.getTextContent().trim();
    }

    /** Iznos s točno dvije decimale, kako ga traže sheme. */
    public static BigDecimal iznos(BigDecimal v) {
        if (v == null) {
            return null;
        }
        try {
            return v.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new NeispravanZahtjevException(List.of("Iznos smije imati najviše dvije decimale: " + v.toPlainString()));
        }
    }
}
