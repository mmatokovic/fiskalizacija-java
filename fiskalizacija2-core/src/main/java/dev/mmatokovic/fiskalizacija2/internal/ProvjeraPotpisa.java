package dev.mmatokovic.fiskalizacija2.internal;

import dev.mmatokovic.fiskalizacija2.PotpisOdgovora;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Provjerava potpis odgovora Porezne uprave i lanac certifikata potpisnika do zadanih CA.
 *
 * <p>Potpis mora biti dijete elementa koji se provjerava i mora pokrivati upravo taj element
 * ({@code URI="#id"} ili {@code URI=""}), inače bi se potpisani i pročitani sadržaj mogli razlikovati
 * (XML signature wrapping).
 */
public final class ProvjeraPotpisa {

    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String XADES_SIGNED_PROPERTIES = "http://uri.etsi.org/01903#SignedProperties";
    private static final int MIN_RSA_BITOVA = 2048;
    private static final int MAKS_REFERENCI = 5;

    private static final Set<String> KANONIKALIZACIJE = Set.of(
            CanonicalizationMethod.INCLUSIVE, CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS,
            CanonicalizationMethod.EXCLUSIVE, CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS,
            "http://www.w3.org/2006/12/xml-c14n11", "http://www.w3.org/2006/12/xml-c14n11#WithComments");
    private static final Set<String> TRANSFORMACIJE = union(KANONIKALIZACIJE, Set.of(Transform.ENVELOPED));
    private static final Set<String> SAZECI = Set.of(
            DigestMethod.SHA1, DigestMethod.SHA256, DigestMethod.SHA384, DigestMethod.SHA512);
    private static final Set<String> POTPISI = Set.of(
            SignatureMethod.RSA_SHA1, SignatureMethod.RSA_SHA256, SignatureMethod.RSA_SHA384, SignatureMethod.RSA_SHA512,
            SignatureMethod.ECDSA_SHA256, SignatureMethod.ECDSA_SHA384, SignatureMethod.ECDSA_SHA512);

    private ProvjeraPotpisa() {
    }

    /**
     * @param potpisani element čiji se sadržaj koristi (npr. {@code EvidentirajNaplatuOdgovor}); potpis je njegovo dijete
     */
    public static PotpisOdgovora provjeri(Element potpisani, Set<TrustAnchor> sidra, Collection<X509Certificate> posrednici) {
        return provjeri(potpisani, sidra, posrednici, null);
    }

    /**
     * @param trenutak trenutak na koji se provjerava valjanost lanca certifikata, ili {@code null} za sada
     */
    public static PotpisOdgovora provjeri(Element potpisani, Set<TrustAnchor> sidra, Collection<X509Certificate> posrednici,
            Instant trenutak) {
        var signatureEl = potpis(potpisani);
        if (signatureEl == null) {
            return PotpisOdgovora.NEDOSTAJE;
        }

        var potpisnik = new X509Certificate[1];
        var ctx = new DOMValidateContext(new KeySelector() {
            @Override
            public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext context)
                    throws KeySelectorException {
                if (keyInfo != null) {
                    for (var sadrzaj : keyInfo.getContent()) {
                        if (sadrzaj instanceof X509Data x509) {
                            for (var o : x509.getContent()) {
                                if (o instanceof X509Certificate c) {
                                    potpisnik[0] = c;
                                    return () -> c.getPublicKey();
                                }
                            }
                        }
                    }
                }
                throw new KeySelectorException("Potpis ne sadrži X509 certifikat");
            }
        }, signatureEl);
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        var fac = XMLSignatureFactory.getInstance("DOM");
        var zadani = fac.getURIDereferencer();
        ctx.setURIDereferencer((uri, c) -> {
            String u = uri.getURI();
            if (u != null && !u.isEmpty() && !u.startsWith("#")) {
                throw new URIReferenceException("Dopuštene su samo reference unutar dokumenta: " + u);
            }
            return zadani.dereference(uri, c);
        });

        String id = registrirajId(ctx, potpisani);
        var idUnutarPotpisa = new HashSet<String>();
        registrirajIdove(ctx, signatureEl, idUnutarPotpisa);
        var pojaveId = new HashMap<String, Integer>();
        prebrojiIdove(potpisani.getOwnerDocument().getDocumentElement(), pojaveId);

