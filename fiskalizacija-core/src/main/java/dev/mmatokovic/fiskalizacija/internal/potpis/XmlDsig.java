package dev.mmatokovic.fiskalizacija.internal.potpis;

import dev.mmatokovic.fiskalizacija.Potpisivac;
import org.w3c.dom.Element;

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Enveloped XMLDSig potpis za fiskalizaciju u krajnjoj potrošnji (tehnička specifikacija v2.7, poglavlje 7):
 * exc-c14n, RSA-SHA256, SHA-256 sažetak i {@code KeyInfo} s certifikatom i izdavateljem.
 */
public final class XmlDsig {

    private XmlDsig() {
    }

    /** Dodaje {@code Signature} kao zadnje dijete elementa {@code zahtjev}, čiji atribut {@code Id} ima vrijednost {@code id}. */
    public static void potpisi(Element zahtjev, String id, Potpisivac potpisivac) {
        var certifikat = potpisivac.certifikat();
        try {
            var fac = XMLSignatureFactory.getInstance("DOM");
            var excC14n = fac.newTransform(CanonicalizationMethod.EXCLUSIVE, (TransformParameterSpec) null);
            var referenca = fac.newReference("#" + id, fac.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null), excC14n), null, null);
            var signedInfo = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                    List.of(referenca));
            var kif = fac.getKeyInfoFactory();
            var izdavatelj = kif.newX509IssuerSerial(certifikat.getIssuerX500Principal().getName(), certifikat.getSerialNumber());
            var keyInfo = kif.newKeyInfo(List.of(kif.newX509Data(List.of(certifikat, izdavatelj))));

            var ctx = new DOMSignContext(PristupKljucu.kljuc(potpisivac), zahtjev);
            ctx.setIdAttributeNS(zahtjev, null, "Id");
            fac.newXMLSignature(signedInfo, keyInfo).sign(ctx);
        } catch (GeneralSecurityException | MarshalException | XMLSignatureException e) {
            throw new IllegalStateException("Potpisivanje nije uspjelo: " + e.getMessage(), e);
        }
    }
}
