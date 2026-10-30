package dev.mmatokovic.fiskalizacija2;

import org.w3c.dom.Element;

import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Potpisuje zahtjeve XAdES-B potpisom: referenca na cijeli zahtjev (enveloped, exc-c14n) i referenca na
 * {@code SignedProperties} s vremenom potpisa i SHA-256 sažetkom certifikata.
 */
public final class Potpisivac {

    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String NS_XADES = "http://uri.etsi.org/01903/v1.3.2#";

    private final PrivateKey kljuc;
    private final X509Certificate certifikat;
    private final Clock sat;

    /** Za ključ iz HSM-a ili vlastitog keystorea; RSA ili EC. */
    public Potpisivac(PrivateKey kljuc, X509Certificate certifikat) {
        this(kljuc, certifikat, Clock.systemUTC());
    }

    Potpisivac(PrivateKey kljuc, X509Certificate certifikat, Clock sat) {
        this.kljuc = Objects.requireNonNull(kljuc, "kljuc");
        this.certifikat = Objects.requireNonNull(certifikat, "certifikat");
        this.sat = Objects.requireNonNull(sat, "sat");
    }

    /**
     * Učitava prvi ključ iz PKCS#12 datoteke kakvu izdaje FINA.
     *
     * @param datoteka PKCS#12 datoteka
     * @param lozinka  lozinka datoteke i ključa
     * @return potpisivač
     * @throws IllegalArgumentException ako se datoteka ne može pročitati ili ne sadrži privatni ključ
     */
    public static Potpisivac izP12(Path datoteka, char[] lozinka) {
        try (var in = Files.newInputStream(datoteka)) {
            return izP12(in, lozinka);
        } catch (IOException e) {
            throw new IllegalArgumentException("Ne mogu pročitati " + datoteka, e);
        }
    }

    /**
     * Učitava prvi ključ iz PKCS#12 sadržaja; tok se ne zatvara.
     *
     * @param in      PKCS#12 sadržaj
     * @param lozinka lozinka sadržaja i ključa
     * @return potpisivač
     * @throws IllegalArgumentException ako sadržaj nije ispravan PKCS#12 ili ne sadrži privatni ključ
     */
    public static Potpisivac izP12(InputStream in, char[] lozinka) {
        try {
            var ks = KeyStore.getInstance("PKCS12");
            ks.load(in, lozinka);
            for (String alias : Collections.list(ks.aliases())) {
                if (ks.isKeyEntry(alias)) {
                    return new Potpisivac((PrivateKey) ks.getKey(alias, lozinka), (X509Certificate) ks.getCertificate(alias));
                }
            }
            throw new IllegalArgumentException("PKCS#12 ne sadrži privatni ključ");
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Ne mogu učitati PKCS#12: " + e.getMessage(), e);
        }
    }

    public X509Certificate certifikat() {
        return certifikat;
    }

    /**
     * Dodaje {@code ds:Signature} kao zadnje dijete elementa {@code zahtjev}, kako traži shema.
     *
     * @param zahtjev element koji se potpisuje
     * @param id      vrijednost njegovog {@code id} atributa, na koju pokazuje referenca potpisa
     */
    public void potpisi(Element zahtjev, String id) {
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
