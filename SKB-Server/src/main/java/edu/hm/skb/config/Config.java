package edu.hm.skb.config;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Following properties are checked to be present on initialisation:
 *
 * <li>
 * <ul>client public key path</ul>
 * <ul>server hostname</ul>
 * <ul>mount path</ul>
 * <ul>owner</ul>
 * <ul>block size</ul>
 * </li>
 * <p>
 * These properties are env and thus can't be deleted/changed after start up
 */
public interface Config {

    // ##### Quarkus Config value #####

    /**
     * The Password for the Keystore Object
     */
    @NotNull
    String KEYSTORE_PASSWORD = ConfigProvider.getConfig()
            .getValue("key-store-password", String.class);

    /**
     * The Keystore Object to make mTLS Requests
     */
    @NotNull
    KeyStore KEYSTORE = getKeyStore();

    /**
     * Get Keystore from application.properties
     *
     * @return the KeyStore Object
     */
    @NotNull
    private static KeyStore getKeyStore() {
        try {
            return KeyStore.getInstance(new java.io.File(ConfigProvider.getConfig()
                    .getValue("key-store", String.class)), KEYSTORE_PASSWORD.toCharArray());
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException |
                CertificateException e) {
            throw new IllegalStateException(e);
        }
    }

    // ##### ENV #####

    /**
     * The Path is verified to exist.
     *
     * @return Returns the path to the Public Key.
     */
    @NotNull
    String getClientPublicKey() throws FileNotFoundException;

    /**
     * The port and hostname is only the external access, internally, the same port is used and a
     * port mapping is needed if the port is not the same.
     *
     * @return The Hostname of this server with optional port
     */
    @NotNull
    String getHostname();

    /**
     * @return The Path where all Files and blocks are saved
     */
    @NotNull
    String getMountPath();

    /**
     * @return The name of the Owner
     */
    @NotNull
    String getOwner();

    /**
     * @return The size of a Block in bytes
     */
    @Range(from = 1, to = Long.MAX_VALUE)
    long getBlockSize();

    /**
     * @return The portion of blocks in percent to check on every healthcheck
     */
    @Range(from = 1, to = 100)
    byte getHealthCheckPercent();

    /**
     * The Env will specify the interval in the following way:
     * <br/>
     * "amount unit"
     * <br/>
     * Where unit is one of: m, h, d (minutes, hours, days)
     *
     * @return Get the healthcheck Interval in minutes
     */
    @Range(from = 1, to = Integer.MAX_VALUE)
    int getHealthCheckInterval();

    // ##### Config / Database #####

    /**
     * @return List of files saved
     */
    @NotNull
    List<File> getFiles();

    /**
     * @param id The file id
     * @return The Path to the file
     */
    @Nullable
    String getFilePath(@NotNull String id);

    /**
     * Saved the info to a new file in the database
     *
     * @param file The file information
     * @return false if a file with this id already exists
     */
    boolean addNewFile(@NotNull File file);

    /**
     * Delete a file from the database
     *
     * @param id The id of the file
     * @return false if the file didn't exist
     */
    boolean deleteFile(@NotNull String id);

    /**
     * @return List of blocks
     */
    @NotNull
    List<Block> getBlocks();

    /**
     * @param id The id of the block
     * @return The Block if it exists
     */
    @Nullable
    Block getBlock(@NotNull String id);

    /**
     * Add a new block to the database
     *
     * @param block Block data
     * @return false if a block with this id already exists
     */
    boolean addNewBlock(@NotNull Block block);

    /**
     * Delete a block from the database
     *
     * @param id The id of the block
     * @return false if the block didn't exist
     */
    boolean deleteBlock(@NotNull String id);

    /**
     * Adds a new server to the list of servers for this block
     *
     * @param id         The internal block id
     * @param hostname   The new server where the block is saved
     * @param externalId The external id of this block
     * @return false if the block didn't exist
     */
    boolean addBlockServer(@NotNull String id, @NotNull String hostname,
            @NotNull String externalId);

    /**
     * Removes a server from the list of servers for this block
     *
     * @param id       The internal block id
     * @param hostname The server to be removed
     * @return false if the block didn't exist or the server was not in the list
     */
    boolean removeBlockServer(@NotNull String id, @NotNull String hostname);

    /**
     * @return List of external blocks
     */
    @NotNull
    List<ExternalBlock> getExternalBlocks();

    /**
     * @param id The id of the block
     * @return The Block if it exists
     */
    @Nullable
    ExternalBlock getExternalBlock(@NotNull String id);

