package edu.hm.skb.api.security;

import io.quarkus.security.credential.CertificateCredential;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The mTLS Service to check a hostname
 */
@ApplicationScoped
public class MTLSService {

    /**
     * Checks if the given hostname is given in the TLS certificate
     *
     * @param identity The identity instance containing the certificate
     * @param host     the hostname
     * @return if the hostname is verified
     */
    public boolean checkHostname(SecurityIdentity identity, String host) {
        CertificateCredential credential = identity.getCredential(CertificateCredential.class);
        if (credential == null) {
            return false;
        }
        X509Certificate certificate = credential.getCertificate();
        if (certificate == null) {
            return false;
        }
        Collection<List<?>> alternativeNames = null;
        try {
            alternativeNames = certificate.getSubjectAlternativeNames();
        } catch (CertificateParsingException ignored) {
        }
        List<String> names = new ArrayList<>();
        String mainName = certificate.getIssuerX500Principal().getName();
        if (mainName.contains("CN=")) {
            int last = mainName.indexOf(',', mainName.indexOf("CN="));
            mainName = mainName.substring(mainName.indexOf("CN=") + 3, last == -1 ?
                    mainName.length() :
                    last);
        }
        names.add(mainName);
        if (alternativeNames != null) {
            names.addAll(alternativeNames.stream()
                    .map(list -> list.get(1))
                    .filter(entry -> entry instanceof String)
                    .map(entry -> (String) entry)
                    .toList());
        }
        return names.contains(host);
    }

}
