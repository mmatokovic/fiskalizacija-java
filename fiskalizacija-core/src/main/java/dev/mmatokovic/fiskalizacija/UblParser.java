package dev.mmatokovic.fiskalizacija;

import dev.mmatokovic.fiskalizacija.internal.Xml;
import dev.mmatokovic.fiskalizacija.model.DokumentPopust;
import dev.mmatokovic.fiskalizacija.model.DokumentTrosak;
import dev.mmatokovic.fiskalizacija.model.DokumentUkupanIznos;
import dev.mmatokovic.fiskalizacija.model.Izdavatelj;
import dev.mmatokovic.fiskalizacija.model.KlasifikacijaArtikla;
import dev.mmatokovic.fiskalizacija.model.PrethodniRacun;
import dev.mmatokovic.fiskalizacija.model.PrijenosSredstava;
import dev.mmatokovic.fiskalizacija.model.Primatelj;
import dev.mmatokovic.fiskalizacija.model.Racun;
import dev.mmatokovic.fiskalizacija.model.RaspodjelaPdv;
import dev.mmatokovic.fiskalizacija.model.Stavka;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Čita UBL 2.1 {@code Invoice} ili {@code CreditNote} (HR CIUS) u {@link Racun}.
 * Dokument smije biti omotan u StandardBusinessDocument. Oznake BT/HR-BT u porukama o greškama
 * odgovaraju poslovnim pojmovima iz specifikacije eRačuna.
 */
public final class UblParser {

    private static final Map<String, String> NS = Map.of(
            "inv", "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
            "cn", "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2",
            "cac", "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
            "cbc", "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
            "ext", "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2",
            "hrextac", "urn:mfin.gov.hr:schema:xsd:HRExtensionAggregateComponents-1");

    private static final String HR_TAX_SUBTOTAL =
            "ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent/hrextac:HRFISK20Data/hrextac:HRTaxTotal/hrextac:HRTaxSubtotal";

    private UblParser() {
    }

    /**
     * Strogo čitanje: baca {@link UblException} sa svim pronađenim greškama odjednom.
     *
     * @param xml UBL Invoice ili CreditNote, po želji u StandardBusinessDocument omotnici
     * @return podaci računa, sa svim obveznim poljima
     * @throws UblException ako dokument nije čitljiv ili nedostaju obvezna polja
     */
    public static Racun racun(byte[] xml) {
        var greske = new ArrayList<String>();
        var racun = racun(xml, greske);
        if (!greske.isEmpty()) {
            throw new UblException(greske);
        }
        return racun;
    }

    /**
     * Tolerantno čitanje: greške se dodaju u {@code greske}, a polja koja se nisu mogla pročitati ostaju {@code null}.
     * Korisno za ulazne eRačune koje ne kontrolirate. Ako se takav račun preda klijentu, polja koja nedostaju i iznosi
     * s više od dvije decimale prijave se kao {@link NeispravanZahtjevException}.
     *
     * @param xml    UBL Invoice ili CreditNote, po želji u StandardBusinessDocument omotnici
     * @param greske lista u koju se dodaju pronađene greške
     * @return podaci računa; polja koja se nisu mogla pročitati su {@code null}
     * @throws UblException samo ako dokument uopće nije čitljiv: nije ispravan XML ili ne sadrži Invoice ni CreditNote
     */
    public static Racun racun(byte[] xml, List<String> greske) {
        return racun(parsiraj(xml), greske);
    }

