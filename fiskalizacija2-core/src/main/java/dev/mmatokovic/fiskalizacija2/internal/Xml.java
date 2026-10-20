package dev.mmatokovic.fiskalizacija2.internal;

import org.w3c.dom.Document;
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
import java.nio.charset.StandardCharsets;

/** Parsiranje bez DOCTYPE-a (zaštita od XXE) i serijalizacija u UTF-8. */
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
}
