package edu.hm.skb.api.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Filter where the hostname is checked against the mTLS certificate.
 */
@Provider
@HostnameCheck
@Priority(Priorities.AUTHENTICATION)
public class HostnameCheckFilter implements ContainerRequestFilter {

    /**
     * The mTLS Service
     */
    @Inject
    /* default */ MTLSService mTlsService;

    /**
     * The Identity Instance
     */
    @Inject
    /* default */ SecurityIdentity identity;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String domain = requestContext.getHeaderString("domain");
        if (domain == null) {
            throw new WebApplicationException("Missing header: domain",
                    Response.Status.BAD_REQUEST);
        }
        if (domain.contains(":")) {
            domain = domain.substring(0, domain.indexOf(':'));
        }
        if (!mTlsService.checkHostname(identity, domain)) {
            throw new WebApplicationException("Client signature verification failed",
                    Response.Status.UNAUTHORIZED);
        }
    }
}
