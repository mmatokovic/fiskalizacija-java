package dev.mmatokovic.fiskalizacija.eracun;

import com.helger.base.location.ILocation;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.phive.api.execute.ValidationExecutionManager;
import com.helger.phive.api.executorset.ValidationExecutorSetRegistry;
import com.helger.phive.api.validity.IValidityDeterminator;
import com.helger.phive.en16931.EN16931Validation;
import com.helger.phive.eracun.HReRacunValidation;
import com.helger.phive.xml.source.IValidationSourceXML;
import com.helger.phive.xml.source.ValidationSourceXML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validacija eRačuna (UBL 2.1 Invoice ili CreditNote, po želji u StandardBusinessDocument omotnici) prema UBL XSD-u,
 * EN 16931 i HR CIUS pravilima ({@value #VERZIJA_HR_PRAVILA}). Thread-safe; pravila se učitavaju pri prvoj upotrebi.
 */
public final class ERacunValidator {

    public static final String VERZIJA_HR_PRAVILA = "1.0.4";

    private static final String NS_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String NS_CREDIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";

    private ERacunValidator() {
    }

    public static RezultatValidacije validiraj(byte[] xml) {
        Document dokument;
        try {
            dokument = parsiraj(xml);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return greska("Neispravan XML: " + e.getMessage());
        }
        Element ubl = ubl(dokument);
        if (ubl == null) {
            return greska("Dokument ne sadrži UBL Invoice ni CreditNote");
        }
        DVRCoordinate pravila = NS_INVOICE.equals(ubl.getNamespaceURI())
                ? HReRacunValidation.VID_HR_ERACUN_UBL_INVOICE_104
                : HReRacunValidation.VID_HR_ERACUN_UBL_CREDITNOTE_104;
        var izvor = ubl == dokument.getDocumentElement()
                ? ValidationSourceXML.create(null, dokument)
                : ValidationSourceXML.createPartial(null, ubl);
        var rezultati = ValidationExecutionManager.executeValidation(IValidityDeterminator.createDefault(),
                Registar.INSTANCA.getOfID(pravila), izvor, Locale.ROOT);

        var nalazi = new ArrayList<Nalaz>();
        for (var rezultat : rezultati) {
            if (rezultat.getValidity().isSkipped()) {
                continue;
            }
            for (var e : rezultat.getErrorList()) {
                if (e.isError() || e.isFailure()) {
                    nalazi.add(new Nalaz(e.isError() ? Nalaz.Razina.GRESKA : Nalaz.Razina.UPOZORENJE,
                            e.getErrorID(), e.getErrorText(Locale.ROOT), lokacija(e.getErrorFieldName(), e.getErrorLocation())));
                }
            }
        }
        return new RezultatValidacije(nalazi);
    }

    private static String lokacija(String polje, ILocation mjesto) {
        if (polje != null && !polje.isBlank()) {
            return polje;
        }
        return mjesto != null && mjesto.hasLineNumber() ? "redak " + mjesto.getLineNumber() : null;
    }

    private static RezultatValidacije greska(String poruka) {
        return new RezultatValidacije(List.of(new Nalaz(Nalaz.Razina.GRESKA, null, poruka, null)));
    }

    private static Element ubl(Document dokument) {
        for (String ns : new String[]{NS_INVOICE, NS_CREDIT_NOTE}) {
            var el = dokument.getElementsByTagNameNS(ns, ns.equals(NS_INVOICE) ? "Invoice" : "CreditNote");
            if (el.getLength() > 0) {
                return (Element) el.item(0);
            }
        }
        return null;
    }

    private static Document parsiraj(byte[] xml) throws ParserConfigurationException, SAXException, IOException {
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static final class Registar {

        static final ValidationExecutorSetRegistry<IValidationSourceXML> INSTANCA = new ValidationExecutorSetRegistry<>();

        static {
            EN16931Validation.initEN16931(INSTANCA);
            HReRacunValidation.init(INSTANCA);
        }
    }
}
