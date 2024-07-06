package edu.hm.skb.api.security;


import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

/**
 * Filter where the Signature of the request if checked
 */
@Provider
@SkbCheckSignature
@Priority(Priorities.AUTHENTICATION)
public class SignatureCheckFilter implements ContainerRequestFilter {

    /**
     * The security Service to verify the signature
     */
    @Inject
    /* default */ SecurityService securityService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String sig = requestContext.getHeaderString("SIGNATURE");
        byte[] body = requestContext.getEntityStream().readAllBytes();

        if (sig == null) {
            throw new WebApplicationException("Missing header: SIGNATURE",
                    Response.Status.BAD_REQUEST);
        }

        try {
            if (!securityService.verify(body, sig)) {
                throw new WebApplicationException("Client signature verification failed",
                        Response.Status.UNAUTHORIZED);
            }
        } catch (SignatureException ignored) {
            throw new WebApplicationException("Client signature verification failed",
                    Response.Status.UNAUTHORIZED);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException ignored) {
            throw new WebApplicationException("Internal Key error",
                    Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            // Reposition the input stream
            requestContext.setEntityStream(new ByteArrayInputStream(body));
        }
    }
}
