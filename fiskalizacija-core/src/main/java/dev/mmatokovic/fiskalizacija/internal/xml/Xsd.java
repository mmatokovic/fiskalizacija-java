package dev.mmatokovic.fiskalizacija.internal.xml;

import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Provjera poruke prema službenim XSD shemama Porezne uprave (eFiskalizacija, eIzvjestavanje i fiskalizacija u krajnjoj potrošnji), ugrađenim u library. */
public final class Xsd {

    private static final Schema SHEMA = ucitaj();

    private Xsd() {
    }

    /** Vraća sve greške; prazna lista znači da je poruka valjana. */
    public static List<String> greske(Element poruka) {
        var greske = new ArrayList<String>();
        var validator = SHEMA.newValidator();
        validator.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException e) {
            }

            public void error(SAXParseException e) {
                greske.add(e.getMessage());
            }

            public void fatalError(SAXParseException e) {
                greske.add(e.getMessage());
            }
        });
        try {
            validator.validate(new DOMSource(poruka));
        } catch (SAXException | IOException e) {
            greske.add(e.getMessage());
        }
        return greske;
    }

    private static Schema ucitaj() {
        try {
            var sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            var ls = (DOMImplementationLS) DocumentBuilderFactory.newInstance().newDocumentBuilder().getDOMImplementation();
            // import iz classpatha, jer JAXP ne dopušta jar:nested: URL-ove Spring Boot izvršnog jara
            sf.setResourceResolver((type, ns, publicId, systemId, baseUri) -> {
                var in = systemId == null ? null : Xsd.class.getResourceAsStream("xsd/" + systemId);
                if (in == null) {
                    return null;
                }
                var izvor = ls.createLSInput();
                izvor.setByteStream(in);
                izvor.setSystemId(systemId);
                return izvor;
            });
            return sf.newSchema(new StreamSource[]{
                    izvor("eFiskalizacijaSchema.xsd"),
                    izvor("eIzvjestavanjeSchema.xsd"),
                    izvor("FiskalizacijaSchema.xsd")});
        } catch (SAXException | ParserConfigurationException e) {
            throw new IllegalStateException("Ne mogu učitati ugrađene XSD sheme", e);
        }
    }

    private static StreamSource izvor(String ime) {
        var in = Objects.requireNonNull(Xsd.class.getResourceAsStream("xsd/" + ime), () -> "Nedostaje ugrađena shema " + ime);
        return new StreamSource(in, ime);
    }
}
