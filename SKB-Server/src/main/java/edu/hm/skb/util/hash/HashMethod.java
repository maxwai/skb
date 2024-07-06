package edu.hm.skb.util.hash;

import edu.hm.skb.api.fed.FedService;
import edu.hm.skb.config.Config;
import edu.hm.skb.data.Data;
import edu.hm.skb.util.model.Field;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

/**
 * Hash Method Interface. Used to verify block integrity
 */
public interface HashMethod {

    /**
     * @return List of supported Hash Methods, ordered by priority
     */
    static List<HashMethod> getHashMethods() {
        return List.of(new SHA256());
    }

    /**
     * Make hash Code verification on remote Block
     *
     * @param remoteHostname the server where the block is saved
     * @param ownHostname    the own hostname
     * @param remoteBlockId  the remote block Id
     * @param data           the data interface instance
     * @param block          the block to check
     * @return if the hash code verification was true
     * @throws FileNotFoundException if the local hashcode couldn't be calculated
     * @throws IOException           if the files contained in the block couldn't be read
     */
    static boolean checkIntegrity(String remoteHostname, String ownHostname, String remoteBlockId,
            Data data, Config.Block block) throws FileNotFoundException, IOException {
        byte[] generatedSalt = new byte[64];
        new SecureRandom().nextBytes(generatedSalt);
        // FIXME: reduce to Hash Methods supported by server
        HashMethod hashMethod = getHashMethods().get(0);

        Field.Hash hash = FedService.getFedRestClient(remoteHostname)
                .verifyBlock(ownHostname, remoteBlockId, new Field.BlockVerify(hashMethod
                        .getHashMethodName(), Base64.getEncoder().encodeToString(generatedSalt)));

        String calculatedHash;
        calculatedHash = data.getHash(block, generatedSalt, hashMethod.getHashFunction());
        return calculatedHash.equals(hash.hash());
    }

    /**
     * @return the short name of the hashMethod (for example: SHA256)
     */
    @NotNull
    String getHashMethodName();

    /**
     * @return Function to calculate HashFunction
     */
    @NotNull
    Function<InputStream, String> getHashFunction();
}