    /**
     * @param hostname The server for which to get the blocks
     * @return The list of Blocks from the specified server
     */
    @NotNull
    List<ExternalBlock> getExternalBlocks(@NotNull String hostname);

    /**
     * Add a new external block to the database
     *
     * @param block Block data
     * @return false if a block with this id already exists
     */
    boolean addNewExternalBlock(@NotNull ExternalBlock block);

    /**
     * Delete an external block from the database
     *
     * @param id The id of the block
     * @return false if the block didn't exist
     */
    boolean deleteExternalBlock(@NotNull String id);

    /**
     * @return List of known servers
     */
    @NotNull
    List<Server> getServers();

    /**
     * Will get the Server for the hostname. Will also check the futureHostname field and,
     * if found there, update the entry to the new hostname
     *
     * @param hostname The hostname
     * @return The Server object if it was found
     */
    @Nullable
    Server getServer(@NotNull String hostname);

    /**
     * Add new known server
     *
     * @param server The serverObject
     * @return false if a server with this hostname already is saved
     */
    boolean addNewServer(@NotNull Server server);

    /**
     * Delete a known server
     *
     * @param hostname the hostname of the server
     * @return false if the server was not found
     */
    boolean deleteServer(@NotNull String hostname);

    /**
     * Update all the info of a server except the hostname
     *
     * @param server The updated Server object
     * @return false if the server hostname is not found
     */
    boolean updateServer(@NotNull Server server);

    /**
     * Update the hostname of a server. The old hostname will be added to the olfHostnames List
     *
     * @param oldHostname the old hostname
     * @param newHostname the new hostname
     * @return false if the server wasn't found
     */
    boolean updateServerHostname(@NotNull String oldHostname, @NotNull String newHostname);

    /**
     * @param blockId The Block ID for which the JWT Key is wanted
     * @return The JWT Key if any is known
     */
    @Nullable
    String getJwtKey(@NotNull String blockId);

    /**
     * Put a new JWT Key for the Block into the Database
     *
     * @param key     The new JWT Key
     * @param blockId The Block ID for which the JWT Key is put in
     * @return if there was a JWT Key saved before
     */
    boolean putJwtKey(@NotNull String key, @NotNull String blockId);

    /**
     * Delete a JWT Key for the Block
     *
     * @param blockId The Block ID for which to delete the JWT Key
     */
    void deleteJwtKey(@NotNull String blockId);

    // ##### Datatypes #####

    /**
     * Representation of a file on the filesystem
     *
     * @param id   The internal UUID64 id of the file
     * @param path The path to the file
     */
    @RegisterForReflection
    record File(@NotNull String id, @NotNull String path) {
    }


    /**
     * Representation of a Block
     *
     * @param id              Internal ID of the Block
     * @param serverToId      Map from Server hostnames to external Block ids
     * @param fileToByteRange Map from file id to Bytes Range Array (start and stop, start inclusive
     *                        and stop exclusive)
     */
    @RegisterForReflection
    record Block(@NotNull String id, @NotNull Map<String, String> serverToId,
                 @NotNull List<FileRange> fileToByteRange) {
    }


    /**
     * Representation of a file range
     *
     * @param fileId the file id
     * @param start  the start index of the bytes, inclusive
     * @param stop   the stop index of the bytes, exclusive
     */
    @RegisterForReflection
    record FileRange(@NotNull String fileId, long start, long stop) {
    }


    /**
     * Representation of a block send from an external server
     *
     * @param id             Internal ID of the Block
     * @param serverHostname The hostname of the external server that send this block
     */
    @RegisterForReflection
    record ExternalBlock(@NotNull String id, @NotNull String serverHostname) {
    }


    /**
     * Representation of a Remote Server
     *
     * @param hostname       The hostname
     * @param oldHostnames   the old hostnames
     * @param isVerified     if we verified this server
     * @param healthy        if the server is currently healthy (available)
     * @param maintenance    optional maintenance window object
     * @param futureHostname optional future hostname
     * @param backupCode     The backup Code we gave the external server with which someone can
     *                       access his blocks
     */
    @RegisterForReflection
    record Server(@NotNull String hostname, @NotNull List<String> oldHostnames, boolean isVerified,
                  boolean healthy, @Nullable Maintenance maintenance,
                  @Nullable String futureHostname, @NotNull String backupCode) {
    }


    /**
     * Maintenance window representation
     *
     * @param start The start time of the maintenance window
     * @param end   The end of the maintenance window
     */
    @RegisterForReflection
    record Maintenance(@NotNull Date start, @NotNull Date end) {
    }
}
