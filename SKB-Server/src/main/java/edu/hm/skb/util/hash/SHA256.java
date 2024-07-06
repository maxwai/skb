package edu.hm.skb.util.hash;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

/**
 * SHA256 HashMethod
 */
public class SHA256 implements HashMethod {

    @Override
    @NotNull
    public String getHashMethodName() {
        return "SHA256";
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    @NotNull
    public Function<InputStream, String> getHashFunction() {
        return inputStream -> {
            HashingInputStream hin = new HashingInputStream(Hashing.sha256(), inputStream);
            try {
                //noinspection StatementWithEmptyBody
                while (hin.read() != -1) { // NOPMD
                }
            } catch (IOException e) {
                throw new RuntimeException(e); // NOPMD
            }
            return hin.hash().toString();
        };
    }
}
