package edu.hm.skb.util;

import edu.hm.skb.util.model.Field;
import io.quarkus.runtime.Startup;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.impl.jose.JWK;
import io.vertx.ext.auth.impl.jose.JWT;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * Util class to create and verify JWT Tokens
 */
@Startup
@ApplicationScoped
public class JwtUtil {

    /**
     * Local JWT instance to create and validate JWT Tokens
     */
    private final JWT jwt = new JWT();

    /**
     * Generates a new random RSA Key pair and registers them in the JWT Instance. Meaning that JWT
     * Tokens are only valid during one application runtime.
     */
    /* default */ JwtUtil () {
        try {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            final KeyPair pair = keyGen.generateKeyPair();

            StringBuilder sb = new StringBuilder(61).append("-----BEGIN RSA PUBLIC KEY-----\n")
                    .append(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()))
                    .append("\n-----END RSA PUBLIC KEY-----\n");
            final String pub = sb.toString();

            sb = new StringBuilder(63).append("-----BEGIN RSA PRIVATE KEY-----\n")
                    .append(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()))
                    .append("\n-----END RSA PRIVATE KEY-----\n");
            final String priv = sb.toString();

            jwt.addJWK(new JWK(new PubSecKeyOptions().setAlgorithm("RS256").setBuffer(pub)));
            jwt.addJWK(new JWK(new PubSecKeyOptions().setAlgorithm("RS256").setBuffer(priv)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Generate a JWT Token
     *
     * @param hostname         the hostname for which the hostname is made
     * @param expiresInSeconds the expiry in seconds for the JWT token
     * @return the JWT Token
     */
    public String generate(Field.Hostname hostname, int expiresInSeconds) {
        return generate(hostname, new JWTOptions().setHeader(new JsonObject().put("Bearer",
                "Bearer")).setAlgorithm("RS256").setExpiresInSeconds(expiresInSeconds));
    }

    /**
     * Generate JWT Token
     *
     * @param payload the payload of the Token
     * @param options The options of the token
     * @return the JWT Token
     */
    private String generate(Field.Hostname payload, JWTOptions options) {
        return jwt.sign(JsonObject.mapFrom(payload), options);
    }

    /**
     * Checks a JWT Token if it is valid
     *
     * @param token the JWT Token
     * @return true if the JWT Token is true
     */
    public boolean validate(String token) {
        return validateAndDecode(token) != null;
    }

    /**
     * Decode a JWT Token
     *
     * @param token the JWT Token
     * @return the JSON Object or null if the JWT Token isn't valid
     */
    private JsonObject validateAndDecode(String token) {
        try {
            final JsonObject body = jwt.decode(token);
            if (body.getLong("exp") < Instant.now().getEpochSecond()) {
                return null;
            }
            return body;
        } catch (Exception e) {
            return null;
        }
    }
}