    /**
     * Tolerantno čitanje već parsiranog dokumenta, s istim pravilima kao {@link #racun(byte[], List)}.
     *
     * @param dokument namespace-aware DOM dokument
     * @param greske   lista u koju se dodaju pronađene greške
     * @return podaci računa; polja koja se nisu mogla pročitati su {@code null}
     * @throws UblException ako dokument ne sadrži Invoice ni CreditNote
     */
    public static Racun racun(Document dokument, List<String> greske) {
        var c = new Citac(greske);
        var root = (Element) c.cvor(dokument, "//inv:Invoice | //cn:CreditNote");
        if (root == null) {
            throw new UblException(List.of("Dokument ne sadrži UBL Invoice ni CreditNote"));
        }
        boolean invoice = "Invoice".equals(root.getLocalName());
        var prethodni = prethodniRacuni(c, root);
        var izdavatelj = izdavatelj(c, root);
        var primatelj = primatelj(c, root);
        var prijenosi = prijenosiSredstava(c, root);
        var ukupno = ukupanIznos(c, root);
        var raspodjela = raspodjelaPdv(c, root);
        var popusti = popusti(c, root);
        var troskovi = troskovi(c, root);
        var stavke = stavke(c, root, invoice);

        String kopija = c.tekst(root, "cbc:CopyIndicator");
        return new Racun(
                c.obvezanTekst(root, "cbc:ID", "BT-1 broj računa"),
                c.datum(root, "cbc:IssueDate", "BT-2 datum izdavanja", true),
                c.obvezanTekst(root, invoice ? "cbc:InvoiceTypeCode" : "cbc:CreditNoteTypeCode", "BT-3 vrsta dokumenta"),
                c.obvezanTekst(root, "cbc:DocumentCurrencyCode", "BT-5 valuta"),
                c.datum(root, invoice ? "cbc:DueDate" : "cac:PaymentMeans/cbc:PaymentDueDate", "BT-9 datum dospijeća", false),
                c.obvezanTekst(root, "cbc:ProfileID", "BT-23 vrsta poslovnog procesa"),
                c.tekst(root, "cac:ContractDocumentReference/cbc:ID"),
                c.datum(root, "cac:Delivery/cbc:ActualDeliveryDate", "BT-72 datum isporuke", false),
                prethodni,
                izdavatelj,
                primatelj,
                prijenosi,
                ukupno,
                raspodjela,
                popusti,
                troskovi,
                stavke,
                "true".equals(kopija) || "1".equals(kopija));
    }

    private static List<PrethodniRacun> prethodniRacuni(Citac c, Element root) {
        return c.cvorovi(root, "cac:BillingReference").stream()
                .map(n -> new PrethodniRacun(
                        c.obvezanTekst(n, "cac:InvoiceDocumentReference/cbc:ID", "BT-25 broj prethodnog računa"),
                        c.datum(n, "cac:InvoiceDocumentReference/cbc:IssueDate", "BT-26 datum prethodnog računa", true)))
                .toList();
    }

    private static Izdavatelj izdavatelj(Citac c, Element root) {
        var dobavljac = c.cvor(root, "cac:AccountingSupplierParty");
        if (dobavljac == null) {
            c.greske.add("Nedostaje BG-4 prodavatelj (cac:AccountingSupplierParty)");
            return null;
        }
        return new Izdavatelj(
                c.obvezanTekst(dobavljac, "cac:Party/cac:PartyLegalEntity/cbc:RegistrationName", "BT-27 naziv prodavatelja"),
                c.oib(dobavljac, "BT-31", "BT-34"),
                c.oibOperatera(dobavljac));
    }

    private static Primatelj primatelj(Citac c, Element root) {
        var kupac = c.cvor(root, "cac:AccountingCustomerParty");
        if (kupac == null) {
            c.greske.add("Nedostaje BG-7 kupac (cac:AccountingCustomerParty)");
            return null;
        }
        return new Primatelj(
                c.obvezanTekst(kupac, "cac:Party/cac:PartyLegalEntity/cbc:RegistrationName", "BT-44 naziv kupca"),
                c.oib(kupac, "BT-48", "BT-49"));
    }

    private static List<PrijenosSredstava> prijenosiSredstava(Citac c, Element root) {
        return c.cvorovi(root, "cac:PaymentMeans/cac:PayeeFinancialAccount").stream()
                .map(n -> new PrijenosSredstava(
                        c.obvezanTekst(n, "cbc:ID", "BT-84 identifikator računa za plaćanje"),
                        c.tekst(n, "cbc:Name"),
                        c.tekst(n, "cac:FinancialInstitutionBranch/cbc:ID")))
                .toList();
    }

