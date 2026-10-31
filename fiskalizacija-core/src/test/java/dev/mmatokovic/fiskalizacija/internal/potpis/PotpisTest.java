package dev.mmatokovic.fiskalizacija.internal.potpis;

import dev.mmatokovic.fiskalizacija.FinaCertifikati;
import dev.mmatokovic.fiskalizacija.Okruzenje;
import dev.mmatokovic.fiskalizacija.PotpisOdgovora;
import dev.mmatokovic.fiskalizacija.Potpisivac;
import dev.mmatokovic.fiskalizacija.Primjeri;
import dev.mmatokovic.fiskalizacija.b2b.NacinPlacanja;
import dev.mmatokovic.fiskalizacija.b2b.Naplata;
import dev.mmatokovic.fiskalizacija.internal.b2b.Zahtjevi;
import dev.mmatokovic.fiskalizacija.internal.http.SoapPoziv;
import dev.mmatokovic.fiskalizacija.internal.xml.Xml;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.math.BigDecimal;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.TrustAnchor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotpisTest {

    private final Potpisivac potpisivac = Primjeri.potpisivac();

    @Test
    void potpisPrezivljavaSerijalizacijuIProvjeruOdStraneDrugeStrane() throws Exception {
        var doc = potpisanIPrenesen();

        assertEquals(PotpisOdgovora.ISPRAVAN, ProvjeraPotpisa.provjeri(zahtjev(doc), Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void izmjenaSadrzajaNakonPotpisaSeOtkriva() throws Exception {
        var doc = potpisanIPrenesen();
        doc.getElementsByTagNameNS("*", "naplaceniIznos").item(0).setTextContent("1.00");

        assertEquals(PotpisOdgovora.NEISPRAVAN, ProvjeraPotpisa.provjeri(zahtjev(doc), Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void izmjenaVremenaPotpisaSeOtkriva() throws Exception {
        var doc = potpisanIPrenesen();
        doc.getElementsByTagNameNS("*", "SigningTime").item(0).setTextContent("2020-01-01T00:00:00Z");

        assertEquals(PotpisOdgovora.NEISPRAVAN, ProvjeraPotpisa.provjeri(zahtjev(doc), Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void ispravanPotpisNepoznatogCertifikataNijePouzdan() throws Exception {
        var doc = potpisanIPrenesen();
        Set<TrustAnchor> fina = FinaCertifikati.sidra(Okruzenje.PRODUKCIJA);

        assertEquals(PotpisOdgovora.NEPOUZDAN_CERTIFIKAT,
                ProvjeraPotpisa.provjeri(zahtjev(doc), fina, FinaCertifikati.posrednici(Okruzenje.PRODUKCIJA)));
    }

    @Test
    void p12SKrivomLozinkomJeJasnaGreska() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> Potpisivac.izP12(Primjeri.class.getResourceAsStream("/test-potpis.p12"), "kriva".toCharArray()));

        assertTrue(e.getMessage().contains("PKCS#12"), e.getMessage());
    }

    @Test
    void dokumentBezPotpisa() throws Exception {
        var doc = Xml.parsiraj("<a/>");

        assertEquals(PotpisOdgovora.NEDOSTAJE, ProvjeraPotpisa.provjeri(doc.getDocumentElement(), Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void potpisKojiPokrivaSamoDioElementaNijeIspravan() throws Exception {
        String ns = Zahtjevi.NS_EIZV;
        var doc = Xml.parsiraj("<eizv:Vanjski xmlns:eizv=\"" + ns + "\" eizv:id=\"v\">"
                + "<eizv:prihvacenZahtjev>true</eizv:prihvacenZahtjev><eizv:Unutarnji eizv:id=\"u\">x</eizv:Unutarnji></eizv:Vanjski>");
        var vanjski = doc.getDocumentElement();
        var unutarnji = (Element) doc.getElementsByTagNameNS(ns, "Unutarnji").item(0);
        Xades.potpisi(unutarnji, "u", potpisivac, Clock.systemUTC());
        vanjski.appendChild(unutarnji.getLastChild());

        assertEquals(PotpisOdgovora.NEISPRAVAN, ProvjeraPotpisa.provjeri(vanjski, Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void originalSkrivenUPotpisuUzLazniSadrzajNijeIspravan() throws Exception {
        String ns = Zahtjevi.NS_EIZV;
        String ds = "http://www.w3.org/2000/09/xmldsig#";
        var doc = Xml.parsiraj("<soapenv:Envelope xmlns:soapenv=\"" + SoapPoziv.NS_SOAP + "\"><soapenv:Body>"
                + "<eizv:EvidentirajNaplatuOdgovor xmlns:eizv=\"" + ns + "\" eizv:id=\"I\"><eizv:Odgovor>"
                + "<eizv:prihvacenZahtjev>false</eizv:prihvacenZahtjev></eizv:Odgovor></eizv:EvidentirajNaplatuOdgovor>"
                + "</soapenv:Body></soapenv:Envelope>");
        Xades.potpisi((Element) doc.getElementsByTagNameNS(ns, "EvidentirajNaplatuOdgovor").item(0), "I", potpisivac, Clock.systemUTC());
        doc = Xml.parsiraj(Xml.serijaliziraj(doc));
        var original = (Element) doc.getElementsByTagNameNS(ns, "EvidentirajNaplatuOdgovor").item(0);
        var tijelo = (Element) original.getParentNode();
        var potpis = (Element) original.getElementsByTagNameNS(ds, "Signature").item(0);
        original.removeChild(potpis);
        var lazni = doc.createElementNS(ns, "eizv:EvidentirajNaplatuOdgovor");
        lazni.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:eizv", ns);
        lazni.setAttributeNS(ns, "eizv:id", "I");
        lazni.appendChild(doc.createElementNS(ns, "eizv:Odgovor"))
                .appendChild(doc.createElementNS(ns, "eizv:prihvacenZahtjev")).setTextContent("true");
        tijelo.replaceChild(lazni, original);
        lazni.appendChild(potpis);
        potpis.appendChild(doc.createElementNS(ds, "ds:Object")).appendChild(original);

        var prenesen = Xml.parsiraj(Xml.serijaliziraj(doc));
        var procitan = (Element) prenesen.getElementsByTagNameNS(SoapPoziv.NS_SOAP, "Body").item(0).getFirstChild();

        assertEquals(PotpisOdgovora.NEISPRAVAN, ProvjeraPotpisa.provjeri(procitan, Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void referencaIzvanDokumentaSeOdbija() throws Exception {
        var doc = potpisanIPrenesen();
        var ref = (Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Reference").item(0);
        ref.setAttribute("URI", "http://127.0.0.1:9/ne-smije-se-dohvatiti");

        assertEquals(PotpisOdgovora.NEISPRAVAN, ProvjeraPotpisa.provjeri(zahtjev(doc), Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void nedopustenaTransformacijaSeOdbija() throws Exception {
        var doc = potpisanIPrenesen();
        var transform = (Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Transform").item(0);
        transform.setAttribute("Algorithm", "http://www.w3.org/TR/1999/REC-xslt-19991116");

        assertEquals(PotpisOdgovora.NEISPRAVAN, ProvjeraPotpisa.provjeri(zahtjev(doc), Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void odgovorUOblikuSluzbenogPrimjeraJeIspravan() throws Exception {
        String ns = "http://www.porezna-uprava.gov.hr/fin/2024/types/eizjestavanje";
        var doc = Xml.parsiraj("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:eiz=\"" + ns + "\">"
                + "<soapenv:Body><eiz:EvidentirajNaplatuOdgovor eiz:id=\"o1\"><eiz:Odgovor><eiz:idZahtjeva>z1</eiz:idZahtjeva>"
                + "<eiz:prihvacenZahtjev>true</eiz:prihvacenZahtjev></eiz:Odgovor></eiz:EvidentirajNaplatuOdgovor>"
                + "</soapenv:Body></soapenv:Envelope>");
        var odgovor = (Element) doc.getElementsByTagNameNS(ns, "EvidentirajNaplatuOdgovor").item(0);

        var ks = KeyStore.getInstance("PKCS12");
        try (var in = Primjeri.class.getResourceAsStream("/test-potpis.p12")) {
            ks.load(in, "test1234".toCharArray());
        }
        String alias = ks.aliases().nextElement();
        var fac = XMLSignatureFactory.getInstance("DOM");
        var c14n = fac.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null);
        var ref = fac.newReference("#o1", fac.newDigestMethod(DigestMethod.SHA256, null),
                List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null), c14n), null, null);
        var si = fac.newSignedInfo(fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                fac.newSignatureMethod(SignatureMethod.RSA_SHA1, null), List.of(ref));
        var kif = fac.getKeyInfoFactory();
        var ctx = new DOMSignContext((PrivateKey) ks.getKey(alias, "test1234".toCharArray()), odgovor);
        ctx.setIdAttributeNS(odgovor, ns, "id");
        fac.newXMLSignature(si, kif.newKeyInfo(List.of(kif.newX509Data(List.of(potpisivac.certifikat()))))).sign(ctx);
        var prenesen = Xml.parsiraj(Xml.serijaliziraj(doc));

        var el = (Element) prenesen.getElementsByTagNameNS(ns, "EvidentirajNaplatuOdgovor").item(0);
        assertEquals(PotpisOdgovora.ISPRAVAN, ProvjeraPotpisa.provjeri(el, Primjeri.sidra(potpisivac), List.of()));
    }

    @Test
    void potpisImaXadesSvojstvaISazetakCertifikata() throws Exception {
        var doc = potpisanIPrenesen();

        var potpis = (Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").item(0);
        var zahtjev = (Element) potpis.getParentNode();
        assertEquals("EvidentirajNaplatuZahtjev", zahtjev.getLocalName());
        assertEquals(potpis, zadnjiElement(zahtjev));
        var sazetak = doc.getElementsByTagNameNS("http://uri.etsi.org/01903/v1.3.2#", "CertDigest").item(0).getLastChild().getTextContent();
        assertEquals(Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(potpisivac.certifikat().getEncoded())),
                sazetak);
        String signedInfo = Xml.serijaliziraj(doc);
        assertTrue(signedInfo.contains("Type=\"http://uri.etsi.org/01903#SignedProperties\""));
        assertTrue(signedInfo.contains("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"));
    }

    private Document potpisanIPrenesen() throws Exception {
        var z = Zahtjevi.evidentirajNaplatu(List.of(new Naplata("1-P1-1", LocalDate.of(2026, 3, 1), "12345678901", "11111111119",
                LocalDate.of(2026, 3, 2), new BigDecimal("125.00"), NacinPlacanja.T)), Primjeri.SAT);
        Xades.potpisi(z.element(), z.id(), potpisivac, Clock.systemUTC());
        return Xml.parsiraj(Xml.serijaliziraj(z.dokument()));
    }

    private static Element zahtjev(Document doc) {
        return (Element) doc.getElementsByTagNameNS(Zahtjevi.NS_EIZV, "EvidentirajNaplatuZahtjev").item(0);
    }

    private static Element zadnjiElement(Element roditelj) {
        Element zadnji = null;
        for (var n = roditelj.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                zadnji = e;
            }
        }
        return zadnji;
    }
}