        try {
            XMLSignature potpis = fac.unmarshalXMLSignature(ctx);
            if (!dopusten(potpis.getSignedInfo(), id, idUnutarPotpisa, pojaveId) || !potpis.validate(ctx)) {
                return PotpisOdgovora.NEISPRAVAN;
            }
        } catch (Exception e) {
            return PotpisOdgovora.NEISPRAVAN;
        }
        if (potpisnik[0].getPublicKey() instanceof RSAPublicKey rsa && rsa.getModulus().bitLength() < MIN_RSA_BITOVA) {
            return PotpisOdgovora.NEISPRAVAN;
        }

        try {
            var cilj = new X509CertSelector();
            cilj.setCertificate(potpisnik[0]);
            var parametri = new PKIXBuilderParameters(sidra, cilj);
            parametri.setRevocationEnabled(false);
            if (trenutak != null) {
                parametri.setDate(Date.from(trenutak));
            }
            var certifikati = new ArrayList<X509Certificate>(posrednici);
            certifikati.add(potpisnik[0]);
            parametri.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(certifikati)));
            CertPathBuilder.getInstance("PKIX").build(parametri);
            return PotpisOdgovora.ISPRAVAN;
        } catch (Exception e) {
            return PotpisOdgovora.NEPOUZDAN_CERTIFIKAT;
        }
    }

    private static Element potpis(Element potpisani) {
        Element nadjen = null;
        for (var n = potpisani.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && NS_DS.equals(e.getNamespaceURI()) && "Signature".equals(e.getLocalName())) {
                if (nadjen != null) {
                    return null;
                }
                nadjen = e;
            }
        }
        return nadjen;
    }

    private static boolean dopusten(SignedInfo si, String id, Set<String> idUnutarPotpisa, Map<String, Integer> pojaveId) {
        if (!KANONIKALIZACIJE.contains(si.getCanonicalizationMethod().getAlgorithm())
                || !POTPISI.contains(si.getSignatureMethod().getAlgorithm())
                || si.getReferences().size() > MAKS_REFERENCI) {
            return false;
        }
        boolean pokrivaElement = false;
        for (Object o : si.getReferences()) {
            var ref = (Reference) o;
            if (!SAZECI.contains(ref.getDigestMethod().getAlgorithm())) {
                return false;
            }
            for (Object t : ref.getTransforms()) {
                if (!TRANSFORMACIJE.contains(((Transform) t).getAlgorithm())) {
                    return false;
                }
            }
            String uri = ref.getURI() == null ? "" : ref.getURI();
            if (uri.isEmpty()) {
                pokrivaElement = true;
                continue;
            }
            if (!uri.startsWith("#")) {
                return false;
            }
            String cilj = uri.substring(1);
            if (pojaveId.getOrDefault(cilj, 0) != 1) {
                return false;
            }
            if (cilj.equals(id)) {
                pokrivaElement = true;
            } else if (!(XADES_SIGNED_PROPERTIES.equals(ref.getType()) && idUnutarPotpisa.contains(cilj))) {
                return false;
            }
        }
        return pokrivaElement;
    }

    private static void prebrojiIdove(Element el, Map<String, Integer> pojave) {
        var atributi = el.getAttributes();
        for (int i = 0; i < atributi.getLength(); i++) {
            var a = (Attr) atributi.item(i);
            String ime = a.getLocalName() != null ? a.getLocalName() : a.getName();
            if ("id".equals(ime) || "Id".equals(ime)) {
                pojave.merge(a.getValue(), 1, Integer::sum);
            }
        }
        for (var n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element dijete) {
                prebrojiIdove(dijete, pojave);
            }
        }
    }

    private static String registrirajId(DOMValidateContext ctx, Element el) {
        var atributi = el.getAttributes();
        for (int i = 0; i < atributi.getLength(); i++) {
            var a = (Attr) atributi.item(i);
            String ime = a.getLocalName() != null ? a.getLocalName() : a.getName();
            if ("id".equals(ime) || "Id".equals(ime)) {
                ctx.setIdAttributeNS(el, a.getNamespaceURI(), ime);
                return a.getValue();
            }
        }
        return null;
    }

    private static void registrirajIdove(DOMValidateContext ctx, Element el, Set<String> idovi) {
        String id = registrirajId(ctx, el);
        if (id != null) {
            idovi.add(id);
        }
        for (var n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element dijete) {
                registrirajIdove(ctx, dijete, idovi);
            }
        }
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        var s = new HashSet<>(a);
        s.addAll(b);
        return Set.copyOf(s);
    }
}