    private static DokumentUkupanIznos ukupanIznos(Citac c, Element root) {
        BigDecimal pdv = null;
        for (String putanja : new String[]{"cac:TaxTotal[cbc:TaxAmount/@currencyID = ../cbc:DocumentCurrencyCode]/cbc:TaxAmount",
                "cac:TaxTotal[cac:TaxSubtotal]/cbc:TaxAmount", "cac:TaxTotal/cbc:TaxAmount"}) {
            pdv = c.broj(root, putanja, "BT-110 ukupni PDV", false);
            if (pdv != null) {
                break;
            }
        }
        return new DokumentUkupanIznos(
                c.broj(root, "cac:LegalMonetaryTotal/cbc:LineExtensionAmount", "BT-106 zbroj neto iznosa stavki", true),
                c.broj(root, "cac:LegalMonetaryTotal/cbc:AllowanceTotalAmount", "BT-107", false),
                c.broj(root, "cac:LegalMonetaryTotal/cbc:ChargeTotalAmount", "BT-108", false),
                c.broj(root, "cac:LegalMonetaryTotal/cbc:TaxExclusiveAmount", "BT-109 iznos bez PDV-a", true),
                pdv == null ? BigDecimal.ZERO : pdv,
                c.broj(root, "cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount", "BT-112 iznos s PDV-om", true),
                c.broj(root, "cac:LegalMonetaryTotal/cbc:PrepaidAmount", "BT-113", false),
                c.broj(root, "cac:LegalMonetaryTotal/cbc:PayableAmount", "BT-115 iznos za plaćanje", true));
    }

    private static List<RaspodjelaPdv> raspodjelaPdv(Citac c, Element root) {
        var hrRaspodjela = c.cvorovi(root, HR_TAX_SUBTOTAL);
        var raspodjela = !hrRaspodjela.isEmpty()
                ? hrRaspodjela.stream().map(n -> hrRaspodjela(c, root, n)).toList()
                : c.cvorovi(root, "cac:TaxTotal/cac:TaxSubtotal").stream().map(n -> new RaspodjelaPdv(
                        c.obvezanTekst(n, "cac:TaxCategory/cbc:ID", "BT-118 kategorija PDV-a"),
                        c.broj(n, "cbc:TaxableAmount", "BT-116 oporezivi iznos", true),
                        c.broj(n, "cbc:TaxAmount", "BT-117 iznos poreza", true),
                        c.broj(n, "cac:TaxCategory/cbc:Percent", "BT-119 stopa", false),
                        c.tekst(n, "cac:TaxCategory/cbc:TaxExemptionReasonCode"),
                        c.tekst(n, "cac:TaxCategory/cbc:TaxExemptionReason"),
                        null)).toList();
        if (raspodjela.isEmpty()) {
            c.greske.add("Nedostaje raščlamba PDV-a (BG-23 ili HR-BG-2)");
        }
        return raspodjela;
    }

    private static RaspodjelaPdv hrRaspodjela(Citac c, Element root, Node n) {
        String kategorija = c.obvezanTekst(n, "hrextac:HRTaxCategory/cbc:ID", "HR-BT-18 kategorija PDV-a");
        var stopa = c.broj(n, "hrextac:HRTaxCategory/cbc:Percent", "HR-BT-19 stopa", false);
        String razlog = c.tekst(n, "hrextac:HRTaxCategory/cbc:TaxExemptionReasonCode");
        String tekstRazloga = c.tekst(n, "hrextac:HRTaxCategory/cbc:TaxExemptionReason");
        if ((razlog == null || tekstRazloga == null) && kategorija != null) {
            var standardna = standardnaKategorija(c, root, kategorija, stopa);
            if (standardna != null) {
                razlog = razlog != null ? razlog : c.tekst(standardna, "cbc:TaxExemptionReasonCode");
                tekstRazloga = tekstRazloga != null ? tekstRazloga : c.tekst(standardna, "cbc:TaxExemptionReason");
            }
        }
        return new RaspodjelaPdv(kategorija, c.broj(n, "cbc:TaxableAmount", "HR-BT-16 oporezivi iznos", true),
                c.broj(n, "cbc:TaxAmount", "HR-BT-17 iznos poreza", true), stopa, razlog, tekstRazloga,
                c.tekst(n, "hrextac:HRTaxCategory/cbc:Name"));
    }

