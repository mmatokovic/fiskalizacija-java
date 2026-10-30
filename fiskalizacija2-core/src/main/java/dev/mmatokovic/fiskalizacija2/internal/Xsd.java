package dev.mmatokovic.fiskalizacija2.internal;

import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Provjera poruke prema službenim XSD shemama Porezne uprave (eFiskalizacija i eIzvjestavanje), ugrađenim u library. */
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
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            return sf.newSchema(new StreamSource[]{
                    new StreamSource(resurs("xsd/eFiskalizacijaSchema.xsd")),
                    new StreamSource(resurs("xsd/eIzvjestavanjeSchema.xsd"))});
        } catch (SAXException e) {
            throw new IllegalStateException("Ne mogu učitati ugrađene XSD sheme", e);
        }
    }

    private static String resurs(String ime) {
        return Objects.requireNonNull(Xsd.class.getResource(ime), () -> "Nedostaje ugrađena shema " + ime).toExternalForm();
    }
}
