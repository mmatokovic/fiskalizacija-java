package dev.mmatokovic.fiskalizacija.internal.potpis;

import dev.mmatokovic.fiskalizacija.Potpisivac;
import org.w3c.dom.Element;

import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * XAdES-B potpis za Fiskalizaciju 2.0: referenca na cijeli zahtjev (enveloped, exc-c14n) i referenca na
 * {@code SignedProperties} s vremenom potpisa i SHA-256 sažetkom certifikata.
 */
public final class Xades {

    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String NS_XADES = "http://uri.etsi.org/01903/v1.3.2#";

    private Xades() {
    }

    /**
     * Dodaje {@code ds:Signature} kao zadnje dijete elementa {@code zahtjev}, kako traži shema.
     *
     * @param id  vrijednost {@code id} atributa zahtjeva, na koju pokazuje referenca potpisa
     * @param sat za vrijeme potpisa ({@code SigningTime})
     */
    public static void potpisi(Element zahtjev, String id, Potpisivac potpisivac, Clock sat) {
        var kljuc = PristupKljucu.kljuc(potpisivac);
        var certifikat = potpisivac.certifikat();
        try {
            var doc = zahtjev.getOwnerDocument();
            var fac = XMLSignatureFactory.getInstance("DOM");
            String sufiks = UUID.randomUUID().toString();
            String signatureId = "Sig-" + sufiks;
            String signedPropertiesId = "xades-" + sufiks;

            var qualifying = doc.createElementNS(NS_XADES, "xades:QualifyingProperties");
            qualifying.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xades", NS_XADES);
            qualifying.setAttribute("Target", "#" + signatureId);
            var signedProps = (Element) qualifying.appendChild(doc.createElementNS(NS_XADES, "xades:SignedProperties"));
            signedProps.setAttribute("Id", signedPropertiesId);
            var sigProps = signedProps.appendChild(doc.createElementNS(NS_XADES, "xades:SignedSignatureProperties"));
            sigProps.appendChild(doc.createElementNS(NS_XADES, "xades:SigningTime"))
                    .setTextContent(sat.instant().truncatedTo(ChronoUnit.SECONDS).toString());
            var certDigest = sigProps.appendChild(doc.createElementNS(NS_XADES, "xades:SigningCertificateV2"))
                    .appendChild(doc.createElementNS(NS_XADES, "xades:Cert"))
                    .appendChild(doc.createElementNS(NS_XADES, "xades:CertDigest"));
            var digestMethod = (Element) certDigest.appendChild(doc.createElementNS(NS_DS, "ds:DigestMethod"));
            digestMethod.setAttribute("Algorithm", DigestMethod.SHA256);
            certDigest.appendChild(doc.createElementNS(NS_DS, "ds:DigestValue")).setTextContent(
                    Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(certifikat.getEncoded())));

            var sha256 = fac.newDigestMethod(DigestMethod.SHA256, null);
            var excC14n = fac.newTransform(CanonicalizationMethod.EXCLUSIVE, (TransformParameterSpec) null);
            var zahtjevRef = fac.newReference("#" + id, sha256,
                    List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null), excC14n), null, null);
            var propsRef = fac.newReference("#" + signedPropertiesId, sha256, List.of(excC14n),
                    "http://uri.etsi.org/01903#SignedProperties", null);
            String algoritam = "EC".equals(kljuc.getAlgorithm())
                    ? "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"
                    : "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
            var signedInfo = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(algoritam, null),
                    List.of(zahtjevRef, propsRef));
            var kif = fac.getKeyInfoFactory();
            var keyInfo = kif.newKeyInfo(List.of(kif.newX509Data(List.of(certifikat))));
            var objekt = fac.newXMLObject(List.of(new DOMStructure(qualifying)), null, null, null);

            var ctx = new DOMSignContext(kljuc, zahtjev);
            ctx.setDefaultNamespacePrefix("ds");
            ctx.setIdAttributeNS(zahtjev, zahtjev.getNamespaceURI(), "id");
            ctx.setIdAttributeNS(signedProps, null, "Id");
            fac.newXMLSignature(signedInfo, keyInfo, List.of(objekt), signatureId, null).sign(ctx);
        } catch (GeneralSecurityException | javax.xml.crypto.MarshalException | javax.xml.crypto.dsig.XMLSignatureException e) {
            throw new IllegalStateException("Potpisivanje nije uspjelo: " + e.getMessage(), e);
        }
    }
}