    private static Node standardnaKategorija(Citac c, Element root, String kategorija, BigDecimal stopa) {
        for (var kat : c.cvorovi(root, "cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory")) {
            var s = c.broj(kat, "cbc:Percent", "BT-119 stopa", false);
            if (kategorija.equals(c.tekst(kat, "cbc:ID"))
                    && (stopa == null ? s == null : s != null && stopa.compareTo(s) == 0)) {
                return kat;
            }
        }
        return null;
    }

    private static List<DokumentPopust> popusti(Citac c, Element root) {
        return c.cvorovi(root, "cac:AllowanceCharge[normalize-space(cbc:ChargeIndicator)='false' or normalize-space(cbc:ChargeIndicator)='0']").stream()
                .map(n -> new DokumentPopust(
                        c.broj(n, "cbc:Amount", "BT-92 iznos popusta", true),
                        c.obvezanTekst(n, "cac:TaxCategory/cbc:ID", "BT-95 kategorija PDV-a popusta"),
                        c.broj(n, "cac:TaxCategory/cbc:Percent", "BT-96", false),
                        c.tekst(n, "cbc:AllowanceChargeReason"),
                        c.tekst(n, "cbc:AllowanceChargeReasonCode")))
                .toList();
    }

    private static List<DokumentTrosak> troskovi(Citac c, Element root) {
        return c.cvorovi(root, "cac:AllowanceCharge[normalize-space(cbc:ChargeIndicator)='true' or normalize-space(cbc:ChargeIndicator)='1']").stream()
                .map(n -> new DokumentTrosak(
                        c.broj(n, "cbc:Amount", "BT-99 iznos troška", true),
                        c.obvezanTekst(n, "cac:TaxCategory/cbc:ID", "BT-102 kategorija PDV-a troška"),
                        c.tekst(n, "cac:TaxCategory/cbc:Name"),
                        c.broj(n, "cac:TaxCategory/cbc:Percent", "BT-103", false),
                        c.tekst(n, "cac:TaxCategory/cbc:TaxExemptionReason"),
                        c.tekst(n, "cac:TaxCategory/cbc:TaxExemptionReasonCode")))
                .toList();
    }

    private static List<Stavka> stavke(Citac c, Element root, boolean invoice) {
        String kolicina = invoice ? "cbc:InvoicedQuantity" : "cbc:CreditedQuantity";
        var stavke = c.cvorovi(root, invoice ? "cac:InvoiceLine" : "cac:CreditNoteLine").stream()
                .map(n -> new Stavka(
                        c.broj(n, kolicina, "BT-129 količina", true),
                        c.obvezanTekst(n, kolicina + "/@unitCode", "BT-130 jedinica mjere"),
                        c.broj(n, "cbc:LineExtensionAmount", "BT-131 neto iznos stavke", true),
                        c.broj(n, "cac:Price/cbc:PriceAmount", "BT-146 neto cijena", true),
                        c.broj(n, "cac:Price/cac:AllowanceCharge/cbc:BaseAmount", "BT-148", false),
                        c.broj(n, "cac:Price/cbc:BaseQuantity", "BT-149", false),
                        c.tekst(n, "cac:Price/cbc:BaseQuantity/@unitCode"),
                        c.obvezanTekst(n, "cac:Item/cac:ClassifiedTaxCategory/cbc:ID", "BT-151 kategorija PDV-a stavke"),
                        c.broj(n, "cac:Item/cac:ClassifiedTaxCategory/cbc:Percent", "BT-152", false),
                        c.obvezanTekst(n, "cac:Item/cbc:Name", "BT-153 naziv artikla"),
                        c.tekst(n, "cac:Item/cbc:Description"),
                        c.tekst(n, "cac:Item/cac:ClassifiedTaxCategory/cbc:Name"),
                        klasifikacije(c, n)))
                .toList();
        if (stavke.isEmpty()) {
            c.greske.add("Račun nema nijednu stavku (BG-25)");
        }
        return stavke;
    }

    private static List<KlasifikacijaArtikla> klasifikacije(Citac c, Node stavka) {
        return c.cvorovi(stavka, "cac:Item/cac:CommodityClassification/cbc:ItemClassificationCode[@listID='CG']").stream()
                .map(k -> new KlasifikacijaArtikla(
                        c.tekst(k, "."),
                        c.obvezanTekst(k, "@listID", "BT-158-1 shema klasifikacije"),
                        c.tekst(k, "@listVersionID")))
                .toList();
    }

