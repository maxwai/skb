package edu.hm.skb.api.security;

import edu.hm.skb.config.ConfigInjector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Security Service to check a SHA256 with RSA Signature against a body
 */
@ApplicationScoped
public class SecurityService {

    /**
     * The public key, cached for performance
     */
    private String publicKey;
    /**
     * The config Instance
     */
    @Inject
    /* default */ ConfigInjector config;

    /**
     * Verify a byte Array against a signature.
     *
     * @param message   The byte array of the message
     * @param signature the signature, base64 encoded
     * @return if the signature is valid
     *
     * @throws NoSuchAlgorithmException if no Provider supports RSA
     * @throws InvalidKeySpecException  if the given key specification is inappropriate for this key
     *                                  factory to produce a public key.
     * @throws InvalidKeyException      if the key is invalid
     * @throws SignatureException       if this signature object is not initialized properly, the
     *                                  passed-in signature is improperly encoded or of the wrong
     *                                  type, if this signature algorithm is unable to process the
     *                                  input data provided, etc.
     * @throws IOException              if the public key couldn't be read
     */
    public boolean verify(byte[] message, @NotNull String signature)
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException,
            SignatureException, IOException {
        final Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(getPublicKey());
        sig.update(message);
        return sig.verify(Base64.getDecoder().decode(signature));
    }

    /**
     * Get the Public Key instance
     *
     * @return the Public Key instance
     *
     * @throws NoSuchAlgorithmException if no Provider supports RSA
     * @throws InvalidKeySpecException  if the given key specification is inappropriate for this key
     *                                  factory to produce a public key.
     * @throws IOException              if the public key couldn't be read
     */
    @NotNull
    private PublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException,
            IOException {
        byte[] decoded = Base64.getDecoder().decode(getPublicKeyContent());
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(keySpec);
    }

    /**
     * Returns the content of a public key, headers removed
     *
     * @return the content of the public key
     *
     * @throws IOException if the public key couldn't be read
     */
    @NotNull
    private String getPublicKeyContent() throws IOException {
        if (publicKey == null) {
            final String filePath = config.getConfig().getClientPublicKey();

            StringBuilder stringBuilder = new StringBuilder();
            try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
            }

            publicKey = stringBuilder.toString()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
        }
        return publicKey;
    }
}
