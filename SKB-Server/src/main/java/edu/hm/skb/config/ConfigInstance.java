package edu.hm.skb.config;

import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

/**
 * Implementation of the Config Interface
 */
/* default */ class ConfigInstance implements Config {

    /**
     * Config instance
     */
    @NotNull
    public static final Config CONFIG = new ConfigInstance();

    /**
     * The hostname from the env variable
     */
    @NotNull
    private static final String HOSTNAME;
    /**
     * The Mount path from the env variable
     */
    @NotNull
    private static final String MOUNT_PATH;
    /**
     * The owner from the env variable
     */
    @NotNull
    private static final String OWNER;
    /**
     * The block size from the env variable
     */
    private static final long BLOCK_SIZE;
    /**
     * The health check percent value from the env variable
     */
    private static final byte HEALTH_CHECK_PERCENT;
    /**
     * The health check interval from the env variable
     */
    private static final int HEALTH_CHECK_INTERVAL;
    /**
     * The log instance
     */
    @NotNull
    private static final Logger LOG = Logger.getLogger(ConfigInstance.class);
    /**
     * Default value for the config file path if none is given
     */
    @NotNull
    private static final String DEFAULT_CONFIG_FILE_PATH = "./appdata/config.json";
    /**
     * The config file path
     */
    @NotNull
    private static final String CONFIG_FILE_PATH;
    /**
     * The path to the client public key
     */
    @NotNull
    private static final String CLIENT_PUBLIC_KEY;

    static {
        // CLIENT_PUBLIC_KEY
        CLIENT_PUBLIC_KEY = System.getenv("CLIENT_PUBLIC_KEY");
        if (CLIENT_PUBLIC_KEY == null || CLIENT_PUBLIC_KEY.isEmpty()) {
            throw new IllegalArgumentException(
                    "CLIENT_PUBLIC_KEY environment variable is not set or is empty");
        }
        java.io.File keyFile = new java.io.File(CLIENT_PUBLIC_KEY);
        if (!keyFile.exists() || !keyFile.canRead()) {
            throw new IllegalArgumentException(
                    "CLIENT_PUBLIC_KEY environment variable value not valid, " + "path doesn't exists or can't be read: " + CLIENT_PUBLIC_KEY);
        }

        // HOSTNAME
        HOSTNAME = System.getenv("HOSTNAME");
        if (HOSTNAME == null || HOSTNAME.isEmpty()) {
            throw new IllegalArgumentException(
                    "HOSTNAME environment variable is not set or is empty");
        }

        // MOUNT_PATH
        MOUNT_PATH = System.getenv("MOUNT_PATH");
        if (MOUNT_PATH == null || MOUNT_PATH.isEmpty()) {
            throw new IllegalArgumentException(
                    "MOUNT_PATH environment variable is not set or is empty");
        }
        java.io.File mountPath = new java.io.File(MOUNT_PATH);
        if (!mountPath.exists() || !mountPath.canRead()) {
            throw new IllegalArgumentException(
                    "MOUNT_PATH environment variable value not valid, " + "path doesn't exists or can't be read: " + MOUNT_PATH);
        }

        // OWNER
        OWNER = System.getenv("OWNER");
        if (OWNER == null || OWNER.isEmpty()) {
            throw new IllegalArgumentException("OWNER environment variable is not set or is empty");
        }

        // BLOCK_SIZE
        String tmpBlockSize = System.getenv("BLOCK_SIZE");
        if (tmpBlockSize == null || tmpBlockSize.isEmpty()) {
            throw new IllegalArgumentException(
                    "BLOCK_SIZE environment variable is not set or is empty");
        }
        try {
            BLOCK_SIZE = Long.parseLong(tmpBlockSize);
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException(
                    "BLOCK_SIZE environment variable can't be parsed to Long");
        }
        if (BLOCK_SIZE < 1000) {
            throw new IllegalArgumentException(
                    "BLOCK_SIZE environment variable needs to be at least 1000 Bytes");
        }

        // HEALTH_CHECK_PERCENT
        String tmpHealthCheckPercent = System.getenv("HEALTH_CHECK_PERCENT");
        if (tmpHealthCheckPercent == null || tmpHealthCheckPercent.isEmpty()) {
            throw new IllegalArgumentException(
                    "HEALTH_CHECK_PERCENT environment variable is not set or is empty");
        }
        try {
            HEALTH_CHECK_PERCENT = Byte.parseByte(tmpHealthCheckPercent);
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException(
                    "HEALTH_CHECK_PERCENT environment variable can't be parsed to Byte");
        }
        if (HEALTH_CHECK_PERCENT < 1 || HEALTH_CHECK_PERCENT > 100) {
            throw new IllegalArgumentException(
                    "HEALTH_CHECK_PERCENT environment variable needs to between 1 and 100");
        }

        // HEALTH_CHECK_INTERVAL
        String tmpHealthCheckInterval = System.getenv("HEALTH_CHECK_INTERVAL");
        if (tmpHealthCheckInterval == null || tmpHealthCheckInterval.isEmpty()) {
            throw new IllegalArgumentException(
                    "HEALTH_CHECK_INTERVAL environment variable is not set or is empty");
        }
        try {
            int amount = Integer.parseInt(tmpHealthCheckInterval.substring(0, tmpHealthCheckInterval
                    .length() - 1));
            String unit = tmpHealthCheckInterval.substring(tmpHealthCheckInterval.length() - 1);
            switch (unit) {
                case "m" -> HEALTH_CHECK_INTERVAL = amount;
                case "h" -> HEALTH_CHECK_INTERVAL = amount * 60;
                case "d" -> HEALTH_CHECK_INTERVAL = amount * 60 * 24;
                default -> throw new IllegalArgumentException(
                        "HEALTH_CHECK_INTERVAL environment variable needs to finish with one of these units: m,h,d");
            }
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException(
                    "HEALTH_CHECK_INTERVAL environment variable can't be parsed to int");
        }

        // CONFIG_PATH
        CONFIG_FILE_PATH = System.getenv("CONFIG_PATH") != null ?
                System.getenv("CONFIG_PATH") :
                DEFAULT_CONFIG_FILE_PATH;
        ObjectMapper mapper = new ObjectMapper();
        java.io.File configFile = new java.io.File(CONFIG_FILE_PATH);
        if (configFile.exists() && configFile.canRead()) {
            try {
                mapper.readValue(configFile, JsonSchema.class);
            } catch (IOException e) {
                throw new IllegalArgumentException("Config file is not valid", e);
            }
        } else {
            LOG.infof("%s", configFile.getAbsolutePath());
            if (configFile.getParentFile().exists() && configFile.getParentFile().canWrite()) {
                try {
                    if (configFile.createNewFile()) {
                        mapper.writeValue(configFile, new JsonSchema(Collections.emptyList(),
                                Collections.emptyList(), Collections.emptyList(), Collections
                                        .emptyList()));
                    } else {
                        throw new IllegalStateException("Couldn't create config file");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e); // NOPMD
                }
            } else {
                if (CONFIG_FILE_PATH.equals(DEFAULT_CONFIG_FILE_PATH)) {
                    throw new IllegalArgumentException(
                            "CONFIG_FILE_PATH environment variable not given and default path doesn't exist or isn't readable.");
                } else {
                    LOG.errorf("Config file does not exist or is not readable at {0}",
                            CONFIG_FILE_PATH);
                    LOG.warnf("Falling back to default config file path: {0}",
                            DEFAULT_CONFIG_FILE_PATH);
                    configFile = new java.io.File(DEFAULT_CONFIG_FILE_PATH);
                    if (configFile.exists() && configFile.canRead()) {
                        try {
                            mapper.readValue(configFile, JsonSchema.class);
                        } catch (IOException e) {
                            throw new IllegalArgumentException("Config file is not valid", e);
                        }
                    } else {
                        if (configFile.getParentFile().exists() && configFile.getParentFile()
                                .canWrite()) {
                            try {
                                if (configFile.createNewFile()) {
                                    mapper.writeValue(configFile, new JsonSchema(Collections
                                            .emptyList(), Collections.emptyList(), Collections
                                                    .emptyList(), Collections.emptyList()));
                                } else {
                                    throw new IllegalStateException("Couldn't create config file");
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);  // NOPMD
                            }
                        } else {
                            throw new IllegalArgumentException(
                                    CONFIG_FILE_PATH + " and " + DEFAULT_CONFIG_FILE_PATH + " don't exist or aren't readable.");
                        }
                    }
                }
            }
        }
        LOG.infof("Using %s", CONFIG_FILE_PATH);
    }

    /**
     * JWT Keys registered
     */
    @NotNull
    private final Map<String, String> blockIdToJwtKey = new ConcurrentHashMap<>();

    @NotNull
    private static <T> List<T> readConfig(@NotNull Function<JsonSchema, List<T>> dataExtractor) {
        ObjectMapper mapper = new ObjectMapper();
        java.io.File configFile = new java.io.File(CONFIG_FILE_PATH);
        if (!configFile.exists() || !configFile.canRead()) {
            LOG.error("Config file does not exist or is not readable at " + CONFIG_FILE_PATH, // NOPMD
                    new RuntimeException());
            return Collections.emptyList();
        }
        try {
            return dataExtractor.apply(mapper.readValue(configFile, JsonSchema.class));
        } catch (DatabindException e) {
            LOG.error("Config file not valid", e);
            return Collections.emptyList();
        } catch (IOException e) {
            LOG.error(e);
            return Collections.emptyList();
        }
    }

    private static boolean writeConfig(@NotNull Function<JsonSchema, JsonSchema> dataInserter) {
        ObjectMapper mapper = new ObjectMapper();
        java.io.File configFile = new java.io.File(CONFIG_FILE_PATH);
        if (!configFile.exists() || !configFile.canRead()) {
            LOG.error("Config file does not exist or is not readable at " + CONFIG_FILE_PATH, // NOPMD
                    new RuntimeException());
            return false;
        }
        try {
            // Read the entire config JSON object
            JsonSchema configData = mapper.readValue(configFile, JsonSchema.class);

            // Save and write the entire config JSON object to file
            mapper.writeValue(configFile, dataInserter.apply(configData));
            return true;
        } catch (IOException e) {
            LOG.error(e);
            return false;
        }
    }

    @Override
    @NotNull
    public String getClientPublicKey() {
        return CLIENT_PUBLIC_KEY;
    }

    @Override
    @NotNull
    public String getHostname() {
        return HOSTNAME;
    }

    @Override
    @NotNull
    public String getMountPath() {
        return MOUNT_PATH;
    }

    @Override
    @NotNull
    public String getOwner() {
        return OWNER;
    }

    @Override
    public long getBlockSize() {
        return BLOCK_SIZE;
    }

    @Override
    public byte getHealthCheckPercent() {
        return HEALTH_CHECK_PERCENT;
    }

    @Override
    public int getHealthCheckInterval() {
        return HEALTH_CHECK_INTERVAL;
    }

    @Override
    @NotNull
    public List<File> getFiles() {
        return readConfig(JsonSchema::files);
    }

    @Override
    @Nullable
    public String getFilePath(@NotNull String id) {
        List<File> files = getFiles();
        return files.stream()
                .filter(file -> file.id().equals(id))
                .findAny()
                .map(File::path)
                .orElse(null);
    }

    @Override
    public boolean addNewFile(@NotNull File file) {
        return writeConfig(jsonSchema -> {
            jsonSchema.files().add(file);
            return jsonSchema;
        });
    }

    @Override
    public boolean deleteFile(@NotNull String id) {
        return writeConfig(jsonSchema -> {
            jsonSchema.files().removeIf(file -> file.id().equals(id));
            return jsonSchema;
        });
    }

    @Override
    @NotNull
    public List<Block> getBlocks() {
        return readConfig(JsonSchema::blocks);
    }

    @Override
    @Nullable
    public Block getBlock(@NotNull String id) {
        List<Block> blocks = getBlocks();
        for (Block block : blocks) {
            if (block.id().equals(id)) {
                return block;
            }
        }
        return null;
    }

    @Override
    public boolean addNewBlock(@NotNull Block block) {
        return getBlock(block.id()) == null && writeConfig(jsonSchema -> {
            jsonSchema.blocks().add(block);
            return jsonSchema;
        });
    }

    @Override
    public boolean deleteBlock(@NotNull String id) {
        Block blockToRemove = getBlock(id);
        return blockToRemove != null && writeConfig(jsonSchema -> {
            jsonSchema.blocks().remove(blockToRemove);
            return jsonSchema;
        });

    }

    @Override
    public boolean addBlockServer(@NotNull String id, @NotNull String hostname,
            @NotNull String externalId) {
        return getBlocks().stream().anyMatch(block -> block.id().equals(id)) && writeConfig(
                jsonSchema -> {
                    jsonSchema.blocks()
                            .stream()
                            .filter(block -> block.id().equals(id))
                            .findAny()
                            .ifPresent(block -> block.serverToId().put(hostname, externalId));
                    return jsonSchema;
                });
    }

    @Override
    public boolean removeBlockServer(@NotNull String id, @NotNull String hostname) {
        return getBlocks().stream().anyMatch(block -> block.id().equals(id)) && writeConfig(
                jsonSchema -> {
                    jsonSchema.blocks()
                            .stream()
                            .filter(block -> block.id().equals(id))
                            .findAny()
                            .ifPresent(block -> block.serverToId().remove(hostname));
                    return jsonSchema;
                });
    }

    @Override
    @NotNull
    public List<ExternalBlock> getExternalBlocks() {
        return readConfig(JsonSchema::externalBlocks);
    }

    @Override
    @Nullable
    public ExternalBlock getExternalBlock(@NotNull String id) {
        return getExternalBlocks().stream()
                .filter(block -> block.id().equals(id))
                .findAny()
                .orElse(null);
    }

    @Override
    @NotNull
    public List<ExternalBlock> getExternalBlocks(@NotNull String hostname) {
        return getExternalBlocks().stream()
                .filter(block -> block.serverHostname().equals(hostname))
                .collect(Collectors.toList());
    }

    @Override
    public boolean addNewExternalBlock(@NotNull ExternalBlock block) {
        return getExternalBlock(block.id()) == null && writeConfig(jsonSchema -> {
            jsonSchema.externalBlocks().add(block);
            return jsonSchema;
        });
    }

    @Override
    public boolean deleteExternalBlock(@NotNull String id) {
        ExternalBlock blockToRemove = getExternalBlock(id);
        return blockToRemove != null && writeConfig(jsonSchema -> {
            jsonSchema.externalBlocks().remove(blockToRemove);
            return jsonSchema;
        });

    }

    @Override
    @NotNull
    public List<Server> getServers() {
        return readConfig(JsonSchema::servers);
    }

    @Override
    @Nullable
    public Server getServer(@NotNull String hostname) {
        List<Server> servers = getServers();
        Optional<Server> migratedServer = servers.stream()
                .filter(server -> hostname.equals(server.futureHostname()))
                .findAny();
        migratedServer.ifPresent(server -> {
            server.oldHostnames().add(server.hostname());
            assert server.futureHostname() != null;
            updateServer(new Server(server.hostname(), server.oldHostnames(), server.isVerified(),
                    server.healthy(), server.maintenance(), null, server.backupCode()));
            updateServerHostname(server.hostname(), server.futureHostname());
        });
        if (migratedServer.isPresent()) {
            servers = getServers();
        }
        return servers.stream()
                .filter(server -> server.hostname().equals(hostname))
                .findAny()
                .orElse(null);
    }

    @Override
    public boolean addNewServer(@NotNull Server server) {
        return getServer(server.hostname()) == null && writeConfig(jsonSchema -> {
            jsonSchema.servers().add(server);
            return jsonSchema;
        });
    }

    @Override
    public boolean deleteServer(@NotNull String hostname) {
        Server serverToRemove = getServer(hostname);
        return serverToRemove != null && writeConfig(jsonSchema -> {
            jsonSchema.servers().remove(serverToRemove);
            return jsonSchema;
        });

    }

    @Override
    public boolean updateServer(@NotNull Server server) {
        Server serverToUpdate = getServer(server.hostname());
        return serverToUpdate != null && writeConfig(jsonSchema -> {
            jsonSchema.servers().remove(serverToUpdate);
            jsonSchema.servers().add(server);

            return jsonSchema;
        });

    }

    @Override
    public boolean updateServerHostname(@NotNull String oldHostname, @NotNull String newHostname) {
        Server serverToUpdate = getServer(oldHostname);
        return serverToUpdate != null && writeConfig(jsonSchema -> {
            jsonSchema.servers().remove(serverToUpdate);
            List<String> oldHostnames = new ArrayList<>(serverToUpdate.oldHostnames());
            oldHostnames.add(oldHostname);
            jsonSchema.servers()
                    .add(new Server(newHostname, oldHostnames, serverToUpdate.isVerified(),
                            serverToUpdate.healthy(), serverToUpdate.maintenance(), serverToUpdate
                                    .futureHostname(), serverToUpdate.backupCode()));

            return jsonSchema;
        });

    }

    @Override
    @Nullable
    public String getJwtKey(@NotNull String blockId) {
        return blockIdToJwtKey.get(blockId);
    }

    @Override
    public boolean putJwtKey(@NotNull String key, @NotNull String blockId) {
        return blockIdToJwtKey.put(blockId, key) != null;
    }

    @Override
    public void deleteJwtKey(@NotNull String blockId) {
        blockIdToJwtKey.remove(blockId);
    }

    /**
     * Internal Class for the JSON Schema
     *
     * @param externalBlocks List containing the external Blocks
     * @param servers        List containing the servers
     * @param files          List containing the files
     * @param blocks         List containing the blocks
     */
    @RegisterForReflection
    private record JsonSchema(@NotNull List<ExternalBlock> externalBlocks,
                              @NotNull List<Server> servers, @NotNull List<File> files,
                              @NotNull List<Block> blocks) {}
}