    private static Document parsiraj(byte[] xml) {
        try {
            return Xml.parsiraj(xml);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new UblException(List.of("Neispravan XML: " + e.getMessage()));
        }
    }

    private static final class Citac {

        private final XPath xp = XPathFactory.newInstance().newXPath();
        private final List<String> greske;

        Citac(List<String> greske) {
            this.greske = greske;
            xp.setNamespaceContext(new NamespaceContext() {
                public String getNamespaceURI(String prefix) {
                    return NS.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
                }

                public String getPrefix(String uri) {
                    return null;
                }

                public Iterator<String> getPrefixes(String uri) {
                    return null;
                }
            });
        }

        Node cvor(Node ctx, String putanja) {
            try {
                return (Node) xp.evaluate(putanja, ctx, XPathConstants.NODE);
            } catch (XPathExpressionException e) {
                throw new IllegalStateException(putanja, e);
            }
        }

        List<Node> cvorovi(Node ctx, String putanja) {
            try {
                var nl = (NodeList) xp.evaluate(putanja, ctx, XPathConstants.NODESET);
                var lista = new ArrayList<Node>(nl.getLength());
                for (int i = 0; i < nl.getLength(); i++) {
                    lista.add(nl.item(i));
                }
                return lista;
            } catch (XPathExpressionException e) {
                throw new IllegalStateException(putanja, e);
            }
        }

        String tekst(Node ctx, String putanja) {
            var n = cvor(ctx, putanja);
            if (n == null) {
                return null;
            }
            String t = n.getTextContent().trim();
            return t.isEmpty() ? null : t;
        }

        String obvezanTekst(Node ctx, String putanja, String opis) {
            String t = tekst(ctx, putanja);
            if (t == null) {
                greske.add("Nedostaje " + opis + " (" + putanja + ")");
            }
            return t;
        }

        BigDecimal broj(Node ctx, String putanja, String opis, boolean obvezno) {
            String t = obvezno ? obvezanTekst(ctx, putanja, opis) : tekst(ctx, putanja);
            if (t == null) {
                return null;
            }
            try {
                return new BigDecimal(t);
            } catch (NumberFormatException e) {
                greske.add("Neispravan broj za " + opis + ": '" + t + "'");
                return null;
            }
        }

        LocalDate datum(Node ctx, String putanja, String opis, boolean obvezno) {
            String t = obvezno ? obvezanTekst(ctx, putanja, opis) : tekst(ctx, putanja);
            if (t == null) {
                return null;
            }
            try {
                return LocalDate.parse(t);
            } catch (DateTimeParseException e) {
                greske.add("Neispravan datum za " + opis + ": '" + t + "'");
                return null;
            }
        }

        String oib(Node grupa, String btCompanyId, String btEndpointId) {
            String companyId = tekst(grupa, "cac:Party/cac:PartyTaxScheme/cbc:CompanyID");
            String izCompanyId = companyId != null && companyId.startsWith("HR") ? companyId.substring(2) : null;
            if (Oib.ispravan(izCompanyId)) {
                return izCompanyId;
            }
            String endpoint = tekst(grupa, "cac:Party/cbc:EndpointID[@schemeID='9934']");
            if (endpoint != null) {
                if (!Oib.ispravan(endpoint)) {
                    greske.add("Neispravan OIB u " + btEndpointId + ": '" + endpoint + "'");
                }
                return endpoint;
            }
            if (izCompanyId != null && izCompanyId.matches("\\d{11}")) {
                greske.add("Neispravan OIB u " + btCompanyId + ": '" + companyId + "'");
                return izCompanyId;
            }
            greske.add("OIB nije pronađen ni u " + btCompanyId + " (CompanyID s HR prefiksom) ni u "
                    + btEndpointId + " (EndpointID sa schemeID 9934)");
            return null;
        }

        String oibOperatera(Node dobavljac) {
            String oib = obvezanTekst(dobavljac, "cac:SellerContact/cbc:ID", "HR-BT-5 OIB operatera");
            if (oib != null && !Oib.ispravan(oib)) {
                greske.add("Neispravan OIB u HR-BT-5 OIB operatera: '" + oib + "'");
            }
            return oib;
        }
    }
}
